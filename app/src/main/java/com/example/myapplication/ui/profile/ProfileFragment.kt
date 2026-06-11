package com.example.myapplication.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.ui.auth.AuthChoiceActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class ProfileFragment : Fragment() {

    private val repository = AuthRepositoryImpl()

    private lateinit var ivProfileImage: ImageView
    private lateinit var tvFullName: TextView
    private lateinit var tvEmail: TextView

    private val databaseUrl =
        "https://maccproject-7f15c-default-rtdb.europe-west1.firebasedatabase.app"

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageUri: Uri? = result.data?.data
                imageUri?.let { uploadProfileImageToFirebase(it) }
            }
        }

    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openGallery()
            } else {
                Toast.makeText(
                    context,
                    "Photo permission is required to choose a profile image",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    private fun checkGalleryPermissionAndOpen() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openGallery()
        } else {
            requestGalleryPermissionLauncher.launch(permission)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivProfileImage = view.findViewById(R.id.ivProfileImage)
        tvFullName = view.findViewById(R.id.tvProfileFullName)
        tvEmail = view.findViewById(R.id.tvProfileEmail)

        val btnLogout = view.findViewById<MaterialButton>(R.id.btnProfileLogout)
        val cardProfileImage = view.findViewById<MaterialCardView>(R.id.cardProfileImage)

        val currentUser = FirebaseAuth.getInstance().currentUser

        tvEmail.text = currentUser?.email ?: "No email"
        tvFullName.text = currentUser?.displayName ?: "Pacemate User"

        currentUser?.photoUrl?.let { photoUrl ->
            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(ivProfileImage)
        }

        cardProfileImage.setOnClickListener {
            checkGalleryPermissionAndOpen()
        }

        btnLogout.setOnClickListener {
            repository.logout()

            val intent = Intent(requireContext(), AuthChoiceActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            activity?.finish()
        }
    }

    private fun uploadProfileImageToFirebase(uri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid

        Toast.makeText(context, "Uploading profile photo...", Toast.LENGTH_SHORT).show()

        val storageRef = FirebaseStorage
            .getInstance()
            .reference
            .child("profile_images")
            .child(uid)
            .child("profile.jpg")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        saveProfilePhotoUrl(downloadUri)
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            context,
                            "Error getting image URL",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Upload error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun saveProfilePhotoUrl(photoUri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val photoUrl = photoUri.toString()

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(photoUri)
            .build()

        user.updateProfile(profileUpdates)
            .addOnSuccessListener {
                FirebaseDatabase
                    .getInstance(databaseUrl)
                    .reference
                    .child("users")
                    .child(user.uid)
                    .child("profile_photo_url")
                    .setValue(photoUrl)
                    .addOnSuccessListener {
                        Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .into(ivProfileImage)

                        Toast.makeText(
                            context,
                            "Profile photo updated!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            context,
                            "Database error: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Profile update error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}