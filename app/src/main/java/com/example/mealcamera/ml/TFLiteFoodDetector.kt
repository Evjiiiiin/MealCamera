package com.example.mealcamera.ml

import android.content.Context
import android.graphics.Bitmap
import com.example.mealcamera.util.IngredientTranslator
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteFoodDetector(private val context: Context) {
    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize = 224
    private val numBytesPerChannel = 4
    private val numChannels = 3

    init {
        val modelBuffer = loadModelFile()
        interpreter = Interpreter(modelBuffer)
        labels = context.assets.open("labels.txt").bufferedReader().readLines()
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = context.assets.openFd("model_unquant.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    suspend fun detectFood(bitmap: Bitmap): List<DetectedFood> {
        return try {
            val inputBuffer = preprocessImage(bitmap)
            val outputArray = Array(1) { FloatArray(labels.size) }
            interpreter.run(inputBuffer, outputArray)
            val results = mutableListOf<DetectedFood>()
            val probabilities = outputArray[0]
            probabilities
                .withIndex()
                .sortedByDescending { it.value }
                .take(3)
                .filter { it.value > 0.3f }
                .forEach { (index, confidence) ->
                    val labelParts = labels[index].split(" ", limit = 2)
                    val englishName = if (labelParts.size > 1) labelParts[1] else labelParts[0]

                    val russianName = IngredientTranslator.translate(englishName)
                    results.add(
                        DetectedFood(
                            name = russianName,
                            originalLabel = englishName,
                            confidence = confidence,
                            imagePath = ""
                        )
                    )
                }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(
            1 * inputSize * inputSize * numChannels * numBytesPerChannel
        )
        byteBuffer.order(ByteOrder.nativeOrder())
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        return byteBuffer
    }
}
