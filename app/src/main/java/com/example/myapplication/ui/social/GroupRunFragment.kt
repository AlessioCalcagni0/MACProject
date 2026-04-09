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
        listenToLobbyNames()
        startLocationUpdates()
        listenToStatsOptimized()
        listenToPhotosOptimized()
        listenToRunStatus()
        handler.post(timerRunnable)
    }

    private fun listenToLobbyNames() {
        lobbyRef.child("names").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val uId = child.key ?: continue
                    val name = child.value?.toString() ?: continue
                    if (!name.isNullOrBlank()) {
                        userNamesMap[uId] = name
                    }
                }
                activity?.runOnUiThread {
                    statsAdapter.updateNames(userNamesMap)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToStatsOptimized() {
        statsChildListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) { updateParticipant(snapshot) }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) { updateParticipant(snapshot) }
            override fun onChildRemoved(snapshot: DataSnapshot) {}
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
        
        val liveName = snapshot.child("name").getValue(String::class.java)
        if (!liveName.isNullOrBlank()) {
            userNamesMap[uId] = liveName
        }

        val pos = LatLng(lat, lng)
        drawParticipantOnMap(uId, pos, bearing)
        participantStatsMap[uId] = ParticipantLiveStats(uId, speed, (dist * 70 * 0.9).toInt(), dist)
        
        activity?.runOnUiThread {
            statsAdapter.updateNames(userNamesMap)
            statsAdapter.updateData(participantStatsMap.values.toList())
        }
    }

    private fun listenToPhotosOptimized() {
        photosChildListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val url = snapshot.getValue(String::class.java) ?: return
                if (!latestPhotos.contains(url)) {
                    latestPhotos.add(0, url) 
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
                val status = snapshot.child("status").value?.toString()
                if (status == "finished" && !isFinishing) {
                    val historyId = snapshot.child("historyId").value?.toString()
                    finishRunAndShowSummary(historyId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        lobbyRef.addValueEventListener(statusListener)
    }

    private fun terminateNow() {
        if (isFinishing) return
        isFinishing = true
        isRunning = false
        btnStop.isEnabled = false
        Toast.makeText(context, "Closing run...", Toast.LENGTH_SHORT).show()

        val boundsBuilder = LatLngBounds.Builder()
        var pointsFound = false
        participantPaths.values.forEach { path ->
            path.forEach { point ->
                boundsBuilder.include(point)
                pointsFound = true
            }
        }

        if (googleMap != null && pointsFound) {
            try {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                
                handler.postDelayed({
                    googleMap?.snapshot { bitmap ->
                        if (bitmap != null) {
                            uploadMapSnapshot(bitmap) { finalizeTermination() }
                        } else {
                            finalizeTermination()
                        }
                    }
                }, 1000)
            } catch (e: Exception) {
                finalizeTermination()
            }
        } else {
            finalizeTermination()
        }
    }

    private fun finalizeTermination() {
        val historyId = saveGroupRunToHistory(participantStatsMap.values.toList(), latestPhotos)
        val update = mapOf("status" to "finished", "historyId" to historyId)
        lobbyRef.updateChildren(update).addOnCompleteListener {
            finishRunAndShowSummary(historyId)
        }
    }

    private fun uploadMapSnapshot(bitmap: Bitmap, onComplete: () -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("group_runs/${groupId}/map_summary.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        storageRef.putBytes(baos.toByteArray()).addOnSuccessListener {
            it.metadata?.reference?.downloadUrl?.addOnSuccessListener { uri ->
                val url = uri.toString()
                photosRef.push().setValue(url).addOnCompleteListener { onComplete() }
            }
        }.addOnFailureListener { onComplete() }
    }

    private fun saveGroupRunToHistory(stats: List<ParticipantLiveStats>, photos: List<String>): String {
        val historyRef = database.child("group_run_history").push()
        val historyId = historyRef.key ?: return ""
        
        val participantNames = mutableMapOf<String, String>()
        // Includiamo i nomi di chiunque abbia partecipato (chi è in stats)
        stats.forEach { s ->
            val name = userNamesMap[s.userId] ?: "Participant"
            participantNames[s.userId] = name
        }
        
        // Includiamo anche gli altri membri del gruppo per completezza
        memberIds.forEach { mId ->
            if (!participantNames.containsKey(mId)) {
                userNamesMap[mId]?.let { participantNames[mId] = it }
            }
        }

        val historyData = mapOf(
            "id" to historyId, 
            "groupName" to runName,
            "timestamp" to System.currentTimeMillis(), 
            "durationSeconds" to seconds,
            "stats" to stats.map { mapOf("userId" to it.userId, "speed" to it.speed, "calories" to it.calories, "distance" to it.distance) }, 
            "photos" to photos, 
            "participantNames" to participantNames,
            "type" to "group"
        )
        
        // Salviamo l'attività per TUTTI i membri che hanno partecipato o sono nel gruppo
        val allInvolved = (memberIds + participantNames.keys).distinct()
        allInvolved.forEach { database.child("users").child(it).child("recent_activities").child(historyId).setValue(historyData) }
        
        return historyId
    }

    private fun finishRunAndShowSummary(historyId: String? = null) {
        isFinishing = true
        isRunning = false
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        lobbyRef.removeEventListener(statusListener)
        if (isOrganizer) handler.postDelayed({ lobbyRef.removeValue() }, 15000)

        val summaryFragment = GroupRunSummaryFragment.newInstance(
            participantStatsMap.values.toList(), 
            latestPhotos, 
            userNamesMap, 
            seconds, 
            true, 
            runId = historyId
        )
        (activity as? MainActivity)?.navigateToFragment(summaryFragment, "SUMMARY", "Run Summary")
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
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        // Cerchiamo il nome dell'utente corrente
        val myName = userNamesMap[userId] ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "Runner"
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastLocation?.let { totalDistance += it.distanceTo(location) }
                    lastLocation = location
                    val pos = LatLng(location.latitude, location.longitude)
                    val myStats = mapOf(
                        "lat" to location.latitude, 
                        "lng" to location.longitude, 
                        "speed" to (location.speed * 3.6), 
                        "distance" to (totalDistance / 1000.0), 
                        "name" to myName,
                        "bearing" to location.bearing.toDouble()
                    )
                    statsRef.child(FirebaseAuth.getInstance().currentUser!!.uid).setValue(myStats)
                    
                    // Se followMe è attivo, centriamo la mappa sulla posizione corrente
                    if (followMe) {
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLng(pos))
                    }
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
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val isMe = userId == currentUid
            val color = if (isMe) Color.BLUE else Color.RED

            if (participantMarkers.containsKey(userId)) {
                participantMarkers[userId]?.position = position
                participantMarkers[userId]?.rotation = bearing
                participantMarkers[userId]?.title = userNamesMap[userId] ?: "Runner"
            } else {
                val arrowBitmap = getArrowBitmap(color)
                val marker = googleMap?.addMarker(MarkerOptions()
                    .position(position)
                    .title(userNamesMap[userId] ?: "Runner")
                    .icon(BitmapDescriptorFactory.fromBitmap(arrowBitmap))
                    .flat(true)
                    .anchor(0.5f, 0.5f))
                if (marker != null) {
                    participantMarkers[userId] = marker
                    
                    // AUTOZOOM: Se il marker creato è il mio, facciamo lo zoom iniziale
                    if (isMe) {
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 17f))
                    }
                }
            }

            val path = participantPaths.getOrPut(userId) { mutableListOf() }
            if (path.isEmpty() || path.last() != position) {
                path.add(position)
                if (participantPolylines.containsKey(userId)) {
                    participantPolylines[userId]?.points = path
                } else {
                    participantPolylines[userId] = googleMap!!.addPolyline(PolylineOptions()
                        .addAll(path)
                        .color(color)
                        .width(10f))
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

    private fun openCamera() { takePhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }
    
    private fun uploadPhotoToFirebase(bitmap: Bitmap) {
        val storageRef = FirebaseStorage.getInstance().reference.child("group_runs/${groupId}/${System.currentTimeMillis()}.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        storageRef.putBytes(baos.toByteArray()).addOnSuccessListener { 
            it.metadata?.reference?.downloadUrl?.addOnSuccessListener { uri -> 
                photosRef.push().setValue(uri.toString()) 
            } 
        }
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
}
