package com.example.diazymouse.settings.model

object MouseSpeedConfig {

    // Pointer acceleration thresholds.
    const val DISTANCE_LEVEL_1 = 3f
    const val DISTANCE_LEVEL_2 = 5f
    const val DISTANCE_LEVEL_3 = 12f

    // Current multipliers are preserved from the working version.
    const val MULTIPLIER_LEVEL_1 = 0.7f
    const val MULTIPLIER_LEVEL_2 = 1.9f
    const val MULTIPLIER_LEVEL_3 = 4.9f
    const val MULTIPLIER_LEVEL_4 = 2.8f

    // Scroll ring radius settings.
    const val SCROLL_MIN_RADIUS_RATIO = 0.05f
    const val SCROLL_MAX_RADIUS_RATIO = 0.95f

    // Scroll-ring direction dead zone.
    const val SCROLL_DIRECTION_DEAD_ZONE_DEG = 1.0

    // Angular movement required for one scroll step.
    const val SCROLL_STEP_ANGLE_DEG = 1.5
}