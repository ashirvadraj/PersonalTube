package com.personal.tube.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object FormatUtils {

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val remainingSeconds = seconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
        }
    }

    fun formatDurationMs(millis: Long): String {
        return formatDuration(millis / 1000)
    }

    fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1fB views", views / 1_000_000_000.0)
            views >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM views", views / 1_000_000.0)
            views >= 1_000 -> String.format(Locale.getDefault(), "%.1fK views", views / 1_000.0)
            views > 0 -> "$views views"
            else -> "0 views"
        }
    }

    fun formatSubscribers(subs: Long): String {
        return when {
            subs >= 1_000_000 -> String.format(Locale.getDefault(), "%.2fM subscribers", subs / 1_000_000.0)
            subs >= 1_000 -> String.format(Locale.getDefault(), "%.1fK subscribers", subs / 1_000.0)
            subs > 0 -> "$subs subscribers"
            else -> ""
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }

    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
    }
}
