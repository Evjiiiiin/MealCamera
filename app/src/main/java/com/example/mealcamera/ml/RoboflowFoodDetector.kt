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
                "https://serverless.roboflow.com/$model/$version?api_key=$apiKey&confidence=${(REQUEST_CONFIDENCE * 100).toInt()}&overlap=$REQUEST_OVERLAP&format=json"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
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
                Log.w(TAG, "Roboflow non-2xx: code=$responseCode, body=$body")
                return emptyList()
            }

            parseDetections(body, bitmap.width, bitmap.height, responseCode)
        } catch (e: Exception) {
            Log.w(TAG, "Roboflow detection failed (network/parse), local fallback should be used", e)
            emptyList()
        }
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseDetections(
        body: String,
        imageWidth: Int,
        imageHeight: Int,
        responseCode: Int
    ): List<DetectedFood> {
        val predictions = JSONObject(body).optJSONArray("predictions")
        if (predictions == null) {
            Log.w(TAG, "Roboflow response has no predictions array: code=$responseCode")
            return emptyList()
        }

        val rawCount = predictions.length()
        val detections = mutableListOf<DetectedFood>()
        var rejectedByConfidence = 0
        var rejectedByLabel = 0
        var rejectedByArea = 0

        for (index in 0 until rawCount) {
            val item = predictions.optJSONObject(index) ?: continue
            val confidence = item.optDouble("confidence", 0.0).toFloat()
            if (confidence < POST_FILTER_CONFIDENCE) {
                rejectedByConfidence++
                continue
            }

            val label = item.optString("class", "").trim()
            if (label.isBlank()) {
                rejectedByLabel++
                continue
            }

            val width = item.optDouble("width", 0.0).toFloat()
            val height = item.optDouble("height", 0.0).toFloat()
            if (width * height < imageWidth * imageHeight * MIN_BOX_AREA_RATIO) {
                rejectedByArea++
                continue
            }

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

        val finalDetections = detections.sortedByDescending { it.confidence }.take(MAX_DETECTIONS)
        Log.d(
            TAG,
            "Roboflow parsed: code=$responseCode, raw=$rawCount, keptBeforeLimit=${detections.size}, final=${finalDetections.size}, " +
                    "rejectConfidence=$rejectedByConfidence, rejectLabel=$rejectedByLabel, rejectArea=$rejectedByArea"
        )
        return finalDetections
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

        // DEMO PROFILE: консервативные пороги для стабильности.
        // Отдельный порог для сервера: чем выше, тем меньше шум в ответе API.
        private const val REQUEST_CONFIDENCE = 0.30f // старое: 0.25
        // Дополнительный локальный фильтр после ответа API.
        private const val POST_FILTER_CONFIDENCE = 0.40f // старое: 0.35
        private const val REQUEST_OVERLAP = 30
        private const val MIN_BOX_AREA_RATIO = 0.004f // старое: 0.003
        private const val MAX_DETECTIONS = 12 // старое: 20

        private const val JPEG_QUALITY = 90
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val READ_TIMEOUT_MS = 6_000
    }
}