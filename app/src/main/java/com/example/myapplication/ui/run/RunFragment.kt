package com.example.myapplication.ui.run

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.ui.home.MainActivity
import com.example.myapplication.ui.social.GroupPhotosAdapter
import com.example.myapplication.ui.social.GroupRunSummaryFragment
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.sqrt

class RunFragment : Fragment(), OnMapReadyCallback, SensorEventListener {

    private lateinit var layoutSetupGoal: View
    private lateinit var rgGoalType: RadioGroup
    private lateinit var layoutPickers: View
    private lateinit var pickerValueMain: NumberPicker
    private lateinit var pickerValueSub: NumberPicker
    private lateinit var tvSeparator: TextView
    private lateinit var tvUnit: TextView
    private lateinit var btnStartRun: MaterialButton

    private lateinit var layoutRunningStats: View
    private lateinit var tvDuration: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvCurrentGoalDisplay: TextView
    private lateinit var btnPauseRun: MaterialButton
    private lateinit var btnStopRun: MaterialButton

    private lateinit var rvRunningPhotos: RecyclerView
    private lateinit var photosAdapter: GroupPhotosAdapter
    private lateinit var btnTakePhoto: MaterialButton

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private var userMarker: Marker? = null
    private var pathPolyline: Polyline? = null
    private val pathPoints = mutableListOf<LatLng>()

    private var isRunning = false
    private var isPaused = false
    private var seconds = 0
    private var totalDistance = 0f
    private var lastLocation: Location? = null
    private var pendingLocation: Location? = null
    private var currentRunId: String? = null
    private var currentFirebaseRunKey: String? = null
    
    private var goalType: Int = -1
    private var goalValueText: String = ""
    private var goalTargetValue: Double = 0.0
    private val capturedPhotos = mutableListOf<String>()

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var stepCount = 0
    private var lastStepTime = 0L
    private val STEP_COOLDOWN = 350L // ms tra passi
    private val ACCEL_THRESHOLD = 12.0 // Soglia per rilevare il passo (magnitudo accelerazione)
    
