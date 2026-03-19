package com.example.myapplication.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.myapplication.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object AuthFirebaseDataSource {
    private const val TAG = "AuthFirebaseDataSource"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    suspend fun login(email: String, password: String): Boolean {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            false
        }
    }

    suspend fun register(name: String, email: String, password: String): Boolean {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            false
        }
    }

    suspend fun signInWithGoogle(context: Context): Boolean {
        Log.d(TAG, "Starting Google Sign-In process...")
        val credentialManager = CredentialManager.create(context)
        
        val webClientId = context.getString(R.string.default_web_client_id)
        Log.d(TAG, "Using Web Client ID: $webClientId")

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            Log.d(TAG, "Calling getCredential...")
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            Log.d(TAG, "getCredential returned a result of type: ${result.credential.type}")
            
            val credential = result.credential
            
            // Proviamo a estrarre il token usando il metodo statico della classe per maggiore compatibilità
            val googleIdTokenCredential = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create GoogleIdTokenCredential from data", e)
                null
            }

            if (googleIdTokenCredential != null) {
                val idToken = googleIdTokenCredential.idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(firebaseCredential).await()
                Log.d(TAG, "Firebase login successful")
                true
            } else {
                Log.e(TAG, "Received unexpected credential data format")
                false
            }
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In Error (GetCredentialException): ${e.message} - Type: ${e.type}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In Error (Generic): ${e.message}", e)
            false
        }
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun logout() {
        auth.signOut()
    }
}
