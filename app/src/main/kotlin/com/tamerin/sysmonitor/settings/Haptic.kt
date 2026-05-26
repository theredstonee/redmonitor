package com.tamerin.sysmonitor.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Categorizes the kind of action so we pick the right physical pattern.
 * Mapped to actual VibrationEffect via [Haptic.perform] depending on user intensity.
 */
enum class HapticType {
    TAP,           // normaler Button/Card tap → leichter Tick
    TOGGLE,        // Switch flip
    DESTRUCTIVE,   // Force-Stop, Delete, Drain Mode → kräftig
    CONFIRM,       // erfolgreiche Bestätigung → double-tick
    ERROR,         // fehlgeschlagene Aktion → kurzes warn-Pattern
    SLIDER_TICK,   // dezenter Tick beim Slider-Step
    LONG_PRESS     // beim Halten
}

object Haptic {

    fun perform(context: Context, type: HapticType) {
        if (!AppPrefs.hapticFeedbackEnabled(context)) return
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val intensity = AppPrefs.hapticIntensity(context)
        val effect = effectFor(type, intensity) ?: return
        runCatching { vibrator.vibrate(effect) }
    }

    private fun effectFor(type: HapticType, intensity: HapticIntensity): VibrationEffect? {
        // Predefined effects (Q+) cover most cases; older devices fall back to createOneShot
        return when (type) {
            HapticType.TAP -> tick(intensity)
            HapticType.TOGGLE -> when (intensity) {
                HapticIntensity.WEAK -> tick(intensity)
                HapticIntensity.MEDIUM -> click(intensity)
                HapticIntensity.STRONG -> heavyClick(intensity)
            }
            HapticType.DESTRUCTIVE -> when (intensity) {
                HapticIntensity.WEAK -> click(intensity)
                HapticIntensity.MEDIUM -> heavyClick(intensity)
                HapticIntensity.STRONG -> doubleClickHeavy(intensity)
            }
            HapticType.CONFIRM -> when (intensity) {
                HapticIntensity.WEAK -> click(intensity)
                HapticIntensity.MEDIUM -> doubleClick()
                HapticIntensity.STRONG -> doubleClick()
            }
            HapticType.ERROR -> errorPattern()
            HapticType.SLIDER_TICK -> tick(intensity)
            HapticType.LONG_PRESS -> heavyClick(intensity)
        }
    }

    private fun tick(intensity: HapticIntensity): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            val dur = when (intensity) {
                HapticIntensity.WEAK -> 8L
                HapticIntensity.MEDIUM -> 12L
                HapticIntensity.STRONG -> 18L
            }
            VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private fun click(intensity: HapticIntensity): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            val dur = when (intensity) {
                HapticIntensity.WEAK -> 18L
                HapticIntensity.MEDIUM -> 25L
                HapticIntensity.STRONG -> 35L
            }
            VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private fun heavyClick(intensity: HapticIntensity): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            val dur = when (intensity) {
                HapticIntensity.WEAK -> 30L
                HapticIntensity.MEDIUM -> 45L
                HapticIntensity.STRONG -> 70L
            }
            VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private fun doubleClick(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 22, 60, 22), -1)
        }
    }

    private fun doubleClickHeavy(intensity: HapticIntensity): VibrationEffect {
        return VibrationEffect.createWaveform(longArrayOf(0, 35, 50, 50), -1)
    }

    private fun errorPattern(): VibrationEffect {
        return VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1)
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

/**
 * Compose helper: returns a (HapticType) -> Unit you can call inline.
 *   val haptic = rememberHaptic()
 *   Button(onClick = { haptic(HapticType.DESTRUCTIVE); doDestructive() }) { ... }
 */
@Composable
fun rememberHaptic(): (HapticType) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { type -> Haptic.perform(context, type) }
    }
}
