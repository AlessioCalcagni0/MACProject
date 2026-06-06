package com.example.myapplication.ui.social

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.ui.home.MainActivity
import com.google.android.gms.maps.model.LatLng
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
            goalTarget: String? = null,
            @Suppress("UNUSED_PARAMETER") pathPoints: List<LatLng> = emptyList()
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

        @Suppress("DEPRECATION")
        val stats = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable("stats", ArrayList::class.java)?.filterIsInstance<ParticipantLiveStats>()
        } else {
            (arguments?.getSerializable("stats") as? ArrayList<*>)?.filterIsInstance<ParticipantLiveStats>()
        } ?: emptyList()

        val photos = arguments?.getStringArrayList("photos") ?: emptyList()
        
        @Suppress("DEPRECATION")
        val namesMap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable("names_map", HashMap::class.java) as? Map<String, String>
        } else {
            arguments?.getSerializable("names_map") as? Map<String, String>
        } ?: emptyMap()

        val duration = arguments?.getInt("duration") ?: 0
        val isGroup = arguments?.getBoolean("is_group", true) ?: true
        
        runId = arguments?.getString("run_id")
        initialCoverUrl = arguments?.getString("cover_photo")

        val tvTitle = view.findViewById<TextView>(R.id.tvSummaryTitle)
        val tvDuration = view.findViewById<TextView>(R.id.tvSummaryDuration)
        val cardGoalResult = view.findViewById<MaterialCardView>(R.id.cardGoalResult)
        val tvParticipantsHeader = view.findViewById<TextView>(R.id.tvParticipantsHeader)
        val rvParticipantStats = view.findViewById<RecyclerView>(R.id.rvParticipantStats)
        val cardSingleStats = view.findViewById<MaterialCardView>(R.id.cardSingleStats)
        val tvSummaryTotalDistance = view.findViewById<TextView>(R.id.tvSummaryTotalDistance)
        val tvSummaryTotalCalories = view.findViewById<TextView>(R.id.tvSummaryTotalCalories)
        val tvSummaryAvgSpeed = view.findViewById<TextView>(R.id.tvSummaryAvgSpeed)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvSummaryPhotos)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseSummary)

        tvTitle?.text = if (isGroup) "Group Run Summary" else "Single Run Summary"

        val hrs = duration / 3600
        val mins = (duration % 3600) / 60
        val secs = duration % 60
        tvDuration?.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)

        if (isGroup) {
            tvParticipantsHeader?.visibility = View.VISIBLE
            rvParticipantStats?.visibility = View.VISIBLE
            cardSingleStats?.visibility = View.GONE
            rvParticipantStats?.layoutManager = LinearLayoutManager(requireContext())
            rvParticipantStats?.adapter = ParticipantStatsAdapter(stats, namesMap)
        } else {
            tvParticipantsHeader?.visibility = View.GONE
            rvParticipantStats?.visibility = View.GONE
            cardSingleStats?.visibility = View.VISIBLE
            val myStat = stats.firstOrNull()
            tvSummaryTotalDistance?.text = String.format(Locale.getDefault(), "%.2f km", myStat?.distance ?: 0.0)
            tvSummaryTotalCalories?.text = String.format(Locale.getDefault(), "%d kcal", myStat?.calories ?: 0)
            tvSummaryAvgSpeed?.text = String.format(Locale.getDefault(), "%.1f km/h", myStat?.speed ?: 0.0)
        }

        if (arguments?.containsKey("goal_achieved") == true) {
            val achieved = arguments?.getBoolean("goal_achieved") ?: false

            cardGoalResult?.visibility = View.VISIBLE

            val tvGoalStatus = view.findViewById<TextView>(R.id.tvGoalStatus)
            val tvGoalDetails = view.findViewById<TextView>(R.id.tvGoalDetails)

            if (achieved) {
                tvGoalStatus?.text = "CONGRATULATIONS! 🎉"
                tvGoalDetails?.text = "You reached your goal. Great job!"

                tvGoalStatus?.setTextColor(Color.parseColor("#4CAF50"))
                cardGoalResult?.setStrokeColor(Color.parseColor("#4CAF50"))
            } else {
                tvGoalStatus?.text = "GOAL NOT REACHED 🏃"
                tvGoalDetails?.text = "You missed your goal this time... But you can still try again!"

                tvGoalStatus?.setTextColor(Color.parseColor("#F44336"))
                cardGoalResult?.setStrokeColor(Color.parseColor("#F44336"))
            }
        }

        if (photos.isNotEmpty() || initialCoverUrl != null) {
            val allPhotos = if (initialCoverUrl != null && !photos.contains(initialCoverUrl)) {
                listOf(initialCoverUrl!!) + photos
            } else photos
            
            rvPhotos?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            rvPhotos?.adapter = GroupPhotosAdapter(allPhotos, isSelectionEnabled = true, selectedPhotoUrl = initialCoverUrl) { url ->
                initialCoverUrl = url
                updateCoverInFirebase(url)
            }
        }

        btnClose?.setOnClickListener {
            (activity as? MainActivity)?.switchFragmentByTag("HOME") ?: parentFragmentManager.beginTransaction().remove(this).commit()
        }
    }

    private fun updateCoverInFirebase(url: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val rId = runId ?: return

        FirebaseDatabase
            .getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app")
            .reference
            .child("users")
            .child(userId)
            .child("recent_activities")
            .child(rId)
            .child("coverPhoto")
            .setValue(url)
            .addOnSuccessListener {
                initialCoverUrl = url
            }
    }
}
