package com.example.mealcamera.util

import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.BindingAdapter
import com.example.mealcamera.R

object SymbolBindingAdapter {

    @JvmStatic
    @BindingAdapter("symbolName")
    fun setSymbol(view: TextView, name: String?) {
        if (name == null) return
        
        // Устанавливаем шрифт Google Symbols
        val typeface = ResourcesCompat.getFont(view.context, R.font.google_symbols)
        view.typeface = typeface
        
        // В шрифте Material Symbols иконка отображается по её текстовому названию
        view.text = name
    }
}