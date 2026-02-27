package com.example.mealcamera.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.ui.auth.LoginActivity
import com.example.mealcamera.ui.home.MainActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val MIN_SPLASH_DISPLAY_TIME_MS = 2000L
        private const val APP_INIT_TIMEOUT_MS = 7000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val app = application as MealCameraApplication

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()

            // Ждем инициализацию приложения, но не бесконечно.
            withTimeoutOrNull(APP_INIT_TIMEOUT_MS) {
                app.isAppInitialized.first { it }
            }

            // Гарантируем минимальную длительность splash для плавного UX.
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < MIN_SPLASH_DISPLAY_TIME_MS) {
                delay(MIN_SPLASH_DISPLAY_TIME_MS - elapsed)
            }

            val destination = if (FirebaseAuth.getInstance().currentUser == null) {
                LoginActivity::class.java
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
