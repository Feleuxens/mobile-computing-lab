package de.team10.ble_weather

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

class BLECommandQueue(private val gatt: BluetoothGatt) {
    private val queue: Queue<BLECommand> = LinkedList()
    private var busy = false

    fun add(command: BLECommand) {
        queue.offer(command)
        processNext()
    }

    private fun processNext() {
        if (busy) return
        val command = queue.poll()
        if (command != null) {
            busy = command.execute(gatt)
            if (!busy) {
                processNext()
            }
        }

    }

    fun onOperationComplete() {
        busy = false
        processNext()
    }
}


interface BLECommand {
    fun execute(gatt: BluetoothGatt): Boolean
}

class ReadCharacteristicCommand(private val characteristic: BluetoothGattCharacteristic) :
    BLECommand {
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun execute(gatt: BluetoothGatt): Boolean {
        Log.i("queue", "Read characteristic " + characteristic.uuid.toString())
        return gatt.readCharacteristic(characteristic)
    }
}

class EnableNotificationCommand(private val characteristic: BluetoothGattCharacteristic) :
    BLECommand {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun execute(gatt: BluetoothGatt): Boolean {
        Log.i(
            "queue",
            "Set Local Notification for characteristic " + characteristic.uuid.toString()
        )
        gatt.setCharacteristicNotification(characteristic, true)
        return false
    }
}

private val CLIENT_CHARACTERISTIC_CONFIGURATION =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class WriteNotificationDescriptorCommand(
    private val characteristic: BluetoothGattCharacteristic,
    private val enable: Boolean
) : BLECommand {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun execute(gatt: BluetoothGatt): Boolean {
        Log.i("queue", "Write Descriptor for characteristic " + characteristic.uuid.toString())
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION)
        if (enable)
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        else
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

        return true
    }
}



