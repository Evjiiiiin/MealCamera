package com.example.mealcamera.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
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

        apiKey = readMetaAsString(metaData, META_API_KEY)
        model = readMetaAsString(metaData, META_MODEL)
        version = readMetaAsString(metaData, META_VERSION)

        Log.d(
            TAG,
            "Roboflow config loaded: keyPresent=${apiKey.isNotBlank()}, model=$model, version=$version"
        )
    }

    fun isAvailable(): Boolean {
        if (apiKey.isBlank() || model.isBlank() || version.isBlank()) {
            Log.w(
                TAG,
                "Roboflow disabled: missing manifest meta-data (key=${apiKey.isNotBlank()}, model=${model.isNotBlank()}, version=${version.isNotBlank()})"
            )
            return false
        }

        if (!hasInternetConnection()) {
            Log.w(TAG, "Roboflow disabled: internet unavailable")
            return false
        }

        return true
    }

    fun detectFood(bitmap: Bitmap): List<DetectedFood> {
        if (!isAvailable()) return emptyList()

        return try {
            val imageBase64 = encodeBitmap(bitmap)
            val endpoint =
                "https://serverless.roboflow.com/$model/$version?api_key=$apiKey&confidence=${(REQUEST_CONFIDENCE * 100).toInt()}&overlap=30&format=json"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4_000
                readTimeout = 6_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            connection.outputStream.use { output ->
                output.write(imageBase64.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val body = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()

            if (responseCode !in 200..299) {
                Log.w(TAG, "Roboflow response code: $responseCode, body=$body")
                return emptyList()
            }

            parseDetections(body, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.w(TAG, "Roboflow detection failed, local model will be used", e)
            emptyList()
        }
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
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
                    ),
                    source = SOURCE_ROBOFLOW,
                    isLowConfidence = confidence in LOW_CONFIDENCE_MIN..LOW_CONFIDENCE_MAX
                )
            )
        }

        Log.d(TAG, "Roboflow detections count=${detections.size}")
        return detections.sortedByDescending { it.confidence }.take(MAX_DETECTIONS)
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun readMetaAsString(metaData: Bundle?, key: String): String {
        if (metaData == null || !metaData.containsKey(key)) return ""

        metaData.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        return runCatching { metaData.getInt(key).toString() }
            .getOrElse { "" }
    }

    companion object {
        private const val TAG = "RoboflowFoodDetector"
        private const val META_API_KEY = "com.example.mealcamera.ROBOFLOW_API_KEY"
        private const val META_MODEL = "com.example.mealcamera.ROBOFLOW_MODEL"
        private const val META_VERSION = "com.example.mealcamera.ROBOFLOW_VERSION"

        // Request parameter sent to Roboflow endpoint
        private const val REQUEST_CONFIDENCE = 0.25f
        // Local post-filter in app
        private const val CONFIDENCE_THRESHOLD = 0.35f
        private const val MIN_BOX_AREA_RATIO = 0.003f
        private const val MAX_DETECTIONS = 20
    }
}