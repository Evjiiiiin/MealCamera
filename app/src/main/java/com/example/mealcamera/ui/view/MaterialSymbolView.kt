package com.example.mealcamera.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.example.mealcamera.R

class MaterialSymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        if (!isInEditMode) {
            typeface = ResourcesCompat.getFont(context, R.font.google_symbols)
        }
    }
}
