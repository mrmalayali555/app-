package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AlertEntity
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlertMonitorService : Service(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var database: AppDatabase

    private var eventSource: EventSource? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        const val CHANNEL_ID = "SlotMonitorServiceChannel"
        const val ALERT_CHANNEL_ID = "SlotMonitorAlertChannel"
        const val ACTION_STOP_ALARM = "com.example.ACTION_STOP_ALARM"
        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        database = AppDatabase.getDatabase(this)
        createNotificationChannels()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarmAndTts()
            }
            ACTION_STOP -> {
                stopSelf()
            }
            else -> {
                startForeground(1, createForegroundNotification("Starting monitor..."))
                serviceScope.launch {
                    settingsRepo.setMonitoring(true)
                    startListening()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startListening() {
        eventSource?.cancel()

        val topic = settingsRepo.topicNameFlow.first()
        if (topic.isBlank()) {
            updateNotification("No topic configured")
            return
        }

        updateNotification("Listening to ntfy.sh/$topic")
        
        val request = Request.Builder()
            .url("https://ntfy.sh/$topic/sse")
            .build()

        val sseFactory = EventSources.createFactory(client)
        eventSource = sseFactory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type == "message") {
                    handleMessage(data)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e("AlertMonitor", "SSE connection failed. Retrying in 10s...", t)
                // Auto reconnect after delay
                serviceScope.launch {
                    kotlinx.coroutines.delay(10000)
                    if (settingsRepo.isMonitoringFlow.first()) {
                        startListening()
                    }
                }
            }
        })
    }

    private fun handleMessage(data: String) {
        try {
            val json = JSONObject(data)
            val message = json.optString("message", "Slot Available!")
            val title = json.optString("title", "Slot Alert")

            serviceScope.launch {
                database.alertDao().insertAlert(AlertEntity(title = title, message = message, timestamp = System.currentTimeMillis()))
                triggerAlarm(title, message)
            }
        } catch (e: Exception) {
            Log.e("AlertMonitor", "Error parsing message", e)
        }
    }

    private suspend fun triggerAlarm(title: String, message: String) {
        val ringtoneUriStr = settingsRepo.ringtoneUriFlow.first()
        val ttsEnabled = settingsRepo.ttsEnabledFlow.first()

        val uri = if (ringtoneUriStr != null) Uri.parse(ringtoneUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        // Stop existing
        stopAlarmAndTts()

        // Play sound
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlertMonitorService, uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlertMonitor", "Error playing ringtone", e)
        }

        // Play TTS
        if (ttsEnabled && isTtsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "alert_tts")
        }

        // Show high priority notification
        val stopIntent = Intent(this, AlertMonitorService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("🚨 $title")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Alarm", stopPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, notification)
    }

    private fun stopAlarmAndTts() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        
        tts?.let {
            if (it.isSpeaking) {
                it.stop()
            }
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(2)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            settingsRepo.setMonitoring(false)
        }
        eventSource?.cancel()
        stopAlarmAndTts()
        tts?.shutdown()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Slot Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Slot Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority slot alerts"
                setBypassDnd(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(status: String): Notification {
        val stopIntent = Intent(this, AlertMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(this, 4, openIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Slot Monitor Running")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setContentIntent(openPendingIntent)
            .build()
    }
    
    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createForegroundNotification(status))
    }
}
