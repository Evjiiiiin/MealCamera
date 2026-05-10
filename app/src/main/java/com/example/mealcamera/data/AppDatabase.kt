package com.example.mealcamera.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mealcamera.data.dao.FavoriteDao
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.dao.ShoppingListDao
import com.example.mealcamera.data.model.*

@Database(
    entities = [
        Recipe::class,
        Ingredient::class,
        RecipeIngredientCrossRef::class,
        ShoppingListItem::class,
        RecipeStep::class,
        FavoriteRecipe::class,
        StepIngredient::class
    ],
    version = 16, // 👈 Подняли с 15 до 16
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN calories INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN proteins REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN fats REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN carbs REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем поле totalWeight для расчета граммовки
                db.execSQL("ALTER TABLE recipes ADD COLUMN totalWeight INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meal_camera_database"
                )
                    .addMigrations(MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}