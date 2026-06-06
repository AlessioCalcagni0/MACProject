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
import com.example.myapplication.domain.home.PastRunData
import com.example.myapplication.ui.social.GroupRunSummaryFragment
import com.example.myapplication.utils.NetworkMonitor
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
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
    private lateinit var networkMonitor: NetworkMonitor

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
        
        val factory = HomeViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
        networkMonitor = NetworkMonitor(requireContext())
        
        observeUiState()
        observeNetwork()
        checkLocationPermission()
        
        return view
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is HomeUiState.Loading -> {
                        // Opcionale: mostra uno spinner o uno stato di caricamento
                    }
                    is HomeUiState.Success -> {
                        updateWeatherUI(state.weather, state.weatherDescription, state.runningAdvice)
                        rvRecent.adapter = RecentActivitiesAdapter(state.activities) { data -> openRunDetails(data) }
                    }
                    is HomeUiState.PartialSuccess -> {
                        // Mostriamo le attività anche se il meteo è ancora in caricamento
                        rvRecent.adapter = RecentActivitiesAdapter(state.activities) { data -> openRunDetails(data) }
                        tvCity.text = "Loading weather..."
                    }
                    is HomeUiState.Error -> {
                        tvCity.text = "Error loading data"
                        tvWeatherDesc.text = state.message
                    }
                }
            }
        }
    }

    private fun observeNetwork() {
        viewLifecycleOwner.lifecycleScope.launch {
            networkMonitor.isConnected.collectLatest { isConnected ->
                if (isConnected) {
                    viewModel.retryConnection()
                }
            }
        }
    }

    private fun updateWeatherUI(response: com.example.myapplication.data.WeatherResponse, description: String, advice: String) {
        tvCity.text = "Current Weather"
        tvTemp.text = "${response.current.temperature_2m.toInt()}°C"
        tvWeatherDesc.text = description
        tvRunningAdvice.text = advice

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
            try {
                val date = sdfInput.parse(times[i])
                if (date != null && date.time > now - 3600000 && hourlyList.size < 24) {
                    hourlyList.add(HourlyWeatherData(
                        sdfOutput.format(date), 
                        temps[i].toInt(), 
                        codes[i], 
                        precipProbs.getOrElse(i) { 0 }
                    ))
                }
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }
        rvHourly.adapter = HourlyWeatherAdapter(hourlyList)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getDeviceLocation()
        } else {
            triggerDataLoad(41.89, 12.49)
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

        val imageSource = when {
            !act.coverPhoto.isNullOrBlank() -> act.coverPhoto
            act.photos.isNotEmpty() -> act.photos[0]
            else -> null
        }

        if (imageSource != null) {
            val glideSource = if (imageSource.startsWith("http")) {
                imageSource
            } else {
                java.io.File(imageSource)
            }

            holder.ivCover.clearColorFilter()

            Glide.with(holder.itemView.context)
                .load(glideSource)
                .placeholder(R.drawable.ic_run)
                .error(R.drawable.ic_run)
                .into(holder.ivCover)
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_run)
            holder.ivCover.setColorFilter(Color.parseColor("#6200EE"))
        }
        
        if (imageSource != null) {
            Glide.with(holder.itemView.context)
                .load(imageSource)
                .placeholder(R.drawable.ic_run)
                .into(holder.ivCover)
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_run)
            holder.ivCover.setColorFilter(Color.parseColor("#6200EE"))
        }

        holder.itemView.setOnClickListener { onClick(act) }
    }
    override fun getItemCount() = activities.size
}
