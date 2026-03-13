package com.example.mealcamera.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppStatsManager(context: Context) {

    data class RecentCookedRecipe(
        val recipeId: Long,
        val name: String,
        val cookedAtMillis: Long
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_stats", Context.MODE_PRIVATE)

    fun registerCookedRecipe(userId: String?, recipeId: Long, recipeName: String) {
        if (recipeId <= 0L) return

        val userSuffix = userId.orEmpty()
        val countKey = "cooked_count_$userSuffix"
        val uniqueKey = "cooked_unique_$userSuffix"
        val recentKey = "cooked_recent_$userSuffix"

        val currentCount = prefs.getInt(countKey, 0)
        prefs.edit().putInt(countKey, currentCount + 1).apply()

        val uniqueSet = prefs.getStringSet(uniqueKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        uniqueSet.add(recipeId.toString())
        prefs.edit().putStringSet(uniqueKey, uniqueSet).apply()

        val recentList = getRecentCookedRecipes(userId).toMutableList()
        recentList.add(
            0,
            RecentCookedRecipe(
                recipeId = recipeId,
                name = recipeName,
                cookedAtMillis = System.currentTimeMillis()
            )
        )

        val trimmed = recentList.take(10)
        val jsonArray = JSONArray()
        trimmed.forEach { item ->
            val obj = JSONObject()
                .put("recipeId", item.recipeId)
                .put("name", item.name)
                .put("cookedAtMillis", item.cookedAtMillis)
            jsonArray.put(obj)
        }
        prefs.edit().putString(recentKey, jsonArray.toString()).apply()
    }

    fun getCookedRecipesCount(userId: String?): Int {
        val userSuffix = userId.orEmpty()
        return prefs.getInt("cooked_count_$userSuffix", 0)
    }

    fun getUniqueCookedRecipesCount(userId: String?): Int {
        val userSuffix = userId.orEmpty()
        return prefs.getStringSet("cooked_unique_$userSuffix", emptySet())?.size ?: 0
    }

    fun getRecentCookedRecipes(userId: String?): List<RecentCookedRecipe> {
        val userSuffix = userId.orEmpty()
        val raw = prefs.getString("cooked_recent_$userSuffix", null) ?: return emptyList()

        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        RecentCookedRecipe(
                            recipeId = obj.optLong("recipeId", -1L),
                            name = obj.optString("name", ""),
                            cookedAtMillis = obj.optLong("cookedAtMillis", 0L)
                        )
                    )
                }
            }.filter { it.recipeId > 0L && it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun clearAll(userId: String?) {
        val userSuffix = userId.orEmpty()
        prefs.edit()
            .remove("cooked_count_$userSuffix")
            .remove("cooked_unique_$userSuffix")
            .remove("cooked_recent_$userSuffix")
            .apply()
    }
}