package com.example.diazymouse.alltouch.dictionary

/**
 * Dictionary 1/3: every semantic action AllTouch may request.
 *
 * Gesture recognition and Fn selection never send HID/ADB commands directly.
 * They first resolve to one of these action IDs.  The executor layer can then
 * choose ADB, Bluetooth HID, Android-local behavior, or a combination.
 */
object AllTouchFunctionDictionary {

    enum class FunctionId {
        // Pointer / mouse
        CURSOR_MOVE,
        LEFT_CLICK,
        DOUBLE_LEFT_CLICK,
        LEFT_DRAG_START,
        LEFT_DRAG_MOVE,
        LEFT_DRAG_END,
        RIGHT_CLICK,
        MIDDLE_CLICK,
        SCROLL_2D,
        ZOOM_IN,
        ZOOM_OUT,
        NAV_BACK,
        NAV_FORWARD,

        // Three-finger Fn controller
        FN_EXECUTE_CURRENT,
        FN_NEXT_IN_LANE,
        FN_SWITCH_LANE,

        // Window switching / Android-local
        KEY_ENTER,
        KEY_HANKAKU_ZENKAKU,
        OPEN_DIAZY_CONFIG,
        ACTIVE_WINDOW_PREVIOUS,
        ACTIVE_WINDOW_NEXT,
        ACTIVE_WINDOW_SWITCH_START_PREVIOUS,
        ACTIVE_WINDOW_SWITCH_START_NEXT,
        ACTIVE_WINDOW_SWITCH_STEP_PREVIOUS,
        ACTIVE_WINDOW_SWITCH_STEP_NEXT,
        ACTIVE_WINDOW_SWITCH_COMMIT,

        // AllTouch Fn targets
        AUDIO_MUTE,
        PRINT_SCREEN_FULL,
        TOGGLE_ANDROID_KEYBOARD_WITH_HANZEN,
        KEY_HENKAN,
        KEY_F5_REFRESH,
        KEY_HOME,
        KEY_END,
        PRINT_ACTIVE_WINDOW,
        CLIPBOARD_COPY,
        CLIPBOARD_PASTE
    }

    enum class Target {
        POINTER,
        WINDOWS,
        ANDROID,
        FN_CONTROLLER
    }

    enum class TransportCapability {
        /** Existing EiryMouse path can already perform this action. */
        EXISTING,

        /** Dictionary is ready; executor/transport command is added in a later integration step. */
        NEEDS_EXECUTOR,

        /** Performed locally on Android rather than sent to the PC. */
        ANDROID_LOCAL,

        /** Changes AllTouch's internal Fn selection state only. */
        INTERNAL
    }

    data class FunctionEntry(
        val id: FunctionId,
        val label: String,
        val target: Target,
        val capability: TransportCapability,
        val note: String = ""
    )

    private fun entry(
        id: FunctionId,
        label: String,
        target: Target,
        capability: TransportCapability,
        note: String = ""
    ) = FunctionEntry(id, label, target, capability, note)

