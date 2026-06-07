package com.akshay.circularring

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), RingCallback {

    private lateinit var bleManager: RingBleManager

    // Views
    private lateinit var btnScan: Button
    private lateinit var btnDisconnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvCharging: TextView
    private lateinit var tvFirmware: TextView
    private lateinit var tvSerial: TextView
    private lateinit var tvModel: TextView
    private lateinit var tvBsm: TextView
    private lateinit var layoutData: View
    private lateinit var tvRawLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var etCommand: EditText
    private lateinit var btnSend: Button
    private lateinit var deviceListLayout: LinearLayout

    private val PERMISSIONS_REQUEST = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bleManager = RingBleManager(this, this)

        btnScan = findViewById(R.id.btnScan)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBattery = findViewById(R.id.tvBattery)
        tvCharging = findViewById(R.id.tvCharging)
        tvFirmware = findViewById(R.id.tvFirmware)
        tvSerial = findViewById(R.id.tvSerial)
        tvModel = findViewById(R.id.tvModel)
        tvBsm = findViewById(R.id.tvBsm)
        layoutData = findViewById(R.id.layoutData)
        tvRawLog = findViewById(R.id.tvRawLog)
        scrollLog = findViewById(R.id.scrollLog)
        etCommand = findViewById(R.id.etCommand)
        btnSend = findViewById(R.id.btnSend)
        deviceListLayout = findViewById(R.id.deviceListLayout)

        btnScan.setOnClickListener {
            if (checkPermissions()) {
                deviceListLayout.removeAllViews()
                deviceListLayout.visibility = View.VISIBLE
                tvStatus.text = "Scanning..."
                bleManager.startScan()
                // Stop scan after 10 sec
                btnScan.postDelayed({ bleManager.stopScan() }, 10000)
            }
        }

        btnDisconnect.setOnClickListener {
            bleManager.disconnect()
            layoutData.visibility = View.GONE
            btnDisconnect.visibility = View.GONE
            tvStatus.text = "Disconnected"
        }

        btnSend.setOnClickListener {
            val cmd = etCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                bleManager.sendCommand(cmd)
                etCommand.setText("")
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return if (perms.isEmpty()) true
        else { ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERMISSIONS_REQUEST); false }
    }

    // --- RingCallback ---
    override fun onConnected() {
        runOnUiThread {
            tvStatus.text = "Connected ✓"
            deviceListLayout.visibility = View.GONE
            btnDisconnect.visibility = View.VISIBLE
            layoutData.visibility = View.VISIBLE
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            tvStatus.text = "Disconnected"
            layoutData.visibility = View.GONE
            btnDisconnect.visibility = View.GONE
        }
    }

    override fun onDataUpdated(data: RingData) {
        runOnUiThread {
            if (data.battery >= 0) tvBattery.text = "${data.battery}%"
            tvCharging.text = if (data.isCharging) "Charging ⚡" else "Not charging"
            if (data.firmwareVersion.isNotEmpty()) tvFirmware.text = data.firmwareVersion
            if (data.serialNumber.isNotEmpty()) tvSerial.text = data.serialNumber
            if (data.modelInfo.isNotEmpty()) tvModel.text = data.modelInfo
            if (data.bsmMode.isNotEmpty()) tvBsm.text = "BSM: ${data.bsmMode}"
        }
    }

    override fun onRawLog(direction: String, message: String) {
        runOnUiThread {
            val arrow = if (direction == "APP→RING") "→" else "←"
            tvRawLog.append("$arrow $message\n")
            scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onScanResult(device: BluetoothDevice) {
        runOnUiThread {
            bleManager.stopScan()
            tvStatus.text = "Found: ${device.name}"

            val btn = Button(this).apply {
                text = "${device.name}\n${device.address}"
                setOnClickListener {
                    deviceListLayout.visibility = View.GONE
                    tvStatus.text = "Connecting..."
                    bleManager.connect(device)
                }
            }
            deviceListLayout.addView(btn)
        }
    }

    override fun onError(msg: String) {
        runOnUiThread { tvStatus.text = "Error: $msg" }
    }
}
