package de.team10.ble_weather

import android.Manifest
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
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import java.nio.ByteBuffer
import java.util.UUID
import java.nio.ByteOrder
import android.provider.Settings

private val WEATHER_SERVICE_UUID = UUID.fromString("00000002-0000-0000-FDFD-FDFDFDFDFDFD")
private val TEMP_CHAR_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")
private val HUMIDITY_CHAR_UUID = UUID.fromString("00002A6F-0000-1000-8000-00805f9b34fb")

class MainActivity : ComponentActivity() {

    val handler: Handler = Handler(Looper.getMainLooper())

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback
    private lateinit var locationManager: LocationManager
    private var bluetoothLocationActive: MutableState<Boolean> = mutableStateOf(false)
    private var permissions = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activationLauncher.launch(enableBtIntent)
            }
            permissions = true
        } else {
            permissions = false
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    // Set up ActivityResultLauncher for Bluetooth enabling
    val activationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth was enabled, check location permission

        } else {
            // User denied Bluetooth enabling
            Toast.makeText(this, "Please enable bluetooth/GPS to continue", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("onCreate", "Activity created.")
        super.onCreate(savedInstanceState)

        handler.post(object : Runnable {
            override fun run() {
                bluetoothLocationActive.value =
                    bluetoothAdapter.isEnabled and locationManager.isLocationEnabled
                handler.postDelayed(this, 1000)
            }
        })

        val context = applicationContext

        val bluetoothManager: BluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isLocationEnabled) {
            // Prompt the user to enable location services
            val locationSettingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            activationLauncher.launch(locationSettingsIntent)
        }

        val deviceList = mutableStateListOf<BluetoothDevice>()

        setContent {

            bluetoothLocationActive =
                remember { mutableStateOf(bluetoothAdapter.isEnabled and locationManager.isLocationEnabled) }
            val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
            val temperature = remember { mutableStateOf("N/A") }
            val humidity = remember { mutableStateOf("N/A") }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!bluetoothLocationActive.value) {
                        Text("Please enable Bluetooth and GPS.")
                    } else if (selectedDevice.value == null) {
                        scanner = bluetoothAdapter.bluetoothLeScanner
                        // No device selected => show device list
                        Column {
                            Button(onClick = { getPermissionsAndStartScan() }) { Text("Start scanning") }
                            Text("Devicelist:")
                            LazyColumn(modifier = Modifier.padding(16.dp)) {


                                items(deviceList) { device ->
                                    if (ActivityCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        return@items
                                    }

                                    if (device.name == null) {
                                        return@items
                                    }

                                    Text(
                                        "${device.name} - ${device.address}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .clickable {
                                                selectedDevice.value = device
                                                connectToDevice(
                                                    device,
                                                    temperature,
                                                    humidity,
                                                    context
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    } else {
                        // connected to weather sensor => show data
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

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                // if (!deviceList.any { it.address == device.address } && device.name != null) {
                if (!deviceList.any { it.address == device.address }) {
                    deviceList.add(device)
                }
            }
        }
    }

    private fun getPermissionsAndStartScan() {
        Log.i("getPermissionsAndStartScan", "getting permissions")
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        if (permissions) startScan()
    }

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("scanner", "Missing permissions!")
            return
        }
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()
        scanner.startScan(null, ScanSettings.Builder().build(), scanCallback)

        // Stop after 10 seconds
        window.decorView.postDelayed({
            scanner.stopScan(scanCallback)
        }, 10_000)
    }


    private var gatt: BluetoothGatt? = null
    private var bleCommandQueue: BLECommandQueue? = null

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
                    bleCommandQueue!!.add(ReadCharacteristicCommand(tempChar))
                    bleCommandQueue!!.add(EnableNotificationCommand(tempChar))
                    bleCommandQueue!!.add(WriteNotificationDescriptorCommand(tempChar, true))

                    Log.i("Service", "Get temperature service")
                }

                if (humChar != null) {
                    bleCommandQueue!!.add(ReadCharacteristicCommand(humChar))
                    bleCommandQueue!!.add(EnableNotificationCommand(humChar))
                    bleCommandQueue!!.add(WriteNotificationDescriptorCommand(humChar, true))

                    Log.i("service", "Get humidity service")
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                Log.i("characteristic", "Status: " + status + " " + characteristic.uuid.toString())
                when (characteristic.uuid) {
                    TEMP_CHAR_UUID -> temperature.value = parseTemperature(value)
                    HUMIDITY_CHAR_UUID -> humidity.value = parseHumidity(value)
                }

                bleCommandQueue!!.onOperationComplete()
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                when (characteristic.uuid) {
                    TEMP_CHAR_UUID -> temperature.value = parseTemperature(characteristic.value)
                    HUMIDITY_CHAR_UUID -> humidity.value = parseHumidity(characteristic.value)
                }

                bleCommandQueue!!.onOperationComplete()
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)

                bleCommandQueue!!.onOperationComplete()
            }

        })

        bleCommandQueue = BLECommandQueue(gatt!!)
    }

    private fun disconnectFromDevice() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // This shouldn't happen since we couldn't have connected in the first place
            gatt = null
            return
        }
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }


    /// Parsing of: 3.208 Temperature Measurement
    fun parseTemperature(data: ByteArray?): String {
        if (data == null) {
            return "No data received"
        }

        val flags = data[0].toInt() and 0xFF

        val unitIsFahrenheit = flags and 0x01 != 0

        var index = 1

        // IEEE-11073 FLOAT = 32 bits (mantissa + exponent)
        val tempRaw = ByteBuffer.wrap(data, index, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val temperature = ieee11073ToFloat(tempRaw)
        index += 4

        val unit = if (unitIsFahrenheit) "°F" else "°C"
        return "%.2f %s".format(temperature, unit)
    }

    // Convert IEEE-11073 FLOAT to native float
    fun ieee11073ToFloat(raw: Int): Float {
        val mantissa = raw and 0x00FFFFFF
        val exponent = (raw shr 24).toByte().toInt()
        return (if (mantissa and 0x00800000 != 0) mantissa or -0x1000000 else mantissa).toFloat() * Math.pow(
            10.0,
            exponent.toDouble()
        ).toFloat()
    }


    // Parsing of: 3.114 Humidity
    fun parseHumidity(data: ByteArray?): String {
        if (data == null || data.isEmpty()) {
            Log.e("HumidityParser", "Data is null or empty")
            return "No data"
        }

        if (data.size < 2) {
            Log.e("HumidityParser", "Data size is too small (must be at least 2 bytes)")
            return "Not enough data"
        }

        val humidityValue = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        return "%.2f %%".format(humidityValue / 100.0f)
    }
}

