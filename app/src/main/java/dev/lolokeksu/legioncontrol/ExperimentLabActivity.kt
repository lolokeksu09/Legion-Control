package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView

/** Legacy callers use the hub; reports keep their own place in the Android task. */
class ExperimentLabActivity : FragmentActivity() {
    companion object {
        const val EXTRA_PACKAGE = "experiment_package"
        const val EXTRA_SESSION = "experiment_session"

        fun fromReport(context: Context, session: GameSessionHeader): Intent =
            Intent(context, ExperimentLabActivity::class.java)
                .putExtra(EXTRA_PACKAGE, session.packageName)
                .putExtra(EXTRA_SESSION, session.id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.hasExtra(EXTRA_SESSION)) {
            ModuleNavigation.forwardLegacyEntry(this)
            return
        }

        val host = FragmentContainerView(this).apply { id = R.id.report_experiment_container }
        LegionUiSupport.setModuleActivityContent(this, host, "ЭКСПЕРИМЕНТЫ A/B")
        if (savedInstanceState == null) {
            val fragment = ExperimentLabFragment().apply { arguments = intent.extras }
            supportFragmentManager.beginTransaction()
                .replace(host.id, fragment)
                .commitNow()
        }
    }
}
