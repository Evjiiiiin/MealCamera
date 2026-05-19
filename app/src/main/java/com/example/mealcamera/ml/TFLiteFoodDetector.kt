package com.example.mealcamera.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.mealcamera.util.IngredientTranslator
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

class TFLiteFoodDetector(context: Context) {

    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    private val inputSize = 640

    // Локальный режим оставляем менее строгим, т.к. это fallback без интернета.
    // Далее стабильность усиливается в ScanViewModel (smoothing + tracking).
    private val minConfidenceThreshold = 0.40f
    private val nmsThreshold = 0.45f
    private val maxDetections = 50

    private var inputBuffer: ByteBuffer? = null
    private var pixels: IntArray? = null

    private var outputShape: IntArray = intArrayOf(1, 81, 8400)
    private var outputBuffer: Any? = null

    private val lock = ReentrantLock()
    private var isClosed = false

    init {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply { setNumThreads(4) }

            interpreter = Interpreter(modelBuffer, options)

            val outputTensor = interpreter!!.getOutputTensor(0)
            outputShape = outputTensor.shape()
            outputBuffer = createOutputBuffer(outputShape)

            labels = appContext.assets.open("labels.txt")
                .bufferedReader()
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            Log.d(TAG, "Модель загружена. Выход: ${outputShape.contentToString()}, labels=${labels.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации", e)
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = appContext.assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun createOutputBuffer(shape: IntArray): Any {
        return when (shape.size) {
            3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
            4 -> Array(shape[0]) { Array(shape[1]) { Array(shape[2]) { FloatArray(shape[3]) } } }
            else -> throw IllegalStateException("Unsupported output shape: ${shape.contentToString()}")
        }
    }

    fun detectFood(bitmap: Bitmap): List<DetectedFood> {
        lock.withLock {
            if (isClosed || interpreter == null || outputBuffer == null) return emptyList()

            try {
                val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
                val buffer = prepareInputBuffer(resized)
                if (resized != bitmap) resized.recycle()

                interpreter?.run(buffer, outputBuffer)
                val matrix = extractYoloMatrix() ?: return emptyList()
                return parseYoloV8Output(matrix, bitmap.width, bitmap.height)
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
            }
            return emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractYoloMatrix(): Array<FloatArray>? {
        return when (outputShape.size) {
            3 -> {
                val output = outputBuffer as Array<Array<FloatArray>>
                normalizeCandidateMatrix(output[0])
            }
            4 -> {
                val output = outputBuffer as Array<Array<Array<FloatArray>>>
                val batch = output[0]

                when {
                    // [1, 1, 8400, 81] / [1, 1, 8400, 84]
                    outputShape[1] == 1 -> normalizeCandidateMatrix(batch[0])

                    // [1, 81, 8400, 1]
                    outputShape[3] == 1 -> Array(outputShape[1]) { attr ->
                        FloatArray(outputShape[2]) { candidate -> batch[attr][candidate][0] }
                    }

                    else -> null
                }
            }
            else -> null
        }
    }

    private fun normalizeCandidateMatrix(matrix: Array<FloatArray>): Array<FloatArray> {
        if (matrix.isEmpty()) return matrix

        val rows = matrix.size
        val columns = matrix[0].size

        val expectedAttributes = labels.size + 4
        val expectedAttributesWithObjectness = labels.size + 5

        val rowsAreAttributes =
            rows == expectedAttributes ||
                    rows == expectedAttributesWithObjectness ||
                    rows < columns

        if (rowsAreAttributes) return matrix

        return Array(columns) { attr ->
            FloatArray(rows) { candidate -> matrix[candidate][attr] }
        }
    }

    private fun parseYoloV8Output(
        output: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectedFood> {
        if (output.size < 5 || output[0].isEmpty()) return emptyList()

        val attributes = output.size
        val candidates = output[0].size

        val hasObjectness = attributes >= labels.size + 5
        val classOffset = if (hasObjectness) 5 else 4
        val numClasses = min(labels.size, attributes - classOffset)
        if (numClasses <= 0) return emptyList()

        val results = mutableListOf<DetectedFood>()
        val coordinatesAreNormalized = coordinatesAreNormalized(output)

        for (candidate in 0 until candidates) {
            val objectness = if (hasObjectness) output[4][candidate].coerceIn(0f, 1f) else 1f
            if (objectness <= 0f) continue

            var maxClassScore = 0f
            var classIndex = -1

            for (classPosition in 0 until numClasses) {
                val score = output[classOffset + classPosition][candidate].coerceIn(0f, 1f)
                if (score > maxClassScore) {
                    maxClassScore = score
                    classIndex = classPosition
                }
            }

            val confidence = objectness * maxClassScore
            if (confidence < minConfidenceThreshold || classIndex < 0) continue

            val cx = output[0][candidate]
            val cy = output[1][candidate]
            val width = output[2][candidate]
            val height = output[3][candidate]

            val xFactor = if (coordinatesAreNormalized) imageWidth.toFloat() else imageWidth.toFloat() / inputSize
            val yFactor = if (coordinatesAreNormalized) imageHeight.toFloat() else imageHeight.toFloat() / inputSize

            val left = (cx - width / 2f) * xFactor
            val top = (cy - height / 2f) * yFactor
            val right = (cx + width / 2f) * xFactor
            val bottom = (cy + height / 2f) * yFactor
            if (right <= left || bottom <= top) continue

            val originalLabel = labels.getOrNull(classIndex) ?: "unknown"
            results.add(
                DetectedFood(
                    name = IngredientTranslator.translate(originalLabel),
                    originalLabel = originalLabel,
                    confidence = confidence,
                    boundingBox = RectF(
                        left.coerceIn(0f, imageWidth.toFloat()),
                        top.coerceIn(0f, imageHeight.toFloat()),
                        right.coerceIn(0f, imageWidth.toFloat()),
                        bottom.coerceIn(0f, imageHeight.toFloat())
                    ),
                    source = SOURCE_LOCAL,
                    isLowConfidence = confidence in LOW_CONFIDENCE_MIN..LOW_CONFIDENCE_MAX
                )
            )
        }

        return nms(results).take(maxDetections)
    }

    private fun coordinatesAreNormalized(output: Array<FloatArray>): Boolean {
        val sampleCount = min(100, output[0].size)
        if (sampleCount == 0) return false

        var maxCoordinate = 0f
        for (i in 0 until sampleCount) {
            maxCoordinate = maxOf(
                maxCoordinate,
                output[0][i],
                output[1][i],
                output[2][i],
                output[3][i]
            )
        }
        return maxCoordinate <= 2f
    }

    private fun prepareInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = inputBuffer ?: ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            inputBuffer = this
        }
        buffer.rewind()

        if (pixels == null || pixels?.size != inputSize * inputSize) {
            pixels = IntArray(inputSize * inputSize)
        }

        bitmap.getPixels(pixels!!, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels!!) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }

        return buffer
    }

    private fun nms(detections: List<DetectedFood>): List<DetectedFood> {
        if (detections.isEmpty()) return emptyList()

        return detections
            .groupBy { it.originalLabel }
            .values
            .flatMap { classDetections ->
                val sorted = classDetections.sortedByDescending { it.confidence }.toMutableList()
                val selected = mutableListOf<DetectedFood>()

                while (sorted.isNotEmpty()) {
                    val current = sorted.removeAt(0)
                    val currentBox = current.boundingBox ?: continue
                    selected.add(current)

                    val iterator = sorted.iterator()
                    while (iterator.hasNext()) {
                        val nextBox = iterator.next().boundingBox ?: continue
                        if (calculateIoU(currentBox, nextBox) > nmsThreshold) {
                            iterator.remove()
                        }
                    }
                }
                selected
            }
            .sortedByDescending { it.confidence }
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val left = maxOf(box1.left, box2.left)
        val top = maxOf(box1.top, box2.top)
        val right = minOf(box1.right, box2.right)
        val bottom = minOf(box1.bottom, box2.bottom)

        val intersection = if (right > left && bottom > top) (right - left) * (bottom - top) else 0f
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)
        val union = area1 + area2 - intersection

        return if (union <= 0f) 0f else intersection / union
    }

    fun close() {
        lock.withLock {
            isClosed = true
            interpreter?.close()
            interpreter = null
            outputBuffer = null
            inputBuffer = null
            pixels = null
        }
    }

    companion object {
        private const val TAG = "TFLiteFoodDetector"
    }
}