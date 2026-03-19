package com.example.myapplication.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        val tvUserEmailFragment = view.findViewById<TextView>(R.id.tvUserEmailFragment)
        
        val currentUser = FirebaseAuth.getInstance().currentUser
        tvUserEmailFragment.text = currentUser?.email ?: "Utente"
        
        return view
    }
}
