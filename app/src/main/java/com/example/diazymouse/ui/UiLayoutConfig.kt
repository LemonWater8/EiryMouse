package com.example.diazymouse.ui

/**
 * Layout-only constants.
 *
 * Large regions use relative ratios so the UI scales between devices such as
 * TORQUE G04 and Galaxy S20. Small gaps use dp.
 */
object UiLayoutConfig {

    const val SIDE_WIDTH_RATIO =
        0.20f

    const val MAIN_AREA_TOP_RATIO =
        0.435f

    const val SCROLL_AREA_HEIGHT_RATIO =
        0.175f

    const val TOUCH_PAD_HEIGHT_RATIO =
        0.225f

    const val FUNCTION_KEY_HEIGHT_RATIO =
        0.090f

    const val FUNCTION_KEY_MAIN_GAP_RATIO =
        0.008f

    const val FUNCTION_KEY_SIDE_MARGIN_RATIO =
        0.012f

    const val FUNCTION_KEY_GAP_RATIO =
        0.012f

    const val FUNCTION_KEY_CENTER_SCALE =
        0.80f

    const val FUNCTION_KEY_TOP_GAP_DP =
        4f

    const val BOTTOM_DEAD_ZONE_DP =
        2f

    /**
     * The visible Scroll Ring remains circular.
     * Only the touch target is widened horizontally.
     */
    const val SCROLL_TOUCH_WIDTH_TO_HEIGHT_RATIO =
        1.65f
}
