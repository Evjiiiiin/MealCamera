package com.example.mealcamera.data.local

import android.content.Context

class AppStatsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class RecentCookedRecipe(
        val name: String,
        val cookedAtMillis: Long
    )

    fun registerCookedRecipe(userId: String?, recipeId: Long) {
        registerCookedRecipe(userId, recipeId, "Без названия")
    }

    fun registerCookedRecipe(userId: String?, recipeId: Long, recipeName: String) {
        val userSuffix = userId ?: "guest"
        val uniqueKey = uniqueCookedRecipesKey(userSuffix)
        val currentIds = prefs.getStringSet(uniqueKey, emptySet()).orEmpty().toMutableSet()
        currentIds.add(recipeId.toString())

        val updatedRecent = buildUpdatedRecentList(userId, recipeName)

        prefs.edit()
            .putStringSet(uniqueKey, currentIds)
            .putInt(cookedRecipesKey(userId), prefs.getInt(cookedRecipesKey(userId), 0) + 1)
            .putString(recentCookedRecipesKey(userId), updatedRecent)
            .apply()
    }

    fun getCookedRecipesCount(userId: String?): Int {
        return prefs.getInt(cookedRecipesKey(userId), 0)
    }

    fun getUniqueCookedRecipesCount(userId: String?): Int {
        val userSuffix = userId ?: "guest"
        return prefs.getStringSet(uniqueCookedRecipesKey(userSuffix), emptySet())?.size ?: 0
    }

    fun getRecentCookedRecipes(userId: String?): List<RecentCookedRecipe> {
        val raw = prefs.getString(recentCookedRecipesKey(userId), "").orEmpty()
        if (raw.isBlank()) return emptyList()

        return raw.split(ENTRY_SEPARATOR)
            .mapNotNull { token ->
                val delimiterIndex = token.indexOf(FIELD_SEPARATOR)
                if (delimiterIndex <= 0 || delimiterIndex >= token.lastIndex) return@mapNotNull null

                val timePart = token.substring(0, delimiterIndex)
                val namePart = token.substring(delimiterIndex + 1)
                val millis = timePart.toLongOrNull() ?: return@mapNotNull null
                if (namePart.isBlank()) return@mapNotNull null

                RecentCookedRecipe(name = namePart, cookedAtMillis = millis)
            }
    }

    private fun buildUpdatedRecentList(userId: String?, recipeName: String): String {
        val current = getRecentCookedRecipes(userId).toMutableList()
        current.add(0, RecentCookedRecipe(recipeName.ifBlank { "Без названия" }, System.currentTimeMillis()))

        while (current.size > MAX_RECENT_ITEMS) {
            current.removeAt(current.lastIndex)
        }

        return current.joinToString(ENTRY_SEPARATOR) { item ->
            "${item.cookedAtMillis}$FIELD_SEPARATOR${sanitizeRecipeName(item.name)}"
        }
    }

    private fun sanitizeRecipeName(name: String): String {
        return name
            .replace(ENTRY_SEPARATOR, " ")
            .replace(FIELD_SEPARATOR, " ")
            .trim()
            .ifBlank { "Без названия" }
    }

    companion object {
        private const val PREFS_NAME = "meal_camera_stats"
        private const val MAX_RECENT_ITEMS = 5
        private const val ENTRY_SEPARATOR = "||"
        private const val FIELD_SEPARATOR = "::"

        private fun cookedRecipesKey(userId: String?): String {
            return "cooked_recipes_${userId ?: "guest"}"
        }

        private fun uniqueCookedRecipesKey(userSuffix: String): String {
            return "unique_cooked_recipes_$userSuffix"
        }

        private fun recentCookedRecipesKey(userId: String?): String {
            return "recent_cooked_recipes_${userId ?: "guest"}"
        }
    }
}