package com.premiumtv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

object Ui {
    const val WHITE: Int = 0xFFF1F3F7.toInt()
    const val MUTED: Int = 0xFF9FA7B6.toInt()
    fun background(context: Context, accent: Int, radius: Int = 16): StateListDrawable {
        fun shape(focused: Boolean) = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (focused) intArrayOf(0xFF303847.toInt(), 0xFF1D232E.toInt())
            else intArrayOf(0xFF1B1E26.toInt(), 0xFF12151C.toInt())
        ).apply {
            cornerRadius = context.dp(radius).toFloat()
            setStroke(context.dp(if (focused) 2 else 1),
                if (focused) accent else Color.argb(45, 172, 183, 205))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(true))
            addState(intArrayOf(android.R.attr.state_pressed), shape(true))
            addState(intArrayOf(), shape(false))
        }
    }

    fun text(context: Context, value: String, size: Float, color: Int = WHITE, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            isFocusable = false
        }

    fun chip(context: Context, label: String, prefs: Preferences, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(WHITE)
            includeFontPadding = false
            minimumWidth = 0
            minimumHeight = context.dp(40)
            minWidth = 0
            minHeight = context.dp(40)
            setPadding(context.dp(16), context.dp(6), context.dp(16), context.dp(6))
            background = background(context, prefs.accent, 22)
            stateListAnimator = null
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { onClick() }
            onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                animateFocus(view, focused, prefs.animations)
            }
        }

    fun animateFocus(view: View, focused: Boolean, enabled: Boolean) {
        view.animate().cancel()
        val scale = if (focused && enabled) 1.035f else 1f
        if (enabled) view.animate().scaleX(scale).scaleY(scale).setDuration(90L).start()
        else { view.scaleX = scale; view.scaleY = scale }
        // Native elevation creates a restrained edge light without blur filters.
        view.elevation = if (focused && enabled) view.context.dp(3).toFloat() else 0f
    }
}
