package com.example.myapplication.ui.home

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.ui.profile.ProfileFragment
import com.example.myapplication.ui.run.RunFragment
import com.example.myapplication.ui.social.SocialFragment
import com.example.myapplication.ui.stats.StatsFragment
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
            val home = HomeFragment()
            supportFragmentManager.beginTransaction().add(R.id.nav_host_fragment, home, TAG_HOME).commit()
            activeFragment = home
            tvToolbarTitle.text = "Home"
        } else {
            activeFragment = supportFragmentManager.fragments.firstOrNull { it.isVisible }
            updateTitleByTag(activeFragment?.tag ?: TAG_HOME)
        }

        ivToolbarProfile.setOnClickListener {
            switchFragment(TAG_PROFILE, "Profilo") { ProfileFragment() }
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchFragment(TAG_HOME, "Home") { HomeFragment() }; true }
                R.id.nav_run -> { switchFragment(TAG_RUN, "Corsa Singola") { RunFragment() }; true }
                R.id.nav_social -> { switchFragment(TAG_SOCIAL, "Social") { SocialFragment() }; true }
                R.id.nav_stats -> { switchFragment(TAG_STATS, "Statistiche") { StatsFragment() }; true }
                else -> false
            }
        }
    }

    fun updateToolbarProfileImage() {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            if (it.photoUrl != null) {
                Glide.with(this)
                    .load(it.photoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(ivToolbarProfile)
            } else {
                ivToolbarProfile.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateToolbarProfileImage()
    }

    private fun syncUserWithBackend() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userData = mapOf(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "display_name" to (user.displayName ?: "Runner")
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tokenResult = Tasks.await(user.getIdToken(false))
                val token = tokenResult.token
                if (token != null) {
                    RetrofitClient.api.syncUser("Bearer $token", userData)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Errore sincronizzazione: ${e.message}")
            }
        }
    }

    private fun switchFragment(tag: String, title: String, creator: () -> Fragment) {
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        
        val transaction = fm.beginTransaction()
        
        activeFragment?.let { transaction.hide(it) }

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
        updateToolbarProfileImage()
    }

    private fun updateTitleByTag(tag: String) {
        tvToolbarTitle.text = when(tag) {
            TAG_HOME -> "Home"
            TAG_RUN -> "Corsa Singola"
            TAG_SOCIAL -> "Social"
            TAG_STATS -> "Statistiche"
            TAG_PROFILE -> "Profilo"
            else -> "Pacemate"
        }
    }
}
