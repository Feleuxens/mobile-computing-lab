package de.team10.eddystonebeacon.model

data class BeaconData (
    var beaconId: String? = null,
    var url: String? = null,
    var voltage: Int? = null,
    var temperature: Float? = null,
    var distance: Double? = null
)