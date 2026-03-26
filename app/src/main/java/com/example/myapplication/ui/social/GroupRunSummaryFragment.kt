package com.example.myapplication.ui.social

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.Serializable
import java.util.Locale

class GroupRunSummaryFragment : Fragment() {

    private lateinit var rvStats: RecyclerView
    private lateinit var rvPhotos: RecyclerView
    private lateinit var btnClose: MaterialButton
    
    private lateinit var tvTitle: TextView
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalCalories: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvDuration: TextView

    private lateinit var cardGoalResult: MaterialCardView
    private lateinit var tvGoalStatus: TextView
    private lateinit var tvGoalDetails: TextView

    private var runId: String? = null
    private var initialCoverUrl: String? = null

    companion object {
        fun newInstance(
            stats: List<ParticipantLiveStats>, 
            photos: List<String>, 
            namesMap: Map<String, String>,
            durationSeconds: Int = 0,
            isGroup: Boolean = true,
            runId: String? = null,
            coverPhoto: String? = null,
            goalAchieved: Boolean? = null,
            goalTarget: String? = null
        ): GroupRunSummaryFragment {
            val fragment = GroupRunSummaryFragment()
            val args = Bundle()
            args.putSerializable("stats", ArrayList(stats))
            args.putStringArrayList("photos", ArrayList(photos))
            args.putSerializable("names_map", namesMap as Serializable)
            args.putInt("duration", durationSeconds)
            args.putBoolean("is_group", isGroup)
            args.putString("run_id", runId)
            args.putString("cover_photo", coverPhoto)
            goalAchieved?.let { args.putBoolean("goal_achieved", it) }
            goalTarget?.let { args.putString("goal_target", it) }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_group_run_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val stats = arguments?.getSerializable("stats") as? List<ParticipantLiveStats> ?: emptyList()
        val photos = arguments?.getStringArrayList("photos") ?: emptyList()
        val namesMap = arguments?.getSerializable("names_map") as? Map<String, String> ?: emptyMap()
        val duration = arguments?.getInt("duration") ?: 0
        val isGroup = arguments?.getBoolean("is_group", true) ?: true
        runId = arguments?.getString("run_id")
        initialCoverUrl = arguments?.getString("cover_photo")

        tvTitle = view.findViewById(R.id.tvSummaryTitle)
        tvTotalDistance = view.findViewById(R.id.tvSummaryTotalDistance)
        tvTotalCalories = view.findViewById(R.id.tvSummaryTotalCalories)
        tvAvgSpeed = view.findViewById(R.id.tvSummaryAvgSpeed)
        tvDuration = view.findViewById(R.id.tvSummaryDuration)
        
        cardGoalResult = view.findViewById(R.id.cardGoalResult)
        tvGoalStatus = view.findViewById(R.id.tvGoalStatus)
        tvGoalDetails = view.findViewById(R.id.tvGoalDetails)

        rvStats = view.findViewById(R.id.rvSummaryStats)
        rvPhotos = view.findViewById(R.id.rvSummaryPhotos)
        btnClose = view.findViewById(R.id.btnCloseSummary)

        tvTitle.text = if (isGroup) "Riassunto Corsa di Gruppo" else "Riassunto Corsa Singola"

        var totalDist = 0.0
        var totalCal = 0
        var avgSpeed = 0.0
        
        if (stats.isNotEmpty()) {
            totalDist = stats.sumOf { it.distance }
            totalCal = stats.sumOf { it.calories }
            avgSpeed = stats.map { it.speed }.average()
        }

        tvTotalDistance.text = String.format(Locale.getDefault(), "Distanza: %.2f km", totalDist)
        tvTotalCalories.text = "Calorie: $totalCal kcal"
        tvAvgSpeed.text = String.format(Locale.getDefault(), "Vel. Media: %.1f km/h", avgSpeed)
        
        val hrs = duration / 3600
        val mins = (duration % 3600) / 60
        val secs = duration % 60
        tvDuration.text = String.format(Locale.getDefault(), "Durata: %02d:%02d:%02d", hrs, mins, secs)

        // Visualizzazione Obiettivo
        if (arguments?.containsKey("goal_achieved") == true) {
            val achieved = arguments?.getBoolean("goal_achieved") ?: false
            val target = arguments?.getString("goal_target") ?: ""
            cardGoalResult.visibility = View.VISIBLE
            if (achieved) {
                tvGoalStatus.text = "OBIETTIVO RAGGIUNTO! 🎉"
                tvGoalStatus.setTextColor(Color.parseColor("#4CAF50"))
                cardGoalResult.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")))
                tvGoalDetails.text = "Hai completato il tuo obiettivo di $target"
            } else {
                tvGoalStatus.text = "OBIETTIVO NON RAGGIUNTO 🏃"
                tvGoalStatus.setTextColor(Color.parseColor("#F44336"))
                cardGoalResult.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")))
                tvGoalDetails.text = "Ti mancava poco per raggiungere $target!"
            }
        }

        rvStats.layoutManager = LinearLayoutManager(context)
        rvStats.adapter = ParticipantStatsAdapter(stats, namesMap)

        if (photos.isEmpty()) {
            view.findViewById<View>(R.id.tvPhotosHeader).visibility = View.GONE
            rvPhotos.visibility = View.GONE
        } else {
            val photosHeader = view.findViewById<TextView>(R.id.tvPhotosHeader)
            photosHeader.text = "Foto (Tocca per scegliere la copertina)"
            
            rvPhotos.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            val photosAdapter = GroupPhotosAdapter(photos, isSelectionEnabled = true, selectedPhotoUrl = initialCoverUrl) { url ->
                updateCoverInFirebase(url)
            }
            rvPhotos.adapter = photosAdapter
        }

        btnClose.setOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                parentFragmentManager.beginTransaction().remove(this).commit()
            }
        }
    }

    private fun updateCoverInFirebase(url: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val rId = runId ?: return
        val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        
        database.child("users").child(userId).child("recent_activities").child(rId)
            .child("coverPhoto").setValue(url)
            .addOnSuccessListener {
                Toast.makeText(context, "Foto di copertina impostata!", Toast.LENGTH_SHORT).show()
            }
    }
}
