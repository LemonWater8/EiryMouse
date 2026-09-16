package com.example.diazymouse.ui.screen

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.example.diazymouse.ui.UiDimensions

/** Common entry point for the EiryMouse settings screen. */
object Setting {
    fun create(
        activity: ComponentActivity,
        root: FrameLayout,
        dimensions: UiDimensions,
        onBackToMain: () -> Unit = {}
    ): SettingsScreenArea {
        return SettingsScreenArea(
            activity = activity,
            dimensions = dimensions,
            onBackToMain = onBackToMain
        ).apply { addTo(root) }
    }

    fun show(
        activity: ComponentActivity,
        root: FrameLayout,
        dimensions: UiDimensions,
        onBackToMain: () -> Unit = {}
    ): SettingsScreenArea {
        return create(
            activity = activity,
            root = root,
            dimensions = dimensions,
            onBackToMain = onBackToMain
        ).also { it.show() }
    }
}
