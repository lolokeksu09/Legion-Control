package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
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

    private const val TAG_PREFIX = "legion-feature:"

    fun bind(view: View, featureId: String) {
        view.tag = TAG_PREFIX + featureId
    }

    private fun matching(root: View, predicate: (View) -> Boolean): List<View> {
        if (root.visibility != View.VISIBLE) return emptyList()
        return buildList {
            if(predicate(root)) add(root)
            if(root is ViewGroup) for(index in 0 until root.childCount)
                addAll(matching(root.getChildAt(index), predicate))
        }
    }

    internal fun findTarget(root: View, feature: FeatureEntry): View? {
        val bound = matching(root) { it.tag == TAG_PREFIX + feature.id }
        // Missing or duplicate identity is unavailable; never infer a target from its label.
        return bound.singleOrNull()?.let { targetOf(it, root) }
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
            val target = findTarget(root, feature)
            if (target == null || target.width == 0 || target.height == 0) {
                Toast.makeText(root.context, "Действие сейчас недоступно", Toast.LENGTH_SHORT).show()
                return@Runnable
            }
            // requestRectangleOnScreen scrolls the ancestor ScrollView, not the button.
            target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
            if(!target.isEnabled) {
                val message = "${feature.title}: действие сейчас недоступно"
                target.announceForAccessibility(message)
                Toast.makeText(root.context, message, Toast.LENGTH_SHORT).show()
            } else target.announceForAccessibility(feature.title)
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
