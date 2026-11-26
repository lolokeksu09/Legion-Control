package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

internal enum class ShizukuConnection(val label: String) {
    NOT_INSTALLED("Shizuku не найден"),
    STOPPED("Shizuku не запущен"),
    UNSUPPORTED("Требуется Shizuku API 11+"),
    PERMISSION_REQUIRED("Требуется разрешение Shizuku"),
    SHELL_READY("Подключён · ADB shell (UID 2000)"),
    OTHER_IDENTITY("Подключён другой UID · расширенные действия недоступны"),
    UNKNOWN("Не удалось проверить Shizuku")
}

internal data class ShizukuAccessSnapshot(
    val connection: ShizukuConnection,
    val apiVersion: Int? = null,
    val uid: Int? = null
) {
    fun report(): String = buildString {
        append(connection.label)
        apiVersion?.let { append("\nAPI: ").append(it) }
        uid?.let { append(" · UID: ").append(it) }
        append("\nОбычные функции Legion Control работают без Shizuku.")
    }
}

/** Only the official SDK is used. No shell command executor, hidden API or root operations. */
internal object ShizukuAccessBackend {
    const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 19059

    private fun installed(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(MANAGER_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            true
        }.getOrDefault(false)

    fun snapshot(context: Context): ShizukuAccessSnapshot = try {
        when {
            !Shizuku.pingBinder() -> ShizukuAccessSnapshot(
                if (installed(context)) ShizukuConnection.STOPPED else ShizukuConnection.NOT_INSTALLED)
            Shizuku.isPreV11() -> ShizukuAccessSnapshot(ShizukuConnection.UNSUPPORTED)
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                ShizukuAccessSnapshot(ShizukuConnection.PERMISSION_REQUIRED, Shizuku.getVersion())
            else -> {
                val uid = Shizuku.getUid()
                ShizukuAccessSnapshot(
                    if (uid == 2000) ShizukuConnection.SHELL_READY else ShizukuConnection.OTHER_IDENTITY,
                    Shizuku.getVersion(), uid)
            }
        }
    } catch (_: Exception) {
        ShizukuAccessSnapshot(ShizukuConnection.UNKNOWN)
    }

    fun managerIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)

    /** Explicit click only. False means the manager must be opened or access is unavailable. */
    fun requestAccess(context: Context): Boolean {
        if (snapshot(context).connection != ShizukuConnection.PERMISSION_REQUIRED) return false
        if (Shizuku.shouldShowRequestPermissionRationale()) return false
        Shizuku.requestPermission(REQUEST_CODE)
        return true
    }

    /** Fixed read-only remote probes, invoked on a worker only after the user requests them. */
    fun inspectShell(context: Context): String {
        if (snapshot(context).connection != ShizukuConnection.SHELL_READY)
            return "Для проверки нужен запущенный Shizuku в режиме ADB и разрешение пользователя."
        val permissions = listOf(
            "Диагностика системы (DUMP)" to "android.permission.DUMP",
            "Статистика использования" to "android.permission.PACKAGE_USAGE_STATS",
            "Secure Settings" to "android.permission.WRITE_SECURE_SETTINGS"
        )
        return buildString {
            append("Права процесса ADB shell, не разрешения самого Legion Control:\n")
            permissions.forEach { (title, permission) ->
                val result = try {
                    if (snapshot(context).connection != ShizukuConnection.SHELL_READY) "связь потеряна"
                    else if (Shizuku.checkRemotePermission(permission) == PackageManager.PERMISSION_GRANTED)
                        "доступно" else "не предоставлено"
                } catch (_: Exception) { "не удалось определить" }
                append(title).append(": ").append(result).append('\n')
            }
            append("Проверка ничего не меняет. Наличие права не доказывает поддержку OEM-функции.")
        }
    }
}
