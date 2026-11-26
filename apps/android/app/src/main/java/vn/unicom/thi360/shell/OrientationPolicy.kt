package vn.unicom.thi360.shell

import android.app.Activity
import android.content.pm.ActivityInfo

enum class OrientationMode(val requestedActivityOrientation: Int) {
    PORTRAIT_LOCKED(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    UNRESTRICTED(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
}

object OrientationPolicy {
    fun resolve(smallestScreenWidthDp: Int): OrientationMode =
        if (smallestScreenWidthDp < COMPACT_WIDTH_BREAKPOINT_DP) {
            OrientationMode.PORTRAIT_LOCKED
        } else {
            OrientationMode.UNRESTRICTED
        }

    fun applyTo(activity: Activity) {
        val mode = resolve(activity.resources.configuration.smallestScreenWidthDp)
        if (activity.requestedOrientation != mode.requestedActivityOrientation) {
            activity.requestedOrientation = mode.requestedActivityOrientation
        }
    }
}
