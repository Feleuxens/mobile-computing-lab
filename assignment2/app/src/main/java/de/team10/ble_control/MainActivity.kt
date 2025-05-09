package de.team10.ble_control

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import java.util.UUID

private val LIGHT_SERVICE_UUID = UUID.fromString("00000001-0000-0000-FDFD-FDFDFDFDFDFD")
private val INTENSITY_CHAR_UUID = UUID.fromString("10000001-0000-0000-FDFD-FDFDFDFDFDFD")



class MainActivity : ComponentActivity() {

    private lateinit var scanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) startScan() else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = applicationContext

        val bluetoothManager: BluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        scanner = bluetoothAdapter.bluetoothLeScanner


        val deviceList = mutableStateListOf<BluetoothDevice>()

        setContent {

            val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
            val intensity = remember { mutableStateOf(0) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (selectedDevice.value == null) {
                        // No device selected => show device list
                        Text("Devicelist:")
                        LazyColumn(modifier = Modifier.padding(16.dp)) {


                            items(deviceList) { device ->
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    // TODO: Consider calling
                                    //    ActivityCompat#requestPermissions
                                    // here to request the missing permissions, and then overriding
                                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                    //                                          int[] grantResults)
                                    // to handle the case where the user grants the permission. See the documentation
                                    // for ActivityCompat#requestPermissions for more details.
                                    return@items
                                }

                                Text(
                                    "${device.name} - ${device.address}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .clickable {
                                            selectedDevice.value = device
                                            connectToDevice(device, context)
                                        }
                                )
                            }
                        }
                    } else {
                        // connected to wheater sensor => show data
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Connected to: ${selectedDevice.value?.name}")
                            Text("Intensity: ${intensity.value}")
                            Slider(value = intensity.value.toFloat(),
                                onValueChange = { intensity.value = it.toInt() },
                                valueRange = 0f..65535f,
                                steps = 10
                            )

                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                setIntensity(intensity.value)
                            }) {
                                Text("Set Intensity")
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                disconnectFromDevice()
                                selectedDevice.value = null
                            }) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }
        }

        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                // if (!deviceList.any { it.address == device.address } && device.name != null) {
                if (!deviceList.any { it.address == device.address }) {
                    deviceList.add(device)
                }
            }
        }
    }

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        scanner.startScan(null, ScanSettings.Builder().build(), scanCallback)

        // Stop after 10 seconds
        window.decorView.postDelayed({
            scanner.stopScan(scanCallback)
        }, 10_000)
    }


    private var gatt: BluetoothGatt? = null

    private fun connectToDevice(
        device: BluetoothDevice,
        context: Context
    ) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            println("Cannot connect to BLE device")
            return
        }
        gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        println("Could not connect GATT")
                        return
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.disconnect()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(LIGHT_SERVICE_UUID)
                val intensityChar = service?.getCharacteristic(INTENSITY_CHAR_UUID)

                if(intensityChar != null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connected! Ready to set values.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                println("Write status: $status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    println("Write successful: ${characteristic.value.joinToString(" ") { "%02X".format(it) }}")
                } else {
                    println("Write failed with status $status")
                }
            }

        })
    }

    private fun disconnectFromDevice() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // This shouldn't happen in any case
            gatt = null
            return
        }
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private fun setIntensity(value: Int) {
        val gatt = gatt ?: return
        val service = gatt.getService(LIGHT_SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(INTENSITY_CHAR_UUID) ?: return

        val data = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        println("Writing intensity value: "+ data.joinToString(" ") { "%02X".format(it) })

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        println("Successfully sent")
        gatt.writeCharacteristic(characteristic)


    }
}

