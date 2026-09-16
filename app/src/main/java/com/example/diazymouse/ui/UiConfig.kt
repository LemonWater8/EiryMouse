package com.example.diazymouse.ui

import android.graphics.Color

/**
 * Visual/text constants only.
 *
 * Layout geometry is stored separately in UiLayoutConfig.
 */
object UiConfig {

    // Text sizes.
    const val CLICK_BUTTON_TEXT_SIZE_SP =
        32f

    const val INFO_TEXT_SIZE_SP =
        16f

    /**
     * MyMoveMouse label inside the Touch Pad.
     * Requested size: previous INFO_TEXT_SIZE_SP x 1.5.
     */
    const val TOUCH_PAD_INFO_TEXT_SIZE_SP =
        INFO_TEXT_SIZE_SP * 1.5f

    const val FUNCTION_KEY_TEXT_SIZE_SP =
        14f

    const val TEXT_AREA_TEXT_SIZE_SP =
        14f

    // Labels.
    const val LEFT_CLICK_TEXT =
        "←"

    const val RIGHT_CLICK_TEXT =
        "→"

    const val TOUCH_PAD_INFO_TEXT =
        "MyMoveMouse"

    const val TEXT_AREA_1_TEXT =
        "Text Area 1"

    const val TEXT_AREA_2_TEXT =
        "Text Area 2"

    // Colors.
    val ROOT_BACKGROUND =
        Color.rgb(0, 128, 128) // Win98 desktop teal

    val TOUCH_PAD_BACKGROUND =
        Color.rgb(
            100,
            0,
            0
        )

    val SCROLL_AREA_BACKGROUND =
        Color.rgb(
            180,
            225,
            160
        )

    val CLICK_BUTTON_BACKGROUND =
        Color.rgb(
            220,
            140,
            100
        )

    val TEXT_COLOR =
        Color.RED

    val SCROLL_RING_COLOR =
        Color.rgb(
            110,
            50,
            160
        )

    val SIDE_NAVIGATION_BACKGROUND =
        Color.rgb(
            25,
            90,
            120
        )

    val TEXT_AREA_BACKGROUND =
        Color.rgb(
            120,
            200,
            100
        )

    val DISPLAY_TEXT_COLOR =
        Color.WHITE

    val SCROLL_DEAD_ZONE_COLOR =
        Color.rgb(
            145,
            205,
            95
        )

    val SCROLL_RING_BORDER_COLOR =
        Color.BLACK

    const val SCROLL_RING_BORDER_DP =
        2f
}
