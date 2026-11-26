package dev.lolokeksu.legioncontrol

import android.os.Process
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * Shizuku UserService, not a manifest service. Only a fixed read-only report
 * can be requested; no caller-supplied command, package, path or setting key.
 */
class ShellAuditService : IShellAudit.Stub() {
    private val busy = AtomicBoolean(false)
    private val child = AtomicReference<java.lang.Process?>(null)
    private val closing = AtomicBoolean(false)

    private enum class Probe(val title: String, val args: List<String>) {
        FREEZER("Cached Apps Freezer · override", listOf("/system/bin/settings", "get", "global", "cached_apps_freezer")),
        FREEZER_DEFAULT("Cached Apps Freezer · DeviceConfig", listOf("/system/bin/cmd", "device_config", "get", "activity_manager_native_boot", "use_freezer")),
        LOW_POWER("Энергосбережение", listOf("/system/bin/settings", "get", "global", "low_power")),
        STANDBY("App Standby", listOf("/system/bin/settings", "get", "global", "app_standby_enabled")),
        ADAPTIVE("Адаптивная батарея", listOf("/system/bin/settings", "get", "global", "adaptive_battery_management_enabled"))
    }

    override fun inspect(): String {
        if (Process.myUid() != 2000) return "Доступен только режим ADB shell (UID 2000)."
        if (closing.get() || !busy.compareAndSet(false, true)) return "Диагностика уже выполняется или завершена."
        return try {
            buildString {
                append("ФОНОВЫЕ ПРОЦЕССЫ · ADB shell\n")
                for (probe in Probe.entries) {
                    if (closing.get()) break
                    append(probe.title).append(": ").append(read(probe)).append('\n')
                }
                append("\nЭто значения конфигурации Android, не состояние конкретного процесса. ")
                append("Пустое значение не означает отключение. ROM может применять другие правила. ")
                append("Никакие настройки не изменены.")
            }
        } finally { busy.set(false) }
    }

    private fun read(probe: Probe): String {
        var running: java.lang.Process? = null
        return try {
            if (closing.get()) return ShellAuditPolicy.value(null, false)
            val process = ProcessBuilder(probe.args).redirectErrorStream(true).start()
            running = process
            child.set(process)
            if (closing.get() || !process.waitFor(1500L, TimeUnit.MILLISECONDS) || process.exitValue() != 0)
                ShellAuditPolicy.value(null, false)
            else {
                val bytes = process.inputStream.use { it.readNBytes(257) }
                if (bytes.size > 256) ShellAuditPolicy.value(null, false)
                else ShellAuditPolicy.value(String(bytes, Charsets.UTF_8), true)
            }
        } catch (_: Exception) {
            ShellAuditPolicy.value(null, false)
        } finally {
            running?.let { runCatching { it.destroyForcibly() }; child.compareAndSet(it, null) }
        }
    }

    override fun destroy() {
        closing.set(true)
        child.getAndSet(null)?.let { runCatching { it.destroyForcibly() } }
        exitProcess(0)
    }
}
