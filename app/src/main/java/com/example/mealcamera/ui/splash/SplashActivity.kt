package com.example.mealcamera.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.R
import com.example.mealcamera.ui.auth.WelcomeActivity
import com.example.mealcamera.ui.home.MainActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DISPLAY_TIME_MS = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            delay(SPLASH_DISPLAY_TIME_MS)

            val destination = if (FirebaseAuth.getInstance().currentUser == null) {
                WelcomeActivity::class.java
            } else {
                MainActivity::class.java
            }

            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this@SplashActivity, destination))
                finish()
            }
        }
    }
}
