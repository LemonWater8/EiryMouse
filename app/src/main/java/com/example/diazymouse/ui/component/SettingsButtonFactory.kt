package com.example.diazymouse.ui.component

import android.graphics.Color
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.example.diazymouse.ui.Win98Style

object SettingsButtonFactory {
    private fun skin(activity: ComponentActivity): StateListDrawable = Win98Style.buttonBackground(dp(activity, 2f))

    private fun animatePress(view: View) { view.stateListAnimator = null }

    private fun base(activity: ComponentActivity, text: String): Button = Button(activity).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Win98Style.TEXT)
        background = skin(activity)
        elevation = 0f
        minWidth = 0
        minHeight = 0
        isAllCaps = false
        animatePress(this)
    }

    fun fullWidth(
        activity: ComponentActivity,
        text: String,
        onClick: (() -> Unit)? = null
    ): Button = base(activity, text).apply {
        height = dp(activity, 48f)
        setPadding(dp(activity, 12f), 0, dp(activity, 12f), 0)
        onClick?.let { setOnClickListener { it() } }
    }

    fun compact(
        activity: ComponentActivity,
        text: String,
        onClick: (() -> Unit)? = null
    ): Button = base(activity, text).apply {
        height = dp(activity, 42f)
        setPadding(dp(activity, 8f), 0, dp(activity, 8f), 0)
        onClick?.let { setOnClickListener { it() } }
    }

    private fun dp(activity: ComponentActivity, value: Float): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    fun rowButton(
        activity: ComponentActivity,
        text: String,
        onClick: () -> Unit,
        weight: Float = 1f
    ): Button = base(activity, text).apply {
        height = dp(activity, 44f)
        setPadding(dp(activity, 8f), 0, dp(activity, 8f), 0)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            marginStart = dp(activity, 3f)
            marginEnd = dp(activity, 3f)
        }
        setOnClickListener { onClick() }
    }
}
