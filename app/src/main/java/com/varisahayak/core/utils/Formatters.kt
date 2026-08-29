package com.varisahayak.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.varisahayak.R
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Human-readable distance and elapsed time.
 *
 * Both go through string resources rather than string concatenation, because "120 m away"
 * has a different word order in Marathi and Hindi and a hardcoded `"$value m away"` cannot
 * express that.
 */

/**
 * A wall clock that advances while the screen is open.
 *
 * Relative timestamps rendered against a value captured once at composition freeze at
 * "Just now" and stay there — on a screen a coordinator leaves open for an hour, that is a
 * lie about how stale the queue is. Ticking every 30s costs one recomposition a minute and
 * keeps the minute-granularity labels honest.
 *
 * The effect is bound to composition, so it stops when the screen leaves.
 */
@Composable
fun rememberNowMillis(tickMillis: Long = 30_000L): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMillis) {
        while (true) {
            delay(tickMillis)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Distance to an incident.
 *
 * Precision drops as distance grows on purpose. Under a kilometre, tens of metres is what
 * tells a responder whether to walk or ride. Over it, the extra digits are noise the GPS
 * fix cannot support anyway.
 */
@Composable
fun formatDistance(metres: Double): String = when {
    metres < 10 -> stringResource(R.string.distance_here)

    metres < 1_000 -> {
        // Round to 10 m: a fix accurate to ±15 m does not earn a single-metre readout.
        val rounded = (metres / 10.0).roundToInt() * 10
        stringResource(R.string.distance_metres, rounded)
    }

    metres < 10_000 -> stringResource(
        R.string.distance_kilometres,
        // The configuration's locale, not the process default: switching the app to
        // Marathi must change the decimal separator on the next frame, and
        // Locale.getDefault() is not read as a composition input so it would not.
        String.format(
            // ConfigurationCompat, not Configuration.getLocales(): the latter is API 24
            // and this app ships to API 23.
            // ROOT as the fallback, not getDefault(): the default is the process locale,
            // which is exactly the non-observable read this line exists to avoid. An empty
            // locale list is not a real state, so the fallback only has to be harmless.
            ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT,
            "%.1f",
            metres / 1_000.0,
        ),
    )

    else -> stringResource(
        R.string.distance_kilometres,
        (metres / 1_000.0).roundToInt().toString(),
    )
}

/**
 * How long ago something was reported.
 *
 * Caps at "7 d" and then falls back to an absolute date, because past a week "312 d ago"
 * stops being a time and becomes a puzzle.
 */
@Composable
fun formatRelativeTime(
    epochMillis: Long,
    nowMillis: Long,
): String {
    val elapsed = (nowMillis - epochMillis).coerceAtLeast(0L)

    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L

    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.time_hours_ago, hours.toInt())
        days <= 7 -> stringResource(R.string.time_days_ago, days.toInt())
        else -> {
            val formatted = remember(epochMillis) {
                java.text.DateFormat
                    .getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault())
                    .format(java.util.Date(epochMillis))
            }
            formatted
        }
    }
}
