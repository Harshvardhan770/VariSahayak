package com.varisahayak.core.permissions

import android.Manifest
import android.os.Build

/**
 * The permission sets this app requests, in one place so no screen invents its own.
 */
object AppPermissions {

    /**
     * Both are requested together. Android shows a single dialog with a precise/approximate
     * choice, and granting only coarse is a supported outcome — see
     * [com.varisahayak.core.location.LocationPermissionStatus].
     */
    val LOCATION = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val CAMERA = listOf(Manifest.permission.CAMERA)

    /**
     * Empty below Android 13, where notifications need no runtime grant. Requesting a
     * permission that does not exist on the platform returns an immediate denial and
     * would make the UI think the user refused.
     */
    val NOTIFICATIONS: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
}
