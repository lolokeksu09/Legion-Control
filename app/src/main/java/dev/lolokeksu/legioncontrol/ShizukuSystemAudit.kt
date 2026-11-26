package dev.lolokeksu.legioncontrol

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

/** One explicit, bounded request. All UI/session state is owned by the main looper. */
internal class ShizukuSystemAudit(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var active: Request? = null

    private inner class Request(val callback: (String) -> Unit) : ServiceConnection {
        val args = Shizuku.UserServiceArgs(ComponentName(app, ShellAuditService::class.java))
            .daemon(false).tag("legion-shell-audit").version(1).processNameSuffix("shell_audit")
        val timeout = Runnable { finish(this, "Диагностика прервана: Shizuku не ответил вовремя.") }
        var started = false

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            handler.post {
                if (active !== this || started) return@post
                started = true
                Thread({
                    val result = try {
                        if (ShizukuAccessBackend.snapshot(app).connection != ShizukuConnection.SHELL_READY)
                            "Связь или разрешение Shizuku потеряно."
                        else IShellAudit.Stub.asInterface(binder).inspect()
                            ?: "Shizuku вернул пустой отчёт."
                    } catch (_: Exception) { "Не удалось получить системный отчёт через Shizuku." }
                    handler.post { finish(this, result) }
                }, "legion-shell-audit").apply { isDaemon = true }.start()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handler.post { finish(this, "Связь с Shizuku прервана.") }
        }
    }

    fun run(callback: (String) -> Unit) {
        if (active != null) return
        val access = ShizukuAccessBackend.snapshot(app)
        if (access.connection != ShizukuConnection.SHELL_READY || (access.apiVersion ?: 0) < 12) {
            callback("Нужен запущенный Shizuku API 12+ в режиме ADB и разрешение пользователя.")
            return
        }
        val request = Request(callback)
        active = request
        handler.postDelayed(request.timeout, 20_000L)
        try { Shizuku.bindUserService(request.args, request) }
        catch (_: Exception) { finish(request, "Не удалось запустить диагностику Shizuku.") }
    }

    private fun finish(request: Request, result: String?) {
        if (active !== request) return
        active = null
        handler.removeCallbacks(request.timeout)
        runCatching { Shizuku.unbindUserService(request.args, request, true) }
        runCatching { Shizuku.unbindUserService(request.args, request, false) }
        result?.let(request.callback)
    }

    fun cancel() { active?.let { finish(it, null) } }
}
