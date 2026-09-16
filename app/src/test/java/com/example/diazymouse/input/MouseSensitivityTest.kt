package com.example.diazymouse.input

import org.junit.Assert.assertTrue
import org.junit.Test

class MouseSensitivityTest {
    @Test
    fun defaultPointerCurveStaysGentle() {
        val userScale = 1.0f

        assertTrue(MouseSensitivity.calculateMultiplier(2f, userScale) < 1.0f)
        assertTrue(MouseSensitivity.calculateMultiplier(8f, userScale) < 1.5f)
        assertTrue(MouseSensitivity.calculateMultiplier(20f, userScale) < 2.2f)
    }
}
