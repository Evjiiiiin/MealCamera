package com.example.mealcamera.util

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.example.mealcamera.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

@Suppress("DEPRECATION")
class GoogleAuthHelper(private val context: Context) {

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    fun signIn(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(googleSignInClient.signInIntent)
    }

    fun getCredential(data: Intent?): AuthCredential? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(Exception::class.java)
            GoogleAuthProvider.getCredential(account?.idToken, null)
        } catch (e: Exception) {
            null
        }
    }

    fun signOut() {
        googleSignInClient.signOut()
    }
}