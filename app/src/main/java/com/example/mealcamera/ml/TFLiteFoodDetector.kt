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

class TFLiteFoodDetector(context: Context) {
    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val inputSize = 640
    private val confidenceThreshold = 0.45f

    private var inputBuffer: ByteBuffer? = null
    private var pixels: IntArray? = null
    
    private var outputShape: IntArray = intArrayOf(1, 81, 8400)
    private var outputBuffer: Any? = null

    private val lock = ReentrantLock()
    private var isClosed = false

    init {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)

            val outTensor = interpreter!!.getOutputTensor(0)
            outputShape = outTensor.shape()
            
            outputBuffer = when (outputShape.size) {
                4 -> Array(outputShape[0]) { Array(outputShape[1]) { Array(outputShape[2]) { FloatArray(outputShape[3]) } } }
                3 -> Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
                else -> throw IllegalStateException("Unsupported output shape: ${outputShape.contentToString()}")
            }

            labels = appContext.assets.open("labels.txt").bufferedReader().readLines()
            Log.d("TFLiteFoodDetector", "✅ Модель загружена. Выход: ${outputShape.contentToString()}")
        } catch (e: Exception) {
            Log.e("TFLiteFoodDetector", "❌ Ошибка инициализации: ${e.message}")
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = appContext.assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun detectFood(bitmap: Bitmap): List<DetectedFood> {
        lock.withLock {
            if (isClosed || interpreter == null) return emptyList()

            try {
                val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
                val buffer = prepareInputBuffer(resized)
                if (resized != bitmap) resized.recycle()

                interpreter?.run(buffer, outputBuffer)
                
                @Suppress("UNCHECKED_CAST")
                val rawOutput = when (outputShape.size) {
                    4 -> (outputBuffer as Array<Array<Array<FloatArray>>>)[0][0]
                    3 -> (outputBuffer as Array<Array<FloatArray>>)[0]
                    else -> return emptyList()
                }

                return parseYoloV8Output(rawOutput, bitmap.width, bitmap.height)
            } catch (e: Exception) {
                Log.e("TFLiteFoodDetector", "Inference error: ${e.message}")
            }
            return emptyList()
        }
    }

    private fun parseYoloV8Output(output: Array<FloatArray>, imgWidth: Int, imgHeight: Int): List<DetectedFood> {
        val numElements = output[0].size
        val numClasses = output.size - 4
        val results = mutableListOf<DetectedFood>()

        for (i in 0 until numElements) {
            var maxScore = 0f
            var classIndex = -1

            for (c in 0 until numClasses) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    classIndex = c
                }
            }

            if (maxScore >= confidenceThreshold) {
                val cx = output[0][i]
                val cy = output[1][i]
                val w = output[2][i]
                val h = output[3][i]

                val xFactor = imgWidth.toFloat() / inputSize
                val yFactor = imgHeight.toFloat() / inputSize

                val left = (cx - w / 2f) * xFactor
                val top = (cy - h / 2f) * yFactor
                val right = (cx + w / 2f) * xFactor
                val bottom = (cy + h / 2f) * yFactor

                val label = labels.getOrNull(classIndex) ?: "Unknown"
                results.add(DetectedFood(
                    name = IngredientTranslator.translate(label),
                    originalLabel = label,
                    confidence = maxScore,
                    boundingBox = RectF(
                        left.coerceAtLeast(0f), 
                        top.coerceAtLeast(0f), 
                        right.coerceAtMost(imgWidth.toFloat()), 
                        bottom.coerceAtMost(imgHeight.toFloat())
                    )
                ))
            }
        }
        return nms(results)
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
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectedFood>()

        while (sorted.isNotEmpty()) {
            val first = sorted.removeAt(0)
            selected.add(first)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                val iou = calculateIoU(first.boundingBox!!, next.boundingBox!!)
                if (iou > 0.45f) {
                    iterator.remove()
                }
            }
        }
        return selected
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
        }
    }
}