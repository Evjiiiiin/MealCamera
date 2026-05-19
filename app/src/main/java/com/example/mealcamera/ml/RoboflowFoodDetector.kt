package com.example.mealcamera.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.example.mealcamera.util.IngredientTranslator
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class RoboflowFoodDetector(context: Context) {

    private val appContext = context.applicationContext
    private val apiKey: String
    private val model: String
    private val version: String

    init {
        val metaData = appContext.packageManager
            .getApplicationInfo(appContext.packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData
        apiKey = metaData?.getString(META_API_KEY).orEmpty()
        model = metaData?.getString(META_MODEL).orEmpty()
        version = metaData?.getString(META_VERSION).orEmpty()
    }

    fun isAvailable(): Boolean = apiKey.isNotBlank() && model.isNotBlank() && version.isNotBlank() && hasInternetConnection()

    fun detectFood(bitmap: Bitmap): List<DetectedFood> {
        if (!isAvailable()) return emptyList()
        return try {
            val imageBase64 = encodeBitmap(bitmap)
            val endpoint = "https://serverless.roboflow.com/$model/$version?api_key=$apiKey&confidence=${(CONFIDENCE_THRESHOLD * 100).toInt()}&overlap=30&format=json"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1_500
                readTimeout = 2_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            connection.outputStream.use { output ->
                output.write(imageBase64.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Roboflow response code: ${connection.responseCode}")
                connection.disconnect()
                return emptyList()
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parseDetections(body, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.w(TAG, "Roboflow detection failed, local model will be used", e)
            emptyList()
        }
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseDetections(body: String, imageWidth: Int, imageHeight: Int): List<DetectedFood> {
        val predictions = JSONObject(body).optJSONArray("predictions") ?: return emptyList()
        val detections = mutableListOf<DetectedFood>()
        for (index in 0 until predictions.length()) {
            val item = predictions.optJSONObject(index) ?: continue
            val confidence = item.optDouble("confidence", 0.0).toFloat()
            if (confidence < CONFIDENCE_THRESHOLD) continue

            val label = item.optString("class", "").trim()
            if (label.isBlank()) continue

            val width = item.optDouble("width", 0.0).toFloat()
            val height = item.optDouble("height", 0.0).toFloat()
            if (width * height < imageWidth * imageHeight * MIN_BOX_AREA_RATIO) continue

            val centerX = item.optDouble("x", 0.0).toFloat()
            val centerY = item.optDouble("y", 0.0).toFloat()
            detections.add(
                DetectedFood(
                    name = IngredientTranslator.translate(label),
                    originalLabel = label.lowercase(Locale.ROOT),
                    confidence = confidence,
                    boundingBox = RectF(
                        (centerX - width / 2f).coerceIn(0f, imageWidth.toFloat()),
                        (centerY - height / 2f).coerceIn(0f, imageHeight.toFloat()),
                        (centerX + width / 2f).coerceIn(0f, imageWidth.toFloat()),
                        (centerY + height / 2f).coerceIn(0f, imageHeight.toFloat())
                    )
                )
            )
        }
        return detections.sortedByDescending { it.confidence }.take(MAX_DETECTIONS)
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "RoboflowFoodDetector"
        private const val META_API_KEY = "com.example.mealcamera.ROBOFLOW_API_KEY"
        private const val META_MODEL = "com.example.mealcamera.ROBOFLOW_MODEL"
        private const val META_VERSION = "com.example.mealcamera.ROBOFLOW_VERSION"
        private const val CONFIDENCE_THRESHOLD = 0.75f
        private const val MIN_BOX_AREA_RATIO = 0.015f
        private const val MAX_DETECTIONS = 20
    }
}