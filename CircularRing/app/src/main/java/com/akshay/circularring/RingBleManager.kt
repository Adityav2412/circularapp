package com.akshay.circularring

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.*

// NUS UUIDs
val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
val NUS_RX_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // write to ring
val NUS_TX_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // notify from ring
val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

data class RingData(
    val battery: Int = -1,
    val isCharging: Boolean = false,
    val firmwareVersion: String = "",
    val serialNumber: String = "",
    val modelInfo: String = "",
    val deviceName: String = "",
    val bsmMode: String = "",
    val rawLogs: MutableList<String> = mutableListOf()
)

interface RingCallback {
    fun onConnected()
    fun onDisconnected()
    fun onDataUpdated(data: RingData)
    fun onRawLog(direction: String, message: String)
    fun onScanResult(device: BluetoothDevice)
    fun onError(msg: String)
}

class RingBleManager(private val context: Context, private val callback: RingCallback) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private var writeQueue: ArrayDeque<String> = ArrayDeque()
    private var isWriting = false

    var ringData = RingData()
        private set

    // --- SCAN ---
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (name.startsWith("Circular", ignoreCase = true)) {
                callback.onScanResult(result.device)
            }
        }
    }

    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    // --- CONNECT ---
    fun connect(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    // --- GATT CALLBACK ---
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post { callback.onDisconnected() }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(NUS_SERVICE_UUID) ?: run {
                handler.post { callback.onError("NUS service not found") }
                return
            }
            rxChar = service.getCharacteristic(NUS_RX_UUID)
            txChar = service.getCharacteristic(NUS_TX_UUID)

            // Enable notifications on TX
            txChar?.let { tx ->
                g.setCharacteristicNotification(tx, true)
                val descriptor = tx.getDescriptor(CCCD_UUID)
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Notifications enabled - start init sequence
            handler.post {
                callback.onConnected()
                startInitSequence()
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val msg = characteristic.value?.toString(Charsets.UTF_8)?.trim() ?: return
            handler.post { handleRingMessage(msg) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            isWriting = false
            handler.post { processWriteQueue() }
        }
    }

    // --- INIT SEQUENCE ---
    private fun startInitSequence() {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        enqueueWrite("ACK0001")
        enqueueWrite("CAL$iso")
    }

    // --- HANDLE RING MESSAGES ---
    private fun handleRingMessage(msg: String) {
        callback.onRawLog("RING→APP", msg)
        ringData = ringData.copy(rawLogs = ringData.rawLogs.also { it.add("← $msg") })

        when {
            msg.startsWith("BAT") -> {
                // BAT099/01 format
                val parts = msg.removePrefix("BAT").split("/")
                val batt = parts.getOrNull(0)?.toIntOrNull() ?: -1
                val charging = parts.getOrNull(1) == "01"
                ringData = ringData.copy(battery = batt, isCharging = charging)
            }
            msg == "RFWV" || msg == "FWV" -> {
                enqueueWrite("FWV${ringData.firmwareVersion.ifEmpty { "1.1.142-release" }}")
            }
            msg.startsWith("MOD0") -> {
                val model = msg.removePrefix("MOD0")
                ringData = ringData.copy(modelInfo = model)
                enqueueWrite("MOD1$model")
            }
            msg == "RNAM" || msg == "NAM" -> {
                enqueueWrite("NAME${ringData.deviceName.ifEmpty { "CircularRing" }}")
            }
            msg.startsWith("SNU") || msg == "RSNU" -> {
                val snu = msg.removePrefix("SNU").removePrefix("R")
                if (snu.isNotEmpty()) {
                    ringData = ringData.copy(serialNumber = snu)
                    enqueueWrite("SNU$snu")
                } else {
                    enqueueWrite("SNUCR000000000000")
                }
            }
            msg.startsWith("ALR") || msg == "RALR" -> {
                enqueueWrite("ALREOS")
            }
            msg.startsWith("MAR") || msg == "RMAR" -> {
                enqueueWrite("MAREOS")
            }
            msg.startsWith("IBT") || msg == "RIBT" -> {
                enqueueWrite("IBT0000-00-00T00:00:00Z")
            }
            msg.startsWith("BSM") -> {
                val mode = msg.removePrefix("BSM")
                ringData = ringData.copy(bsmMode = mode)
            }
            msg.startsWith("CAL") -> {
                // Ring's calibration timestamp - ignore or store
            }
            msg.startsWith("FBL") -> {
                val fbl = msg.removePrefix("FBL")
                enqueueWrite("FBL$fbl")
            }
        }

        callback.onDataUpdated(ringData)
    }

    // --- WRITE QUEUE ---
    private fun enqueueWrite(cmd: String) {
        writeQueue.addLast(cmd)
        if (!isWriting) processWriteQueue()
    }

    private fun processWriteQueue() {
        if (writeQueue.isEmpty()) return
        val cmd = writeQueue.removeFirst()
        val char = rxChar ?: return
        isWriting = true
        callback.onRawLog("APP→RING", cmd)
        ringData = ringData.copy(rawLogs = ringData.rawLogs.also { it.add("→ $cmd") })
        char.value = cmd.toByteArray(Charsets.UTF_8)
        gatt?.writeCharacteristic(char)
    }

    // --- PUBLIC COMMANDS ---
    fun sendCommand(cmd: String) {
        enqueueWrite(cmd)
    }
}
