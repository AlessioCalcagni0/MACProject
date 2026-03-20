package com.example.myapplication.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.myapplication.R
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class HomeFragment : Fragment() {

    private lateinit var tvCity: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvWeatherDesc: TextView
    private lateinit var tvRunningAdvice: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        val tvUserEmailFragment = view.findViewById<TextView>(R.id.tvUserEmailFragment)
        tvCity = view.findViewById(R.id.tvCity)
        tvTemp = view.findViewById(R.id.tvTemp)
        tvWeatherDesc = view.findViewById(R.id.tvWeatherDesc)
        tvRunningAdvice = view.findViewById(R.id.tvRunningAdvice)

        val currentUser = FirebaseAuth.getInstance().currentUser
        tvUserEmailFragment.text = currentUser?.email ?: "Utente"
        
        checkLocationPermission()
        
        return view
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            getDeviceLocation()
        } else {
            // Roma default se mancano i permessi
            fetchWeatherData(41.89, 12.49)
        }
    }

    private fun getDeviceLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    fetchWeatherData(location.latitude, location.longitude)
                } else {
                    fetchWeatherData(41.89, 12.49)
                }
            }
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "SecurityException: ${e.message}")
        }
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        // API di Open-Meteo: gratuita, senza chiavi e illimitata per uso personale
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"

        val queue = Volley.newRequestQueue(requireContext())
        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                updateWeatherUI(response)
            },
            { error ->
                tvCity.text = "Errore meteo"
                Log.e("WeatherAPI", "Error: ${error.message}")
                tvRunningAdvice.text = "Impossibile caricare i dati meteo al momento."
            }
        )
        queue.add(jsonObjectRequest)
    }

    private fun updateWeatherUI(response: JSONObject) {
        try {
            val current = response.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val weatherCode = current.getInt("weather_code")

            tvCity.text = "Meteo Attuale"
            tvTemp.text = "${temp.toInt()}°C"
            
            val (description, isBadWeather) = mapWeatherCode(weatherCode)
            tvWeatherDesc.text = description

            setRunningAdvice(temp, isBadWeather)
        } catch (e: Exception) {
            Log.e("HomeFragment", "JSON Parsing error: ${e.message}")
        }
    }

    private fun mapWeatherCode(code: Int): Pair<String, Boolean> {
        // Mapping dei codici WMO (World Meteorological Organization)
        return when (code) {
            0 -> "Cielo sereno" to false
            1, 2, 3 -> "Parzialmente nuvoloso" to false
            45, 48 -> "Nebbia" to false
            51, 53, 55 -> "Pioggerellina" to true
            61, 63, 65 -> "Pioggia" to true
            71, 73, 75 -> "Neve" to true
            80, 81, 82 -> "Rovesci di pioggia" to true
            95, 96, 99 -> "Temporale" to true
            else -> "Variabile" to false
        }
    }

    private fun setRunningAdvice(temp: Double, isBadWeather: Boolean) {
        val advice = when {
            isBadWeather -> 
                "Il tempo non è ideale per correre. Forse meglio un allenamento indoor oggi? 🏠"
            temp > 30 -> 
                "Fa molto caldo! Se decidi di correre, resta all'ombra e bevi molta acqua. ☀️🔥"
            temp < 5 -> 
                "Fa freddo! Assicurati di coprirti bene prima di uscire. 🧣"
            temp in 10.0..22.0 -> 
                "Clima perfetto per una corsa! Esci e goditela al massimo. 🏃‍♂️✨"
            else -> 
                "Il tempo è discreto per correre. Buon allenamento! 👍"
        }
        tvRunningAdvice.text = advice
    }
}
