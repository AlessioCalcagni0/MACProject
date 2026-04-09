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
import com.example.myapplication.ui.home.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.Serializable
import java.util.Locale

class GroupRunSummaryFragment : Fragment() {

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
        
        // RECUPERO RUN ID: Fondamentale per salvare la copertina
        runId = arguments?.getString("run_id")
        initialCoverUrl = arguments?.getString("cover_photo")

        val tvTitle = view.findViewById<TextView>(R.id.tvSummaryTitle)
        val tvDuration = view.findViewById<TextView>(R.id.tvSummaryDuration)
        val cardGoalResult = view.findViewById<MaterialCardView>(R.id.cardGoalResult)
        val tvGoalStatus = view.findViewById<TextView>(R.id.tvGoalStatus)
        val tvGoalDetails = view.findViewById<TextView>(R.id.tvGoalDetails)
        val tvParticipantsHeader = view.findViewById<TextView>(R.id.tvParticipantsHeader)
        val rvParticipantStats = view.findViewById<RecyclerView>(R.id.rvParticipantStats)
        val cardSingleStats = view.findViewById<MaterialCardView>(R.id.cardSingleStats)
        val tvSummaryTotalDistance = view.findViewById<TextView>(R.id.tvSummaryTotalDistance)
        val tvSummaryTotalCalories = view.findViewById<TextView>(R.id.tvSummaryTotalCalories)
        val tvSummaryAvgSpeed = view.findViewById<TextView>(R.id.tvSummaryAvgSpeed)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvSummaryPhotos)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseSummary)

        tvTitle.text = if (isGroup) "Group Run Summary" else "Single Run Summary"

        val hrs = duration / 3600
        val mins = (duration % 3600) / 60
        val secs = duration % 60
        tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)

        if (isGroup) {
            tvParticipantsHeader.visibility = View.VISIBLE
            rvParticipantStats.visibility = View.VISIBLE
            cardSingleStats.visibility = View.GONE
            rvParticipantStats.layoutManager = LinearLayoutManager(requireContext())
            rvParticipantStats.adapter = ParticipantStatsAdapter(stats, namesMap)
        } else {
            tvParticipantsHeader.visibility = View.GONE
            rvParticipantStats.visibility = View.GONE
            cardSingleStats.visibility = View.VISIBLE
            val myStat = stats.firstOrNull()
            tvSummaryTotalDistance.text = String.format(Locale.getDefault(), "%.2f km", myStat?.distance ?: 0.0)
            tvSummaryTotalCalories.text = String.format(Locale.getDefault(), "%d kcal", myStat?.calories ?: 0)
            tvSummaryAvgSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", myStat?.speed ?: 0.0)
        }

        if (arguments?.containsKey("goal_achieved") == true) {
            val achieved = arguments?.getBoolean("goal_achieved") ?: false
            cardGoalResult?.visibility = View.VISIBLE
            if (achieved) {
                tvGoalStatus?.text = "GOAL ACHIEVED! 🎉"
                tvGoalDetails?.text = "Congratulations! You have achieved your goal!"
                tvGoalStatus?.setTextColor(Color.parseColor("#4CAF50"))
                cardGoalResult?.setStrokeColor(Color.parseColor("#4CAF50"))
            } else {
                tvGoalStatus?.text = "GOAL NOT REACHED 🏃"
                tvGoalDetails?.text = "Don't give up! Keep training to reach your next goal."
                tvGoalStatus?.setTextColor(Color.parseColor("#F44336"))
                cardGoalResult?.setStrokeColor(Color.parseColor("#F44336"))
            }
        }

        if (photos.isEmpty()) {
            view.findViewById<View>(R.id.tvPhotosHeader)?.visibility = View.GONE
            rvPhotos?.visibility = View.GONE
        } else {
            rvPhotos?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            rvPhotos?.adapter = GroupPhotosAdapter(photos, isSelectionEnabled = true, selectedPhotoUrl = initialCoverUrl) { url ->
                initialCoverUrl = url // Aggiorniamo localmente
                updateCoverInFirebase(url)
            }
        }

        btnClose?.setOnClickListener {
            val mainActivity = activity as? MainActivity
            if (mainActivity != null) {
                mainActivity.switchFragmentByTag("HOME")
            } else {
                parentFragmentManager.beginTransaction().remove(this).commit()
            }
        }
    }

    private fun updateCoverInFirebase(url: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Se runId è nullo (possibile solo se c'è un errore a monte), non possiamo salvare
        val rId = runId
        if (rId == null) {
            android.util.Log.e("Summary", "Impossibile aggiornare copertina: runId mancante")
            return
        }

        val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        
        // Percorso standard per le attività recenti
        database.child("users").child(userId).child("recent_activities").child(rId).child("coverPhoto").setValue(url)
            .addOnSuccessListener { 
                if (isAdded) {
                    Toast.makeText(requireContext(), "Cover updated!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error updating cover", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
