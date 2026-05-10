package com.example.mealcamera.util

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Хелпер для интеграции Yandex Auth SDK версии 3.2.0.
 */
class YandexAuthHelper(private val context: Context) {

    private val sdk: YandexAuthSdk by lazy {
        YandexAuthSdk.create(YandexAuthOptions(context.applicationContext))
    }

    /**
     * Запуск процесса авторизации.
     */
    fun signIn(launcher: ActivityResultLauncher<Intent>) {
        try {
            val loginOptions = YandexAuthLoginOptions()
            val intent = sdk.contract.createIntent(context, loginOptions)
            launcher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Обработка результата и извлечение токена.
     */
    fun handleResult(resultCode: Int, data: Intent?): String? {
        return try {
            val result = sdk.contract.parseResult(resultCode, data)
            if (result is YandexAuthResult.Success) {
                result.token.value
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Получение данных профиля через API Яндекса по OAuth токену.
     */
    suspend fun fetchUserInfo(token: String): JSONObject? = withContext(Dispatchers.IO) {
        val url = URL("https://login.yandex.ru/info?format=json")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "OAuth $token")
        try {
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                JSONObject(response)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection.disconnect()
        }
    }
}