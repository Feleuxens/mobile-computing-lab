package de.team10.eddystonebeacon.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat.getSystemService
import de.team10.eddystonebeacon.model.BeaconData
import de.team10.eddystonebeacon.model.BeaconViewModel
import kotlin.math.pow

class EddystoneScanner(
    private val context: Context,
    private val viewModel: BeaconViewModel
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val leScanCallback = BluetoothAdapter.LeScanCallback {
        device, rssi, scanRecord ->
        val oldData = viewModel.beaconData
        val beacon = parseScan(scanRecord, rssi, oldData)
        beacon?.let {
            Log.i("scanner", "update: " + beacon.beaconId)
            viewModel.updateData(it)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter!!.startLeScan(leScanCallback)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        bluetoothAdapter?.stopLeScan(leScanCallback)
    }

    private fun parseScan(scanRecord: ByteArray, rssi: Int, oldData: BeaconData): BeaconData? {

        if (containsEddystoneUUID(scanRecord)) {
            Log.i("scanner", bytesToHex(scanRecord, 0, scanRecord.size))
        } else {
            return null
        }

        var startIdx = 0

        if (scanRecord[startIdx] == 0x02.toByte() && scanRecord[startIdx + 1] == 0x01.toByte()) {
            // BLE Flags
            startIdx += 2 + 1
        }

        if (scanRecord[startIdx] == 0x03.toByte() && scanRecord[startIdx + 1] == 0x03.toByte()) {
            // Implemented Services
            if (scanRecord[startIdx+2] != 0xAA.toByte() || scanRecord[startIdx+3] != 0xFE.toByte()){
                // Not Eddystone
                return null
            }
            startIdx += 3 + 1
        }

        var length = 0
        var result: BeaconData? = null
        while (startIdx  < scanRecord.size) {
            length = scanRecord[startIdx].toInt()
            if (length == 0) { break}

            if (scanRecord[startIdx + 1] == 0x16.toByte()){
                // Service Data

                if (scanRecord[startIdx+2] == 0xAA.toByte() && scanRecord[startIdx+3] == 0xFE.toByte()){
                    // Eddystone Data

                    val frameType = scanRecord[startIdx+4].toInt()

                    if (frameType == 0x00) {
                        val power = scanRecord[startIdx+5].toInt()
                        val id = bytesToHex(scanRecord, startIdx + 6, 16)
                        val distance = estimateDistance(power, rssi)

                        if (result == null) {
                            result = oldData.copy()
                        }
                        val avgDistance = viewModel.updateDistance(distance)
                        result.beaconId = id
                        result.distance = avgDistance
                    }
                    else if (frameType == 0x10) {
                        val power = scanRecord[startIdx + 5].toInt()
                        val url = decodeUrl(scanRecord, startIdx+6)
                        val distance = estimateDistance(power, rssi)

                        if (result == null) {
                            result = oldData.copy()
                        }
                        val avgDistance = viewModel.updateDistance(distance)
                        result.url = url
                        result.distance = avgDistance
                    }
                    else if (frameType == 0x20) {
                        val version = scanRecord[startIdx+5].toInt()
                        if (version != 0) { break }

                        val voltage = readInt(scanRecord, startIdx+6)
                        val temperature = readFloat(scanRecord, startIdx + 8)

                        if (result == null) {
                            result = oldData.copy()
                        }
                        result.voltage = voltage
                        result.temperature = temperature

                    }
                }
            }
            startIdx += length + 1
        }

        return result
    }

    fun readInt(scanRecord: ByteArray, offset: Int): Int {
        val h = scanRecord[offset].toInt()
        val l = scanRecord[offset+1].toInt()

        // TODO: is this correct?
        val v = h*255 + l

        return v
    }

    fun readFloat(scanRecord: ByteArray, offset: Int): Float {
        val h = scanRecord[offset].toInt()
        val l = scanRecord[offset+1].toInt()

        // TODO: is this correct?
        val v = h*255 + l
        val fp = v.toFloat() / 256.0

        return fp.toFloat()
    }

    /*
     * Helper for checking if we received a Eddystone Packet at all
     */
    fun containsEddystoneUUID(scanRecord: ByteArray): Boolean {
        for (i in 0 until scanRecord.size - 1) {
            if (scanRecord[i] == 0xAA.toByte() && scanRecord[i + 1] == 0xFE.toByte()) {
                return true
            }
        }
        return false
    }

    private fun bytesToHex(bytes: ByteArray, start: Int, length: Int): String {
        return bytes.slice(start until (start + length)).joinToString("") {
            String.format("%02X", it)
        }
    }

    /*
     * This decodes the rest of the buffer into a url
     */
    private fun decodeUrl(data: ByteArray, offset: Int): String {
        val schemes = arrayOf("http://www.", "https://www.", "http://", "https://")
        val encodings = arrayOf(
            ".com/", ".org/", ".edu/", ".net/", ".info/", ".biz/", ".gov/",
            ".com", ".org", ".edu", ".net", ".info", ".biz", ".gov"
        )
        val prefix = data[offset].toInt()
        val sb = StringBuilder()
        sb.append(schemes.getOrNull(prefix) ?: "")
        for (i in (offset + 1) until data.size) {
            val b = data[i].toInt() and 0xFF
            if (b < encodings.size) {
                sb.append(encodings[b])
                break
            } else {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun estimateDistance(txPower: Int, rssi: Int): Double {
        val pathLossExponent = 3
        val distance = 10.0.pow((txPower - rssi) / (10.0 * pathLossExponent))

        Log.i("scanner", "estimated distance: $distance from txPower: $txPower rssi: $rssi")

        return distance
    }
}
