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
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE recipes ADD COLUMN authorId TEXT DEFAULT 'admin'")
                } catch (e: Exception) { }
                try {
                    db.execSQL("ALTER TABLE recipes ADD COLUMN isPublic INTEGER DEFAULT 1")
                } catch (e: Exception) { }
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_recipes_authorId ON recipes(authorId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_recipes_isPublic ON recipes(isPublic)")
                } catch (e: Exception) { }
            }
        }


        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_list ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_list_userId ON shopping_list(userId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meal_camera_database"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13) // 👈 добавляем обе миграции
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}