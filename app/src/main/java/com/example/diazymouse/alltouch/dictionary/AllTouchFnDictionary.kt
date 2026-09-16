package com.example.diazymouse.alltouch.dictionary

import com.example.diazymouse.alltouch.dictionary.AllTouchFunctionDictionary.FunctionId

/** AllTouch Fn assignments are stored independently from Classic. v1.1 uses five fixed slots. */
object AllTouchFnDictionary {
    enum class FnId(val number: Int) {
        FN1(1), FN2(2), FN3(3), FN4(4), FN5(5);
        companion object {
            fun fromNumber(number: Int): FnId? = values().firstOrNull { it.number == number }
        }
    }

    data class FnEntry(val fn: FnId, val function: FunctionId, val label: String)

    val entries: Map<FnId, FnEntry> = listOf(
        FnEntry(FnId.FN1, FunctionId.AUDIO_MUTE, "Mute"),
        FnEntry(FnId.FN2, FunctionId.TOGGLE_ANDROID_KEYBOARD_WITH_HANZEN, "Keyboard + Han/Zen"),
        FnEntry(FnId.FN3, FunctionId.KEY_F5_REFRESH, "Refresh (F5)"),
        FnEntry(FnId.FN4, FunctionId.CLIPBOARD_COPY, "Copy"),
        FnEntry(FnId.FN5, FunctionId.CLIPBOARD_PASTE, "Paste")
    ).associateBy { it.fn }

    val defaultFn: FnId = FnId.FN1
    fun get(fn: FnId): FnEntry = requireNotNull(entries[fn]) { "Unknown AllTouch Fn: $fn" }
    fun get(number: Int): FnEntry? = FnId.fromNumber(number)?.let(entries::get)
    fun functionFor(fn: FnId): AllTouchFunctionDictionary.FunctionEntry =
        AllTouchFunctionDictionary.get(get(fn).function)
}
