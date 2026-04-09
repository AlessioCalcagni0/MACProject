package com.example.myapplication.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.ui.auth.AuthChoiceActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private val repository = AuthRepositoryImpl()
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvFullName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var etWeight: EditText
    private lateinit var btnUpdateWeight: MaterialButton

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let { uploadProfileImage(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivProfileImage = view.findViewById(R.id.ivProfileImage)
        tvFullName = view.findViewById(R.id.tvProfileFullName)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        etWeight = view.findViewById(R.id.etProfileWeight)
        btnUpdateWeight = view.findViewById(R.id.btnUpdateWeight)
        
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnProfileLogout)
        val cardProfileImage = view.findViewById<MaterialCardView>(R.id.cardProfileImage)

        val currentUser = FirebaseAuth.getInstance().currentUser
        tvEmail.text = currentUser?.email ?: "No email"
        tvFullName.text = currentUser?.displayName ?: "Pacemate User"

        currentUser?.photoUrl?.let {
            Glide.with(this).load(it).into(ivProfileImage)
        }

        cardProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        btnLogout.setOnClickListener {
            repository.logout()
            val intent = Intent(requireContext(), AuthChoiceActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }

        btnUpdateWeight.setOnClickListener { updateWeight() }
        
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { result ->
            val token = "Bearer ${result.token}"
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.api.syncUser(token, emptyMap<String, Any>())
                    withContext(Dispatchers.Main) {
                        response.weight?.let {
                            etWeight.setText(it.toString())
                        }
                        // Update name if necessary
                        response.display_name?.let {
                            tvFullName.text = it
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error loading profile", e)
                }
            }
        }
    }

    private fun updateWeight() {
        val weightStr = etWeight.text.toString()
        val weight = weightStr.toFloatOrNull()
        if (weight == null || weight <= 0) {
            Toast.makeText(context, "Please enter a valid weight", Toast.LENGTH_SHORT).show()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { result ->
            val token = "Bearer ${result.token}"
            lifecycleScope.launch {
                try {
                    val payload = mapOf("weight" to weight)
                    val response = RetrofitClient.api.syncUser(token, payload)
                    withContext(Dispatchers.Main) {
                        response.weight?.let {
                            etWeight.setText(it.toString())
                        }
                        Toast.makeText(context, "Weight updated!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error updating weight", e)
                    Toast.makeText(context, "Error saving weight", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uploadProfileImage(uri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(uri)
            .build()

        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Glide.with(this).load(uri).into(ivProfileImage)
                Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error during update", Toast.LENGTH_SHORT).show()
            }
        }
    }
}