package com.example.diazymouse.ui.screen

import android.graphics.Color
import android.graphics.Typeface
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiLayoutConfig
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.character.CharacterDisplayArea
import com.example.diazymouse.character.CharacterProfile
import com.example.diazymouse.dialogue.DialogueRepository
import com.example.diazymouse.dialogue.DialogueSpeaker
import com.example.diazymouse.dialogue.DialogueState
import com.example.diazymouse.settings.store.FontSettingsStore
import com.example.diazymouse.settings.store.LanguageSettingsStore
import com.example.diazymouse.settings.store.MainTextAreaColorSettingsStore
import com.example.diazymouse.settings.store.MainTextAreaFontSizeStore
import com.example.diazymouse.settings.store.VoiceSettingsStore
import com.example.diazymouse.settings.store.CharacterImageSettingsStore

class TextDisplayArea(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions
) {
    private var mainTextArea: TextView? = null
    private var dialogueView: TextView? = null
    private var characterDisplayArea: CharacterDisplayArea? = null
    private var currentState: DialogueState = DialogueState.NORMAL

    private val dialogueSpeaker =
        DialogueSpeaker(
            activity
        )

    fun addTo(root: FrameLayout) {
        addMainTextArea(root)
        addTextArea2(root)
        addBottomDeadZone(root)
        showDialogueState(DialogueState.NORMAL)
    }

    fun showAction(actionName: String) {
        mainTextArea?.text = actionName
        applyStyle()
    }

    fun applyStyle() {
        mainTextArea?.let { view ->
            FontSettingsStore.applyTo(activity, view)
            view.textSize = MainTextAreaFontSizeStore.getSizeSp(activity).toFloat()
            view.setBackgroundColor(
                MainTextAreaColorSettingsStore.getBackground(activity).toColor()
            )
            view.setTextColor(
                MainTextAreaColorSettingsStore.getText(activity).toColor()
            )
        }
    }

    fun showDialogueState(state: DialogueState) {
        currentState = state

        val character =
            LanguageSettingsStore.character(
                activity
            )

        val message =
            DialogueRepository.randomLine(
                character = character,
                state = state
            )

        characterDisplayArea?.show(
            character = character,
            state = state
        )

        dialogueView?.text =
            message

        /*
         * Voice ON:
         *   Speak the same text displayed in Text Area 3.
         *
         * Voice OFF:
         *   DialogueSpeaker stops and produces no audio.
         */
        dialogueSpeaker.speak(
            text = message,
            character = character
        )
    }

    fun refreshDialogueConfig() {
        showDialogueState(currentState)
    }

    /**
     * Dedicated preview for Fn11 -> Finish confirmation.
     *
     * This intentionally does not overwrite currentState, so Continue can
     * restore the dialogue state that was active before opening Finish confirmation.
     */
    fun showFinishPreview() {
        val character =
            LanguageSettingsStore.character(
                activity
            )

        val message =
            when (character) {
                CharacterProfile.CHAR1 ->
                    "Please come back again. I will be waiting."

                CharacterProfile.CHAR2 ->
                    "Mission Over."
            }

        characterDisplayArea?.show(
            character = character,
            state = DialogueState.SAD
        )

        dialogueView?.text =
            message

        dialogueSpeaker.speak(
            text = message,
            character = character
        )
    }

    fun restoreCurrentDialogue() {
        showDialogueState(currentState)
    }

    fun release() {
        dialogueSpeaker.release()
    }

    private fun addMainTextArea(root: FrameLayout) {
        val view = createTextArea(UiConfig.TEXT_AREA_1_TEXT).apply {
            gravity = Gravity.CENTER
        }
        mainTextArea = view
        applyStyle()

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dimensions.textArea1HeightPx
        ).apply {
            gravity = Gravity.TOP
            topMargin = dimensions.textArea1TopPx
            leftMargin = dimensions.buttonWidthPx
            rightMargin = dimensions.buttonWidthPx
        }
        root.addView(view, params)
    }

    private fun addBottomDeadZone(root: FrameLayout) {
        val deadZoneHeightPx = UiDimensions.dpToPx(
            UiLayoutConfig.BOTTOM_DEAD_ZONE_DP,
            activity.resources.displayMetrics
        )

        val deadZone = FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnTouchListener { _, _ -> true }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            deadZoneHeightPx
        ).apply {
            gravity = Gravity.BOTTOM
        }
        root.addView(deadZone, params)
    }

    private fun addTextArea2(root: FrameLayout) {
        val characterEnabled = CharacterImageSettingsStore.isEnabled(activity)
        val textArea2 = FrameLayout(activity).apply {
            setBackgroundColor(if (characterEnabled) Color.WHITE else Color.BLACK)
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dimensions.textArea2HeightPx
        ).apply {
            gravity = Gravity.TOP
            topMargin = dimensions.textArea2TopPx
        }

        root.addView(textArea2, params)

        // IMAGE OFF means no Classic character and no character dialogue section.
        // Keep the existing mouse UI proportions; only this visual region becomes black.
        if (!characterEnabled) {
            dialogueView = null
            characterDisplayArea = null
            return
        }

        val isLandscape =
            dimensions.screenWidthPx > dimensions.screenHeightPx

        val dialogueHeight =
            if (isLandscape) {
                (dimensions.textArea2HeightPx * 0.09f).toInt().coerceAtLeast(1)
            } else {
                (dimensions.textArea2HeightPx * 0.15f).toInt().coerceAtLeast(1)
            }
        val imageHeight =
            (dimensions.textArea2HeightPx - dialogueHeight).coerceAtLeast(1)

        val imageContainer = FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
        }

        textArea2.addView(
            imageContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                imageHeight
            ).apply {
                gravity = Gravity.TOP
            }
        )

        val display = CharacterDisplayArea(activity)
        characterDisplayArea = display
        display.addTo(
            parent = imageContainer,
            initialCharacter = LanguageSettingsStore.character(activity),
            initialState = DialogueState.NORMAL
        )

        val dialogue = TextView(activity).apply {
            textSize = UiConfig.TEXT_AREA_TEXT_SIZE_SP
            typeface = Typeface.DEFAULT

            /*
             * Text Area 3 dialogue alignment:
             * - Japanese and English use exactly the same behavior.
             * - Short text is centered.
             * - Longer text expands horizontally from the center while the
             *   TextView continues to use the full available width.
             * - If the text exceeds the available width, Android may wrap it
             *   naturally inside Text Area 3.
             */
            gravity = Gravity.CENTER
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER

            setPadding(12, 4, 12, 4)

            // Gray background with near-white text for readability.
            setBackgroundColor(Color.rgb(72, 72, 72))
            setTextColor(Color.rgb(235, 235, 235))
        }
        dialogueView = dialogue

        textArea2.addView(
            dialogue,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dialogueHeight
            ).apply {
                gravity = Gravity.BOTTOM
            }
        )
    }

    private fun createTextArea(label: String): TextView =
        TextView(activity).apply {
            text = label
            textSize = UiConfig.TEXT_AREA_TEXT_SIZE_SP
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(UiConfig.DISPLAY_TEXT_COLOR)
            setBackgroundColor(UiConfig.TEXT_AREA_BACKGROUND)
        }
}
