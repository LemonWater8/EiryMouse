package com.example.diazymouse.alltouch.dictionary

/**
 * Single lookup/validation facade used by the future AllTouch recognizer and
 * executor.  Keeping this tiny prevents the three dictionaries from leaking
 * implementation details into UI code.
 */
object AllTouchDictionaryRegistry {

    fun functionForGesture(
        gesture: AllTouchGestureDictionary.GestureId
    ): AllTouchFunctionDictionary.FunctionEntry =
        AllTouchGestureDictionary.functionFor(gesture)

    fun functionForFn(
        fn: AllTouchFnDictionary.FnId
    ): AllTouchFunctionDictionary.FunctionEntry =
        AllTouchFnDictionary.functionFor(fn)

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        AllTouchFunctionDictionary.FunctionId.values().forEach { id ->
            if (!AllTouchFunctionDictionary.contains(id)) {
                errors += "Function dictionary is missing $id"
            }
        }

        AllTouchFnDictionary.FnId.values().forEach { fn ->
            val fnEntry = AllTouchFnDictionary.entries[fn]
            if (fnEntry == null) {
                errors += "Fn dictionary is missing $fn"
            } else if (!AllTouchFunctionDictionary.contains(fnEntry.function)) {
                errors += "$fn points to missing function ${fnEntry.function}"
            }
        }

        AllTouchGestureDictionary.GestureId.values().forEach { gesture ->
            val gestureEntry = AllTouchGestureDictionary.entries[gesture]
            if (gestureEntry == null) {
                errors += "Gesture dictionary is missing $gesture"
            } else if (!AllTouchFunctionDictionary.contains(gestureEntry.function)) {
                errors += "$gesture points to missing function ${gestureEntry.function}"
            }
        }

        return errors
    }

    fun requireValid() {
        val errors = validate()
        require(errors.isEmpty()) {
            "Invalid AllTouch dictionaries:\n${errors.joinToString("\n")}" 
        }
    }
}
