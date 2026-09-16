package com.example.diazymouse.settings.model

/**
 * Top-level UI mode for DiazyMouse.
 *
 * CLASSIC keeps the existing mouse-control UI.
 * ALL_TOUCH is the full-surface touch mode.  Its first-stage implementation
 * intentionally contains only the character, dialogue and the common config
 * entry button so touch recognition can be added without disturbing CLASSIC.
 */
enum class OperationMode {
    CLASSIC,
    ALL_TOUCH
}
