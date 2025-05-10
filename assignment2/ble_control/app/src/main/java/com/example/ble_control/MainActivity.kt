package com.example.ble_control

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
import android.os.Build
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
import androidx.annotation.RequiresApi

private val LIGHT_SERVICE_UUID = UUID.fromString("00000001-0000-0000-FDFD-FDFDFDFDFDFD")
private val INTENSITY_CHAR_UUID = UUID.fromString("10000001-0000-0000-FDFD-FDFDFDFDFDFD")




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

    @RequiresApi(Build.VERSION_CODES.P)
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
            val intensity = remember { mutableStateOf(0) }

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
                                                connectToDevice(device, context)
                                            }
                                    )
                                }
                            }
                        }
                    } else {
                        // connected to wheater sensor => show data
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Connected to: ${selectedDevice.value?.name}")
                            Text("Intensity: ${intensity.value}")
                            Slider(
                                value = intensity.value.toFloat(),
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