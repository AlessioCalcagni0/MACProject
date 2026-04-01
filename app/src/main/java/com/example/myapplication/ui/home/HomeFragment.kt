package com.example.myapplication.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.ui.social.GroupRunSummaryFragment
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var tvCity: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvWeatherDesc: TextView
    private lateinit var tvRunningAdvice: TextView
    private lateinit var rvRecent: RecyclerView
    private lateinit var rvHourly: RecyclerView
    private lateinit var database: DatabaseReference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvCity = view.findViewById(R.id.tvCity)
        tvTemp = view.findViewById(R.id.tvTemp)
        tvWeatherDesc = view.findViewById(R.id.tvWeatherDesc)
        tvRunningAdvice = view.findViewById(R.id.tvRunningAdvice)
        rvRecent = view.findViewById(R.id.rvRecentActivities)
        rvHourly = view.findViewById(R.id.rvHourlyForecast)
        
        rvRecent.layoutManager = LinearLayoutManager(context)
        rvHourly.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        
        database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        
        loadRecentActivities()
        checkLocationPermission()
        return view
    }

    private fun loadRecentActivities() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database.child("users").child(userId).child("recent_activities").limitToLast(15)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<PastRunData>()
                    for (child in snapshot.children) {
                        val time = child.child("timestamp").value as? Long ?: 0L
                        // FILTRO BUG 1970: se il timestamp è 0 o mancante, saltiamo l'elemento
                        if (time <= 0L) continue

                        val runId = child.key ?: ""
                        val name = child.child("groupName").value?.toString() ?: "Corsa"
                        val type = child.child("type").value?.toString() ?: "group"
                        val coverPhoto = child.child("coverPhoto").value?.toString()
                        val duration = (child.child("durationSeconds").value as? Number)?.toInt() ?: 0
                        
                        val stats = mutableListOf<ParticipantLiveStats>()
                        child.child("stats").children.forEach { s ->
                            val uId = s.child("userId").value?.toString() ?: ""
                            val speed = (s.child("speed").value as? Number)?.toDouble() ?: 0.0
                            val calories = (s.child("calories").value as? Number)?.toInt() ?: 0
                            val dist = (s.child("distance").value as? Number)?.toDouble() ?: 0.0
                            stats.add(ParticipantLiveStats(uId, speed, calories, dist))
                        }
                        
                        val photos = mutableListOf<String>()
                        child.child("photos").children.forEach { p ->
                            p.getValue(String::class.java)?.let { photos.add(it) }
                        }
                        
                        list.add(PastRunData(runId, name, time, stats, photos, type, coverPhoto, duration))
                    }
                    rvRecent.adapter = RecentActivitiesAdapter(list.reversed()) { data -> openRunDetails(data) }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun openRunDetails(data: PastRunData) {
        val mainActivity = activity as? MainActivity ?: return
        val summaryFragment = GroupRunSummaryFragment.newInstance(
            data.stats, data.photos, emptyMap(), data.duration, 
            isGroup = (data.type == "group"), runId = data.id, coverPhoto = data.coverPhoto
        )
        mainActivity.navigateToFragment(summaryFragment, "SUMMARY_DETAIL", "Dettaglio Corsa")
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) getDeviceLocation()
        else fetchWeatherData(41.89, 12.49)
    }

    private fun getDeviceLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) fetchWeatherData(location.latitude, location.longitude)
                else fetchWeatherData(41.89, 12.49)
            }
        } catch (e: SecurityException) {}
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code,precipitation_probability&timezone=auto"
        val queue = Volley.newRequestQueue(requireContext())
        queue.add(JsonObjectRequest(Request.Method.GET, url, null, { response -> updateWeatherUI(response) }, { }))
    }

    private fun updateWeatherUI(response: JSONObject) {
        try {
            val current = response.getJSONObject("current")
            tvCity.text = "Meteo Attuale"
            tvTemp.text = "${current.getDouble("temperature_2m").toInt()}°C"
            val (description, isBadWeather) = mapWeatherCode(current.getInt("weather_code"))
            tvWeatherDesc.text = description
            setRunningAdvice(current.getDouble("temperature_2m"), isBadWeather)

            val hourly = response.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val precipProbs = hourly.getJSONArray("precipitation_probability")
            val hourlyList = mutableListOf<HourlyWeatherData>()
            val sdfInput = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = System.currentTimeMillis()

            for (i in 0 until times.length()) {
                val timeStr = times.getString(i)
                val date = sdfInput.parse(timeStr)
                if (date != null && date.time > now - 3600000 && hourlyList.size < 24) {
                    hourlyList.add(HourlyWeatherData(sdfOutput.format(date), temps.getDouble(i).toInt(), codes.getInt(i), precipProbs.getInt(i)))
                }
            }
            rvHourly.adapter = HourlyWeatherAdapter(hourlyList)
        } catch (e: Exception) {}
    }

    private fun mapWeatherCode(code: Int): Pair<String, Boolean> {
        return when (code) {
            0 -> "Cielo sereno" to false; in 1..3 -> "Parzialmente nuvoloso" to false
            in 45..48 -> "Nebbia" to false; in 51..67 -> "Pioggia" to true
            else -> "Variabile" to true
        }
    }

    private fun setRunningAdvice(temp: Double, isBadWeather: Boolean) {
        tvRunningAdvice.text = if (isBadWeather) "Tempo non ideale. Allenamento indoor? 🏠" 
        else if (temp in 10.0..22.0) "Clima perfetto per correre! 🏃‍♂️✨" 
        else "Tempo discreto per correre. Buon allenamento! 👍"
    }
}

