package com.example.myapplication.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.data.WeatherResponse
import com.example.myapplication.domain.home.PastRunData
import com.example.myapplication.ui.social.GroupRunSummaryFragment
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var tvCity: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvWeatherDesc: TextView
    private lateinit var tvRunningAdvice: TextView
    private lateinit var rvRecent: RecyclerView
    private lateinit var rvHourly: RecyclerView
    
    private lateinit var viewModel: HomeViewModel

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
        
        val factory = HomeViewModelFactory()
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
        
        observeUiState()
        checkLocationPermission()
        
        return view
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is HomeUiState.Loading -> { }
                    is HomeUiState.Success -> {
                        updateWeatherUI(state)
                        rvRecent.adapter = RecentActivitiesAdapter(state.activities) { data -> openRunDetails(data) }
                    }
                    is HomeUiState.Error -> { }
                }
            }
        }
    }

    private fun updateWeatherUI(state: HomeUiState.Success) {
        val response = state.weather
        val current = response.current
        tvCity.text = "Current Weather"
        tvTemp.text = "${current.temperature_2m.toInt()}°C"
        tvWeatherDesc.text = state.weatherDescription
        tvRunningAdvice.text = state.runningAdvice

        val hourly = response.hourly
        val times = hourly.time
        val temps = hourly.temperature_2m
        val codes = hourly.weather_code
        val precipProbs = hourly.precipitation_probability
        val hourlyList = mutableListOf<HourlyWeatherData>()
        val sdfInput = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()

        for (i in times.indices) {
            val timeStr = times[i]
            val date = sdfInput.parse(timeStr)
            if (date != null && date.time > now - 3600000 && hourlyList.size < 24) {
                hourlyList.add(HourlyWeatherData(sdfOutput.format(date), temps[i].toInt(), codes[i], precipProbs[i]))
            }
        }
        rvHourly.adapter = HourlyWeatherAdapter(hourlyList)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getDeviceLocation()
        } else {
            triggerDataLoad(41.89, 12.49) // Default Rome
        }
    }

    private fun getDeviceLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) triggerDataLoad(location.latitude, location.longitude)
                else triggerDataLoad(41.89, 12.49)
            }
        } catch (e: SecurityException) {
            triggerDataLoad(41.89, 12.49)
        }
    }

    private fun triggerDataLoad(lat: Double, lon: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModel.loadData(userId, lat, lon)
    }

    private fun openRunDetails(data: PastRunData) {
        val mainActivity = activity as? MainActivity ?: return
        // PASSAGGIO DELLA MAPPA DEI NOMI SALVATA (participantNames)
        val summaryFragment = GroupRunSummaryFragment.newInstance(
            data.stats, data.photos, data.participantNames, data.duration,
            isGroup = (data.type == "group"), runId = data.id, coverPhoto = data.coverPhoto
        )
        mainActivity.navigateToFragment(summaryFragment, "SUMMARY_DETAIL", "Run Details")
    }
}

data class HourlyWeatherData(val hour: String, val temp: Int, val weatherCode: Int, val precipitationProb: Int)

class HourlyWeatherAdapter(private val items: List<HourlyWeatherData>) : RecyclerView.Adapter<HourlyWeatherAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHour: TextView = view.findViewById(R.id.tvHour)
        val ivIcon: ImageView = view.findViewById(R.id.ivWeatherIcon)
        val tvTemp: TextView = view.findViewById(R.id.tvHourlyTemp)
        val tvPrecip: TextView = view.findViewById(R.id.tvPrecipitation)
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

class RecentActivitiesAdapter(private val activities: List<PastRunData>, private val onClick: (PastRunData) -> Unit) : RecyclerView.Adapter<RecentActivitiesAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRecentName); val tvDate: TextView = view.findViewById(R.id.tvRecentDate); val ivCover: ImageView = view.findViewById(R.id.ivRecentCover)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_activity, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val act = activities[position]; val prefix = if (act.type == "single") "Single Run" else "Group Run"
        holder.tvName.text = if (act.name.equals("Run", true) || act.name.equals(prefix, true)) prefix else "$prefix: ${act.name}"
        holder.tvDate.text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(act.timestamp))
        if (act.coverPhoto != null) Glide.with(holder.itemView.context).load(act.coverPhoto).into(holder.ivCover)
        else if (act.photos.isNotEmpty()) Glide.with(holder.itemView.context).load(act.photos[0]).into(holder.ivCover)
        else { holder.ivCover.setImageResource(R.drawable.ic_run); holder.ivCover.setColorFilter(Color.parseColor("#6200EE")) }
        holder.itemView.setOnClickListener { onClick(act) }
    }
    override fun getItemCount() = activities.size
}
