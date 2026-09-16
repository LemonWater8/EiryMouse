package com.example.diazymouse.alltouch.dictionary

import com.example.diazymouse.alltouch.dictionary.AllTouchFnDictionary.FnId
import com.example.diazymouse.alltouch.dictionary.AllTouchFunctionDictionary.FunctionId
import com.example.diazymouse.alltouch.dictionary.AllTouchGestureDictionary.GestureId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllTouchDictionaryTest {

    @Test
    fun dictionariesAreCompleteAndConsistent() {
        assertTrue(AllTouchDictionaryRegistry.validate().isEmpty())
        assertEquals(5, AllTouchFnDictionary.entries.size)
    }

    @Test
    fun allTouchFnAssignmentsMatchSpecification() {
        assertEquals(
            FunctionId.AUDIO_MUTE,
            AllTouchFnDictionary.get(FnId.FN1).function
        )

        assertEquals(
            FunctionId.TOGGLE_ANDROID_KEYBOARD_WITH_HANZEN,
            AllTouchFnDictionary.get(FnId.FN2).function
        )

        assertEquals(
            FunctionId.KEY_F5_REFRESH,
            AllTouchFnDictionary.get(FnId.FN3).function
        )

        assertEquals(
            FunctionId.CLIPBOARD_COPY,
            AllTouchFnDictionary.get(FnId.FN4).function
        )

        assertEquals(
            FunctionId.CLIPBOARD_PASTE,
            AllTouchFnDictionary.get(FnId.FN5).function
        )
    }

    @Test
    fun correctedTwoFingerDoubleTapIsMiddleClick() {
        assertEquals(
            FunctionId.MIDDLE_CLICK,
            AllTouchGestureDictionary.get(GestureId.TWO_DOUBLE_TAP).function
        )
    }
}