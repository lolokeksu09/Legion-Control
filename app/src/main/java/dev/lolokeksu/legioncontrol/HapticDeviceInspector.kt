package dev.lolokeksu.legioncontrol

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/** Public Android capability report, not a measurement of physical motor output. */
data class HapticCapabilitySnapshot(
    val recordedAtMs:Long,
    val vibratorCount:Int,
    val hasVibrator:Boolean,
    val amplitudeControl:Boolean?,
    val systemHapticsEnabled:Boolean?,
    val effects:String,
    val primitives:String
) {
    fun report():String=buildString {
        append("HAPTIC CAPABILITIES · Android VibratorManager\n")
        append("Обнаружено вибраторов: ").append(vibratorCount).append("\n")
        append("Default.hasVibrator: ").append(hasVibrator).append("\n")
        append("Регулировка амплитуды: ").append(amplitudeControl?.let { if(it)"да" else "нет" } ?: "н/д")
            .append("\n")
        append("Системный HAPTIC_FEEDBACK_ENABLED: ")
            .append(systemHapticsEnabled?.let { if(it)"вкл" else "выкл" } ?: "неизвестно")
            .append("\n")
        append("Предопределённые эффекты: ").append(effects.ifBlank { "нет данных" }).append("\n")
        append("Примитивы композиции: ").append(primitives.ifBlank { "нет данных" }).append("\n")
        append(HapticPattern.caveat).append("\n")
        append("Системные настройки и DND/OEM могут подавлять вибрацию. ")
        append("Возможность API не подтверждает физическую отдачу.")
    }
}

/** Must run off the main thread; snapshot is only stored on explicit user request. */
object HapticDeviceInspector {
    fun capture(context:Context):HapticCapabilitySnapshot {
        val manager=context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val ids=runCatching { manager.vibratorIds }.getOrDefault(intArrayOf())
        val vibrator=manager.defaultVibrator
        val has=runCatching { vibrator.hasVibrator() }.getOrDefault(false)
        val amplitude=if(has) runCatching { vibrator.hasAmplitudeControl() }.getOrNull() else null
        val setting=runCatching {
            Settings.System.getInt(context.contentResolver,Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0
        }.getOrNull()
        val fx=linkedMapOf(
            "CLICK" to VibrationEffect.EFFECT_CLICK,
            "DOUBLE_CLICK" to VibrationEffect.EFFECT_DOUBLE_CLICK,
            "TICK" to VibrationEffect.EFFECT_TICK,
            "HEAVY_CLICK" to VibrationEffect.EFFECT_HEAVY_CLICK
        )
        val effects=runCatching {
            val values=vibrator.areEffectsSupported(*fx.values.toIntArray())
            fx.keys.zip(values.toList()).joinToString(" · ") { (name,code) ->
                name+"="+when(code) {
                    Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> "YES"
                    Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> "NO"
                    else -> "UNKNOWN"
                }
            }
        }.getOrDefault("неизвестно")
        val primitives=runCatching {
            val names=listOf("CLICK","TICK")
            val supports=vibrator.arePrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )
            names.zip(supports.toList())
                .joinToString(" · ") { (name,supported) ->
                    name+"="+(if(supported)"YES" else "NO")
                }
        }.getOrDefault("неизвестно")
        return HapticCapabilitySnapshot(System.currentTimeMillis().coerceAtLeast(1),
            ids.size.coerceIn(0,32),has,amplitude,setting,effects.take(240),primitives.take(240))
    }
}
