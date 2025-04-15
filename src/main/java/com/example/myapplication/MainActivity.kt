package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
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


class MainActivity : AppCompatActivity() {

    private val SCAN_PERIOD: Long = 10000
    private var scanning = false
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var found = false

    private lateinit var gatt: BluetoothGatt
    private lateinit var characteristic: BluetoothGattCharacteristic

    private lateinit var logView: TextView


    private fun log(tag: String, message: String) {
        runOnUiThread {
            logView.append("$message\n")
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
        val slider2 = findViewById<SeekBar>(R.id.slider2)
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
            scanLeDevice()
        }

        writeButton.setOnClickListener {

            val buffer = ByteBuffer.allocate(4 * 3).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(slider1.progress)
            buffer.putInt(slider2.progress)
            buffer.putInt(if (switch.isChecked) 1 else 0)
            writeBytesToCharacteristic(buffer.array())
        }

        // auto start
        scanLeDevice()
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
                    log("[BLE]", "Target not found. Scanning...")
                }
            }, SCAN_PERIOD)

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanning = true
            scanner?.startScan(null, scanSettings, leScanCallback)
            log("[BLE]", "Scan started.")
        } else {
            scanning = false
            scanner?.stopScan(leScanCallback)
            log("[BLE]", "Scan manually stopped.")
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val deviceName = result.device.name ?: "Unnamed"
            log("[BLE]", "Device found: $deviceName, address: ${result.device.address}")

            if (result.device.address == "78:6D:EB:49:97:84" || result.device.address ==  "BE:32:02:F5:6D:84") {
                log("[BLE]", "Target found: ${result.device.name}")
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
                log("[BLE:GAT]", "Connected to GATT server")
                gatt.discoverServices()  // Discover services once connected
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("[BLE:GAT]", "Disconnected from GATT server")
            }else{
                log("[BLE:GAT:STATUS]", newState.toString())
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("[BLE:GAT]", "Services discovered")

                for (service in gatt.services) {
                    log("[BLE:GAT]", "Service UUID: ${service.uuid}")
                    for (characteristic in service.characteristics) {
                        log("[BLE:GAT]", "\tCharacteristic UUID: ${characteristic.uuid}")

                        this@MainActivity.characteristic = characteristic
                        this@MainActivity.gatt = gatt
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
                            this@MainActivity.characteristic = characteristic
                            this@MainActivity.gatt = gatt
                            log("[BLE:GAT]", "\tWritable characteristic set")
                        }
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("[BLE:GAT]", "characteristic write succeeded")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeBytesToCharacteristic(data: ByteArray) {
        // Write data to the characteristic if connected
        log("[BLE]", "attempting write "+data.joinToString(" ") { "%02X".format(it) })
        if (this::gatt.isInitialized && this::characteristic.isInitialized) {
            characteristic.value = data
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                log("[BLE]", "Write initiated for ${characteristic.uuid}")
            } else {
                log("[BLE]", "Write failed")
            }
        } else {
            log("[BLE]", "GATT or characteristic not initialized")
        }
    }
}
