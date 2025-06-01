package com.example.gpstracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.core.net.toUri
import java.util.Locale
import androidx.core.content.edit

class LocationTrackingService : Service() {
    companion object {
        private const val CHANNEL_ID = "LocationTrackingChannel"
        const val PREFERENCE = "tracking"
        const val RUNNING = "running"
        const val FILE_URI = "file_uri"
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    private val binder: IBinder = LocalBinder()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var outputUri: Uri

    private var lastWrite = 0
    private val locationBuffer = mutableListOf<Location>()

    private var distance: Double = 0.0
    private var totalTime: Long = 0
    private var numUpdates = 0

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()

        Log.i("Service", "Service started!")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Setup location callback to receive updates
        locationCallback = object : LocationCallback() {
            override fun onLocationAvailability(p0: LocationAvailability) {
                Log.i("Service", "Location updated!")
                super.onLocationAvailability(p0)
            }
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                Log.i("Service", "Location updated!")
                for (location in locationResult.locations) {

                    // Calculate distance and time
                    distance = if (locationBuffer.size > 0) {
                        location.distanceTo(locationBuffer[0]).toDouble()
                    } else {
                        0.0
                    }

                    numUpdates++
                    totalTime += 1000 // Placeholder for actual time difference logic


                    locationBuffer.add(location)

                    if (lastWrite < locationBuffer.size - 20) {
                        lastWrite = locationBuffer.size
                        writeGPXFile()
                    }

                    // Send the notification with current distance
                    sendNotification()
                }
            }
        }

        createNotificationChannel()
        startForeground(1, createNotification())
    }

    fun writeGPXFile() {
        Log.i("Writer", "Try writing GPXfile")

        if (locationBuffer.isEmpty()) return

        val gpxContent = buildGpx(locationBuffer)

        try {
            contentResolver.openOutputStream(outputUri, "wt")?.use { outputStream ->
                outputStream.write(gpxContent.toByteArray())
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Failed to write GPX", e)
        }
    }

    private fun buildGpx(locationBuffer: MutableList<Location>): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="YourApp" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine("<metadata>")
            appendLine("  <name>track.gpx</name>")
            appendLine("  <desc>Mobile Computing Assignment</desc>")
            appendLine("  <author>")
            appendLine("    <name>Team10</name>")
            appendLine("  </author>")
            appendLine("</metadata>")
            appendLine("<trk>")
            appendLine("  <name>Track</name>")
            appendLine("  <trkseg>")
            locationBuffer.forEach {
                appendLine("""    <trkpt lat="${it.latitude}" lon="${it.longitude}"><time>${it.time}</time></trkpt>""")
            }
            appendLine("  </trkseg>")
            appendLine("</trk>")
            appendLine("</gpx>")
        }

    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("Service", "Start command received!")
        // Create LocationRequest using the builder with default configuration
        val locationRequest: LocationRequest = LocationRequest.Builder(200)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            // .setMinUpdateDistanceMeters(10f)
            .build()

        getSharedPreferences(PREFERENCE, MODE_PRIVATE).edit() { putBoolean(RUNNING, true) }
        outputUri = intent?.getStringExtra(FILE_URI)!!.toUri()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("Service", "Stop command received!")
        writeGPXFile()

        getSharedPreferences(PREFERENCE, MODE_PRIVATE).edit() { putBoolean(RUNNING, false) }

        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "Location Tracking"
            val description = "Channel for location tracking"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val distanceText = buildString {
            append("Tracking your location. ")
            append("Current distance: ")
            append(String.format(Locale.GERMANY, "%.2f", distance))
            append(" meters")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setContentText(distanceText)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Use your app's icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun sendNotification() {
        val notification = createNotification()
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    val longitude: Double
        // RPC methods to expose data
        get() = locationBuffer.lastOrNull()?.longitude ?: 0.0

    val latitude: Double
        get() = locationBuffer.lastOrNull()?.latitude ?: 0.0

    val distanceTravelled: Double
        // RPC methods to expose data
        get() = distance

    val averageSpeed: Double
        get() = if (numUpdates > 0) distance / (totalTime / 1000) else 0.0

}