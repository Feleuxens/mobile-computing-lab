package de.team10.task2

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Timer
import java.util.TimerTask

class SensorReader : Service(), SensorEventListener {

    private var timer: Timer? = null;
    private lateinit var sensorManager: SensorManager;
    private lateinit var lightSensor: Sensor;

    private var value: Float = 0.0f;
    private var threshold: Long = 10000;
    private var interval: Long = 1000;

    override fun onSensorChanged(event: SensorEvent?) {
        this.value = event?.values?.get(0)!!;


        if (this.value.compareTo(threshold) >= 0) {
            Toast.makeText(applicationContext, value.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager;
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!;

        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

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
        TODO("Not yet implemented")
    }

    private fun startTimer() {
        timer?.cancel()
        this.timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                sendUpdate()
            }
        }, 0, this.interval);
    }

    private fun sendUpdate() {
        val intent = Intent("de.team10.task2.timer.UPDATE").apply {
            putExtra("value", value)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}