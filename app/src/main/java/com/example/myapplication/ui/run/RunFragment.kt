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
import android.net.Uri
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
import java.io.File
import java.io.FileOutputStream
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
    private var currentRunId: String? = null
    
    private var goalType: Int = -1
    private var goalValueText: String = ""
    private var goalTargetValue: Double = 0.0
    private val capturedPhotos = mutableListOf<String>()

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var stepCount = 0
    private var lastStepTime = 0L
    private val STEP_COOLDOWN = 350L
    private val ACCEL_THRESHOLD = 12.0
    private var userWeight = 70.0

    private val SENSOR_TAG = "RunStepSensor"

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var viewModel: RunViewModel

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isPaused) { seconds++; updateTimerText() }
            if (isRunning) handler.postDelayed(this, 1000)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) prepareAndStartRun()
        else Toast.makeText(context, "Location permission required", Toast.LENGTH_LONG).show()
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(
                    context,
                    "Camera permission is required to take photos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            (result.data?.extras?.get("data") as? Bitmap)?.let { processCapturedPhoto(it) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_run, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), RunViewModelFactory(requireContext()))[RunViewModel::class.java]
        initViews(view)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mapView = view.findViewById(R.id.mapView); mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                for (loc in res.locations) if (isRunning && !isPaused) { 
                    updateStats(loc); updateMapLocation(loc); currentRunId?.let { viewModel.sendLocation(it, loc.latitude, loc.longitude) }
                }
            }
        }
        fetchUserWeight()
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
        btnTakePhoto.setOnClickListener { openCamera() }
        pickerValueMain.minValue = 0; pickerValueSub.minValue = 0 
    }

    private fun confirmStopRun() {
        AlertDialog.Builder(requireContext())
            .setTitle("End Run")
            .setMessage("Are you sure you want to finish?")
            .setPositiveButton("Finish") { _, _ -> captureMapAndStop() }
            .setNegativeButton("Keep running", null)
            .show()
    }

    private fun captureMapAndStop() {
        if (!isRunning) return

        btnStopRun.isEnabled = false
        Log.d("RunFragment", "captureMapAndStop: starting finalize process")

        var isFinalized = false
        lateinit var failsafeRunnable: Runnable

        val finalizeAction = {
            if (!isFinalized) {
                isFinalized = true
                handler.removeCallbacks(failsafeRunnable)
                Log.d("RunFragment", "Finalizing run UI now")
                stopRun()
            }
        }

        failsafeRunnable = Runnable {
            Log.w("RunFragment", "Map snapshot timeout, finalizing without snapshot")
            finalizeAction()
        }

        handler.postDelayed(failsafeRunnable, 5000)

        val map = googleMap

        if (map == null || pathPoints.isEmpty()) {
            finalizeAction()
            return
        }

        try {
            val boundsBuilder = LatLngBounds.Builder()
            pathPoints.forEach { boundsBuilder.include(it) }

            val bounds = try {
                boundsBuilder.build()
            } catch (e: Exception) {
                null
            }

            if (bounds != null) {
                if (bounds.northeast == bounds.southwest) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.northeast, 16f))
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            }

            handler.postDelayed({
                if (!isFinalized) {
                    try {
                        map.snapshot { bitmap ->
                            if (bitmap != null) {
                                saveMapSnapshotLocally(bitmap) {
                                    finalizeAction()
                                }
                            } else {
                                finalizeAction()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RunFragment", "Snapshot error", e)
                        finalizeAction()
                    }
                }
            }, 500)

        } catch (e: Exception) {
            Log.e("RunFragment", "Map prep error", e)
            finalizeAction()
        }
    }

    private fun saveMapSnapshotLocally(bitmap: Bitmap, onSaved: () -> Unit) {
        val fileName = "map_snap_${System.currentTimeMillis()}.jpg"
        val file = File(requireContext().filesDir, fileName)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.close()

                withContext(Dispatchers.Main) {
                    capturedPhotos.add(0, file.absolutePath)
                    Log.d("RunFragment", "Map snapshot saved locally: ${file.absolutePath}")
                    onSaved()
                }
            } catch (e: Exception) {
                Log.e("RunFragment", "Error saving map snapshot file", e)
                withContext(Dispatchers.Main) {
                    onSaved()
                }
            }
        }
    }

    private fun stopRun() {
        if (!isRunning) return
        isRunning = false
        Log.d(
            SENSOR_TAG,
            "RUN FINISHED -> final stepCount=$stepCount, durationSeconds=$seconds, distanceMeters=$totalDistance, runId=$currentRunId"
        )
        
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        val distKm = totalDistance / 1000.0
        val finalSpeed = if (seconds > 0) (distKm / (seconds / 3600.0)) else 0.0
        val cal = (0.9 * userWeight * distKm).toInt()
        val achieved = when(goalType) { 
            R.id.rbTime -> seconds >= goalTargetValue
            R.id.rbDistance -> distKm >= goalTargetValue
            R.id.rbCalories -> cal >= goalTargetValue
            else -> false 
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
        val stats = listOf(ParticipantLiveStats(uid, finalSpeed, cal, distKm))
        
        // Pass a copy of the list to avoid concurrent modification
        val photosCopy = ArrayList(capturedPhotos)
        currentRunId?.let { 
            viewModel.endRun(it, uid, distKm, finalSpeed, cal, achieved, seconds, stepCount, goalValueText, photosCopy, stats) 
        }
        
        // Navigation is immediate now
        val fragment = GroupRunSummaryFragment.newInstance(stats, photosCopy, emptyMap(), seconds, false, runId = currentRunId, goalAchieved = achieved, goalTarget = goalValueText, pathPoints = pathPoints)
        (activity as? MainActivity)?.navigateToFragment(fragment, "SUMMARY", "Run Summary")
        
        layoutSetupGoal.visibility = View.VISIBLE
        layoutRunningStats.visibility = View.GONE
        btnStopRun.isEnabled = true
    }

    private fun fetchUserWeight() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.syncUser("Bearer fake", com.example.myapplication.data.SyncUserPayload()) 
                // Using searchUsers or sync with token is better, but here we just want weight
                withContext(Dispatchers.Main) { userWeight = response.weight ?: 70.0 }
            } catch (e: Exception) { 
                withContext(Dispatchers.Main) { userWeight = 70.0 } 
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissions.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }) prepareAndStartRun()
        else requestPermissionLauncher.launch(permissions)
    }

    private fun prepareAndStartRun() {
        goalType = rgGoalType.checkedRadioButtonId
        goalValueText = when(goalType) {
            R.id.rbTime -> "${pickerValueMain.value}h ${pickerValueSub.value}m".also { goalTargetValue = (pickerValueMain.value * 3600 + pickerValueSub.value * 60).toDouble() }
            R.id.rbDistance -> "${pickerValueMain.value}.${pickerValueSub.value} km".also { goalTargetValue = pickerValueMain.value.toDouble() + (pickerValueSub.value / 100.0) }
            R.id.rbCalories -> "${pickerValueMain.value} kcal".also { goalTargetValue = pickerValueMain.value.toDouble() }
            else -> "-".also { goalTargetValue = 0.0 }
        }
        viewModel.startRun(FirebaseAuth.getInstance().currentUser?.uid ?: "unknown") { id -> 
            currentRunId = id ?: ("off_" + System.currentTimeMillis())
            startRun()
            if (id == null) Toast.makeText(context, "Running offline - session will sync later", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRun() {
        isRunning = true
        isPaused = false

        seconds = 0
        totalDistance = 0f
        lastLocation = null

        // RESET PASSI A OGNI NUOVA CORSA
        stepCount = 0
        lastStepTime = 0L

        pathPoints.clear()
        googleMap?.clear()
        userMarker = null
        pathPolyline = null

        capturedPhotos.clear()
        photosAdapter.notifyDataSetChanged()

        tvSteps.text = "0"
        tvDistance.text = "0.00 km"
        tvSpeed.text = "0.0 km/h"
        tvCalories.text = "0 kcal"
        tvDuration.text = "00:00:00"
        tvCurrentGoalDisplay.text = goalValueText

        Log.d(
            SENSOR_TAG,
            "NEW SINGLE RUN STARTED -> steps reset to 0, lastStepTime reset, runId=$currentRunId"
        )

        layoutSetupGoal.visibility = View.GONE
        layoutRunningStats.visibility = View.VISIBLE

        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)

        startLocationUpdates()
        registerStepSensors()
    }

    private fun registerStepSensors() {
        if (accelerometer == null) {
            Log.e(SENSOR_TAG, "Accelerometer sensor NOT available on this device")
            return
        }

        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        Log.d(
            SENSOR_TAG,
            "registerStepSensors -> registered=$registered, sensorName=${accelerometer?.name}, sensorType=${accelerometer?.type}"
        )
    }

    private fun openCamera() {
        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        takePhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }
    private fun processCapturedPhoto(bitmap: Bitmap) {
        val fileName = "run_photo_${System.currentTimeMillis()}.jpg"
        val file = File(requireContext().filesDir, fileName)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.close()
                withContext(Dispatchers.Main) {
                    capturedPhotos.add(file.absolutePath)
                    photosAdapter.notifyDataSetChanged()
                    
                    // Background upload attempt
                    val storageRef = FirebaseStorage.getInstance().reference.child("runs/${FirebaseAuth.getInstance().currentUser?.uid}/$fileName")
                    storageRef.putFile(Uri.fromFile(file))
                }
            } catch (e: Exception) { Log.e("RunFragment", "Error saving photo", e) }
        }
    }

    private fun togglePause() {
        isPaused = !isPaused

        if (isPaused) {
            btnPauseRun.text = "RESUME"
            btnPauseRun.setBackgroundColor(Color.parseColor("#4CAF50"))

            sensorManager.unregisterListener(this)

            Log.d(
                SENSOR_TAG,
                "RUN PAUSED -> sensors unregistered, current steps=$stepCount"
            )
        } else {
            btnPauseRun.text = "PAUSE"
            btnPauseRun.setBackgroundColor(Color.parseColor("#FF9800"))

            lastLocation = null
            registerStepSensors()

            Log.d(
                SENSOR_TAG,
                "RUN RESUMED -> sensors registered again, current steps=$stepCount"
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (!isRunning || isPaused) {
            return
        }

        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(
            (x * x + y * y + z * z).toDouble()
        )

        val now = System.currentTimeMillis()
        val deltaFromLastStep = now - lastStepTime

        // Log non troppo frequente: circa ogni 1 secondo
        if (now % 1000 < 30) {
            Log.d(
                SENSOR_TAG,
                "ACCEL raw -> x=$x, y=$y, z=$z, magnitude=$magnitude, threshold=$ACCEL_THRESHOLD, steps=$stepCount"
            )
        }

        if (magnitude > ACCEL_THRESHOLD && deltaFromLastStep > STEP_COOLDOWN) {
            stepCount++
            lastStepTime = now

            Log.d(
                SENSOR_TAG,
                "STEP DETECTED -> stepCount=$stepCount, magnitude=$magnitude, deltaMs=$deltaFromLastStep, runId=$currentRunId"
            )

            activity?.runOnUiThread {
                tvSteps.text = stepCount.toString()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateMapLocation(loc: Location) {
        val pos = LatLng(loc.latitude, loc.longitude); pathPoints.add(pos)
        if (pathPolyline == null) pathPolyline = googleMap?.addPolyline(PolylineOptions().addAll(pathPoints).color(Color.MAGENTA).width(12f))
        else pathPolyline?.points = pathPoints
        if (userMarker == null) {
            val dr = ContextCompat.getDrawable(requireContext(), R.drawable.ic_navigation_arrow)!!
            val bm = Bitmap.createBitmap(dr.intrinsicWidth, dr.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bm); dr.setBounds(0, 0, cv.width, cv.height); dr.setTint(Color.BLUE); dr.draw(cv)
            userMarker = googleMap?.addMarker(MarkerOptions().position(pos).rotation(loc.bearing).anchor(0.5f, 0.5f).flat(true).icon(BitmapDescriptorFactory.fromBitmap(bm)))
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
        } else { userMarker?.position = pos; userMarker?.rotation = loc.bearing; googleMap?.animateCamera(CameraUpdateFactory.newLatLng(pos)) }
    }

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

    private fun updatePickerConfig(id: Int) {
        tvSeparator.visibility = if (id == R.id.rbCalories) View.GONE else View.VISIBLE
        pickerValueSub.visibility = if (id == R.id.rbCalories) View.GONE else View.VISIBLE
        when(id) {
            R.id.rbTime -> { pickerValueMain.maxValue = 23; pickerValueSub.maxValue = 59; tvUnit.text = "hours:min" }
            R.id.rbDistance -> { pickerValueMain.maxValue = 99; pickerValueSub.maxValue = 99; tvUnit.text = "km" }
            R.id.rbCalories -> { pickerValueMain.maxValue = 2000; tvUnit.text = "kcal" }
        }
    }

    override fun onMapReady(map: GoogleMap) { googleMap = map; googleMap?.uiSettings?.isZoomControlsEnabled = true }
    override fun onResume() { super.onResume(); mapView.onResume(); if (isRunning && !isPaused) registerStepSensors() }
    override fun onPause() { super.onPause(); mapView.onPause(); sensorManager.unregisterListener(this) }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy(); sensorManager.unregisterListener(this) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}
