package de.team10.ble_weather

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import kotlin.math.pow

private val WEATHER_SERVICE_UUID = UUID.fromString("00000002-0000-0000-FDFD-FDFDFDFDFDFD")
private val TEMP_CHAR_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")
private val HUMIDITY_CHAR_UUID = UUID.fromString("00002A6F-0000-1000-8000-00805f9b34fb")
private val CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")



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

        val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        scanner = bluetoothAdapter.bluetoothLeScanner


        val deviceList = mutableStateListOf<BluetoothDevice>()

        setContent {

            val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
            val temperature = remember { mutableStateOf("N/A") }
            val humidity = remember { mutableStateOf("N/A") }

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
                                                connectToDevice(device, temperature, humidity, context)
                                            }
                                    )
                            }
                        }
                    } else {
                        // connected to wheater sensor => show data
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Connected to: ${selectedDevice.value?.name}")
                            Text("Temperature: ${temperature.value}")
                            Text("Humidity: ${humidity.value}")
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
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
        temperature: MutableState<String>,
        humidity: MutableState<String>,
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
                val service = gatt.getService(WEATHER_SERVICE_UUID)
                val tempChar = service?.getCharacteristic(TEMP_CHAR_UUID)
                val humChar = service?.getCharacteristic(HUMIDITY_CHAR_UUID)

                if (tempChar != null) {
                    println("Get temperature service")
                    readAndSubscribe(gatt, tempChar)
                }

                if (humChar != null) {
                    println("Get humidity service")
                    readAndSubscribe(gatt, humChar)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                when (characteristic.uuid) {
                    TEMP_CHAR_UUID -> temperature.value = parseTemperature(value)
                    HUMIDITY_CHAR_UUID -> humidity.value = parseHumidity(value)
                }
            }
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                gatt.readCharacteristic(characteristic)

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
    private fun readAndSubscribe(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // -> Your App should implement functions for querying (reading)
        gatt.readCharacteristic(characteristic)

        // -> and subscribing to notifications, i.e., update the values when new ones arrive
        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION)
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }

    // Parsing of: 3.208 Temperature Measurement
    private fun parseTemperature(data: ByteArray): String {
        var pre = "Invalid data"
        if (data.size > 5) {

            val flags = data[0]  // TODO: Can be used for unit later (Celsius vs Fahrenheit)

            // Extract exponent (1 byte) and mantissa (3 bytes) in little endian
            val exponent = data[4].toInt().toByte().toInt() // signed 8-bit
            val mantissa = ((data[3].toInt() and 0xFF) shl 16) or
                    ((data[2].toInt() and 0xFF) shl 8) or
                    (data[1].toInt() and 0xFF)

            // Sign-extend mantissa (24-bit signed to 32-bit signed)
            val signedMantissa = if (mantissa and 0x800000 != 0) {
                mantissa or -0x1000000
            } else {
                mantissa
            }

            val value = signedMantissa * 10.0.pow(exponent.toDouble())
            pre = "%.2f °C".format(value)
        }
        return pre + " " + " ByteArray: " + data.joinToString(" ") { "%02X".format(it) }
    }

    // Parsing of: 3.114 Humidity
    private fun parseHumidity(data: ByteArray): String {
        var pre = "Invalid data"
        if (data.size >= 2) {

            val humRaw = (data[1].toInt() and 0xFF shl 8) or (data[0].toInt() and 0xFF)
            if (humRaw == 0xFFFF) {
                return "value is not known"
            }
            pre = "%.2f %%".format(humRaw.toFloat() / 100.0f)
        }
        return pre + " " + " ByteArray: " + data.joinToString(" ") { "%02X".format(it) }
    }

}

