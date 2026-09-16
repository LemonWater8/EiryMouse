package com.example.diazymouse.dialogue

import com.example.diazymouse.character.CharacterProfile
import kotlin.random.Random

object DialogueRepository {

    fun randomLine(
        character: CharacterProfile,
        state: DialogueState,
        random: Random = Random.Default
    ): String {

        val candidates = when (state) {
            DialogueState.NORMAL -> DialogueLines.NORMAL
            DialogueState.FUN -> DialogueLines.FUN
            DialogueState.SAD -> DialogueLines.SAD
        }

        return candidates[random.nextInt(candidates.size)]
    }
}