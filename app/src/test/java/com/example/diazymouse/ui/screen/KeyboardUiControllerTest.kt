package com.example.diazymouse.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardUiControllerTest {
    @Test
    fun `c guard keeps keyboard input active on both left and right sides`() {
        val cx = 100f
        val cy = 100f
        val radius = 40f
        val guardWidth = 76f
        val rightExtraCoverage = 22f

        assertTrue(
            KeyboardUiController.isLandscapeCGuardEnvelope(
                cx,
                cy,
                cx - radius - guardWidth + 1f,
                cy + 10f,
                radius,
                guardWidth
            )
        )
        assertTrue(
            KeyboardUiController.isLandscapeCGuardEnvelope(
                cx,
                cy,
                cx + radius + guardWidth + rightExtraCoverage - 1f,
                cy + 10f,
                radius,
                guardWidth,
                rightExtraCoverage = rightExtraCoverage
            )
        )
        assertFalse(
            KeyboardUiController.isLandscapeCGuardEnvelope(
                cx,
                cy,
                cx + radius + guardWidth + rightExtraCoverage + 10f,
                cy + 10f,
                radius,
                guardWidth,
                rightExtraCoverage = rightExtraCoverage
            )
        )
    }
}
