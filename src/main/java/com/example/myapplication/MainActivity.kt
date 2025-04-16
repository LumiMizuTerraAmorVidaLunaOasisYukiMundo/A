package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val SCAN_PERIOD: Long = 10000
    private var scanning = false
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var found = false
    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var bluetoothCharacteristic: BluetoothGattCharacteristic
    private lateinit var logView: TextView
    private val TARGET_DEVICE_NAME = "Liam_BLE"

    private fun log(tag: String, message: String) {
        runOnUiThread {
            logView.append("$message\n\n")
            val scrollView = logView.parent as ScrollView
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
        Log.d(tag,message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.logView)
        val slider1 = findViewById<SeekBar>(R.id.slider1)
        val switch = findViewById<Switch>(R.id.switch1)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION),
                1)
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        scanner = bluetoothAdapter.bluetoothLeScanner

        val scanButton = findViewById<Button>(R.id.scanButton)
        val writeButton = findViewById<Button>(R.id.writeButton)

        scanButton.setOnClickListener {
            startScan()
        }

        writeButton.setOnClickListener {

            val buffer = ByteBuffer.allocate(4 * 2).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(slider1.progress)
            buffer.putInt(if (switch.isChecked) 1 else 0)
            writeBytesToCharacteristic(buffer.array())
        }

        // auto start
        startScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val scanFilters = listOf<ScanFilter>(
            ScanFilter.Builder().setDeviceName(TARGET_DEVICE_NAME).build()
        )
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanning = true
        scanner.startScan(scanFilters, scanSettings, leScanCallback)
        log("BLE", "Scan started...")
    }

    private val leScanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val devName = device.name
            log("BLE", "Found device: $devName with address: ${device.address}")
            // Check if it's our target device
            if (devName != null && devName == TARGET_DEVICE_NAME) {
                // Found the target; stop scanning and connect
                bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                scanning = false
                log("BLE", "Target device found. Connecting...")
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "Scan failed with error: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback,BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("BLE", "Connected to GATT server")
                // Discover services on the device.
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("BLE", "Disconnected from GATT server ")
            }
            if(status!=0)
                log("BLE","ERROR $status")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("BLE", "*Services discovered")
                for (service in gatt.services) {
                    log("BLE", "~Service UUID: ${service.uuid}")
                    for (characteristic in service.characteristics) {
                        log("BLE", "  + Characteristic ${characteristic.uuid} p=${characteristic.properties}")
                        if(service.uuid==UUID.fromString("0000180c-0000-1000-8000-00805f9b34fb") && characteristic.uuid==UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb") ) {
                            log("BLE","Found target characteristic")
                            bluetoothCharacteristic=characteristic
                        }
                    }
                }
            } else {
                Log.e("BLE", "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("BLE", "Characteristic write succeeded")
            } else {
                Log.e("BLE", "Characteristic write failed with status: $status")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeBytesToCharacteristic(data: ByteArray) {
        log("[BLE]", "attempting write "+data.joinToString(" ") { "%02X".format(it) })
        if (this::bluetoothGatt.isInitialized && this::bluetoothCharacteristic.isInitialized) {
            bluetoothCharacteristic.value = data
            val success = bluetoothGatt.writeCharacteristic(bluetoothCharacteristic)
            if (success) {
                log("[BLE]", "Write initiated for ${bluetoothCharacteristic.uuid}")
            } else {
                log("[BLE]", "Write failed")
            }
        } else {
            log("[BLE]", "GATT or characteristic not initialized")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        // Clean up the connection.
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
    }


}
