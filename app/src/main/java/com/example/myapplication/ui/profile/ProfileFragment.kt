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

class ProfileFragment : Fragment() {

    private val repository = AuthRepositoryImpl()
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvFullName: TextView
    private lateinit var tvEmail: TextView

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
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnProfileLogout)
        val cardProfileImage = view.findViewById<MaterialCardView>(R.id.cardProfileImage)

        val currentUser = FirebaseAuth.getInstance().currentUser
        tvEmail.text = currentUser?.email ?: "Nessuna email"
        tvFullName.text = currentUser?.displayName ?: "Utente Pacemate"

        // Carica immagine profilo esistente
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
    }

    private fun uploadProfileImage(uri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        // In un'app reale caricheresti su Firebase Storage, qui aggiorniamo localmente per ora
        // e simuliamo il salvataggio nel profilo Firebase
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(uri)
            .build()

        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Glide.with(this).load(uri).into(ivProfileImage)
                Toast.makeText(context, "Foto profilo aggiornata!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Erro le durante l'aggiornamento", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
