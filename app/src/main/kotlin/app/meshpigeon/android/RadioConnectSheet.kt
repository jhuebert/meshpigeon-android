package app.meshpigeon.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meshpigeon.domain.RadioLinkKind
import app.meshpigeon.domain.RadioTarget
import app.meshpigeon.ui.MeshPigeonSpacing
import app.meshpigeon.transport.RadioTarget as TransportTarget
import app.meshpigeon.transport.TcpRadioAdapter
import app.meshpigeon.transport.android.BleRadioAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Radio connect sheet (05 §2): scan → list → connect, then the foreground
 * service owns the link. v1 surfaces Wi-Fi (the desktop simulator for dev,
 * and Wi-Fi boards) and Bluetooth; USB lands with M4's app-driven repeater.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioConnectSheet(graph: AppGraph, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection by graph.connection.collectAsStateWithLifecycle()
    val blePerms = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var blePermsRequested by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("8765") }
    var wifiBusy by remember { mutableStateOf(false) }
    var wifiFound by remember { mutableStateOf(listOf<TransportTarget>()) }
    var bleBusy by remember { mutableStateOf(false) }
    var bleFound by remember { mutableStateOf(listOf<TransportTarget>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // a declined prompt just yields no scan results (adapter guards)
        if (grants.values.all { it }) blePermsRequested = true
    }

    // only reached with runtime permissions granted (startBleScan / the
    // permission launcher's granted callback gate it)
    @android.annotation.SuppressLint("MissingPermission")
    fun doBleScan() {
        scope.launch {
            bleBusy = true
            bleFound = emptyList()
            BleRadioAdapter(context, "").scan().collect { t -> bleFound = bleFound + t }
            bleBusy = false
        }
    }

    fun startBleScan() {
        val missing = blePerms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        doBleScan()
    }

    // scan resumes after the permission prompt returns
    LaunchedEffect(blePermsRequested) {
        if (blePermsRequested) doBleScan()
    }

    fun probeWifi() {
        scope.launch {
            wifiBusy = true
            wifiFound = emptyList()
            TcpRadioAdapter(host.trim(), port.toIntOrNull() ?: 8765)
                .scan()
                .collect { t -> wifiFound = wifiFound + t }
            wifiBusy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeshPigeonSpacing.lg)
                .padding(bottom = MeshPigeonSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.md),
        ) {
            Text("Connect a radio", style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    connection.name != null && connection.detail != null ->
                        "${connection.name} — ${connection.detail}"
                    connection.name != null -> "Connected · ${connection.name}"
                    else -> "Not connected"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (connection.name != null) {
                OutlinedButton(
                    onClick = { context.stopService(Intent(context, RadioConnectionService::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Disconnect") }
            }

            Text("Bluetooth radios", style = MaterialTheme.typography.titleSmall)
            Button(
                onClick = ::startBleScan,
                enabled = !bleBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (bleBusy) "Scanning…" else if (blePermsRequested) "Scan again" else "Scan for Bluetooth radios") }
            if (bleBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            RadioRows(bleFound) { connect(context, graph, scope, onDismiss, it, RadioLinkKind.BLE) }

            Text("Wi-Fi / simulator", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MeshPigeonSpacing.sm)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = ::probeWifi,
                enabled = !wifiBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (wifiBusy) "Probing…" else "Probe address") }
            if (wifiBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            RadioRows(wifiFound) { connect(context, graph, scope, onDismiss, it, RadioLinkKind.WIFI) }
        }
    }
}

@Composable
private fun RadioRows(targets: List<TransportTarget>, onConnect: (TransportTarget) -> Unit) {
    LazyColumn {
        items(targets, key = { it.persistentId }) { target ->
            ListItem(
                headlineContent = { Text(target.name) },
                supportingContent = { Text(target.persistentId) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = { onConnect(target) }) { Text("Connect") }
                },
            )
        }
    }
}

/** Save the target (preference list) and hand the link to the service. */
private fun connect(
    context: Context,
    graph: AppGraph,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
    target: TransportTarget,
    kind: RadioLinkKind,
) {
    scope.launch {
        val order = (graph.radioTargets.observeAll().first().maxOfOrNull { it.prefOrder } ?: -1) + 1
        val addr = when (val link = target.link) {
            is app.meshpigeon.transport.RadioLink.Ble -> link.mac
            is app.meshpigeon.transport.RadioLink.Wifi -> "${link.host}:${link.port}"
            is app.meshpigeon.transport.RadioLink.Usb -> link.port
        }
        graph.radioTargets.upsert(
            RadioTarget(
                id = 0,
                persistentId = target.persistentId,
                name = target.name,
                linkKind = kind,
                linkAddr = addr,
                prefOrder = order,
                preferred = false,
            ),
        )
        val intent = Intent(context, RadioConnectionService::class.java)
            .putExtra(RadioConnectionService.EXTRA_PERSISTENT_ID, target.persistentId)
            .putExtra(RadioConnectionService.EXTRA_NAME, target.name)
            .putExtra(RadioConnectionService.EXTRA_KIND, kind.name)
            .putExtra(RadioConnectionService.EXTRA_ADDR, addr)
        context.startForegroundService(intent)
        onDismiss()
    }
}
