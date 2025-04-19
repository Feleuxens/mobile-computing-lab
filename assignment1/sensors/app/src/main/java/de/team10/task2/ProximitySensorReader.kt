package de.team10.task2

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Timer
import java.util.TimerTask

class ProximitySensorReader : Service(), SensorEventListener {

    private var timer: Timer? = null;
    private lateinit var sensorManager: SensorManager;
    private lateinit var proximitySensor: Sensor;

    private var value: Float = 0.0f;
    private var threshold: Long = 10000;
    private var interval: Long = 1000;
    private var lastUpdateWasThreshold = false

    private lateinit var broadcastManager: LocalBroadcastManager;

    override fun onSensorChanged(event: SensorEvent?) {
        val v = event?.values?.get(0)
        if (v != null) {
            this.value = v
        };
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        broadcastManager = LocalBroadcastManager.getInstance(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager;
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)!!;

        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);

    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        val channelId = "ProximityServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Proximity Sensor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ProximitySensorService")
            .setContentText("Monitoring proximity sensor")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(2, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TIMER" -> {
                this.interval = intent.getLongExtra("interval", 1000)
                this.threshold = intent.getLongExtra("threshold", 10000)
                startTimer()
            }
            "UPDATE_PARAMS" -> {
                this.interval = intent.getLongExtra("interval", this.interval)
                this.threshold = intent.getLongExtra("threshold", this.threshold)
                startTimer()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startTimer() {
        timer?.cancel()
        this.timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                sendUpdate()
                checkThreshold()
            }
        }, 0, this.interval);
    }

    private fun checkThreshold() {
        if (value.compareTo(threshold) <= 0) {
            if (lastUpdateWasThreshold) {
                return
            }

            val intent = Intent("de.team10.task2.timer.proximity.THRESHOLD").apply {
                putExtra("value", value)
            }
            broadcastManager.sendBroadcast(intent)
            lastUpdateWasThreshold = true
        } else {
            lastUpdateWasThreshold = false
        }
    }

    private fun sendUpdate() {
        val intent = Intent("de.team10.task2.timer.proximity.UPDATE").apply {
            putExtra("value", value)
        }
        broadcastManager.sendBroadcast(intent)
    }

}





