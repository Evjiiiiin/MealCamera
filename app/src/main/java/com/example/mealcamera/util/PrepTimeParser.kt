package com.example.mealcamera.util

object PrepTimeParser {
    fun parseToMinutes(prepTime: String): Int {
        var totalMinutes = 0
        val hourRegex = Regex("(\\d+)\\s*ч")
        val minuteRegex = Regex("(\\d+)\\s*мин")

        hourRegex.find(prepTime)?.let {
            totalMinutes += it.groupValues[1].toInt() * 60
        }
        minuteRegex.find(prepTime)?.let {
            totalMinutes += it.groupValues[1].toInt()
        }
        return totalMinutes
    }
}