data class HourlyWeatherData(val hour: String, val temp: Int, val weatherCode: Int, val precipitationProb: Int)

class HourlyWeatherAdapter(private val items: List<HourlyWeatherData>) : RecyclerView.Adapter<HourlyWeatherAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHour: TextView = view.findViewById(R.id.tvHour); val ivIcon: ImageView = view.findViewById(R.id.ivWeatherIcon)
        val tvTemp: TextView = view.findViewById(R.id.tvHourlyTemp); val tvPrecip: TextView = view.findViewById(R.id.tvPrecipitation)
        val rainGraph: RainGraphView = view.findViewById(R.id.viewRainGraph)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_hourly_weather, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvHour.text = item.hour; holder.tvTemp.text = "${item.temp}°"; holder.tvPrecip.text = "${item.precipitationProb}%"
        holder.ivIcon.setImageResource(if (item.weatherCode == 0) R.drawable.ic_weather_sunny else if (item.weatherCode in 1..3) R.drawable.ic_weather_cloudy else R.drawable.ic_weather_rainy)
        holder.rainGraph.setData(item.precipitationProb, if (position > 0) items[position - 1].precipitationProb else null, if (position < items.size - 1) items[position + 1].precipitationProb else null)
    }
    override fun getItemCount() = items.size
}

data class PastRunData(val id: String, val name: String, val timestamp: Long, val stats: List<ParticipantLiveStats>, val photos: List<String>, val type: String, val coverPhoto: String?, val duration: Int)

class RecentActivitiesAdapter(private val activities: List<PastRunData>, private val onClick: (PastRunData) -> Unit) : RecyclerView.Adapter<RecentActivitiesAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRecentName); val tvDate: TextView = view.findViewById(R.id.tvRecentDate); val ivCover: ImageView = view.findViewById(R.id.ivRecentCover)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_activity, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val act = activities[position]; val prefix = if (act.type == "single") "Corsa Singola" else "Corsa di Gruppo"
        holder.tvName.text = if (act.name.equals("Corsa", true) || act.name.equals(prefix, true)) prefix else "$prefix: ${act.name}"
        holder.tvDate.text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(act.timestamp))
        if (act.coverPhoto != null) Glide.with(holder.itemView.context).load(act.coverPhoto).into(holder.ivCover)
        else if (act.photos.isNotEmpty()) Glide.with(holder.itemView.context).load(act.photos[0]).into(holder.ivCover)
        else { holder.ivCover.setImageResource(R.drawable.ic_run); holder.ivCover.setColorFilter(Color.parseColor("#6200EE")) }
        holder.itemView.setOnClickListener { onClick(act) }
    }
    override fun getItemCount() = activities.size
}
