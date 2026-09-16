package com.example.diazymouse.ui

import android.util.DisplayMetrics
import kotlin.math.roundToInt

/**
 * Relative layout dimensions.
 *
 * v7.3:
 * Region edges are calculated explicitly so each requested visual boundary
 * is guaranteed instead of relying only on independent height ratios.
 */
data class UiDimensions(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val buttonWidthPx: Int,
    val touchPadHeightPx: Int,
    val scrollAreaHeightPx: Int,
    val functionAreaHeightPx: Int,
    val uiDownOffsetPx: Int,
    val mainAreaTopPx: Int,
    val sideAreaHeightPx: Int,
    val sideButtonHeightPx: Int,
    val sideButtonTopMarginPx: Int,
    val functionKeyWidthPx: Int,
    val functionKeyHeightPx: Int,
    val functionKeyMainGapPx: Int,
    val textArea1HeightPx: Int,
    val textArea2HeightPx: Int,
    val textArea2FunctionGapPx: Int,
    val textArea2TopPx: Int,
    val textArea1TopPx: Int,
    val touchPadTopPx: Int,
    val touchPadBottomPx: Int,
    val touchPadCenterPx: Int
) {
    fun isLandscape(): Boolean =
        screenWidthPx > screenHeightPx

    fun twoThirdsHeightPx(): Int =
        ((screenHeightPx * 2) / 3).coerceAtLeast(1)

    fun fourFifthsHeightPx(): Int =
        ((screenHeightPx * 4) / 5).coerceAtLeast(1)

    companion object {

        fun from(
            metrics: DisplayMetrics
        ): UiDimensions {

            val width =
                metrics.widthPixels.coerceAtLeast(1)

            val height =
                metrics.heightPixels.coerceAtLeast(1)

            val isLandscape =
                width > height

            /*
             * In landscape, shrink the vertical stack slightly so the entire
             * UI remains inside one screen. This keeps the portrait layout
             * logic stable while trimming the character/dialogue area to make
             * room for the wider display.
             */
            val verticalScale =
                if (isLandscape) {
                    0.86f
                } else {
                    1f
                }

            val functionScale =
                if (isLandscape) {
                    1.18f
                } else {
                    1f
                }

            val buttonWidthPx =
                (
                    width *
                        UiLayoutConfig.SIDE_WIDTH_RATIO
                    ).roundToInt()

            val mainAreaTopPx =
                (
                    height *
                        UiLayoutConfig.MAIN_AREA_TOP_RATIO *
                        verticalScale
                    ).roundToInt()

            val scrollAreaHeightPx =
                (
                    height *
                        UiLayoutConfig.SCROLL_AREA_HEIGHT_RATIO *
                        verticalScale
                    ).roundToInt()

            val touchPadHeightPx =
                (
                    height *
                        UiLayoutConfig.TOUCH_PAD_HEIGHT_RATIO *
                        if (isLandscape) {
                            1.32f
                        } else {
                            verticalScale
                        }
                    ).roundToInt()

            val sideAreaHeightPx =
                scrollAreaHeightPx +
                    touchPadHeightPx

            val functionKeyHeightPx =
                (
                    height *
                        UiLayoutConfig.FUNCTION_KEY_HEIGHT_RATIO *
                        functionScale
                    ).roundToInt()

            val functionKeyMainGapPx =
                (
                    height *
                        UiLayoutConfig.FUNCTION_KEY_MAIN_GAP_RATIO *
                        verticalScale
                    ).roundToInt()

            /*
             * Main input structure:
             *
             * mainAreaTop
             *   Scroll Ring area
             *   Touch Pad
             */
            val touchPadTopPx =
                mainAreaTopPx +
                    scrollAreaHeightPx

            val touchPadBottomPx =
                touchPadTopPx +
                    touchPadHeightPx

            val touchPadCenterPx =
                touchPadTopPx +
                    touchPadHeightPx / 2

            /*
             * Text Area 1:
             * Its upper edge is forced to the Touch Pad lower edge.
             * Its lower edge remains at the screen bottom.
             */
            val textArea1TopPx =
                touchPadBottomPx

            val textArea1HeightPx =
                (
                    height -
                        textArea1TopPx
                    ).coerceAtLeast(1)

            /*
             * Fn row:
             * Keep immediately above mainAreaTop.
             */
            val functionKeyTopPx =
                mainAreaTopPx -
                    functionKeyMainGapPx -
                    functionKeyHeightPx

            /*
             * Text Area 2:
             * Bottom edge touches the Fn row.
             *
             * The top edge now starts at the very top of the application's
             * drawable area. On Android this places it directly against the
             * lower edge of the status / information bar.
             *
             * No additional percentage-based safety gap is kept.
             */
            val textArea2TopPx =
                0

            val textArea2HeightPx =
                (
                    functionKeyTopPx -
                        textArea2TopPx
                    ).coerceAtLeast(1)

            val textArea2FunctionGapPx =
                0

            /*
             * Left/Right Click remain visible downward exactly to
             * the Touch Pad center.
             *
             * Back/Forward start from the Touch Pad center and continue
             * all the way to the screen bottom beside Text Area 1.
             */
            val sideButtonTopMarginPx =
                touchPadCenterPx

            val sideButtonHeightPx =
                (
                    height -
                        sideButtonTopMarginPx
                    ).coerceAtLeast(1)

            val functionKeyWidthPx =
                (
                    width *
                        0.17f
                    ).roundToInt()

            return UiDimensions(
                screenWidthPx =
                    width,
                screenHeightPx =
                    height,
                buttonWidthPx =
                    buttonWidthPx,
                touchPadHeightPx =
                    touchPadHeightPx,
                scrollAreaHeightPx =
                    scrollAreaHeightPx,
                functionAreaHeightPx =
                    functionKeyHeightPx,
                uiDownOffsetPx =
                    0,
                mainAreaTopPx =
                    mainAreaTopPx,
                sideAreaHeightPx =
                    sideAreaHeightPx,
                sideButtonHeightPx =
                    sideButtonHeightPx,
                sideButtonTopMarginPx =
                    sideButtonTopMarginPx,
                functionKeyWidthPx =
                    functionKeyWidthPx,
                functionKeyHeightPx =
                    functionKeyHeightPx,
                functionKeyMainGapPx =
                    functionKeyMainGapPx,
                textArea1HeightPx =
                    textArea1HeightPx,
                textArea2HeightPx =
                    textArea2HeightPx,
                textArea2FunctionGapPx =
                    textArea2FunctionGapPx,
                textArea2TopPx =
                    textArea2TopPx,
                textArea1TopPx =
                    textArea1TopPx,
                touchPadTopPx =
                    touchPadTopPx,
                touchPadBottomPx =
                    touchPadBottomPx,
                touchPadCenterPx =
                    touchPadCenterPx
            )
        }

        fun dpToPx(
            dp: Float,
            metrics: DisplayMetrics
        ): Int =
            (
                dp *
                    metrics.density
                ).roundToInt()
    }
}
