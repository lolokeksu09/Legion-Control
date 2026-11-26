package dev.lolokeksu.legioncontrol

import android.content.Context
import android.os.Build

/** Capability report never confuses API existence with actual OEM functionality. */
object SmartCompatibilityBackend {
    fun inspect(context: Context): String {
        val access = CapabilityAccessBackend.read(context)
        return buildString {
            append("SMART PROFILES · ВОЗМОЖНОСТИ\n")
            append("Android API: ").append(Build.VERSION.SDK_INT).append('\n')
            append(access.report()).append('\n')
            append("DND: состояние доступа, не состояние глобального режима.\n")
            append("Уведомления: состояние приложения, не каждого канала.\n")
            append("Quick Settings: плитку добавляет пользователь.\n")
            append("Запись в другой игре: только после явного запуска FGS; ")
            append("работа на конкретной прошивке НЕ ПРОВЕРЕНА\n")
            append("Root, Shizuku, геолокация: не требуются")
        }
    }
}
