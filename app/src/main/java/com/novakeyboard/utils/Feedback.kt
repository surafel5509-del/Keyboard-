package com.novakeyboard.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.novakeyboard.R

/** Haptic + sound feedback, both respecting the user's in-app settings. */
object Feedback {

    fun keyTap(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    fun tick(view: View) = keyTap(view)

    fun longPress(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    fun buzz(context: Context, milliseconds: Long = 12L) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
            }
        } catch (ignored: Exception) {
            // Haptics are best effort.
        }
    }
}

/**
 * Tiny SoundPool wrapper around the bundled key click. Falls back to the system
 * key click when the pool cannot be prepared (for example in a silent profile).
 */
class KeySoundPlayer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var audioManager: AudioManager? = null
    private var enabled = true
    private var volume = 0.6f

    fun configure(enabled: Boolean, volume: Float) {
        this.enabled = enabled
        this.volume = volume.coerceIn(0f, 1f)
    }

    fun load() {
        if (soundPool != null) return
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val pool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) soundId = loadedSoundId
            }
            loadedSoundId = pool.load(context, R.raw.key_click, 1)
            soundPool = pool
        } catch (ignored: Exception) {
            soundPool = null
        }
    }

    private var loadedSoundId = 0

    fun click() {
        if (!enabled) return
        val played = try {
            val pool = soundPool
            if (pool != null && soundId != 0) {
                pool.play(soundId, volume, volume, 1, 0, 1f)
                true
            } else {
                false
            }
        } catch (ignored: Exception) {
            false
        }
        if (!played) {
            try {
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, volume)
            } catch (ignored: Exception) {
                // Nothing else to do.
            }
        }
    }

    fun release() {
        try {
            soundPool?.release()
        } catch (ignored: Exception) {
        }
        soundPool = null
        soundId = 0
        loadedSoundId = 0
    }
}