    private var userWeight = 70.0

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
    private lateinit var viewModel: RunViewModel

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isPaused) {
                seconds++
                updateTimerText()
            }
            if (isRunning) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (locationGranted) {
            prepareAndStartRun()
        } else {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_LONG).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openCamera()
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            (result.data?.extras?.get("data") as? Bitmap)?.let { uploadPhotoToFirebase(it) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_run, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, RunViewModelFactory())[RunViewModel::class.java]
        initViews(view)
        
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        mapView = view.findViewById(R.id.mapView); mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                for (loc in res.locations) if (isRunning && !isPaused) { 
                    updateStats(loc)
                    updateMapLocation(loc)
                    sendLocationToBackend(loc)
                }
            }
        }
        fetchUserWeight()
    }

    private fun fetchUserWeight() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val users = RetrofitClient.api.searchUsers(user.uid)
                val me = users.find { it.id == user.uid }
                withContext(Dispatchers.Main) { userWeight = me?.weight?.toDouble() ?: 70.0 }
            } catch (e: Exception) { withContext(Dispatchers.Main) { userWeight = 70.0 } }
        }
    }

    private fun initViews(view: View) {
        layoutSetupGoal = view.findViewById(R.id.layoutSetupGoal); rgGoalType = view.findViewById(R.id.rgGoalType)
        layoutPickers = view.findViewById(R.id.layoutPickers); pickerValueMain = view.findViewById(R.id.pickerValueMain)
        pickerValueSub = view.findViewById(R.id.pickerValueSub); tvSeparator = view.findViewById(R.id.tvSeparator)
        tvUnit = view.findViewById(R.id.tvUnit); btnStartRun = view.findViewById(R.id.btnStartRun)
        layoutRunningStats = view.findViewById(R.id.layoutRunningStats); tvDuration = view.findViewById(R.id.tvDuration)
        tvDistance = view.findViewById(R.id.tvDistance); tvSpeed = view.findViewById(R.id.tvSpeed)
        tvCalories = view.findViewById(R.id.tvCalories); tvSteps = view.findViewById(R.id.tvSteps)
        tvCurrentGoalDisplay = view.findViewById(R.id.tvCurrentGoalDisplay); btnPauseRun = view.findViewById(R.id.btnPauseRun); btnStopRun = view.findViewById(R.id.btnStopRun)
        rvRunningPhotos = view.findViewById(R.id.rvRunningPhotos); btnTakePhoto = view.findViewById(R.id.btnTakePhoto)
        photosAdapter = GroupPhotosAdapter(capturedPhotos); rvRunningPhotos.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false); rvRunningPhotos.adapter = photosAdapter

        rgGoalType.setOnCheckedChangeListener { _, id -> layoutPickers.visibility = View.VISIBLE; updatePickerConfig(id) }
        btnStartRun.setOnClickListener { checkPermissionsAndStart() }
        btnPauseRun.setOnClickListener { togglePause() }
        btnStopRun.setOnClickListener { confirmStopRun() }
        btnTakePhoto.setOnClickListener { checkCameraPermissionAndOpen() }
        setupPickersBase()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            btnPauseRun.text = "RESUME"; btnPauseRun.setBackgroundColor(Color.parseColor("#4CAF50"))
            sensorManager.unregisterListener(this)
        } else {
            btnPauseRun.text = "PAUSE"; btnPauseRun.setBackgroundColor(Color.parseColor("#FF9800"))
            lastLocation = null; registerStepSensors()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning || isPaused) return
        
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Calcolo della magnitudo dell'accelerazione (include gravità ~9.8)
            val magnitude = sqrt((x * x + y * y + z * z).toDouble())
            
            // Log dell'accelerometro per il debug
            Log.d("RunFragment", "Accel Magnitude: $magnitude (Threshold: $ACCEL_THRESHOLD)")
            
            val currentTime = System.currentTimeMillis()
            // Se la magnitudo supera la soglia e è passato abbastanza tempo dall'ultimo passo
            if (magnitude > ACCEL_THRESHOLD && (currentTime - lastStepTime) > STEP_COOLDOWN) {
                stepCount++
                Log.d("RunFragment", "Step Detected! Total steps: $stepCount")
                lastStepTime = currentTime
                activity?.runOnUiThread { 
                    tvSteps.text = stepCount.toString() 
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun confirmStopRun() {
        AlertDialog.Builder(requireContext()).setTitle("End Run").setMessage("Are you sure?").setPositiveButton("Yes") { _, _ -> captureMapAndStop() }.setNegativeButton("No", null).show()
    }

    private fun captureMapAndStop() {
        if (googleMap == null || pathPoints.isEmpty()) { stopRun(); return }
        btnStopRun.isEnabled = false
        val boundsBuilder = LatLngBounds.Builder()
        pathPoints.forEach { boundsBuilder.include(it) }
        val bounds = boundsBuilder.build()
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        handler.postDelayed({ googleMap?.snapshot { b -> if (b != null) uploadMapSnapshot(b) { stopRun() } else stopRun() } }, 800)
    }

    private fun uploadMapSnapshot(bitmap: Bitmap, onComplete: () -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("runs/${FirebaseAuth.getInstance().currentUser?.uid}/map_${System.currentTimeMillis()}.jpg")
        val baos = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        storageRef.putBytes(baos.toByteArray()).addOnSuccessListener { it.metadata?.reference?.downloadUrl?.addOnSuccessListener { uri -> capturedPhotos.add(uri.toString()); onComplete() } ?: onComplete() }.addOnFailureListener { onComplete() }
    }

    private fun openCamera() { takePhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }

    private fun uploadPhotoToFirebase(bitmap: Bitmap) {
        val storageRef = FirebaseStorage.getInstance().reference.child("runs/${FirebaseAuth.getInstance().currentUser?.uid}/${System.currentTimeMillis()}.jpg")
        val baos = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        storageRef.putBytes(baos.toByteArray()).addOnSuccessListener { it.metadata?.reference?.downloadUrl?.addOnSuccessListener { uri -> capturedPhotos.add(uri.toString()); photosAdapter.notifyDataSetChanged() } }
    }

    private fun checkPermissionsAndStart() {
        if (rgGoalType.checkedRadioButtonId == -1) { Toast.makeText(context, "Select goal", Toast.LENGTH_SHORT).show(); return }
        if (pickerValueMain.value == 0 && (pickerValueSub.visibility == View.GONE || pickerValueSub.value == 0)) { Toast.makeText(context, "Set value", Toast.LENGTH_SHORT).show(); return }
        
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            prepareAndStartRun()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun prepareAndStartRun() {
        goalType = rgGoalType.checkedRadioButtonId
        goalValueText = when(goalType) {
            R.id.rbTime -> "${pickerValueMain.value}h ${pickerValueSub.value}m".also { goalTargetValue = (pickerValueMain.value * 3600 + pickerValueSub.value * 60).toDouble() }
            R.id.rbDistance -> "${pickerValueMain.value}.${pickerValueSub.value} km".also { goalTargetValue = pickerValueMain.value.toDouble() + (pickerValueSub.value / 100.0) }
            R.id.rbCalories -> "${pickerValueMain.value} kcal".also { goalTargetValue = pickerValueMain.value.toDouble() }
            else -> "-".also { goalTargetValue = 0.0 }
        }
        viewModel.startRun(FirebaseAuth.getInstance().currentUser?.uid ?: "unknown") { id -> if (id != null) { currentRunId = id; startRun() } else Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show() }
    }

    private fun startRun() {
        isRunning = true; isPaused = false; seconds = 0; totalDistance = 0f; lastLocation = null; stepCount = 0; pathPoints.clear(); googleMap?.clear(); userMarker = null; pathPolyline = null; capturedPhotos.clear(); photosAdapter.notifyDataSetChanged(); tvSteps.text = "0"; tvCurrentGoalDisplay.text = goalValueText; layoutSetupGoal.visibility = View.GONE; layoutRunningStats.visibility = View.VISIBLE; btnStopRun.isEnabled = true
        handler.post(timerRunnable); startLocationUpdates(); registerStepSensors()
    }

    private fun registerStepSensors() {
        sensorManager.unregisterListener(this) 
        // Usiamo solo l'accelerometro come richiesto
        accelerometer?.let { 
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) 
        }
    }

    private fun stopRun() {
        isRunning = false; sensorManager.unregisterListener(this); handler.removeCallbacks(timerRunnable); fusedLocationClient.removeLocationUpdates(locationCallback)
        val distKm = totalDistance / 1000.0
        val finalSpeed = if (seconds > 0) (distKm / (seconds / 3600.0)) else 0.0
        val cal = (0.9 * userWeight * distKm).toInt()
        val achieved = when(goalType) { R.id.rbTime -> seconds >= goalTargetValue; R.id.rbDistance -> distKm >= goalTargetValue; R.id.rbCalories -> cal >= goalTargetValue; else -> false }
        currentRunId?.let { viewModel.endRun(it, distKm) }
        saveSingleRunToHistory(distKm, finalSpeed, cal, achieved)
    }

    private fun saveSingleRunToHistory(dist: Double, speed: Double, cal: Int, achieved: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModel.saveRunToHistory(uid, dist, speed, cal, achieved, seconds, stepCount, goalValueText, capturedPhotos, listOf(ParticipantLiveStats(uid, speed, cal, dist))) { k -> currentFirebaseRunKey = k; showSummary(dist, speed, cal, achieved) }
    }

    private fun showSummary(dist: Double, speed: Double, cal: Int, achieved: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val fragment = GroupRunSummaryFragment.newInstance(listOf(ParticipantLiveStats(uid, speed, cal, dist)), capturedPhotos, emptyMap(), seconds, false, runId = currentFirebaseRunKey, goalAchieved = achieved, goalTarget = goalValueText)
        (activity as? MainActivity)?.navigateToFragment(fragment, "SUMMARY", "Run Summary")
        layoutSetupGoal.visibility = View.VISIBLE; layoutRunningStats.visibility = View.GONE
    }

    private fun updateMapLocation(loc: Location) {
        val map = googleMap ?: return
        val pos = LatLng(loc.latitude, loc.longitude)
        pathPoints.add(pos)
        if (pathPolyline == null) pathPolyline = map.addPolyline(PolylineOptions().addAll(pathPoints).color(Color.MAGENTA).width(12f))
        else pathPolyline?.points = pathPoints
        if (userMarker == null) {
            val dr = ContextCompat.getDrawable(requireContext(), R.drawable.ic_navigation_arrow)!!
            val bm = Bitmap.createBitmap(dr.intrinsicWidth, dr.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bm); dr.setBounds(0, 0, cv.width, cv.height); dr.setTint(Color.BLUE); dr.draw(cv)
            userMarker = map.addMarker(MarkerOptions().position(pos).rotation(loc.bearing).anchor(0.5f, 0.5f).flat(true).icon(BitmapDescriptorFactory.fromBitmap(bm)))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
        } else { userMarker?.position = pos; userMarker?.rotation = loc.bearing; map.animateCamera(CameraUpdateFactory.newLatLng(pos)) }
    }

    private fun sendLocationToBackend(loc: Location) { currentRunId?.let { viewModel.sendLocation(it, loc.latitude, loc.longitude) } }

    private fun updateStats(loc: Location) {
        lastLocation?.let { 
            val d = it.distanceTo(loc)
            if (d > 0.5) { 
                totalDistance += d
                val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else {
                    val time = (loc.time - it.time) / 1000.0
                    if (time > 0) (d / time * 3.6).toFloat() else 0f
                }
                tvSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", speedKmh)
            }
        }
        lastLocation = loc
        val distKm = totalDistance / 1000.0
        tvDistance.text = String.format(Locale.getDefault(), "%.2f km", distKm)
        tvCalories.text = "${(0.9 * userWeight * distKm).toInt()} kcal"
    }

    private fun updateTimerText() {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        try { fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper()) } catch (e: SecurityException) {}
    }

    private fun setupPickersBase() { 
        pickerValueMain.minValue = 0; pickerValueSub.minValue = 0 
    }

    private fun updatePickerConfig(id: Int) {
        goalType = id
        tvSeparator.visibility = if (id == R.id.rbCalories) View.GONE else View.VISIBLE
        pickerValueSub.visibility = if (id == R.id.rbCalories) View.GONE else View.VISIBLE
        when(id) {
            R.id.rbTime -> { pickerValueMain.maxValue = 23; pickerValueSub.maxValue = 59; tvUnit.text = "hours:min" }
            R.id.rbDistance -> { pickerValueMain.maxValue = 99; pickerValueSub.maxValue = 99; tvUnit.text = "km" }
            R.id.rbCalories -> { pickerValueMain.maxValue = 2000; tvUnit.text = "kcal" }
        }
    }

    override fun onMapReady(map: GoogleMap) { googleMap = map; googleMap?.uiSettings?.isZoomControlsEnabled = true }
    
    override fun onResume() { 
        super.onResume()
        mapView.onResume()
        if (isRunning && !isPaused) registerStepSensors()
    }
    
    override fun onPause() { 
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(this)
    }
    
    override fun onDestroy() { 
        super.onDestroy()
        mapView.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}
