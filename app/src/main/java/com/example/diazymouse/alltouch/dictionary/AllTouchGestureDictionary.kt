package com.example.diazymouse.alltouch.dictionary

import com.example.diazymouse.alltouch.dictionary.AllTouchFunctionDictionary.FunctionId

/**
 * Dictionary 3/3: TouchTest-style gesture -> semantic action mapping.
 *
 * The recognizer should emit GestureId only. It must not know HID key codes,
 * ADB strings, or Classic-mode button layout.
 */
object AllTouchGestureDictionary {

    enum class GestureId(val fingers: Int) {
        // 1 finger
        ONE_MOVE(1),
        ONE_TAP(1),
        ONE_DOUBLE_TAP(1),
        ONE_DRAG_START(1),
        ONE_DRAG_MOVE(1),
        ONE_DRAG_END(1),

        // 2 fingers
        TWO_TAP(2),
        TWO_DOUBLE_TAP(2),
        TWO_PAN_2D(2),
        TWO_PINCH_IN(2),
        TWO_PINCH_OUT(2),
        TWO_SWIPE_LEFT(2),
        TWO_SWIPE_RIGHT(2),

        // 3 fingers: Fn-specialized
        THREE_TAP(3),
        THREE_DOUBLE_TAP(3),
        THREE_SWIPE_LEFT(3),
        THREE_SWIPE_RIGHT(3),
        THREE_SWIPE_UP(3),
        THREE_SWIPE_DOWN(3),

        // 4 fingers
        FOUR_WINDOW_SWITCH_START_LEFT(4),
        FOUR_WINDOW_SWITCH_START_RIGHT(4),
        FOUR_WINDOW_SWITCH_STEP_LEFT(4),
        FOUR_WINDOW_SWITCH_STEP_RIGHT(4),
        FOUR_WINDOW_SWITCH_COMMIT(4),

        // 5 fingers
        FIVE_LONG_HOLD_CONFIG(5)
    }

    data class GestureEntry(
        val gesture: GestureId,
        val function: FunctionId,
        val label: String
    )

    val entries: Map<GestureId, GestureEntry> = listOf(
        GestureEntry(GestureId.ONE_MOVE, FunctionId.CURSOR_MOVE, "1F move -> cursor"),
        GestureEntry(GestureId.ONE_TAP, FunctionId.LEFT_CLICK, "1F tap -> left click"),
        GestureEntry(GestureId.ONE_DOUBLE_TAP, FunctionId.DOUBLE_LEFT_CLICK, "1F double tap -> double click"),
        GestureEntry(GestureId.ONE_DRAG_START, FunctionId.LEFT_DRAG_START, "1F hold/move -> drag start"),
        GestureEntry(GestureId.ONE_DRAG_MOVE, FunctionId.LEFT_DRAG_MOVE, "1F drag -> drag move"),
        GestureEntry(GestureId.ONE_DRAG_END, FunctionId.LEFT_DRAG_END, "1F release -> drag end"),

        GestureEntry(GestureId.TWO_TAP, FunctionId.RIGHT_CLICK, "2F tap -> right click"),
        GestureEntry(GestureId.TWO_DOUBLE_TAP, FunctionId.MIDDLE_CLICK, "2F double tap -> middle click"),
        GestureEntry(GestureId.TWO_PAN_2D, FunctionId.SCROLL_2D, "2F pan -> 2D scroll"),
        GestureEntry(GestureId.TWO_PINCH_IN, FunctionId.ZOOM_OUT, "2F pinch in -> zoom out"),
        GestureEntry(GestureId.TWO_PINCH_OUT, FunctionId.ZOOM_IN, "2F pinch out -> zoom in"),
        GestureEntry(GestureId.TWO_SWIPE_LEFT, FunctionId.NAV_BACK, "2F left -> back"),
        GestureEntry(GestureId.TWO_SWIPE_RIGHT, FunctionId.NAV_FORWARD, "2F right -> forward"),

        GestureEntry(GestureId.THREE_TAP, FunctionId.FN_EXECUTE_CURRENT, "3F single tap -> no assignment"),
        GestureEntry(GestureId.THREE_DOUBLE_TAP, FunctionId.AUDIO_MUTE, "3F double tap -> Mute"),
        GestureEntry(GestureId.THREE_SWIPE_UP, FunctionId.TOGGLE_ANDROID_KEYBOARD_WITH_HANZEN, "3F up -> Keyboard + Han/Zen"),
        GestureEntry(GestureId.THREE_SWIPE_DOWN, FunctionId.KEY_F5_REFRESH, "3F down -> Refresh (F5)"),
        GestureEntry(GestureId.THREE_SWIPE_LEFT, FunctionId.CLIPBOARD_COPY, "3F left -> Copy"),
        GestureEntry(GestureId.THREE_SWIPE_RIGHT, FunctionId.CLIPBOARD_PASTE, "3F right -> Paste"),

        GestureEntry(GestureId.FOUR_WINDOW_SWITCH_START_LEFT, FunctionId.ACTIVE_WINDOW_SWITCH_START_PREVIOUS, "4F hold + left -> open Alt+Tab and select previous"),
        GestureEntry(GestureId.FOUR_WINDOW_SWITCH_START_RIGHT, FunctionId.ACTIVE_WINDOW_SWITCH_START_NEXT, "4F hold + right -> open Alt+Tab and select next"),
        GestureEntry(GestureId.FOUR_WINDOW_SWITCH_STEP_LEFT, FunctionId.ACTIVE_WINDOW_SWITCH_STEP_PREVIOUS, "4F held left -> move Alt+Tab selection previous"),
        GestureEntry(GestureId.FOUR_WINDOW_SWITCH_STEP_RIGHT, FunctionId.ACTIVE_WINDOW_SWITCH_STEP_NEXT, "4F held right -> move Alt+Tab selection next"),
        GestureEntry(GestureId.FOUR_WINDOW_SWITCH_COMMIT, FunctionId.ACTIVE_WINDOW_SWITCH_COMMIT, "4F release -> commit Alt+Tab selection"),
        GestureEntry(GestureId.FIVE_LONG_HOLD_CONFIG, FunctionId.OPEN_DIAZY_CONFIG, "5F long hold -> DiazyMouse config")
    ).associateBy { it.gesture }

    fun get(gesture: GestureId): GestureEntry =
        requireNotNull(entries[gesture]) { "Unknown AllTouch gesture: $gesture" }

    fun functionFor(gesture: GestureId): AllTouchFunctionDictionary.FunctionEntry =
        AllTouchFunctionDictionary.get(get(gesture).function)
}
