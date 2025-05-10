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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.pow
import java.nio.ByteOrder
import android.provider.Settings

private val WEATHER_SERVICE_UUID = UUID.fromString("00000002-0000-0000-FDFD-FDFDFDFDFDFD")
private val TEMP_CHAR_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")
private val HUMIDITY_CHAR_UUID = UUID.fromString("00002A6F-0000-1000-8000-00805f9b34fb")
private val CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")



class MainActivity : ComponentActivity() {

    val handler: Handler = Handler(Looper.getMainLooper())

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback
    private lateinit var locationManager: LocationManager
    private var bluetoothLocationActive: MutableState<Boolean> = mutableStateOf(false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) startScan() else {
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
            Toast.makeText(this, "Please enable bluetooth/GPS to continue", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("onCreate", "Activity created.")
        super.onCreate(savedInstanceState)

        handler.post(object : Runnable {
            override fun run() {
                bluetoothLocationActive.value = bluetoothAdapter.isEnabled and locationManager.isLocationEnabled
                handler.postDelayed(this, 1000)
            }
        })

        val context = applicationContext

        val bluetoothManager: BluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activationLauncher.launch(enableBtIntent)
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isLocationEnabled) {
            // Prompt the user to enable location services
            val locationSettingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            activationLauncher.launch(locationSettingsIntent)
        }

        val deviceList = mutableStateListOf<BluetoothDevice>()

        setContent {

            bluetoothLocationActive = remember { mutableStateOf(bluetoothAdapter.isEnabled and locationManager.isLocationEnabled) }
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

    private fun getPermissionsAndStartScan() {
        Log.i("getPermissionsAndStartScan", "getting permissions")
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
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
                    Log.i("Service", "Get temperature service")
                    readAndSubscribe(gatt, tempChar)
                }

                if (humChar != null) {
                    Log.i("service", "Get humidity service")
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
    fun parseTemperature(data: ByteArray?): String {
        if (data == null) {
            throw IllegalArgumentException("Input data cannot be null")
        }
        if (data.size < 2) {
            println("Error: Input data too short. Must be at least 2 bytes.")
            return "Invalid Data" // Handle the case where the data is too short.
        }

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val flags = buffer.get().toInt()

        // Determine the format of the temperature value (SFloat or Float)
        val isFloat = (flags and 0x01) != 0 // Check the first bit of flags

        val temperature: Float = if (isFloat) {
            if (data.size < 5) { //check size
                println("Error: Input data too short for Float temperature.  Must be at least 5 bytes.")
                return "Input data too short for Float temperature"
            }
            buffer.float
        } else {
            if (data.size < 3) { //check size
                println("Error: Input data too short for SFloat temperature.  Must be at least 3 bytes.")
                return "Input data too short for SFloat temperature"
            }
            //SFloat (16-bit float)
            val sfloatValue = buffer.short.toInt() // Ensure unsigned short
            convertSFloatToFloat(sfloatValue)
        }

        return "%.2f °C".format(temperature) + " " + " ByteArray: " + data.joinToString(" ") {
            "%02X".format(
                it
            )
        }
    }

    /**
     * Converts a 16-bit SFloat value to a Float.  SFloat is a 16-bit
     * representation with a 1-bit sign, 7-bit exponent, and 8-bit mantissa.
     */
    private fun convertSFloatToFloat(sfloatValue: Int): Float {
        val sign = if ((sfloatValue and 0x8000) != 0) -1 else 1
        val exponent = (sfloatValue shr 8) and 0x7F //bits 8-14
        val mantissa = sfloatValue and 0xFF // bits 0-7

        val floatMantissa: Float = mantissa.toFloat() / 256.0f // 2^8 = 256
        val floatExponent: Float = 10f.pow(exponent - 61) // Bias of 61 for SFloat

        return sign * floatMantissa * floatExponent
    }

    // Parsing of: 3.114 Humidity
    fun parseHumidity(data: ByteArray?): String {
        Log.i("humidity", "hey")
        if (data == null || data.isEmpty()) {
            Log.e("HumidityParser", "Data is null or empty")
            return "No data"
        }

        if (data.size < 2) {
            Log.e("HumidityParser", "Data size is too small (must be at least 2 bytes)")
            return "Not enough data"
        }

        val humidityValue = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        return "%.2f %%".format(humidityValue / 100.0f)
    }
}