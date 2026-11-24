package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

/**
 * A validated feature ID reveals the matching visible control after layout.
 * No permission, service, delete or click is triggered by a search result.
 */
object FeatureReveal {
    fun fromFragment(fragment: Fragment, root: View, module: FeatureModule) {
        val args = fragment.arguments ?: return
        val id = args.getString(FeatureFinder.EXTRA_FEATURE_ID) ?: return
        args.remove(FeatureFinder.EXTRA_FEATURE_ID)
        reveal(root, id, module)
    }

    fun fromActivity(activity: Activity, root: View, module: FeatureModule) {
        val id = activity.intent.getStringExtra(FeatureFinder.EXTRA_FEATURE_ID) ?: return
        activity.intent.removeExtra(FeatureFinder.EXTRA_FEATURE_ID)
        reveal(root, id, module)
    }

    private fun findLabel(root: View, anchor: String): View? {
        if (root.visibility != View.VISIBLE) return null
        if (root is TextView && root.text.toString() == anchor) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findLabel(root.getChildAt(index), anchor)?.let { return it }
            }
        }
        return null
    }

    private fun targetOf(label: View, root: View): View? {
        var current: View? = label
        while (current != null) {
            if (current.isClickable) return current
            if (current === root) break
            current = current.parent as? View
        }
        return null
    }

    private fun reveal(root: View, id: String, module: FeatureModule) {
        val feature = FeatureFinder.resolve(id, module) ?: return
        val action = Runnable {
            if (!root.isAttachedToWindow) return@Runnable
            val target = findLabel(root, feature.anchor)?.let { targetOf(it, root) }
            if (target == null || target.width == 0 || target.height == 0) {
                Toast.makeText(root.context, "Действие сейчас недоступно", Toast.LENGTH_SHORT).show()
                return@Runnable
            }
            // requestRectangleOnScreen scrolls the ancestor ScrollView, not the button.
            target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
            target.announceForAccessibility(feature.title)
        }
        if (root.isLaidOut && !root.isLayoutRequested) root.post(action)
        else root.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
                if (r <= l || b <= t) return
                v.removeOnLayoutChangeListener(this)
                v.post(action)
            }
        })
    }
}
