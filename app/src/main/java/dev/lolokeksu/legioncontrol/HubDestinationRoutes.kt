package dev.lolokeksu.legioncontrol

/** One official AndroidX Navigation route per preserved Activity screen. */
object HubDestinationRoutes {
    private val destinations: Map<Class<*>, Int> = mapOf(
        MainActivity::class.java to R.id.diagnosticsDestination,
        GameToolboxActivity::class.java to R.id.gameToolboxDestination,
        CaptureStudioActivity::class.java to R.id.captureStudioDestination,
        SmartProfilesActivity::class.java to R.id.smartProfilesDestination,
        ApplicationCenterActivity::class.java to R.id.applicationCenterDestination,
        ExperimentLabActivity::class.java to R.id.experimentLabDestination,
        ConnectivityLabActivity::class.java to R.id.connectivityLabDestination,
        AudioLabActivity::class.java to R.id.audioLabDestination,
        HapticLabActivity::class.java to R.id.hapticLabDestination,
        GamingDiagnosticsActivity::class.java to R.id.gamingDiagnosticsDestination,
        HardwareLabActivity::class.java to R.id.hardwareLabDestination,
        CompatibilityLabActivity::class.java to R.id.compatibilityLabDestination,
        GamepadLiveActivity::class.java to R.id.gamepadLiveDestination,
        DisplayPatternActivity::class.java to R.id.displayPatternDestination
    )

    fun destinationFor(activity: Class<*>): Int? = destinations[activity]
}
