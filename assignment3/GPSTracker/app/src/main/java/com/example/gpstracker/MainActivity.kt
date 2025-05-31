package com.example.gpstracker

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gpstracker.ui.theme.GPSTrackerTheme


class MainActivity : ComponentActivity() {

    private val handler: Handler = Handler(Looper.getMainLooper())

    private var locationTrackingService:LocationTrackingService? by mutableStateOf(null)
    private var isBound by mutableStateOf(false)
    private var permissions by mutableStateOf(false)

    // State variables for UI updates
    private var latitude by mutableStateOf(0.0)
    private var longitude by mutableStateOf(0.0)
    private var distance by mutableStateOf(0.0)
    private var speed by mutableStateOf(0.0)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) {
            permissions = true
        } else {
            permissions = false
            Toast.makeText(this, "GPS permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i("MainActivity", "Connected to service!")
            val binder = service as? LocationTrackingService.LocalBinder
            if (binder == null) {
                Log.i("MainActivity", "Failed binder")
            }
            locationTrackingService = binder?.getService()
            isBound = true

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("MainActivity", "ServiceDisconnected")
            locationTrackingService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GPSTrackerTheme {
                // Call the composable function to display the UI
                LocationTrackingUI(
                    latitude = latitude,
                    longitude = longitude,
                    distance = distance,
                    speed = speed,
                    isBound = isBound,
                    onStartStopClick = { onStartStopClick() }
                )
            }
        }

        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        handler.post(object : Runnable {
            override fun run() {
                updateData()
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    // This method is triggered when the "Start Tracking" button is clicked
    private fun onStartStopClick() {
        if (isBound) {
            stopLocationTracking()
        } else {
            startLocationTracking()
        }
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    // Start the tracking by binding to the service and starting it
    private fun startLocationTracking() {
        if (locationTrackingService == null) {
            val intent = Intent(this, LocationTrackingService::class.java)
            if (!isMyServiceRunning(LocationTrackingService::class.java)) {
                ContextCompat.startForegroundService(application, intent)
            }
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Toast.makeText(this, "Started Location Tracking", Toast.LENGTH_SHORT).show()
        }
    }

    // Stop the location tracking and unbind the service
    private fun stopLocationTracking() {
        if (locationTrackingService != null) {
            val intent = Intent(this, LocationTrackingService::class.java)
            unbindService(serviceConnection)
            stopService(intent)
            locationTrackingService = null
            isBound = false
            Toast.makeText(this, "Stopped Location Tracking", Toast.LENGTH_SHORT).show()
        }
    }

    // Update the UI with data from the service
    private fun updateData() {
        locationTrackingService?.let {
            var updated = false
            if (latitude != it.latitude) {
                latitude = it.latitude
                updated = true
            }
            if (longitude != it.longitude) {
                longitude = it.longitude
                updated = true
            }
            if (distance != it.distanceTravelled) {
                distance = it.distanceTravelled
                updated = true
            }
            if (speed != it.averageSpeed) {
                speed = it.averageSpeed
                updated = false
            }

            if (updated) {
                Log.i("UI", "Update now visible")

            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTrackingUI(
    latitude: Double,
    longitude: Double,
    distance: Double,
    speed: Double,
    isBound: Boolean,
    onStartStopClick: () -> Unit
) {
    // Material Theme and UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Latitude
        Text("Latitude: $latitude", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Longitude
        Text("Longitude: $longitude", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Distance
        Text("Distance: $distance meters", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Speed
        Text("Speed: $speed m/s", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Start/Stop Button
        Button(
            onClick = onStartStopClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (!isBound) "Start Tracking" else "Stop Tracking")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GPSTrackerTheme {
        LocationTrackingUI(
            latitude = 0.0,
            longitude = 0.0,
            distance = 0.0,
            speed = 0.0,
            isBound = false,
            onStartStopClick = {}
        )
    }
}
