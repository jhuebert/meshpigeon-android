package app.meshpigeon.transport.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.annotation.SuppressLint
import android.content.Context
import app.meshpigeon.transport.FrameStream
import app.meshpigeon.transport.RadioAdapter
import app.meshpigeon.transport.RadioFrame
import app.meshpigeon.transport.RadioLink
import app.meshpigeon.transport.RadioLinkState
import app.meshpigeon.transport.RadioTarget
import app.meshpigeon.transport.TransportFrameCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BLE adapter (05 §1): GATT client for the firmware's Nordic-UART-Service
 * (write-to-radio char, notify-from-radio char). Multi-connect capable —
 * one BleRadioSession per connected central role instance.
 *
 * Nordic UART Service (must match meshpigeon-firmware/src/transports.h):
 */
object NordicUart {
    val SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val WRITE_CHAR: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val NOTIFY_CHAR: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/**
 * Permission gating: the connection UI holds runtime BLUETOOTH_SCAN/CONNECT
 * before constructing this adapter (and re-checks on every scan/connect);
 * the SecurityException guards below catch a declined prompt gracefully.
 */
@SuppressLint("MissingPermission")
class BleRadioAdapter(
    private val context: Context,
    private val mac: String,
) : RadioAdapter {

    override val state = MutableStateFlow(RadioLinkState(RadioLinkState.Phase.DISCONNECTED))
    override val frames = MutableSharedFlow<RadioFrame>(extraBufferCapacity = 256)

    private val bt: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gatt: BluetoothGatt? = null
    private val stream = FrameStream()

    private val callback = object : BluetoothGattCallback() {
        // GATT callbacks only fire while a permission-holding connection is
        // up; annotate so lint models the runtime contract.
        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    state.value = RadioLinkState(RadioLinkState.Phase.CONNECTED, target)
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    state.value = RadioLinkState(RadioLinkState.Phase.DISCONNECTED)
                }
            }
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(NordicUart.SERVICE) ?: run {
                state.value = RadioLinkState(
                    RadioLinkState.Phase.DISCONNECTED, target,
                    error = "radio has no MeshPigeon UART service",
                )
                return
            }
            val notify = svc.getCharacteristic(NordicUart.NOTIFY_CHAR) ?: return
            g.setCharacteristicNotification(notify, true)
            g.writeDescriptor(
                notify.getDescriptor(NordicUart.CCCD).apply {
                    value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                },
            )
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            // notifications active → radio ready
            state.value = RadioLinkState(RadioLinkState.Phase.RADIO_READY, target)
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            // GATT callbacks arrive on the binder thread; frames flow out as
            // they complete (complete frames may span several notifies).
            synchronized(stream) {
                stream.feed(value).forEach { frames.tryEmit(it) }
            }
        }
    }

    private val target = RadioTarget(
        persistentId = "ble:$mac",
        name = "MeshPigeon radio",
        link = RadioLink.Ble(mac),
    )

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun send(frame: RadioFrame) {
        val g = gatt ?: error("not connected")
        // await RADIO_READY — the CCCD subscription gates real traffic
        state.firstRadioReadyOrThrow()
        val wire = TransportFrameCodec.toWire(frame)
        val svc = g.getService(NordicUart.SERVICE) ?: error("service missing")
        val rx = svc.getCharacteristic(NordicUart.WRITE_CHAR) ?: error("write char missing")
        // chunk to the negotiated MTU-3; pre-MTU default 20 keeps it safe
        var off = 0
        while (off < wire.size) {
            val chunk = minOf(20, wire.size - off)
            rx.value = wire.copyOfRange(off, off + chunk)
            g.writeCharacteristic(rx)
            off += chunk
        }
    }

    private suspend fun StateFlow<RadioLinkState>.firstRadioReadyOrThrow() {
        // short wait loop; the service layer drives reconnection policy
        val deadline = System.currentTimeMillis() + 5_000
        while (value.phase != RadioLinkState.Phase.RADIO_READY) {
            if (System.currentTimeMillis() > deadline) error("radio not ready")
            kotlinx.coroutines.delay(50)
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun scan(): Flow<RadioTarget> = callbackFlow {
        val adapterBt = bt.adapter
        // The caller (UI layer) holds runtime BLUETOOTH_SCAN/CONNECT; a
        // SecurityException here means the prompt was declined — no results.
        val scanner = if (adapterBt?.isEnabled == true) {
            try {
                adapterBt.bluetoothLeScanner
            } catch (_: SecurityException) {
                null
            }
        } else {
            null
        }
        if (scanner == null) {
            close()
            return@callbackFlow
        }
        val scope = kotlinx.coroutines.MainScope()
        var scanCallback: android.bluetooth.le.ScanCallback? = null
        scope.launch(Dispatchers.Main) {
            // scan for the NUS service UUID advertised by MeshPigeon radios
            val filter = android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(NordicUart.SERVICE))
                .build()
            val settings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val cb = object : android.bluetooth.le.ScanCallback() {
                override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                    trySend(
                        RadioTarget(
                            persistentId = "ble:${result.device.address}",
                            name = result.scanRecord?.deviceName ?: "MeshPigeon radio",
                            link = RadioLink.Ble(result.device.address),
                        ),
                    )
                }
            }
            scanCallback = cb
            scanner.startScan(listOf(filter), settings, cb)
        }
        awaitClose {
            scope.cancel()
            try {
                scanCallback?.let { scanner.stopScan(it) }
            } catch (_: Exception) {
            }
        }
    }

    /** Blocking GATT connect (call from a coroutine on Dispatchers.IO). */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connectBlocking(autoConnect: Boolean = false): Boolean {
        // connectGatt is the permission-holding entry point for everything
        // the callbacks do afterwards.
        val device: BluetoothDevice = try {
            bt.adapter?.getRemoteDevice(mac)
        } catch (_: SecurityException) {
            null
        } ?: return false
        gatt = device.connectGatt(context, autoConnect, callback)
        return gatt != null
    }

    fun close() {
        gatt?.close()
        gatt = null
        state.value = RadioLinkState(RadioLinkState.Phase.DISCONNECTED)
    }
}
