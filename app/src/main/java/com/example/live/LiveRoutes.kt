package com.example.live

object LiveRoutes {
    const val LIVE_FEED = "live_feed"
    const val LIVE_VIEWER = "live_viewer/{liveId}"
    const val LIVE_BROADCAST = "live_broadcast"
    const val LIVE_GUEST = "live_guest/{liveId}"

    fun createViewerRoute(liveId: String) = "live_viewer/$liveId"
    fun createGuestRoute(liveId: String) = "live_guest/$liveId"
}
