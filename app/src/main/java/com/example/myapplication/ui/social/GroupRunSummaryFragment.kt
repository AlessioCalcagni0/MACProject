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
        runId = arguments?.getString("run_id")
        initialCoverUrl = arguments?.getString("cover_photo")

        // Riferimenti UI
        val tvTitle = view.findViewById<TextView>(R.id.tvSummaryTitle)
        val tvDuration = view.findViewById<TextView>(R.id.tvSummaryDuration)
        val tvTotalDistance = view.findViewById<TextView>(R.id.tvSummaryTotalDistance)
        val tvTotalCalories = view.findViewById<TextView>(R.id.tvSummaryTotalCalories)
        val tvAvgSpeed = view.findViewById<TextView>(R.id.tvSummaryAvgSpeed)
        
        val cardGoalResult = view.findViewById<MaterialCardView>(R.id.cardGoalResult)
        val tvGoalStatus = view.findViewById<TextView>(R.id.tvGoalStatus)
        val tvGoalDetails = view.findViewById<TextView>(R.id.tvGoalDetails)

        val gridSingle = view.findViewById<View>(R.id.gridSingleStats)
        val tvStatsHeader = view.findViewById<TextView>(R.id.tvStatsHeader)
        val rvStats = view.findViewById<RecyclerView>(R.id.rvSummaryStats)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvSummaryPhotos)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseSummary)

        tvTitle.text = if (isGroup) "Riassunto Corsa di Gruppo" else "Riassunto Corsa Singola"

        // Configurazione DURATA (sempre visibile)
        val hrs = duration / 3600
        val mins = (duration % 3600) / 60
        val secs = duration % 60
        tvDuration.text = String.format(Locale.getDefault(), "Durata totale: %02d:%02d:%02d", hrs, mins, secs)

        // LOGICA CORSA SINGOLA vs GRUPPO
        if (isGroup) {
            gridSingle?.visibility = View.GONE
            tvStatsHeader?.visibility = View.VISIBLE
            rvStats?.visibility = View.VISIBLE
        } else {
            gridSingle?.visibility = View.VISIBLE
            tvStatsHeader?.visibility = View.GONE
            rvStats?.visibility = View.GONE
            
            val myStat = stats.firstOrNull()
            tvTotalDistance?.text = String.format(Locale.getDefault(), "Distanza percorsa: %.2f km", myStat?.distance ?: 0.0)
            tvTotalCalories?.text = String.format(Locale.getDefault(), "Calorie bruciate = %d kcal", myStat?.calories ?: 0)
            tvAvgSpeed?.text = String.format(Locale.getDefault(), "Velocità media = %.1f km/h", myStat?.speed ?: 0.0)
        }

        // Obiettivi
        if (arguments?.containsKey("goal_achieved") == true) {
            val achieved = arguments?.getBoolean("goal_achieved") ?: false
            cardGoalResult?.visibility = View.VISIBLE
            tvGoalStatus?.text = if (achieved) "OBIETTIVO RAGGIUNTO! 🎉" else "OBIETTIVO NON RAGGIUNTO 🏃"
            tvGoalStatus?.setTextColor(if (achieved) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
        }

        // Adapter Statistiche
        rvStats?.layoutManager = LinearLayoutManager(context)
        rvStats?.adapter = ParticipantStatsAdapter(stats, namesMap)

        // Adapter Foto
        if (photos.isEmpty()) {
            view.findViewById<View>(R.id.tvPhotosHeader)?.visibility = View.GONE
            rvPhotos?.visibility = View.GONE
        } else {
            rvPhotos?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            val photosAdapter = GroupPhotosAdapter(photos, isSelectionEnabled = true, selectedPhotoUrl = initialCoverUrl) { url ->
                updateCoverInFirebase(url)
            }
            rvPhotos?.adapter = photosAdapter
        }

        // Chiusura
        btnClose?.setOnClickListener {
            val activity = activity as? MainActivity
            if (activity != null) {
                parentFragmentManager.beginTransaction().remove(this).commit()
                activity.findViewById<View>(R.id.nav_home)?.performClick()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun updateCoverInFirebase(url: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val rId = runId ?: return
        val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        database.child("users").child(userId).child("recent_activities").child(rId).child("coverPhoto").setValue(url)
            .addOnSuccessListener { 
                if (isAdded) {
                    Toast.makeText(requireContext(), "Copertina aggiornata!", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
