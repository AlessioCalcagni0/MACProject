package com.example.myapplication.ui.run

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import java.util.Locale

class RunFragment : Fragment() {

    companion object {
        private const val TAG = "RunFragment"
    }

    private var tvDuration: TextView? = null
    private var tvDistance: TextView? = null
    private var tvSpeed: TextView? = null
    private var btnStartRun: MaterialButton? = null

    private var isRunning = false
    private var seconds = 0
    private var totalDistance = 0f
    private var lastLocation: Location? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                seconds++
                updateTimerText()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
        } else {
            Toast.makeText(context, "Permesso posizione negato", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_run, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvDuration = view.findViewById(R.id.tvDuration)
        tvDistance = view.findViewById(R.id.tvDistance)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        btnStartRun = view.findViewById(R.id.btnStartRun)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (isRunning) {
                        updateStats(location)
                    }
                }
            }
        }

        btnStartRun?.setOnClickListener {
            if (isRunning) {
                stopRun()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                startRun()
            }
            else -> {
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun startRun() {
        Log.d(TAG, "Corsa iniziata - GPS attivo")
        isRunning = true
        seconds = 0
        totalDistance = 0f
        lastLocation = null
        
        btnStartRun?.text = "Ferma Corsa"
        btnStartRun?.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
        
        updateTimerText()
        handler.post(timerRunnable)
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(1000)
            .setMaxUpdateDelayMillis(3000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }

    private fun stopRun() {
        Log.d(TAG, "Corsa fermata")
        isRunning = false
        btnStartRun?.text = "Inizia Corsa"
        btnStartRun?.setBackgroundColor(resources.getColor(R.color.purple_500, null))
        
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateStats(location: Location) {
        // Velocità attuale (convertita da m/s a km/h)
        val speedKmH = location.speed * 3.6f
        tvSpeed?.text = String.format(Locale.getDefault(), "%.1f", speedKmH)
        
        // Calcolo Distanza
        lastLocation?.let {
            val distanceIncrement = it.distanceTo(location)
            if (distanceIncrement > 1.0) { // Filtro per piccoli errori GPS
                totalDistance += distanceIncrement
            }
        }
        lastLocation = location
        
        val distanceKm = totalDistance / 1000f
        tvDistance?.text = String.format(Locale.getDefault(), "%.2f", distanceKm)
        
        // Log di debug in console
        Log.d(TAG, "Update - Speed: $speedKmH km/h, Distance: $distanceKm km, Accuracy: ${location.accuracy}m")
    }

    private fun updateTimerText() {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        tvDuration?.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopRun()
        tvDuration = null
        tvDistance = null
        tvSpeed = null
        btnStartRun = null
    }
}
