package dev.lolokeksu.legioncontrol

/** One native vector vocabulary shared by the dashboard and both navigation shells. */
internal object HubCenterIcons {
    fun center(center:HubCenter):Int = when(center) {
        HubCenter.GAMES -> R.drawable.ic_hub_games
        HubCenter.DEVICE -> R.drawable.ic_hub_device
        HubCenter.MEDIA -> R.drawable.ic_hub_media
        HubCenter.PERIPHERALS -> R.drawable.ic_hub_peripherals
        HubCenter.ANALYTICS -> R.drawable.ic_hub_analytics
    }

    fun tabs():IntArray = intArrayOf(R.drawable.ic_hub_home,R.drawable.ic_hub_games,
        R.drawable.ic_hub_device,R.drawable.ic_hub_analytics,R.drawable.ic_hub_settings)
}
