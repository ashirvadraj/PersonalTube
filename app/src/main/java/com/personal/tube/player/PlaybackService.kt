package com.personal.tube.player

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.personal.tube.PersonalTubeApp
import com.personal.tube.R
import com.personal.tube.data.model.VideoItem
import com.personal.tube.ui.MainActivity

class PlaybackService : Service(), ExoPlayerHolder.PlayerStateListener {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ExoPlayerHolder.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                ExoPlayerHolder.togglePlayPause(this)
            }
            ACTION_REWIND -> {
                ExoPlayerHolder.seekRelative(this, -10000)
            }
            ACTION_FORWARD -> {
                ExoPlayerHolder.seekRelative(this, 10000)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val video = ExoPlayerHolder.currentVideo ?: return
        val player = ExoPlayerHolder.getPlayer(this)
        val isPlaying = player.isPlaying

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val rewindPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_FORWARD },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, PersonalTubeApp.CHANNEL_PLAYBACK_ID)
            .setSmallIcon(R.drawable.ic_youtube_logo)
            .setContentTitle(video.title)
            .setContentText(video.channelTitle)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_replay_10, "Rewind", rewindPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_forward_10, "Forward", forwardPendingIntent)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        updateNotification()
    }

    override fun onPositionDiscontinuity(positionMs: Long, durationMs: Long) {
        // Position update
    }

    override fun onVideoChanged(video: VideoItem) {
        updateNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        ExoPlayerHolder.removeListener(this)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.personal.tube.action.PLAY_PAUSE"
        const val ACTION_REWIND = "com.personal.tube.action.REWIND"
        const val ACTION_FORWARD = "com.personal.tube.action.FORWARD"
        const val ACTION_STOP = "com.personal.tube.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
