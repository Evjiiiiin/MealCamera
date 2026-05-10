package com.example.mealcamera.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class ImageStorage(private val context: Context) {

    private val avatarsDir: File = File(context.filesDir, "avatars")
    private val recipeImagesDir: File = File(context.filesDir, "recipe_images")
    private val stepImagesDir: File = File(context.filesDir, "step_images")
    private val scannedImagesDir: File = File(context.filesDir, "scanned_ingredients")

    init {
        if (!avatarsDir.exists()) avatarsDir.mkdirs()
        if (!recipeImagesDir.exists()) recipeImagesDir.mkdirs()
        if (!stepImagesDir.exists()) stepImagesDir.mkdirs()
        if (!scannedImagesDir.exists()) scannedImagesDir.mkdirs()
    }

    fun saveAvatar(userId: String, bitmap: Bitmap): String {
        val file = File(avatarsDir, "$userId.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    fun getAvatarFile(userId: String): File {
        return File(avatarsDir, "$userId.jpg")
    }

    fun getAvatarPath(userId: String): String {
        return File(avatarsDir, "$userId.jpg").absolutePath
    }

    fun saveRecipeImage(recipeId: Long, bitmap: Bitmap): String {
        val fileName = "recipe_${System.currentTimeMillis()}_$recipeId.jpg"
        val file = File(recipeImagesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    fun saveStepImage(recipeId: Long, stepIndex: Int, bitmap: Bitmap): String {
        val fileName = "step_${recipeId}_${stepIndex}_${System.currentTimeMillis()}.jpg"
        val file = File(stepImagesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    fun saveImage(bitmap: Bitmap, filename: String): String {
        val file = File(scannedImagesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    fun deleteImage(path: String) {
        try {
            File(path).delete()
        } catch (e: Exception) { }
    }
}