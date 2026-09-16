package com.example.diazymouse.character

import androidx.annotation.DrawableRes
import com.example.diazymouse.dialogue.DialogueState

/** Character artwork was removed from EiryMouse. */
object CharacterAssets {
    @DrawableRes
    fun drawableRes(
        @Suppress("UNUSED_PARAMETER") character: CharacterProfile,
        @Suppress("UNUSED_PARAMETER") state: DialogueState
    ): Int = 0
}
