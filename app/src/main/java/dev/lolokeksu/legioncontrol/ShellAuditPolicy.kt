package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Interpret only known scalar values. Never expose raw command output. */
internal object ShellAuditPolicy {
    fun value(raw: String?, success: Boolean): String {
        if (!success || raw == null) return "н/д · чтение недоступно"
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "1", "true" -> "включено (значение конфигурации)"
            "0", "false" -> "выключено (значение конфигурации)"
            "null", "" -> "не задано · применяется системная политика"
            else -> "н/д · неизвестный формат"
        }
    }
}
