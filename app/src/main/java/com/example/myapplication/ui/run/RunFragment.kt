package com.example.myapplication.ui.run

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.RetrofitClient
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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

class RunFragment : Fragment(), OnMapReadyCallback {

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
    private var goalTargetValue: Double = 0.0 // Distanza in km, Tempo in sec, o Calorie
    private val capturedPhotos = mutableListOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            prepareAndStartRun()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                uploadPhotoToFirebase(it)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_run, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (isRunning && !isPaused) {
                        updateStats(location)
                        updateMapLocation(location)
                        sendLocationToBackend(location)
                    }
                }
            }
        }
    }

    private fun initViews(view: View) {
        layoutSetupGoal = view.findViewById(R.id.layoutSetupGoal)
        rgGoalType = view.findViewById(R.id.rgGoalType)
        layoutPickers = view.findViewById(R.id.layoutPickers)
        pickerValueMain = view.findViewById(R.id.pickerValueMain)
        pickerValueSub = view.findViewById(R.id.pickerValueSub)
        tvSeparator = view.findViewById(R.id.tvSeparator)
        tvUnit = view.findViewById(R.id.tvUnit)
        btnStartRun = view.findViewById(R.id.btnStartRun)
        
        layoutRunningStats = view.findViewById(R.id.layoutRunningStats)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvDistance = view.findViewById(R.id.tvDistance)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        tvCalories = view.findViewById(R.id.tvCalories)
        tvCurrentGoalDisplay = view.findViewById(R.id.tvCurrentGoalDisplay)
        btnPauseRun = view.findViewById(R.id.btnPauseRun)
        btnStopRun = view.findViewById(R.id.btnStopRun)
        
        rvRunningPhotos = view.findViewById(R.id.rvRunningPhotos)
        btnTakePhoto = view.findViewById(R.id.btnTakePhoto)

        photosAdapter = GroupPhotosAdapter(capturedPhotos)
        rvRunningPhotos.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvRunningPhotos.adapter = photosAdapter

        rgGoalType.setOnCheckedChangeListener { _, checkedId ->
            layoutPickers.visibility = View.VISIBLE
            updatePickerConfig(checkedId)
        }
        btnStartRun.setOnClickListener { checkPermissionsAndStart() }
        btnPauseRun.setOnClickListener { togglePause() }
        btnStopRun.setOnClickListener { confirmStopRun() }
        btnTakePhoto.setOnClickListener { openCamera() }
        setupPickersBase()
    }

    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            btnPauseRun.text = "RIPRENDI"
            btnPauseRun.setBackgroundColor(Color.parseColor("#4CAF50"))
        } else {
            btnPauseRun.text = "PAUSA"
            btnPauseRun.setBackgroundColor(Color.parseColor("#FF9800"))
            lastLocation = null
        }
    }

    private fun confirmStopRun() {
        AlertDialog.Builder(requireContext())
            .setTitle("Termina Corsa")
            .setMessage("Sei sicuro di voler fermare la corsa?")
            .setPositiveButton("Sì") { _, _ -> captureMapAndStop() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun captureMapAndStop() {
        if (googleMap == null || pathPoints.isEmpty()) {
            stopRun()
            return
        }

        btnStopRun.isEnabled = false
        Toast.makeText(context, "Salvataggio percorso...", Toast.LENGTH_SHORT).show()

        val boundsBuilder = LatLngBounds.Builder()
        for (point in pathPoints) boundsBuilder.include(point)
        val bounds = boundsBuilder.build()
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

        handler.postDelayed({
            googleMap?.snapshot { bitmap ->
                if (bitmap != null) {
                    uploadMapSnapshot(bitmap) {
                        stopRun()
                    }
                } else {
                    stopRun()
                }
            }
        }, 800)
    }

    private fun uploadMapSnapshot(bitmap: Bitmap, onComplete: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference
            .child("runs/$userId/map_${System.currentTimeMillis()}.jpg")
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val data = baos.toByteArray()

        storageRef.putBytes(data).addOnSuccessListener { taskSnapshot ->
            taskSnapshot.metadata?.reference?.downloadUrl?.addOnSuccessListener { uri ->
                capturedPhotos.add(uri.toString())
                onComplete()
            }?.addOnFailureListener { onComplete() }
        }.addOnFailureListener { onComplete() }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePhotoLauncher.launch(intent)
    }

    private fun uploadPhotoToFirebase(bitmap: Bitmap) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference
            .child("runs/$userId/${System.currentTimeMillis()}.jpg")
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val data = baos.toByteArray()

        storageRef.putBytes(data).addOnSuccessListener { taskSnapshot ->
            taskSnapshot.metadata?.reference?.downloadUrl?.addOnSuccessListener { uri ->
                capturedPhotos.add(uri.toString())
                photosAdapter.notifyDataSetChanged()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Errore caricamento foto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        if (rgGoalType.checkedRadioButtonId == -1) {
            Toast.makeText(context, "Seleziona un obiettivo", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (pickerValueMain.value == 0 && (pickerValueSub.visibility == View.GONE || pickerValueSub.value == 0)) {
            Toast.makeText(context, "Imposta un valore maggiore di zero", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            prepareAndStartRun()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun prepareAndStartRun() {
        goalType = rgGoalType.checkedRadioButtonId
        goalValueText = when(goalType) {
            R.id.rbTime -> {
                goalTargetValue = (pickerValueMain.value * 3600 + pickerValueSub.value * 60).toDouble()
                "${pickerValueMain.value}h ${pickerValueSub.value}m"
            }
            R.id.rbDistance -> {
                goalTargetValue = pickerValueMain.value.toDouble() + (pickerValueSub.value / 100.0)
                "${pickerValueMain.value}.${pickerValueSub.value} km"
            }
            R.id.rbCalories -> {
                goalTargetValue = pickerValueMain.value.toDouble()
                "${pickerValueMain.value} kcal"
            }
            else -> {
                goalTargetValue = 0.0
                "-"
            }
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = RetrofitClient.api.startRun(userId)
                currentRunId = res.run_id
                handler.post { startRun() }
            } catch (e: Exception) {
                handler.post { Toast.makeText(context, "Errore server", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun startRun() {
        isRunning = true
        isPaused = false
        seconds = 0
        totalDistance = 0f
        lastLocation = null
        pathPoints.clear()
        googleMap?.clear()
        capturedPhotos.clear()
        photosAdapter.notifyDataSetChanged()
        
        tvCurrentGoalDisplay.text = goalValueText
        layoutSetupGoal.visibility = View.GONE
        layoutRunningStats.visibility = View.VISIBLE
        btnStopRun.isEnabled = true
        handler.post(timerRunnable)
        startLocationUpdates()
    }

    private fun stopRun() {
        isRunning = false
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val distanceKm = totalDistance / 1000.0
        val finalSpeed = if (seconds > 0) (distanceKm / (seconds / 3600.0)) else 0.0
        val calories = (seconds * 0.1).toInt()

        // Calcolo raggiungimento obiettivo
        val isGoalAchieved = when(goalType) {
            R.id.rbTime -> seconds >= goalTargetValue
            R.id.rbDistance -> distanceKm >= goalTargetValue
            R.id.rbCalories -> calories >= goalTargetValue
            else -> false
        }

        currentRunId?.let { runId ->
            CoroutineScope(Dispatchers.IO).launch {
                try { RetrofitClient.api.endRun(runId, distanceKm) } catch (e: Exception) { }
            }
        }

        saveSingleRunToHistory(distanceKm, finalSpeed, calories, isGoalAchieved)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val summaryStats = listOf(ParticipantLiveStats(userId, finalSpeed, calories, distanceKm))
        val summaryFragment = GroupRunSummaryFragment.newInstance(
            summaryStats, 
            capturedPhotos, 
            emptyMap(), 
            seconds, 
            false,
            goalAchieved = isGoalAchieved,
            goalTarget = goalValueText
        )
        
        parentFragmentManager.beginTransaction()
            .add(R.id.nav_host_fragment, summaryFragment)
            .addToBackStack(null)
            .commit()

        layoutSetupGoal.visibility = View.VISIBLE
        layoutRunningStats.visibility = View.GONE
    }

    private fun saveSingleRunToHistory(distance: Double, speed: Double, calories: Int, isGoalAchieved: Boolean) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        val historyRef = database.child("users").child(userId).child("recent_activities").push()
        val stats = listOf(ParticipantLiveStats(userId, speed, calories, distance))
        val historyData = mapOf(
            "id" to historyRef.key,
            "groupName" to "Corsa Singola",
            "type" to "single",
            "timestamp" to System.currentTimeMillis(),
            "durationSeconds" to seconds,
            "stats" to stats,
            "photos" to capturedPhotos,
            "participants" to listOf(userId),
            "goalAchieved" to isGoalAchieved,
            "goalTarget" to goalValueText
        )
        historyRef.setValue(historyData)
    }

    private fun updateMapLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        pathPoints.add(latLng)
        if (pathPolyline == null) {
            pathPolyline = googleMap?.addPolyline(PolylineOptions().addAll(pathPoints).color(Color.MAGENTA).width(12f))
        } else { pathPolyline?.points = pathPoints }

        if (userMarker == null) {
            val arrowBitmap = getArrowBitmap(Color.BLUE)
            userMarker = googleMap?.addMarker(MarkerOptions().position(latLng).rotation(location.bearing).anchor(0.5f, 0.5f).flat(true).icon(BitmapDescriptorFactory.fromBitmap(arrowBitmap)))
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        } else {
            userMarker?.position = latLng
            userMarker?.rotation = location.bearing
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }
    }

    private fun getArrowBitmap(color: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_navigation_arrow)!!
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.setTint(color)
        drawable.draw(canvas)
        return bitmap
    }

    private fun sendLocationToBackend(location: Location) {
        currentRunId?.let { runId ->
            CoroutineScope(Dispatchers.IO).launch {
                try { RetrofitClient.api.sendLocation(runId, location.latitude, location.longitude) }
                catch (e: Exception) { }
            }
        }
    }

    private fun updateStats(location: Location) {
        lastLocation?.let { totalDistance += it.distanceTo(location) }
        lastLocation = location
        tvDistance.text = String.format(Locale.getDefault(), "%.2f km", totalDistance / 1000f)
        tvSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", location.speed * 3.6f)
        val cal = (seconds * 0.1).toInt()
        tvCalories.text = "$cal kcal"
    }

    private fun updateTimerText() {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        try { fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper()) } catch (e: SecurityException) {}
    }

    override fun onMapReady(map: GoogleMap) { 
        googleMap = map 
        googleMap?.uiSettings?.isZoomControlsEnabled = true
    }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out) }
    private fun setupPickersBase() { pickerValueMain.minValue = 0; pickerValueSub.minValue = 0 }
    private fun updatePickerConfig(id: Int) {
        goalType = id
        tvSeparator.visibility = if (id == R.id.rbCalories) View.GONE else View.VISIBLE
        pickerValueSub.visibility = if (id == R.id.rbCalories) View.GONE else View.VISIBLE
        when(id) {
            R.id.rbTime -> { pickerValueMain.maxValue = 23; pickerValueSub.maxValue = 59; tvUnit.text = "ore:min" }
            R.id.rbDistance -> { pickerValueMain.maxValue = 99; pickerValueSub.maxValue = 99; tvUnit.text = "km" }
            R.id.rbCalories -> { pickerValueMain.maxValue = 2000; tvUnit.text = "kcal" }
        }
    }
}
