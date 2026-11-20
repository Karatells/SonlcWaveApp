package com.sonicwave.sonic_wave.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sonicwave.sonic_wave.MainActivity
import com.sonicwave.sonic_wave.R
import com.sonicwave.sonic_wave.SonicWaveApplication

class SonicWaveWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as? SonicWaveApplication ?: return
        val controller = app.container.playbackController
        when (intent.action) {
            ACTION_PREV -> controller.skipToPrevious()
            ACTION_PLAY_PAUSE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.skipToNext()
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, "SonicWave") ?: "SonicWave"
        val subtitle = prefs.getString(KEY_SUBTITLE, "SonicWave Studio") ?: "SonicWave Studio"
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context, title, subtitle, isPlaying))
        }
    }

    companion object {
        private const val PREFS_NAME = "sonicwave_widget_prefs"
        private const val KEY_TITLE = "track_title"
        private const val KEY_SUBTITLE = "track_subtitle"
        private const val KEY_IS_PLAYING = "is_playing"

        private const val ACTION_PREV = "com.sonicwave.sonic_wave.widget.PREV"
        private const val ACTION_PLAY_PAUSE = "com.sonicwave.sonic_wave.widget.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.sonicwave.sonic_wave.widget.NEXT"

        private fun buildActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, SonicWaveWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildRemoteViews(
            context: Context,
            title: String,
            subtitle: String,
            isPlaying: Boolean
        ): RemoteViews {
            val openAppIntent = PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return RemoteViews(context.packageName, R.layout.sonic_wave_widget).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_subtitle, subtitle)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
                setOnClickPendingIntent(R.id.widget_root, openAppIntent)
                setOnClickPendingIntent(R.id.widget_prev, buildActionPendingIntent(context, ACTION_PREV, 1))
                setOnClickPendingIntent(R.id.widget_play_pause, buildActionPendingIntent(context, ACTION_PLAY_PAUSE, 2))
                setOnClickPendingIntent(R.id.widget_next, buildActionPendingIntent(context, ACTION_NEXT, 3))
            }
        }

        fun updateWidget(context: Context, title: String, artist: String, isPlaying: Boolean) {
            val subtitle = if (artist.isBlank()) "SonicWave Studio" else artist
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_SUBTITLE, subtitle)
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SonicWaveWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                manager.updateAppWidget(ids, buildRemoteViews(context, title, subtitle, isPlaying))
            }
        }
    }
}
