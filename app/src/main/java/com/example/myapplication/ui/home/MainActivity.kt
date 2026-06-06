package com.example.myapplication.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.SyncUserPayload
import com.example.myapplication.ui.profile.ProfileFragment
import com.example.myapplication.ui.run.RunFragment
import com.example.myapplication.ui.social.SocialFragment
import com.example.myapplication.ui.stats.StatsFragment
import com.example.myapplication.ui.social.GroupLobbyFragment
import com.example.myapplication.ui.social.GroupRunFragment
import com.example.myapplication.ui.social.GroupRunSummaryFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var ivToolbarProfile: ShapeableImageView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var tvToolbarTitle: TextView
    
    private val TAG_HOME = "HOME"
    private val TAG_RUN = "RUN"
    private val TAG_SOCIAL = "SOCIAL"
    private val TAG_STATS = "STATS"
    private val TAG_PROFILE = "PROFILE"
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivToolbarProfile = findViewById(R.id.ivToolbarProfile)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)

        syncUserWithBackend()
        updateToolbarProfileImage()

        if (savedInstanceState == null) {
            switchFragment(TAG_HOME, "Home") { HomeFragment() }
        }

        ivToolbarProfile.setOnClickListener {
            switchFragmentByTag(TAG_PROFILE)
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchFragment(TAG_HOME, "Home") { HomeFragment() }; true }
                R.id.nav_run -> { switchFragment(TAG_RUN, "Single Run") { RunFragment() }; true }
                R.id.nav_social -> { switchFragment(TAG_SOCIAL, "Social") { SocialFragment() }; true }
                R.id.nav_stats -> { switchFragment(TAG_STATS, "Stats") { StatsFragment() }; true }
                else -> false
            }
        }
    }

    fun navigateToFragment(fragment: Fragment, tag: String, title: String) {
        val transaction = supportFragmentManager.beginTransaction()
        activeFragment?.let { if (it.isAdded) transaction.hide(it) }
        transaction.add(R.id.nav_host_fragment, fragment, tag)
        transaction.addToBackStack(tag)
        activeFragment = fragment
        tvToolbarTitle.text = title
        ivToolbarProfile.visibility = View.GONE
        transaction.commit()
    }

    fun switchFragmentByTag(tag: String) {
        val menuId = when(tag) {
            TAG_HOME -> R.id.nav_home
            TAG_RUN -> R.id.nav_run
            TAG_SOCIAL -> R.id.nav_social
            TAG_STATS -> R.id.nav_stats
            else -> -1
        }
        if (menuId != -1) {
            bottomNavigation.selectedItemId = menuId
        } else if (tag == TAG_PROFILE) {
            switchFragment(TAG_PROFILE, "Profile") { ProfileFragment() }
        }
    }

    private fun switchFragment(tag: String, title: String, creator: () -> Fragment) {
        val fm = supportFragmentManager
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val transaction = fm.beginTransaction()

        fm.fragments.forEach { 
            if (it is GroupLobbyFragment || it is GroupRunFragment || it is GroupRunSummaryFragment) {
                transaction.remove(it)
            }
        }

        val mainTags = listOf(TAG_HOME, TAG_RUN, TAG_SOCIAL, TAG_STATS, TAG_PROFILE)
        mainTags.forEach { t ->
            fm.findFragmentByTag(t)?.let { transaction.hide(it) }
        }

        val existing = fm.findFragmentByTag(tag)
        if (existing != null) {
            transaction.show(existing)
            activeFragment = existing
        } else {
            val newFragment = creator()
            transaction.add(R.id.nav_host_fragment, newFragment, tag)
            activeFragment = newFragment
        }
        
        transaction.commit()
        tvToolbarTitle.text = title
        val isMainSection = tag in listOf(TAG_HOME, TAG_RUN, TAG_SOCIAL, TAG_STATS)
        ivToolbarProfile.visibility = if (isMainSection) View.VISIBLE else View.GONE
        updateToolbarProfileImage()
    }

    fun updateToolbarProfileImage() {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            if (it.photoUrl != null) {
                Glide.with(this).load(it.photoUrl).circleCrop().into(ivToolbarProfile)
            } else {
                ivToolbarProfile.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    private fun syncUserWithBackend() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val payload = SyncUserPayload(
            displayName = user.displayName ?: "Runner",
            email = user.email ?: ""
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = Tasks.await(user.getIdToken(false)).token
                if (token != null) RetrofitClient.api.syncUser("Bearer $token", payload)
            } catch (e: Exception) {}
        }
    }
}
