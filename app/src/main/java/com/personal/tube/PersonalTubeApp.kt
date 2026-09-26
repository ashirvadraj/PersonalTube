package com.personal.tube

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.personal.tube.data.local.AppDatabase
import com.personal.tube.util.UserManager

class PersonalTubeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        UserManager.init(this)
        createNotificationChannels()
        // Pre-warm database
        AppDatabase.getInstance(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(playbackChannel)
        }
    }

    companion object {
        const val CHANNEL_PLAYBACK_ID = "personal_tube_playback_channel"
        lateinit var instance: PersonalTubeApp
            private set
    }
}
