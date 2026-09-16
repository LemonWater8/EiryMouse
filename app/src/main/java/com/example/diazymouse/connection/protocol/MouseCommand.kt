package com.example.diazymouse.connection.protocol

object MouseCommand {

    const val LEFT_CLICK = "LEFT_CLICK"
    const val RIGHT_CLICK = "RIGHT_CLICK"

    const val LEFT_DOWN = "LEFT_DOWN"
    const val LEFT_UP = "LEFT_UP"

    const val MIDDLE_CLICK = "MIDDLE_CLICK"

    const val BACK = "BACK"
    const val FORWARD = "FORWARD"
    const val DOUBLE_LEFT_CLICK = "DOUBLE_LEFT_CLICK"
    const val HSCROLL_PREFIX = "HSCROLL"
    const val ZOOM_IN = "ZOOM_IN"
    const val ZOOM_OUT = "ZOOM_OUT"
    const val ACTIVE_WINDOW_PREVIOUS = "ACTIVE_WINDOW_PREVIOUS"
    const val ACTIVE_WINDOW_NEXT = "ACTIVE_WINDOW_NEXT"
    const val ACTIVE_WINDOW_SWITCH_START_PREVIOUS = "ACTIVE_WINDOW_SWITCH_START_PREVIOUS"
    const val ACTIVE_WINDOW_SWITCH_START_NEXT = "ACTIVE_WINDOW_SWITCH_START_NEXT"
    const val ACTIVE_WINDOW_SWITCH_STEP_PREVIOUS = "ACTIVE_WINDOW_SWITCH_STEP_PREVIOUS"
    const val ACTIVE_WINDOW_SWITCH_STEP_NEXT = "ACTIVE_WINDOW_SWITCH_STEP_NEXT"
    const val ACTIVE_WINDOW_SWITCH_COMMIT = "ACTIVE_WINDOW_SWITCH_COMMIT"
    const val KEY_HANKAKU_ZENKAKU = "KEY_HANKAKU_ZENKAKU"
    const val KEY_HENKAN = "KEY_HENKAN"
    const val KEY_F5 = "KEY_F5"
    const val KEY_HOME = "KEY_HOME"
    const val KEY_END = "KEY_END"
    const val PRINT_SCREEN_FULL = "PRINT_SCREEN_FULL"
    const val PRINT_ACTIVE_WINDOW = "PRINT_ACTIVE_WINDOW"
    const val AUDIO_MUTE = "AUDIO_MUTE"

    fun scroll(amount: Int): String =
        "SCROLL,$amount"

    fun horizontalScroll(amount: Int): String =
        "$HSCROLL_PREFIX,$amount"
}
