package com.example.mealcamera.data.dao

import androidx.room.*
import com.example.mealcamera.data.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ShoppingListItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: ShoppingListItem): Long

    @Update
    suspend fun updateItem(item: ShoppingListItem)

    @Delete
    suspend fun deleteItem(item: ShoppingListItem)

    @Query("SELECT * FROM shopping_list WHERE LOWER(name) = LOWER(:name) AND LOWER(unit) = LOWER(:unit) AND userId = :userId LIMIT 1")
    suspend fun getItemByNameAndUnit(userId: String, name: String, unit: String): ShoppingListItem?

    @Query("SELECT * FROM shopping_list WHERE userId = :userId ORDER BY isChecked ASC, name ASC")
    fun getAllItems(userId: String): Flow<List<ShoppingListItem>>

    @Query("DELETE FROM shopping_list WHERE isChecked = 1 AND userId = :userId")
    suspend fun deleteCheckedItems(userId: String)
}