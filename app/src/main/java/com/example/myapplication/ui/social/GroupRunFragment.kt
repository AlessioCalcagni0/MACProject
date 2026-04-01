package com.example.myapplication.ui.social

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.ui.home.MainActivity
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.*

class GroupRunFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var tvTimer: TextView
    private lateinit var rvStats: RecyclerView
    private lateinit var rvPhotos: RecyclerView
    private lateinit var btnStop: MaterialButton
    private lateinit var fabPhoto: MaterialButton

    private var groupId: String? = null
    private var runName: String? = null
    private var memberIds: List<String> = emptyList()
    private var userNamesMap = mutableMapOf<String, String>()
    private var isOrganizer: Boolean = false

    private lateinit var database: DatabaseReference
    private lateinit var statsRef: DatabaseReference
    private lateinit var photosRef: DatabaseReference
    private lateinit var lobbyRef: DatabaseReference
    
    private var statsChildListener: ChildEventListener? = null
    private var photosChildListener: ChildEventListener? = null
    private lateinit var statusListener: ValueEventListener

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }
    private lateinit var locationCallback: LocationCallback
    
    private val participantMarkers = mutableMapOf<String, Marker>()
    private val participantPolylines = mutableMapOf<String, Polyline>()
    private val participantPaths = mutableMapOf<String, MutableList<LatLng>>()
    private val participantStatsMap = mutableMapOf<String, ParticipantLiveStats>()
    private var latestPhotos = mutableListOf<String>()
    
    private lateinit var statsAdapter: ParticipantStatsAdapter
    private lateinit var photosAdapter: GroupPhotosAdapter

    private var seconds = 0
    private var isRunning = true
    private var isFinishing = false
    private var totalDistance = 0f
    private var lastLocation: Location? = null
    private var followMe = true 
    private val handler = Handler(Looper.getMainLooper())
    
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { uploadPhotoToFirebase(it) }
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                seconds++
                updateTimerUI()
                handler.postDelayed(this, 1000)
            }
        }
    }

    companion object {
        fun newInstance(groupId: String, groupName: String, memberIds: List<String>, namesMap: Map<String, String>): GroupRunFragment {
            val fragment = GroupRunFragment()
            val args = Bundle()
            args.putString("group_id", groupId)
            args.putString("group_name", groupName)
            args.putStringArrayList("member_ids", ArrayList(memberIds))
            args.putSerializable("names_map", namesMap as Serializable)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_group_run, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        groupId = arguments?.getString("group_id")
        runName = arguments?.getString("group_name")
        memberIds = arguments?.getStringArrayList("member_ids") ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val initialNames = arguments?.getSerializable("names_map") as? Map<String, String> ?: emptyMap()
        userNamesMap.putAll(initialNames)

        database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        lobbyRef = database.child("lobbies").child(groupId!!)
        statsRef = lobbyRef.child("live_stats")
        photosRef = lobbyRef.child("photos")

        mapView = view.findViewById(R.id.groupMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        tvTimer = view.findViewById(R.id.tvGroupRunTimer)
        rvStats = view.findViewById(R.id.rvParticipantsStats)
        rvPhotos = view.findViewById(R.id.rvGroupPhotos)
        btnStop = view.findViewById(R.id.btnStopGroupRun)
        fabPhoto = view.findViewById(R.id.fabAddPhoto)

        rvStats.layoutManager = LinearLayoutManager(context)
        statsAdapter = ParticipantStatsAdapter(participantStatsMap.values.toList(), userNamesMap)
        rvStats.adapter = statsAdapter

        rvPhotos.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        photosAdapter = GroupPhotosAdapter(latestPhotos)
        rvPhotos.adapter = photosAdapter

        checkOrganizerStatus()
        btnStop.setOnClickListener { if (isOrganizer) terminateNow() }
        fabPhoto.setOnClickListener { openCamera() }

        syncStartTime()
        startLocationUpdates()
        listenToStatsOptimized()
        listenToPhotosOptimized()
        listenToRunStatus()
        handler.post(timerRunnable)
    }

    private fun listenToStatsOptimized() {
        statsChildListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) { updateParticipant(snapshot) }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) { updateParticipant(snapshot) }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val uId = snapshot.key ?: return
                participantMarkers[uId]?.remove()
                participantMarkers.remove(uId)
                participantPolylines[uId]?.remove()
                participantPolylines.remove(uId)
                participantPaths.remove(uId)
                participantStatsMap.remove(uId)
                statsAdapter.updateData(participantStatsMap.values.toList())
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        statsRef.addChildEventListener(statsChildListener!!)
    }

    private fun updateParticipant(snapshot: DataSnapshot) {
        val uId = snapshot.key ?: return
        val lat = snapshot.child("lat").getValue(Double::class.java) ?: return
        val lng = snapshot.child("lng").getValue(Double::class.java) ?: return
        val speed = snapshot.child("speed").getValue(Double::class.java) ?: 0.0
        val dist = snapshot.child("distance").getValue(Double::class.java) ?: 0.0
        val bearing = snapshot.child("bearing").getValue(Float::class.java) ?: 0f
        
        val pos = LatLng(lat, lng)
        drawParticipantOnMap(uId, pos, bearing)
        participantStatsMap[uId] = ParticipantLiveStats(uId, speed, 0, dist)
        
        activity?.runOnUiThread {
            statsAdapter.updateData(participantStatsMap.values.toList())
        }
    }

    private fun listenToPhotosOptimized() {
        photosChildListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val url = snapshot.getValue(String::class.java) ?: return
                if (!latestPhotos.contains(url)) {
                    latestPhotos.add(0, url) // Aggiungi in cima per visibilità immediata
                    photosAdapter.updatePhotos(latestPhotos)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        photosRef.addChildEventListener(photosChildListener!!)
    }

    private fun syncStartTime() {
        lobbyRef.child("startTime").get().addOnSuccessListener { snapshot ->
            val startTs = snapshot.value as? Long ?: System.currentTimeMillis()
            seconds = ((System.currentTimeMillis() - startTs) / 1000).toInt()
        }
    }

    private fun listenToRunStatus() {
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.value == "finished" && !isFinishing) finishRunAndShowSummary()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        lobbyRef.child("status").addValueEventListener(statusListener)
    }

    private fun terminateNow() {
        if (isFinishing) return
        isFinishing = true
        isRunning = false
        btnStop.isEnabled = false
        Toast.makeText(context, "Chiusura corsa...", Toast.LENGTH_SHORT).show()

        lobbyRef.child("status").setValue("finished").addOnCompleteListener {
            saveGroupRunToHistory(participantStatsMap.values.toList(), latestPhotos)
            if (googleMap != null) {
                googleMap?.snapshot { bitmap -> if (bitmap != null) uploadMapSnapshot(bitmap) {} }
            }
            finishRunAndShowSummary()
        }
    }

    private fun saveGroupRunToHistory(stats: List<ParticipantLiveStats>, photos: List<String>) {
        val historyRef = database.child("group_run_history").push()
        val historyId = historyRef.key ?: return
        val serializableStats = stats.map { s ->
            mapOf("userId" to s.userId, "speed" to s.speed, "calories" to s.calories, "distance" to s.distance)
        }
        val historyData = mapOf(
            "id" to historyId, "groupId" to groupId, "groupName" to runName,
            "timestamp" to System.currentTimeMillis(), "durationSeconds" to seconds,
            "stats" to serializableStats, "photos" to photos, "participants" to memberIds
        )
        memberIds.forEach { database.child("users").child(it).child("recent_activities").child(historyId).setValue(historyData) }
        database.child("completed_group_runs").child(historyId).setValue(historyData)
    }

    private fun finishRunAndShowSummary() {
        isFinishing = true
        isRunning = false
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        statsChildListener?.let { statsRef.removeEventListener(it) }
        photosChildListener?.let { photosRef.removeEventListener(it) }
        lobbyRef.child("status").removeEventListener(statusListener)

        if (isOrganizer) handler.postDelayed({ lobbyRef.removeValue() }, 10000)

        val summaryFragment = GroupRunSummaryFragment.newInstance(participantStatsMap.values.toList(), latestPhotos, userNamesMap, seconds, true, groupId)
        val mainActivity = activity as? MainActivity ?: return
        mainActivity.navigateToFragment(summaryFragment, "SUMMARY", "Riassunto Corsa")
    }

    private fun checkOrganizerStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lobbyRef.child("organizer").get().addOnSuccessListener { snapshot ->
            isOrganizer = (userId == snapshot.value as? String)
            btnStop.visibility = if (isOrganizer) View.VISIBLE else View.GONE
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastLocation?.let { totalDistance += it.distanceTo(location) }
                    lastLocation = location
                    val myStats = mapOf("lat" to location.latitude, "lng" to location.longitude, "speed" to (location.speed * 3.6), "distance" to (totalDistance / 1000.0), "bearing" to location.bearing.toDouble())
                    statsRef.child(FirebaseAuth.getInstance().currentUser!!.uid).setValue(myStats)
                    if (followMe) googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17f))
                }
            }
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }
    }

    private fun drawParticipantOnMap(userId: String, position: LatLng, bearing: Float) {
        activity?.runOnUiThread {
            if (googleMap == null) return@runOnUiThread
            val isMe = userId == FirebaseAuth.getInstance().currentUser?.uid
            if (participantMarkers.containsKey(userId)) {
                participantMarkers[userId]?.position = position
                participantMarkers[userId]?.rotation = bearing
            } else {
                val arrowBitmap = getArrowBitmap(if (isMe) Color.BLUE else Color.RED)
                val marker = googleMap?.addMarker(MarkerOptions().position(position).title(userNamesMap[userId] ?: "Runner").icon(BitmapDescriptorFactory.fromBitmap(arrowBitmap)).flat(true).anchor(0.5f, 0.5f))
                if (marker != null) participantMarkers[userId] = marker
            }
            val path = participantPaths.getOrPut(userId) { mutableListOf() }
            if (path.isEmpty() || path.last() != position) {
                path.add(position)
                if (participantPolylines.containsKey(userId)) participantPolylines[userId]?.points = path
                else participantPolylines[userId] = googleMap!!.addPolyline(PolylineOptions().addAll(path).color(if (isMe) Color.BLUE else Color.RED).width(10f))
            }
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

    private fun uploadMapSnapshot(bitmap: Bitmap, onComplete: () -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("group_runs/${groupId}/map_${System.currentTimeMillis()}.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        storageRef.putBytes(baos.toByteArray()).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri -> photosRef.push().setValue(uri.toString()).addOnCompleteListener { onComplete() } }.addOnFailureListener { onComplete() }
        }.addOnFailureListener { onComplete() }
    }

    private fun openCamera() { takePhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }
    private fun uploadPhotoToFirebase(bitmap: Bitmap) {
        val storageRef = FirebaseStorage.getInstance().reference.child("group_runs/${groupId}/${System.currentTimeMillis()}.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        storageRef.putBytes(baos.toByteArray()).addOnSuccessListener { storageRef.downloadUrl.addOnSuccessListener { uri -> photosRef.push().setValue(uri.toString()) } }
    }

    private fun updateTimerUI() {
        val hrs = seconds / 3600; val mins = (seconds % 3600) / 60; val secs = seconds % 60
        tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
    }

    override fun onMapReady(map: GoogleMap) { 
        googleMap = map; googleMap?.uiSettings?.isZoomControlsEnabled = true; googleMap?.setOnMapClickListener { followMe = false } 
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { 
        super.onDestroy()
        mapView.onDestroy()
        statsChildListener?.let { statsRef.removeEventListener(it) }
        photosChildListener?.let { photosRef.removeEventListener(it) }
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}
