package com.example.mealcamera.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.ui.home.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val MIN_SPLASH_DISPLAY_TIME_MS = 2000L // Минимум 2 секунды показа Splash-экрана

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val app = application as MealCameraApplication

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()

            // Ждем завершения инициализации приложения И минимального времени показа Splash-экрана
            // combine объединяет два Flow и выдает значение, когда оба emit-нули.
            combine(
                app.isAppInitialized, // Flow, который завершается, когда приложение инициализировано
                flow { // Искусственный Flow для обеспечения минимальной задержки
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < MIN_SPLASH_DISPLAY_TIME_MS) {
                        delay(MIN_SPLASH_DISPLAY_TIME_MS - elapsedTime)
                    }
                    emit(true) // Показываем, что минимальная задержка соблюдена
                }
            ) { isAppReady, minDelayMet ->
                isAppReady && minDelayMet
            }.collect { isReadyToProceed ->
                if (isReadyToProceed) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish() // Закрываем SplashActivity
                }
            }
        }
    }
}
