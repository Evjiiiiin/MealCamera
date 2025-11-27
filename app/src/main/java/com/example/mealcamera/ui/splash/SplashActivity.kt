package com.example.mealcamera.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.mealcamera.R
import com.example.mealcamera.ui.home.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Задержка в 3 секунды (2000 миллисекунд)
        Handler(Looper.getMainLooper()).postDelayed({
            // Создаем Intent для перехода на MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            // Закрываем SplashActivity, чтобы пользователь не мог на него вернуться
            finish()
        }, 3000)
    }
}
