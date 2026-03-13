package com.example.mealcamera.data.dao

import androidx.room.*
import com.example.mealcamera.data.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ShoppingListItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ShoppingListItem)

    @Update
    suspend fun updateItem(item: ShoppingListItem)

    @Query("SELECT * FROM shopping_list ORDER BY isChecked ASC, name ASC")
    fun getAllItems(): Flow<List<ShoppingListItem>>

    @Query("DELETE FROM shopping_list WHERE id = :itemId")
    suspend fun delete(itemId: Long)

    @Query("DELETE FROM shopping_list WHERE isChecked = 1")
    suspend fun deleteCheckedItems()

    @Query("DELETE FROM shopping_list")
    suspend fun deleteAll()
}