package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val SCAN_PERIOD: Long = 10000
    private var scanning = false
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var found = false

    private lateinit var gatt: BluetoothGatt
    private lateinit var characteristic: BluetoothGattCharacteristic


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Permission check
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
            scanLeDevice()  // Start scanning when the button is clicked
        }

        writeButton.setOnClickListener {
            writeBytesToCharacteristic(byteArrayOf(0x01, 0x02, 0x03))  // Example data to write
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        if (!scanning) {
            handler.postDelayed({
                if (found) {
                    scanning = false
                    scanner?.stopScan(leScanCallback)
                    Log.d("[BLE]", "Target found. Scan stopped.")
                } else {
                    Log.d("[BLE]", "Target not found. Scanning...")
                }
            }, SCAN_PERIOD)

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanning = true
            scanner?.startScan(null, scanSettings, leScanCallback)
            Log.d("[BLE]", "Scan started.")
        } else {
            scanning = false
            scanner?.stopScan(leScanCallback)
            Log.d("[BLE]", "Scan manually stopped.")
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val deviceName = result.device.name ?: "Unnamed"
            Log.d("[BLE]", "Device found: $deviceName, address: ${result.device.address}")

            if (result.device.address == "78:6D:EB:49:97:84") {
                Log.d("[BLE]", "Target found: ${result.device.name}")
                found = true
                result.device.connectGatt(this@MainActivity, false, gattCallback) // Connect to the device
                scanner?.stopScan(this)  // Stop scanning once the target is found
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("[BLE:GAT]", "Connected to GATT server")
                gatt.discoverServices()  // Discover services once connected
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("[BLE:GAT]", "Disconnected from GATT server")
            }else{
                Log.d("[BLE:GAT]", newState.toString())
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("[BLE:GAT]", "Services discovered")

                val CHARACTERISTIC_UUID = UUID.fromString("0000xxxx-0000-1000-8000-00805f9b34fb") // Replace with the actual characteristic UUID
                val SERVICE_UUID = UUID.fromString("0000xxxx-0000-1000-8000-00805f9b34fb") // Replace with the actual service UUID

                val service = gatt.getService(SERVICE_UUID)
                characteristic = service.getCharacteristic(CHARACTERISTIC_UUID) // Get the target characteristic
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("[BLE:GAT]", "Successfully written to characteristic!")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeBytesToCharacteristic(data: ByteArray) {
        // Write data to the characteristic if connected
        if (this::gatt.isInitialized && this::characteristic.isInitialized) {
            characteristic.value = data
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                Log.d("[BLE]", "Write initiated")
            } else {
                Log.d("[BLE]", "Write failed")
            }
        } else {
            Log.d("[BLE]", "GATT or characteristic not initialized")
        }
    }
}
