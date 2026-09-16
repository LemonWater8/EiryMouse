package com.example.diazymouse.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.TextView

/** Pure visual skin helpers. No layout, input, HID, or navigation behavior lives here. */
object Win98Style {
    val DESKTOP = Color.rgb(0, 128, 128)
    val FACE = Color.rgb(212, 212, 212)
    val FACE_LIGHT = Color.rgb(238, 238, 238)
    val HIGHLIGHT = Color.WHITE
    val SHADOW = Color.rgb(128, 128, 128)
    val DARK_SHADOW = Color.rgb(0, 0, 0)
    val TEXT = Color.rgb(0, 0, 0)
    val TITLE_BLUE = Color.rgb(0, 62, 196)
    val TITLE_BLUE_ACTIVE = Color.rgb(0, 72, 210)

    fun buttonBackground(edgePx: Int = 2): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), BevelDrawable(FACE, edgePx, true))
        addState(intArrayOf(), BevelDrawable(FACE, edgePx, false))
    }

    fun panelBackground(edgePx: Int = 2): Drawable = BevelDrawable(FACE, edgePx, false)
    fun sunkenBackground(edgePx: Int = 2, fill: Int = FACE_LIGHT): Drawable = BevelDrawable(fill, edgePx, true)
    fun raisedBackground(fill: Int, edgePx: Int = 2): Drawable = BevelDrawable(fill, edgePx, false)

    fun applyButton(view: TextView, edgePx: Int = 2) {
        view.setTextColor(TEXT)
        view.background = buttonBackground(edgePx)
        view.elevation = 0f
        view.stateListAnimator = null
    }

    private class BevelDrawable(
        private val fill: Int,
        private val edge: Int,
        private val pressed: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        override fun draw(canvas: Canvas) {
            val b = bounds
            paint.color = fill
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
            val hi = if (pressed) DARK_SHADOW else HIGHLIGHT
            val lo = if (pressed) HIGHLIGHT else DARK_SHADOW
            val midHi = if (pressed) SHADOW else FACE_LIGHT
            val midLo = if (pressed) FACE_LIGHT else SHADOW
            fun rect(color: Int, l: Int, t: Int, r: Int, bot: Int) {
                paint.color = color; canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), bot.toFloat(), paint)
            }
            rect(hi, b.left, b.top, b.right, b.top + edge)
            rect(hi, b.left, b.top, b.left + edge, b.bottom)
            rect(lo, b.left, b.bottom - edge, b.right, b.bottom)
            rect(lo, b.right - edge, b.top, b.right, b.bottom)
            if (edge >= 2) {
                rect(midHi, b.left + edge, b.top + edge, b.right - edge, b.top + edge + 1)
                rect(midHi, b.left + edge, b.top + edge, b.left + edge + 1, b.bottom - edge)
                rect(midLo, b.left + edge, b.bottom - edge - 1, b.right - edge, b.bottom - edge)
                rect(midLo, b.right - edge - 1, b.top + edge, b.right - edge, b.bottom - edge)
            }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
