package com.novakeyboard.ime

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * The actual Android input method. All rendering and editing logic lives in
 * [KeyboardController]; this class only wires the platform callbacks.
 */
class NovaInputMethodService : InputMethodService() {

    private lateinit var controller: KeyboardController

    override fun onCreate() {
        super.onCreate()
        controller = KeyboardController(this)
        controller.onCreate()
    }

    override fun onCreateInputView(): View {
        val view = controller.createInputView()
        window?.window?.setDimAmount(0f)
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.onStartInputView(info, restarting)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onSelectionChanged(newSelStart, newSelEnd)
    }

    override fun onFinishInput() {
        controller.onFinishInput()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.onFinishInputView()
        super.onFinishInputView(finishingInput)
    }

    /** Never take over the whole screen: the keyboard is designed to fit. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onConfigurationChanged(newConfig: Configuration) {
        controller.onConfigurationChanged()
        super.onConfigurationChanged(newConfig)
    }

    override fun onTrimMemory(level: Int) {
        controller.onTrimMemory(level)
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }

    /**
     * Physical keyboards keep working natively: the platform delivers hardware
     * key events to the editor, so we only intercept the modifier-less keys the
     * on-screen keyboard would otherwise duplicate.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = super.onKeyDown(keyCode, event)
}
