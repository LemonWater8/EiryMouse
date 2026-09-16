package com.example.diazymouse.ui.component

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.connection.MainInputTransport
import com.example.diazymouse.settings.vibration.UiVibrationConfig
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiLayoutConfig
import com.example.diazymouse.ui.UiVibrator

/** Classic fixed five-key Fn row for DiazyMouse v1.0. */
class FunctionKeyArea(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions,
    private val inputTransport: MainInputTransport,
    private val onToggleKeyboard: (() -> Boolean)? = null,
    private val onActionDisplay: (String) -> Unit
) {
    private var isDragLocked = false
    private val uiVibrator = UiVibrator(activity)
    private val keys = mutableListOf<TextView>()

    private val normalColor = Color.rgb(90, 90, 90)
    private val dragColor = Color.rgb(200, 70, 70)

    fun addTo(root: FrameLayout) {
        root.post {
            keys.clear()
            val metrics = activity.resources.displayMetrics
            val topGapPx = UiDimensions.dpToPx(UiLayoutConfig.FUNCTION_KEY_TOP_GAP_DP, metrics)
            val outerMargin = (root.width * UiLayoutConfig.FUNCTION_KEY_SIDE_MARGIN_RATIO).toInt()
            val gap = (root.width * UiLayoutConfig.FUNCTION_KEY_GAP_RATIO).toInt()
            val usableWidth = (root.width - outerMargin * 2 - gap * 4).coerceAtLeast(5)
            val keyWidth = usableWidth / 5
            val keyHeight = (dimensions.functionKeyHeightPx - topGapPx).coerceAtLeast(1)
            val topPosition = dimensions.mainAreaTopPx -
                dimensions.functionKeyMainGapPx -
                dimensions.functionKeyHeightPx + topGapPx

            var x = outerMargin
            repeat(5) { index ->
                val number = index + 1
                val key = TextView(activity).apply {
                    text = "Fn$number"
                    textSize = UiConfig.FUNCTION_KEY_TEXT_SIZE_SP
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setBackgroundColor(normalColor)
                    setOnClickListener {
                        vibrate(number)
                        onActionDisplay("Fn$number")
                        execute(number, this)
                    }
                }
                root.addView(
                    key,
                    FrameLayout.LayoutParams(keyWidth, keyHeight).apply {
                        gravity = Gravity.TOP or Gravity.START
                        leftMargin = x
                        topMargin = topPosition
                    }
                )
                keys.add(key)
                x += keyWidth + gap
            }
        }
    }

    private fun execute(number: Int, key: TextView) {
        when (number) {
            1 -> {
                if (!isDragLocked) {
                    inputTransport.leftDown()
                    isDragLocked = true
                    key.setBackgroundColor(dragColor)
                    onActionDisplay("Fn1: Drag ON")
                } else {
                    inputTransport.leftUp()
                    isDragLocked = false
                    key.setBackgroundColor(normalColor)
                    onActionDisplay("Fn1: Drag OFF")
                }
            }
            2 -> {
                inputTransport.middleClick()
                onActionDisplay("Fn2: Middle Click")
            }
            3 -> {
                inputTransport.openBrowserSearch()
                onActionDisplay("Fn3: Browser Search")
            }
            4 -> {
                val enabled = onToggleKeyboard?.invoke() == true
                onActionDisplay(if (enabled) "Fn4: Keyboard ON" else "Fn4: Keyboard OFF")
            }
            5 -> {
                inputTransport.enter()
                onActionDisplay("Fn5: Enter")
            }
        }
    }

    private fun vibrate(number: Int) {
        val pattern = when (number) {
            1 -> UiVibrationConfig.FN1
            2 -> UiVibrationConfig.FN2
            3 -> UiVibrationConfig.FN3
            4 -> UiVibrationConfig.FN4
            5 -> UiVibrationConfig.FN5
            else -> return
        }
        uiVibrator.vibrate(pattern)
    }
}
