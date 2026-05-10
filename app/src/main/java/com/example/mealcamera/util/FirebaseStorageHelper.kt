package com.example.mealcamera.util

import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

class FirebaseStorageHelper {
    private val storage = FirebaseStorage.getInstance().reference

    suspend fun uploadRecipeImage(bitmap: Bitmap): String? {
        val fileName = "recipes/${UUID.randomUUID()}.jpg"
        val ref = storage.child(fileName)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val data = baos.toByteArray()

        return try {
            ref.putBytes(data).await()
            val downloadUrl = ref.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun uploadProfileImage(userId: String, uri: Uri): String? {
        val ref = storage.child("avatars/$userId.jpg")
        return try {
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
