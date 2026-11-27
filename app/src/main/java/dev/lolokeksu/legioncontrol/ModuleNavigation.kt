package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment

/**
 * Reuse canonical NavHost destinations when present. Existing explicit Activity
 * wrappers remain the fallback when a screen is opened by a legacy caller or Quick Tile.
 * This class does not trigger any backend operation, permission request or service.
 */
object ModuleNavigation {
    private val devicePages: Map<Class<*>, DiagnosticsPage> = mapOf(
        GamingDiagnosticsActivity::class.java to DiagnosticsPage.GAMING,
        HardwareLabActivity::class.java to DiagnosticsPage.HARDWARE,
        ConnectivityLabActivity::class.java to DiagnosticsPage.NETWORK,
        CompatibilityLabActivity::class.java to DiagnosticsPage.COMPAT,
        AudioLabActivity::class.java to DiagnosticsPage.AUDIO,
        HapticLabActivity::class.java to DiagnosticsPage.HAPTIC
    )

    internal fun devicePageFor(target: Class<*>): DiagnosticsPage? = devicePages[target]

    const val EXTRA_MODULE_ROUTE = "legion_module_route"
    const val EXTRA_MODULE_ARGS = "legion_module_args"

    /**
     * Only explicit, known fragment facades use this adapter. Standalone media,
     * diagnostics and display Activities remain separate by design.
     */
    fun forwardLegacyEntry(activity: android.app.Activity) {
        val page = devicePageFor(activity.javaClass)
        if (page != null) {
            activity.startActivity(deviceIntent(activity, page, activity.intent?.extras))
            activity.finish()
            return
        }
        val route = HubDestinationRoutes.destinationFor(activity.javaClass)
            ?: throw IllegalArgumentException("Unknown module entry: ${activity.javaClass.simpleName}")
        val intent = Intent(activity, LegionHubActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_MODULE_ROUTE, route)
            activity.intent?.extras?.let { putExtra(EXTRA_MODULE_ARGS, Bundle(it)) }
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun open(from:Fragment, target:Class<*>, args:Bundle?=null) {
        val page = devicePageFor(target)
        if (page != null) {
            val host = from.activity as? MainActivity
            if (host?.openPage(page) != true) {
                from.startActivity(deviceIntent(from.requireContext(), page, args))
            }
            return
        }
        val route=HubDestinationRoutes.destinationFor(target)
        val controller=runCatching { NavHostFragment.findNavController(from) }.getOrNull()
        if(route != null && controller != null) {
            controller.navigate(route,args)
        } else {
            from.startActivity(Intent(from.requireContext(),target).apply {
                if(args != null) putExtras(args)
            })
        }
    }

    internal fun deviceIntent(context: android.content.Context, page: DiagnosticsPage, args: Bundle? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            args?.let { putExtras(it) }
            putExtra(MainActivity.EXTRA_PAGE, page.name)
        }
}
