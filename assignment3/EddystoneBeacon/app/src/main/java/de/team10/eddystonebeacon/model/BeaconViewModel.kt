package de.team10.eddystonebeacon.model

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class BeaconViewModel : ViewModel() {
    var beaconData by mutableStateOf(BeaconData())
        private set

    private val lastDistances = ArrayDeque<Double>()
    private val maxSamples = 4


    fun updateData(data: BeaconData) {
        beaconData = data
    }

    fun updateDistance(newDistance: Double): Double {
        if (lastDistances.size >= maxSamples) {
            lastDistances.removeFirst()
        }
        lastDistances.addLast(newDistance)

        return lastDistances.average()
    }

}
