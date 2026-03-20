package com.example.myapplication.ui.run

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import java.util.Locale

class RunFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val TAG = "RunFragment"
    }

    // Views per il setup
    private lateinit var layoutSetupGoal: View
    private lateinit var rgGoalType: RadioGroup
    private lateinit var layoutPickers: View
    private lateinit var pickerValueMain: NumberPicker
    private lateinit var pickerValueSub: NumberPicker
    private lateinit var tvSeparator: TextView
    private lateinit var tvUnit: TextView
    private lateinit var btnStartRun: MaterialButton

    // Views per le statistiche
    private lateinit var layoutRunningStats: View
    private lateinit var tvDuration: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvCurrentGoalDisplay: TextView
    private lateinit var btnStopRun: MaterialButton

    // Mappa
    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private var userMarker: Marker? = null
    private var pathPolyline: Polyline? = null
    private val pathPoints = mutableListOf<LatLng>()

    // Stato corsa
    private var isRunning = false
    private var seconds = 0
    private var totalDistance = 0f
    private var lastLocation: Location? = null
    
    // Obiettivo
    private var goalType: Int = -1
    private var targetValue: Float = 0f // In secondi, metri o kcal
    private var goalReached = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                seconds++
                updateTimerText()
                checkGoalProgress()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startRun()
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
        
        // Inizializzazione views Setup
        layoutSetupGoal = view.findViewById(R.id.layoutSetupGoal)
        rgGoalType = view.findViewById(R.id.rgGoalType)
        layoutPickers = view.findViewById(R.id.layoutPickers)
        pickerValueMain = view.findViewById(R.id.pickerValueMain)
        pickerValueSub = view.findViewById(R.id.pickerValueSub)
        tvSeparator = view.findViewById(R.id.tvSeparator)
        tvUnit = view.findViewById(R.id.tvUnit)
        btnStartRun = view.findViewById(R.id.btnStartRun)

        // Inizializzazione views Stats
        layoutRunningStats = view.findViewById(R.id.layoutRunningStats)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvDistance = view.findViewById(R.id.tvDistance)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        tvCurrentGoalDisplay = view.findViewById(R.id.tvCurrentGoalDisplay)
        btnStopRun = view.findViewById(R.id.btnStopRun)

        // Inizializzazione MapView
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (isRunning) {
                        updateStats(location)
                        updateMapLocation(location)
                        checkGoalProgress()
                    }
                }
            }
        }

        setupPickersBase()

        rgGoalType.setOnCheckedChangeListener { _, checkedId ->
            layoutPickers.visibility = View.VISIBLE
            updatePickerConfig(checkedId)
        }

        btnStartRun.setOnClickListener {
            checkPermissionsAndStart()
        }

        btnStopRun.setOnClickListener {
            stopRun()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
        }
    }

    private fun updateMapLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        pathPoints.add(latLng)
        
        // Aggiorna il percorso (Polyline)
        if (pathPolyline == null) {
            val polylineOptions = PolylineOptions()
                .addAll(pathPoints)
                .color(Color.parseColor("#6200EE"))
                .width(12f)
                .jointType(JointType.ROUND)
                .startCap(RoundCap())
                .endCap(RoundCap())
            pathPolyline = googleMap?.addPolyline(polylineOptions)
        } else {
            pathPolyline?.points = pathPoints
        }

        // Aggiorna il cursore a freccia
        if (userMarker == null) {
            val markerOptions = MarkerOptions()
                .position(latLng)
                .rotation(location.bearing)
                .anchor(0.5f, 0.5f)
                .flat(true)
                .icon(getBitmapDescriptorFromVector(requireContext(), R.drawable.ic_navigation_arrow))
            userMarker = googleMap?.addMarker(markerOptions)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        } else {
            userMarker?.position = latLng
            userMarker?.rotation = location.bearing
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }
    }

    private fun getBitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
        vectorDrawable!!.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun setupPickersBase() {
        pickerValueMain.minValue = 0
        pickerValueSub.minValue = 0
    }

    private fun updatePickerConfig(checkedId: Int) {
        pickerValueSub.visibility = View.VISIBLE
        tvSeparator.visibility = View.VISIBLE
        goalType = checkedId

        when (checkedId) {
            R.id.rbTime -> {
                pickerValueMain.maxValue = 23
                pickerValueMain.value = 0
                pickerValueSub.maxValue = 59
                pickerValueSub.value = 30
                tvSeparator.text = ":"
                tvUnit.text = "ore:min"
            }
            R.id.rbDistance -> {
                pickerValueMain.maxValue = 99
                pickerValueMain.value = 5
                pickerValueSub.maxValue = 99
                pickerValueSub.value = 0
                tvSeparator.text = "."
                tvUnit.text = "km"
            }
            R.id.rbCalories -> {
                pickerValueMain.maxValue = 2000
                pickerValueMain.value = 300
                pickerValueSub.visibility = View.GONE
                tvSeparator.visibility = View.GONE
                tvUnit.text = "kcal"
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (rgGoalType.checkedRadioButtonId == -1) {
            Toast.makeText(context, "Seleziona un obiettivo", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Calcolo e visualizzazione obiettivo
        when (goalType) {
            R.id.rbTime -> {
                val totalSeconds = (pickerValueMain.value * 3600) + (pickerValueSub.value * 60)
                targetValue = totalSeconds.toFloat()
                tvCurrentGoalDisplay.text = String.format(Locale.getDefault(), "Obiettivo: %02d:%02d:00", pickerValueMain.value, pickerValueSub.value)
            }
            R.id.rbDistance -> {
                val totalMeters = (pickerValueMain.value * 1000) + (pickerValueSub.value * 10)
                targetValue = totalMeters.toFloat()
                tvCurrentGoalDisplay.text = String.format(Locale.getDefault(), "Obiettivo: %d.%02d km", pickerValueMain.value, pickerValueSub.value)
            }
            R.id.rbCalories -> {
                targetValue = pickerValueMain.value.toFloat()
                tvCurrentGoalDisplay.text = String.format(Locale.getDefault(), "Obiettivo: %d kcal", pickerValueMain.value)
            }
        }

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
        isRunning = true
        seconds = 0
        totalDistance = 0f
        lastLocation = null
        goalReached = false
        userMarker = null
        pathPolyline = null
        pathPoints.clear()
        googleMap?.clear()
        
        // Reset stile visualizzazione obiettivo
        tvCurrentGoalDisplay.setTextColor(Color.parseColor("#6200EE"))
        tvCurrentGoalDisplay.scaleX = 1f
        tvCurrentGoalDisplay.scaleY = 1f
        
        layoutSetupGoal.visibility = View.GONE
        layoutRunningStats.visibility = View.VISIBLE
        
        updateTimerText()
        handler.post(timerRunnable)
        startLocationUpdates()
    }

    private fun checkGoalProgress() {
        if (goalReached) return

        var progress = 0f
        when (goalType) {
            R.id.rbTime -> progress = seconds.toFloat()
            R.id.rbDistance -> progress = totalDistance
            R.id.rbCalories -> progress = (totalDistance * 0.06f) // Stima kcal
        }

        if (progress >= targetValue && targetValue > 0) {
            onGoalReached()
        }
    }

    private fun onGoalReached() {
        goalReached = true
        tvCurrentGoalDisplay.text = "OBIETTIVO RAGGIUNTO! 🎉"
        tvCurrentGoalDisplay.setTextColor(Color.parseColor("#4CAF50"))
        
        // Animazione pulsazione
        val scaleX = ObjectAnimator.ofFloat(tvCurrentGoalDisplay, "scaleX", 1f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(tvCurrentGoalDisplay, "scaleY", 1f, 1.3f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 600
            start()
        }
        
        Log.d(TAG, "Obiettivo raggiunto!")
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        }
    }

    private fun stopRun() {
        isRunning = false
        layoutSetupGoal.visibility = View.VISIBLE
        layoutRunningStats.visibility = View.GONE
        rgGoalType.clearCheck()
        layoutPickers.visibility = View.GONE
        
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateStats(location: Location) {
        val speedKmH = location.speed * 3.6f
        tvSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", speedKmH)
        
        lastLocation?.let {
            val distanceIncrement = it.distanceTo(location)
            if (distanceIncrement > 1.0) {
                totalDistance += distanceIncrement
            }
        }
        lastLocation = location
        
        val distanceKm = totalDistance / 1000f
        tvDistance.text = String.format(Locale.getDefault(), "%.2f km", distanceKm)
    }

    private fun updateTimerText() {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRunning) stopRun()
    }
}
