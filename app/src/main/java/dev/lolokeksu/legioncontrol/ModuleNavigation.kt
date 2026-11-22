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
    fun open(from:Fragment, target:Class<*>, args:Bundle?=null) {
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
}
