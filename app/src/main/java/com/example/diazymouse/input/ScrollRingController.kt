package com.example.diazymouse.input

import android.util.Log
import com.example.diazymouse.settings.model.MouseSpeedConfig
import com.example.diazymouse.settings.store.MouseSettingsStore
import com.example.diazymouse.settings.vibration.UiVibrationConfig
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiLayoutConfig
import com.example.diazymouse.ui.UiVibrator
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

class ScrollRingController(

    private val uiVibrator: UiVibrator,

    private val touchVibration:
        UiVibrationConfig.Pattern,

    private val stepVibration:
        UiVibrationConfig.Pattern,

    private val onTouchDisplay: () -> Unit,

    // +1 = scroll up
    // -1 = scroll down
    private val onScroll: (Int) -> Unit

) {

    private var lastScrollAngle: Double? =
        null

    private var accumulatedAngle =
        0.0

    fun attach(
        scrollArea: FrameLayout,
        scrollAreaHeightPx: Int
    ) {

        val metrics =
            scrollArea.resources.displayMetrics

        val outerCircleSizePx =
            scrollAreaHeightPx

        // ==============================
        // OUTER SCROLL CIRCLE
        // ==============================

        val outerCircle =
            TextView(scrollArea.context).apply {

                background =
                    android.graphics.drawable.GradientDrawable().apply {

                        shape =
                            android.graphics.drawable.GradientDrawable.OVAL

                        setColor(
                            UiConfig.SCROLL_RING_COLOR
                        )

                        setStroke(
                            (
                                UiConfig.SCROLL_RING_BORDER_DP *
                                    metrics.density
                                ).roundToInt(),
                            UiConfig.SCROLL_RING_BORDER_COLOR
                        )
                    }

                isClickable =
                    false
            }

        val outerCircleParams =
            FrameLayout.LayoutParams(
                outerCircleSizePx,
                outerCircleSizePx
            ).apply {
                gravity =
                    Gravity.CENTER
            }

        scrollArea.addView(
            outerCircle,
            outerCircleParams
        )

        // ==============================
        // CENTER DEAD ZONE
        // ==============================

        val deadZoneDiameterPx =
            (
                outerCircleSizePx *
                    MouseSpeedConfig.SCROLL_MIN_RADIUS_RATIO *
                    2f
                ).roundToInt()

        val deadZoneCircle =
            TextView(scrollArea.context).apply {

                background =
                    android.graphics.drawable.GradientDrawable().apply {

                        shape =
                            android.graphics.drawable.GradientDrawable.OVAL

                        setColor(
                            UiConfig.SCROLL_DEAD_ZONE_COLOR
                        )

                        setStroke(
                            (
                                UiConfig.SCROLL_RING_BORDER_DP *
                                    metrics.density
                                ).roundToInt(),
                            UiConfig.SCROLL_RING_BORDER_COLOR
                        )
                    }

                isClickable =
                    false

                isFocusable =
                    false
            }

        val deadZoneParams =
            FrameLayout.LayoutParams(
                deadZoneDiameterPx,
                deadZoneDiameterPx
            ).apply {
                gravity =
                    Gravity.CENTER
            }

        scrollArea.addView(
            deadZoneCircle,
            deadZoneParams
        )

        // ==============================
        // WIDER TOUCH TARGET
        // ==============================

        /*
         * The visible ring remains circular.
         * Only the touch target becomes wider horizontally.
         */
        val touchWidthPx =
            (
                outerCircleSizePx *
                    UiLayoutConfig.SCROLL_TOUCH_WIDTH_TO_HEIGHT_RATIO
                ).roundToInt()

        val touchArea =
            FrameLayout(scrollArea.context).apply {

                setBackgroundColor(
                    android.graphics.Color.TRANSPARENT
                )

                isClickable =
                    true
            }

        val touchAreaParams =
            FrameLayout.LayoutParams(
                touchWidthPx,
                outerCircleSizePx
            ).apply {
                gravity =
                    Gravity.CENTER
            }

        scrollArea.addView(
            touchArea,
            touchAreaParams
        )

        touchArea.setOnTouchListener { view, event ->

            if (
                event.actionMasked ==
                    MotionEvent.ACTION_UP ||
                event.actionMasked ==
                    MotionEvent.ACTION_CANCEL
            ) {

                lastScrollAngle =
                    null

                accumulatedAngle =
                    0.0

                Log.d(
                    "ScrollCircle",
                    "UP / CANCEL"
                )

                return@setOnTouchListener true
            }

            val centerX =
                view.width / 2f

            val centerY =
                view.height / 2f

            val dx =
                event.x - centerX

            val dy =
                event.y - centerY

            /*
             * Normalize X/Y separately.
             *
             * X radius is wider than the visible circle.
             * Y radius remains equal to the visible circle.
             * This produces a horizontally expanded elliptical hit target
             * while keeping the displayed ring itself unchanged.
             */
            val radiusX =
                view.width / 2f

            val radiusY =
                view.height / 2f

            if (
                radiusX <= 0f ||
                radiusY <= 0f
            ) {
                return@setOnTouchListener true
            }

            val normalizedX =
                dx / radiusX

            val normalizedY =
                dy / radiusY

            val normalizedDistanceSquared =
                normalizedX *
                    normalizedX +
                    normalizedY *
                    normalizedY

            val minRadius =
                MouseSpeedConfig.SCROLL_MIN_RADIUS_RATIO

            val maxRadius =
                MouseSpeedConfig.SCROLL_MAX_RADIUS_RATIO

            val minRadiusSquared =
                minRadius *
                    minRadius

            val maxRadiusSquared =
                maxRadius *
                    maxRadius

            // ==============================
            // CENTER DEAD ZONE
            // ==============================

            if (
                normalizedDistanceSquared <
                    minRadiusSquared
            ) {

                lastScrollAngle =
                    null

                accumulatedAngle =
                    0.0

                return@setOnTouchListener true
            }

            // ==============================
            // OUTER DEAD ZONE
            // ==============================

            if (
                normalizedDistanceSquared >
                    maxRadiusSquared
            ) {

                lastScrollAngle =
                    null

                accumulatedAngle =
                    0.0

                return@setOnTouchListener true
            }

            // ==============================
            // VALID RING
            // ==============================

            when (
                event.actionMasked
            ) {

                MotionEvent.ACTION_DOWN -> {

                    uiVibrator.vibrate(
                        touchVibration
                    )

                    onTouchDisplay()

                    val angleDeg =
                        calculateAngle(
                            normalizedX,
                            normalizedY
                        )

                    lastScrollAngle =
                        angleDeg

                    accumulatedAngle =
                        0.0
                }

                MotionEvent.ACTION_MOVE -> {

                    val angleDeg =
                        calculateAngle(
                            normalizedX,
                            normalizedY
                        )

                    val previousAngle =
                        lastScrollAngle

                    if (
                        previousAngle == null
                    ) {

                        lastScrollAngle =
                            angleDeg

                        accumulatedAngle =
                            0.0

                        return@setOnTouchListener true
                    }

                    var delta =
                        angleDeg -
                            previousAngle

                    if (
                        delta > 180.0
                    ) {

                        delta -=
                            360.0

                    } else if (
                        delta < -180.0
                    ) {

                        delta +=
                            360.0
                    }

                    if (
                        kotlin.math.abs(
                            delta
                        ) >=
                        MouseSpeedConfig.SCROLL_DIRECTION_DEAD_ZONE_DEG
                    ) {

                        accumulatedAngle +=
                            delta
                    }

                    // ==============================
                    // Match smartphone behavior:
                    // a ring gesture that feels like "up" should scroll content down.
                    // ==============================

                    while (
                        accumulatedAngle >=
                        MouseSpeedConfig.SCROLL_STEP_ANGLE_DEG
                    ) {

                        onScroll(
                            +MouseSettingsStore.getScrollRingAmount(
                                scrollArea.context
                            )
                        )

                        uiVibrator.vibrate(
                            stepVibration
                        )

                        accumulatedAngle -=
                            MouseSpeedConfig.SCROLL_STEP_ANGLE_DEG

                        Log.d(
                            "ScrollCircle",
                            "SCROLL DOWN"
                        )
                    }

                    while (
                        accumulatedAngle <=
                        -MouseSpeedConfig.SCROLL_STEP_ANGLE_DEG
                    ) {

                        onScroll(
                            -MouseSettingsStore.getScrollRingAmount(
                                scrollArea.context
                            )
                        )

                        uiVibrator.vibrate(
                            stepVibration
                        )

                        accumulatedAngle +=
                            MouseSpeedConfig.SCROLL_STEP_ANGLE_DEG

                        Log.d(
                            "ScrollCircle",
                            "SCROLL UP"
                        )
                    }

                    lastScrollAngle =
                        angleDeg
                }
            }

            true
        }
    }

    private fun calculateAngle(
        dx: Float,
        dy: Float
    ): Double {

        val angleRad =
            atan2(
                dy.toDouble(),
                dx.toDouble()
            )

        var angleDeg =
            angleRad *
                180.0 /
                PI

        if (
            angleDeg < 0
        ) {

            angleDeg +=
                360.0
        }

        return angleDeg
    }
}
