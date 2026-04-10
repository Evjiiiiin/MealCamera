package com.example.mealcamera.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_list",
    indices = [Index(value = ["userId"], name = "index_shopping_list_userId")]
)
data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "")
    val userId: String,
    val name: String,
    val quantity: String = "",
    val unit: String = "",
    val isChecked: Boolean = false
)