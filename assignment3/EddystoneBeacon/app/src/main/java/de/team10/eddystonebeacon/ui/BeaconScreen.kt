package de.team10.eddystonebeacon.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.team10.eddystonebeacon.model.BeaconData
import de.team10.eddystonebeacon.model.BeaconViewModel

@Composable
fun BeaconScreen(viewModel: BeaconViewModel) {
    val beaconData = viewModel.beaconData
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp).padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Scanning Eddystone Beacon", fontSize = 22.sp)
        Text("Beacon ID: ${beaconData.beaconId ?: "--"}")
        Text("URL: ${beaconData.url ?: "--"}")
        Text("Voltage: ${beaconData.voltage ?: "--"} mV")
        Text("Temperature: ${beaconData.temperature?.let { "%.2f".format(it) } ?: "--"} °C")
        Text("Distance: ${beaconData.distance?.let { "%.2f".format(it) } ?: "--"} m")
    }
}
