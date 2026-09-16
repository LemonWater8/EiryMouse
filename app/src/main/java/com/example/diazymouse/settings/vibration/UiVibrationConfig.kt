package com.example.diazymouse.settings.vibration

/**
 * Default vibration settings.
 *
 * These values are used only when the user has not saved a custom value.
 * Saved values are loaded by VibrationSettingsStore.
 */
object UiVibrationConfig {

    data class Pattern(
        val key: String,
        val durationMs: Long,
        val amplitude: Int
    )

    data class NamedPattern(
        val name: String,
        val pattern: Pattern
    )

    val TOUCH_PAD =
        Pattern(
            key = "touch_pad",
            durationMs = 35L,
            amplitude = 90
        )

    val SCROLL_RING_TOUCH =
        Pattern(
            key = "scroll_ring_touch",
            durationMs = 30L,
            amplitude = 80
        )

    val SCROLL_RING_STEP =
        Pattern(
            key = "scroll_ring_step",
            durationMs = 18L,
            amplitude = 70
        )

    val LEFT_CLICK =
        Pattern(
            key = "left_click",
            durationMs = 45L,
            amplitude = 130
        )

    val RIGHT_CLICK =
        Pattern(
            key = "right_click",
            durationMs = 45L,
            amplitude = 130
        )

    val BACK =
        Pattern(
            key = "back",
            durationMs = 40L,
            amplitude = 110
        )

    val FORWARD =
        Pattern(
            key = "forward",
            durationMs = 40L,
            amplitude = 110
        )

    val FN1 =
        Pattern(
            key = "fn1",
            durationMs = 40L,
            amplitude = 120
        )

    val FN2 =
        Pattern(
            key = "fn2",
            durationMs = 40L,
            amplitude = 120
        )

    val FN3 =
        Pattern(
            key = "fn3",
            durationMs = 40L,
            amplitude = 120
        )

    val FN4 =
        Pattern(
            key = "fn4",
            durationMs = 40L,
            amplitude = 120
        )

    val FN5 =
        Pattern(
            key = "fn5",
            durationMs = 45L,
            amplitude = 130
        )

    val FN6 =
        Pattern(
            key = "fn6",
            durationMs = 45L,
            amplitude = 130
        )

    val FN7 =
        Pattern(
            key = "fn7",
            durationMs = 45L,
            amplitude = 130
        )

    val FN8 =
        Pattern(
            key = "fn8",
            durationMs = 45L,
            amplitude = 130
        )

    val FN9 =
        Pattern(
            key = "fn9",
            durationMs = 50L,
            amplitude = 140
        )

    val FN10 =
        Pattern(
            key = "fn10",
            durationMs = 50L,
            amplitude = 140
        )

    val FN11 =
        Pattern(
            key = "fn11",
            durationMs = 50L,
            amplitude = 140
        )

    val FN12 =
        Pattern(
            key = "fn12",
            durationMs = 50L,
            amplitude = 140
        )

    val FNC =
        Pattern(
            key = "fnc",
            durationMs = 65L,
            amplitude = 170
        )

    /**
     * Display order on the Config screen.
     */
    val ALL_PATTERNS =
        listOf(
            NamedPattern("Touch Pad", TOUCH_PAD),
            NamedPattern("Scroll Ring Touch", SCROLL_RING_TOUCH),
            NamedPattern("Scroll Ring Step", SCROLL_RING_STEP),
            NamedPattern("Left Click", LEFT_CLICK),
            NamedPattern("Right Click", RIGHT_CLICK),
            NamedPattern("Back", BACK),
            NamedPattern("Forward", FORWARD),
            NamedPattern("Fn1", FN1),
            NamedPattern("Fn2", FN2),
            NamedPattern("Fn3", FN3),
            NamedPattern("Fn4", FN4),
            NamedPattern("Fn5", FN5),
            NamedPattern("Fn6", FN6),
            NamedPattern("Fn7", FN7),
            NamedPattern("Fn8", FN8),
            NamedPattern("Fn9", FN9),
            NamedPattern("Fn10", FN10),
            NamedPattern("Fn11", FN11),
            NamedPattern("Fn12", FN12),
            NamedPattern("FnC", FNC)
        )
}
