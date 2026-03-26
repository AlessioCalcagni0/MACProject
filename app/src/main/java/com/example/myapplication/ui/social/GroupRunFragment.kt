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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
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
    private var groupName: String? = null
    private var memberIds: List<String> = emptyList()
    private var userNamesMap: Map<String, String> = emptyMap()
    private var isOrganizer: Boolean = false

    private lateinit var database: DatabaseReference
    private lateinit var statsRef: DatabaseReference
    private lateinit var photosRef: DatabaseReference
    private lateinit var lobbyRef: DatabaseReference
    
    private lateinit var statsListener: ValueEventListener
    private lateinit var photosListener: ValueEventListener
    private lateinit var statusListener: ValueEventListener

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }
    private lateinit var locationCallback: LocationCallback
    
    private val participantMarkers = mutableMapOf<String, Marker>()
    private val participantPolylines = mutableMapOf<String, Polyline>()
    private val participantPaths = mutableMapOf<String, MutableList<LatLng>>()
    private val participantStatsMap = mutableMapOf<String, ParticipantLiveStats>()
    private val lastKnownPositions = mutableMapOf<String, Pair<LatLng, Float>>()
    private lateinit var statsAdapter: ParticipantStatsAdapter
    private lateinit var photosAdapter: GroupPhotosAdapter

    private var seconds = 0
    private var isRunning = true
    private var totalDistance = 0f
    private var lastLocation: Location? = null
    private var isFirstFix = true
    private val handler = Handler(Looper.getMainLooper())
    
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                seconds++
                updateTimerUI()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val DB_URL = "https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app"

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { uploadPhotoToFirebase(it) }
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
        groupName = arguments?.getString("group_name")
        memberIds = arguments?.getStringArrayList("member_ids") ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        userNamesMap = arguments?.getSerializable("names_map") as? Map<String, String> ?: emptyMap()

        database = FirebaseDatabase.getInstance(DB_URL).reference
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
        rvPhotos.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        
        memberIds.forEach { id ->
            participantStatsMap[id] = ParticipantLiveStats(id, 0.0, 0, 0.0)
        }
        
        statsAdapter = ParticipantStatsAdapter(participantStatsMap.values.toList(), userNamesMap)
        rvStats.adapter = statsAdapter

        photosAdapter = GroupPhotosAdapter(emptyList())
        rvPhotos.adapter = photosAdapter

        checkOrganizerStatus()

        btnStop.setOnClickListener { 
            if (isOrganizer) {
                terminateRunForAll()
            } else {
                Toast.makeText(context, "Solo l'organizzatore può terminare la corsa", Toast.LENGTH_SHORT).show()
            }
        }
        fabPhoto.setOnClickListener { openCamera() }

        syncTimerWithServer()
        startLocationUpdates()
        listenToAllParticipants()
        listenToPhotos()
        listenToRunStatus()
        handler.post(timerRunnable)
    }

    private fun checkOrganizerStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lobbyRef.child("organizer").get().addOnSuccessListener { snapshot ->
            isOrganizer = (userId == snapshot.value as? String)
            activity?.runOnUiThread {
                btnStop.visibility = if (isOrganizer) View.VISIBLE else View.GONE
            }
        }
    }

    private fun listenToRunStatus() {
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.value as? String
                if (status == "finished") {
                    finishRunAndShowSummary()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        lobbyRef.child("status").addValueEventListener(statusListener)
    }

    private fun terminateRunForAll() {
        lobbyRef.child("status").setValue("finished")
    }

    private fun finishRunAndShowSummary() {
        isRunning = false
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        if (::statsListener.isInitialized) statsRef.removeEventListener(statsListener)
        if (::photosListener.isInitialized) photosRef.removeEventListener(photosListener)
        if (::statusListener.isInitialized) lobbyRef.child("status").removeEventListener(statusListener)

        val summaryStats = participantStatsMap.values.toList()
        val summaryPhotos = photosAdapter.getPhotos()
        
        if (isOrganizer) {
            saveGroupRunToHistory(summaryStats, summaryPhotos)
            handler.postDelayed({
                lobbyRef.removeValue()
            }, 5000)
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) statsRef.child(userId).removeValue()

        val summaryFragment = GroupRunSummaryFragment.newInstance(summaryStats, summaryPhotos, userNamesMap, seconds, true, groupId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, summaryFragment)
            .commit()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePhotoLauncher.launch(intent)
    }

    private fun uploadPhotoToFirebase(bitmap: Bitmap) {
        val storageRef = FirebaseStorage.getInstance().reference
            .child("group_runs/${groupId}/${System.currentTimeMillis()}.jpg")
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val data = baos.toByteArray()

        storageRef.putBytes(data).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                photosRef.push().setValue(uri.toString())
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Errore caricamento foto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun listenToPhotos() {
        photosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<String>()
                for (child in snapshot.children) {
                    child.getValue(String::class.java)?.let { list.add(it) }
                }
                photosAdapter.updatePhotos(list.reversed())
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        photosRef.addValueEventListener(photosListener)
    }

    private fun syncTimerWithServer() {
        lobbyRef.child("startTime").get().addOnSuccessListener { snapshot ->
            val startTs = snapshot.value as? Long ?: System.currentTimeMillis()
            seconds = ((System.currentTimeMillis() - startTs) / 1000).toInt()
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { updateMyPositionOnFirebase(it) }
            }
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }
    }

    private fun updateMyPositionOnFirebase(location: Location) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        lastLocation?.let { totalDistance += it.distanceTo(location) }
        lastLocation = location

        val myStats = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "speed" to (location.speed * 3.6).toDouble(),
            "calories" to (seconds * 0.1).toLong(),
            "distance" to (totalDistance / 1000.0),
            "bearing" to location.bearing.toDouble()
        )
        statsRef.child(userId).setValue(myStats)
    }

    private fun listenToAllParticipants() {
        statsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeUserIds = mutableSetOf<String>()
                for (child in snapshot.children) {
                    val uId = child.key ?: continue
                    activeUserIds.add(uId)
                    val lat = (child.child("lat").value as? Number)?.toDouble() ?: continue
                    val lng = (child.child("lng").value as? Number)?.toDouble() ?: continue
                    val speed = (child.child("speed").value as? Number)?.toDouble() ?: 0.0
                    val calories = (child.child("calories").value as? Number)?.toInt() ?: 0
                    val dist = (child.child("distance").value as? Number)?.toDouble() ?: 0.0
                    val bearing = (child.child("bearing").value as? Number)?.toFloat() ?: 0f
                    
                    val pos = LatLng(lat, lng)
                    lastKnownPositions[uId] = Pair(pos, bearing)
                    drawParticipantOnMap(uId, pos, bearing)
                    participantStatsMap[uId] = ParticipantLiveStats(uId, speed, calories, dist)
                }

                val iterator = participantMarkers.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!activeUserIds.contains(entry.key)) {
                        entry.value.remove()
                        iterator.remove()
                        participantPolylines[entry.key]?.remove()
                        participantPolylines.remove(entry.key)
                        participantPaths.remove(entry.key)
                        participantStatsMap.remove(entry.key)
                        lastKnownPositions.remove(entry.key)
                    }
                }

                zoomToFitAllParticipants()

                activity?.runOnUiThread {
                    statsAdapter.updateData(participantStatsMap.values.toList())
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statsRef.addValueEventListener(statsListener)
    }

    private fun zoomToFitAllParticipants() {
        val map = googleMap ?: return
        if (participantMarkers.isEmpty()) return

        val builder = LatLngBounds.Builder()
        for (marker in participantMarkers.values) {
            builder.include(marker.position)
        }
        
        try {
            val bounds = builder.build()
            val padding = 150
            if (isFirstFix) {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                isFirstFix = false
            }
        } catch (e: Exception) { }
    }

    private fun drawParticipantOnMap(userId: String, position: LatLng, bearing: Float) {
        activity?.runOnUiThread {
            if (googleMap == null) return@runOnUiThread
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val isMe = userId == currentUid

            if (participantMarkers.containsKey(userId)) {
                val marker = participantMarkers[userId]
                marker?.position = position
                marker?.rotation = bearing
            } else {
                val arrowBitmap = getArrowBitmap(if (isMe) Color.BLUE else Color.RED)
                val marker = googleMap?.addMarker(MarkerOptions()
                    .position(position)
                    .title(userNamesMap[userId] ?: "Runner")
                    .rotation(bearing)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .icon(BitmapDescriptorFactory.fromBitmap(arrowBitmap)))
                if (marker != null) {
                    participantMarkers[userId] = marker
                    marker.showInfoWindow()
                }
            }

            val path = participantPaths.getOrPut(userId) { mutableListOf() }
            if (path.isEmpty() || path.last() != position) {
                path.add(position)
                if (participantPolylines.containsKey(userId)) {
                    participantPolylines[userId]?.points = path
                } else {
                    val poly = googleMap?.addPolyline(PolylineOptions()
                        .addAll(path)
                        .color(if (isMe) Color.BLUE else Color.RED)
                        .width(10f)
                        .jointType(JointType.ROUND))
                    if (poly != null) participantPolylines[userId] = poly
                }
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

    private fun updateTimerUI() {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
    }

    private fun saveGroupRunToHistory(stats: List<ParticipantLiveStats>, photos: List<String>) {
        val historyRef = database.child("group_run_history").push()
        val historyId = historyRef.key ?: return
        
        val historyData = mapOf(
            "id" to historyId,
            "groupId" to groupId,
            "groupName" to groupName,
            "timestamp" to System.currentTimeMillis(),
            "durationSeconds" to seconds,
            "stats" to stats,
            "photos" to photos,
            "participants" to memberIds
        )
        
        memberIds.forEach { mId ->
            database.child("users").child(mId).child("recent_activities").child(historyId).setValue(historyData)
        }
        database.child("completed_group_runs").child(historyId).setValue(historyData)
    }

    override fun onMapReady(map: GoogleMap) { 
        googleMap = map 
        googleMap?.uiSettings?.isRotateGesturesEnabled = true
        googleMap?.uiSettings?.isMyLocationButtonEnabled = false
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        
        lastKnownPositions.forEach { (uId, data) ->
            drawParticipantOnMap(uId, data.first, data.second)
        }
    }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { 
        super.onDestroy()
        mapView.onDestroy()
        if (::statusListener.isInitialized) lobbyRef.child("status").removeEventListener(statusListener)
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}