    val entries: Map<FunctionId, FunctionEntry> = listOf(
        entry(FunctionId.CURSOR_MOVE, "Cursor move", Target.POINTER, TransportCapability.EXISTING),
        entry(FunctionId.LEFT_CLICK, "Left click", Target.POINTER, TransportCapability.EXISTING),
        entry(FunctionId.DOUBLE_LEFT_CLICK, "Double left click", Target.POINTER, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.LEFT_DRAG_START, "Drag start", Target.POINTER, TransportCapability.EXISTING),
        entry(FunctionId.LEFT_DRAG_MOVE, "Drag move", Target.POINTER, TransportCapability.EXISTING),
        entry(FunctionId.LEFT_DRAG_END, "Drag end", Target.POINTER, TransportCapability.EXISTING),
        entry(FunctionId.RIGHT_CLICK, "Right click", Target.POINTER, TransportCapability.EXISTING),
        entry(FunctionId.MIDDLE_CLICK, "Middle click", Target.POINTER, TransportCapability.EXISTING),
        entry(FunctionId.SCROLL_2D, "2D scroll", Target.POINTER, TransportCapability.NEEDS_EXECUTOR,
            "Current MainInputTransport exposes vertical wheel; horizontal path will be added with AllTouch executor."),
        entry(FunctionId.ZOOM_IN, "Zoom in", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.ZOOM_OUT, "Zoom out", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.NAV_BACK, "Back", Target.WINDOWS, TransportCapability.EXISTING),
        entry(FunctionId.NAV_FORWARD, "Forward", Target.WINDOWS, TransportCapability.EXISTING),

        entry(FunctionId.FN_EXECUTE_CURRENT, "Execute current Fn", Target.FN_CONTROLLER, TransportCapability.INTERNAL),
        entry(FunctionId.FN_NEXT_IN_LANE, "Next Fn in lane", Target.FN_CONTROLLER, TransportCapability.INTERNAL),
        entry(FunctionId.FN_SWITCH_LANE, "Switch Fn lane", Target.FN_CONTROLLER, TransportCapability.INTERNAL,
            "Lane 1 = Fn1..Fn4, Lane 2 = Fn5..Fn8."),

        entry(FunctionId.KEY_ENTER, "Enter", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.KEY_HANKAKU_ZENKAKU, "Hankaku/Zenkaku", Target.WINDOWS, TransportCapability.EXISTING),
        entry(FunctionId.OPEN_DIAZY_CONFIG, "Open DiazyMouse config", Target.ANDROID, TransportCapability.ANDROID_LOCAL),
        entry(FunctionId.ACTIVE_WINDOW_PREVIOUS, "Previous active window", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.ACTIVE_WINDOW_NEXT, "Next active window", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.ACTIVE_WINDOW_SWITCH_START_PREVIOUS, "Start Alt+Tab previous", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.ACTIVE_WINDOW_SWITCH_START_NEXT, "Start Alt+Tab next", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.ACTIVE_WINDOW_SWITCH_STEP_PREVIOUS, "Alt+Tab previous step", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.ACTIVE_WINDOW_SWITCH_STEP_NEXT, "Alt+Tab next step", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.ACTIVE_WINDOW_SWITCH_COMMIT, "Commit Alt+Tab selection", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),

        entry(FunctionId.AUDIO_MUTE, "Mute", Target.WINDOWS, TransportCapability.EXISTING,
            "Bluetooth Consumer HID path already exists; ADB support can be added by the executor."),
        entry(FunctionId.PRINT_SCREEN_FULL, "Print whole screen", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR,
            "Temporary AllTouch assignment. Intended Windows behavior: standard Print Screen for the whole desktop."),
        entry(FunctionId.TOGGLE_ANDROID_KEYBOARD_WITH_HANZEN, "Android keyboard + Han/Zen", Target.ANDROID, TransportCapability.ANDROID_LOCAL,
            "Temporary AllTouch assignment. Must use MainScreen.toggleKeyboardWithHanZen() so the Classic Han/Zen button appears and disappears together with the Android software keyboard."),
        entry(FunctionId.KEY_HENKAN, "Convert", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.KEY_F5_REFRESH, "Refresh (F5)", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.KEY_HOME, "Home", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.KEY_END, "End", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR),
        entry(FunctionId.PRINT_ACTIVE_WINDOW, "Print active window", Target.WINDOWS, TransportCapability.NEEDS_EXECUTOR,
            "Intended Windows behavior: active-window screenshot rather than whole-screen screenshot."),
        entry(FunctionId.CLIPBOARD_COPY, "Copy", Target.WINDOWS, TransportCapability.EXISTING),
        entry(FunctionId.CLIPBOARD_PASTE, "Paste", Target.WINDOWS, TransportCapability.EXISTING)
    ).associateBy { it.id }

    fun get(id: FunctionId): FunctionEntry =
        requireNotNull(entries[id]) { "Unknown AllTouch FunctionId: $id" }

    fun contains(id: FunctionId): Boolean = entries.containsKey(id)
}
