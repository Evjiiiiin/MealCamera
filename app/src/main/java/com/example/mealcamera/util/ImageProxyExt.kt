package com.example.mealcamera.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

// Переименовано из toBitmap() в toBitmapSafe() чтобы избежать конфликта
fun ImageProxy.toBitmapSafe(): Bitmap {
    val planeProxy = planes[0]
    val buffer: ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val rotationDegrees = imageInfo.rotationDegrees

    val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
    } else {
        originalBitmap
    }
}