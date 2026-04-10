package com.example.mealcamera.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.mealcamera.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

abstract class BaseActivity : AppCompatActivity() {

    private val logoutHandler = Handler(Looper.getMainLooper())
    private val logoutRunnable = Runnable {
        performAutoLogout()
    }

    companion object {
        // 30 минут = 30 * 60 * 1000 миллисекунд
        private const val INACTIVITY_TIMEOUT = 30 * 60 * 1000L
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetInactivityTimer()
    }

    override fun onResume() {
        super.onResume()
        resetInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        stopInactivityTimer()
    }

    private fun resetInactivityTimer() {
        stopInactivityTimer()
        // Не запускаем таймер, если пользователь не авторизован
        if (FirebaseAuth.getInstance().currentUser != null) {
            logoutHandler.postDelayed(logoutRunnable, INACTIVITY_TIMEOUT)
        }
    }

    private fun stopInactivityTimer() {
        logoutHandler.removeCallbacks(logoutRunnable)
    }

    private fun performAutoLogout() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("auto_logout", true)
        }
        startActivity(intent)
        finish()
    }
}
