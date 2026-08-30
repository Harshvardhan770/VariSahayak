package com.varisahayak.core.utils

import android.content.Context
import com.varisahayak.R
import java.util.Locale

/**
 * Non-composable versions of formatters for use in notifications and background tasks.
 */
object DateTimeUtils {

    fun formatRelativeTime(
        context: Context,
        epochMillis: Long,
        nowMillis: Long,
    ): String {
        val elapsed = (nowMillis - epochMillis).coerceAtLeast(0L)

        val minutes = elapsed / 60_000L
        val hours = elapsed / 3_600_000L
        val days = elapsed / 86_400_000L

        return when {
            minutes < 1 -> context.getString(R.string.time_just_now)
            minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes.toInt())
            hours < 24 -> context.getString(R.string.time_hours_ago, hours.toInt())
            days <= 7 -> context.getString(R.string.time_days_ago, days.toInt())
            else -> {
                java.text.DateFormat
                    .getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault())
                    .format(java.util.Date(epochMillis))
            }
        }
    }
}
