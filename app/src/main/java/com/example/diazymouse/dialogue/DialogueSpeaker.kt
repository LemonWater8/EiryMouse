package com.example.diazymouse.dialogue

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.diazymouse.character.CharacterProfile
import com.example.diazymouse.settings.store.VoiceSettingsStore
import java.util.Locale

/**
 * Android standard TextToSpeech wrapper.
 *
 * One TTS engine instance is shared for both Japanese and English.
 * The locale is switched immediately before each utterance.
 */
class DialogueSpeaker(
    context: Context
) : TextToSpeech.OnInitListener {

    private val appContext =
        context.applicationContext

    private var textToSpeech:
        TextToSpeech? =
        null

    private var isReady =
        false

    private var pendingSpeech:
        PendingSpeech? =
        null

    private data class PendingSpeech(
        val text: String,
        val locale: Locale
    )

    init {
        textToSpeech =
            TextToSpeech(
                appContext,
                this
            )
    }

    override fun onInit(
        status: Int
    ) {

        isReady =
            status ==
                TextToSpeech.SUCCESS

        if (
            !isReady
        ) {
            pendingSpeech =
                null

            return
        }

        pendingSpeech?.let {
            pending ->

            pendingSpeech =
                null

            speakInternal(
                text =
                    pending.text,
                locale =
                    pending.locale
            )
        }
    }

    fun speak(
        text: String,
        character: CharacterProfile
    ) {

        if (
            text.isBlank()
        ) {
            return
        }

        if (
            !VoiceSettingsStore.isEnabled(
                appContext
            )
        ) {
            stop()

            return
        }

        val locale =
            when (
                character
            ) {

                CharacterProfile.CHAR1 ->
                    Locale.JAPANESE

                CharacterProfile.CHAR2 ->
                    Locale.ENGLISH
            }

        if (
            !isReady
        ) {

            /*
             * TTS initialization is asynchronous.
             * Keep only the newest dialogue if initialization has not
             * completed yet.
             */
            pendingSpeech =
                PendingSpeech(
                    text =
                        text,
                    locale =
                        locale
                )

            return
        }

        speakInternal(
            text =
                text,
            locale =
                locale
        )
    }

    fun stop() {

        pendingSpeech =
            null

        textToSpeech?.stop()
    }

    fun release() {

        pendingSpeech =
            null

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        textToSpeech =
            null

        isReady =
            false
    }

    private fun speakInternal(
        text: String,
        locale: Locale
    ) {

        val engine =
            textToSpeech ?:
                return

        val languageResult =
            engine.setLanguage(
                locale
            )

        if (
            languageResult ==
                TextToSpeech.LANG_MISSING_DATA ||
            languageResult ==
                TextToSpeech.LANG_NOT_SUPPORTED
        ) {

            return
        }

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "MyMoveMouseDialogue"
        )
    }
}
