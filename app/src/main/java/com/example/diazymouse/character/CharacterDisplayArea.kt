package com.example.diazymouse.character

import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.example.diazymouse.dialogue.DialogueState
import com.example.diazymouse.settings.store.CharacterImageSettingsStore
import android.view.View

/**
 * Character image renderer for Text Area 2.
 *
 * The image keeps its original aspect ratio and is fitted inside
 * the available Text Area 2 region.
 */
class CharacterDisplayArea(
    private val activity: ComponentActivity
) {

    private var imageView:
        ImageView? =
        null

    fun addTo(
        parent: FrameLayout,
        initialCharacter: CharacterProfile =
            CharacterProfile.CHAR1,
        initialState: DialogueState =
            DialogueState.NORMAL
    ) {

        val view =
            ImageView(activity).apply {

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                adjustViewBounds =
                    true

                isClickable =
                    false

                isFocusable =
                    false
            }

        imageView =
            view
        view.visibility = if (CharacterImageSettingsStore.isEnabled(activity)) View.VISIBLE else View.GONE

        parent.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        show(
            character =
                initialCharacter,
            state =
                initialState
        )
    }

    fun show(
        character: CharacterProfile,
        state: DialogueState
    ) {

        imageView?.setImageResource(
            CharacterAssets.drawableRes(
                character =
                    character,
                state =
                    state
            )
        )
    }
}
