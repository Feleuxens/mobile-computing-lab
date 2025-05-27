package de.team10.eddystonebeacon

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import de.team10.eddystonebeacon.ble.EddystoneScanner
import de.team10.eddystonebeacon.model.BeaconViewModel
import de.team10.eddystonebeacon.ui.BeaconScreen
import de.team10.eddystonebeacon.ui.PermissionRequest

class MainActivity : ComponentActivity() {
    private lateinit var scanner: EddystoneScanner
    private val viewModel: BeaconViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askPermissions()

        scanner = EddystoneScanner(this, viewModel)

        setContent {
            MaterialTheme {
                PermissionRequest {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        scanner.startScanning()
                    }
                }

                BeaconScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        scanner.startScanning()
    }

    override fun onPause() {
        super.onPause()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        scanner.stopScanning()
    }

    private fun askPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        permissions += Manifest.permission.ACCESS_FINE_LOCATION

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }
}