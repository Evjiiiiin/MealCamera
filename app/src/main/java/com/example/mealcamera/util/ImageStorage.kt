package com.example.mealcamera.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

class ImageStorage(private val context: Context) {/**
 * Сохраняет Bitmap в приватное хранилище приложения.
 * @param bitmap Изображение для сохранения.
 * @param filename Имя файла.
 * @return Путь к сохраненному файлу.
 */
fun saveImage(bitmap: Bitmap, filename: String): String {
    // Создаем директорию для изображений, если ее нет
    val directory = File(context.filesDir, "ingredient_thumbs")
    if (!directory.exists()) {
        directory.mkdirs()
    }

    val file = File(directory, filename)
    try {
        FileOutputStream(file).use { out ->
            // Сжимаем и сохраняем изображение
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return file.absolutePath
}
}
