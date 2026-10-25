package com.novakeyboard.ime

enum class ShiftState { OFF, ON, CAPS }

/**
 * Shift / Caps-Lock behaviour, kept free of Android types so it can be unit
 * tested: single tap = one-shot shift, double tap within [DOUBLE_TAP_MS] = caps
 * lock, tapping again while locked releases it.
 */
class ShiftStateMachine(private val doubleTapMs: Long = 380L) {

    var state: ShiftState = ShiftState.OFF
        private set

    private var lastShiftTapAt: Long = 0L

    val isUppercase: Boolean get() = state != ShiftState.OFF
    val isCapsLock: Boolean get() = state == ShiftState.CAPS

    fun onShiftTapped(now: Long): ShiftState {
        state = when {
            state == ShiftState.CAPS -> ShiftState.OFF
            state == ShiftState.ON && now - lastShiftTapAt <= doubleTapMs -> ShiftState.CAPS
            state == ShiftState.ON -> ShiftState.OFF
            else -> ShiftState.ON
        }
        lastShiftTapAt = now
        return state
    }

    /** Called after a character was committed. */
    fun onCharacterTyped() {
        if (state == ShiftState.ON) state = ShiftState.OFF
    }

    /** Applies auto-capitalisation without touching caps lock. */
    fun applyAutoCapitalisation(shouldCapitalise: Boolean) {
        if (state == ShiftState.CAPS) return
        state = if (shouldCapitalise) ShiftState.ON else ShiftState.OFF
    }

    fun reset() {
        state = ShiftState.OFF
        lastShiftTapAt = 0L
    }

    fun setCapsLock() {
        state = ShiftState.CAPS
    }

    companion object {
        const val DOUBLE_TAP_MS = 380L
    }
}
