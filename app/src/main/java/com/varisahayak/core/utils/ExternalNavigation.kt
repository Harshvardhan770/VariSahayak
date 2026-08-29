package com.varisahayak.core.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.varisahayak.domain.model.GeoPoint

/**
 * Hands off to whatever maps application the device has.
 *
 * Deliberately a handoff, not embedded turn-by-turn: routing is out of MVP scope, and
 * volunteers already know and trust their own maps app.
 */
object ExternalNavigation {

    /**
     * Opens turn-by-turn navigation to [destination].
     *
     * Tries Google Maps' navigation intent first, then falls back to a generic `geo:` URI
     * that any maps app can handle. Returns false when nothing on the device can service
     * either — the caller shows a message rather than failing silently.
     */
    fun navigateTo(context: Context, destination: GeoPoint, label: String? = null): Boolean {
        val lat = destination.latitude
        val lon = destination.longitude

        val googleMaps = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$lat,$lon&mode=w"),
        ).apply { setPackage("com.google.android.apps.maps") }

        if (context.tryStart(googleMaps)) return true

        // Generic geo: URI — handled by any maps app, Google Maps included.
        val encodedLabel = label?.let { Uri.encode(it) }
        val geoUri = if (encodedLabel != null) {
            "geo:$lat,$lon?q=$lat,$lon($encodedLabel)"
        } else {
            "geo:$lat,$lon?q=$lat,$lon"
        }

        return context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)))
    }

    private fun Context.tryStart(intent: Intent): Boolean = try {
        startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    }
}
