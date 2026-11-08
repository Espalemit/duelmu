package id.eduduel.app.presentation.room

import id.eduduel.app.presentation.components.playfulPress
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import id.eduduel.app.presentation.components.GameOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import id.eduduel.app.presentation.components.GameTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.R
import id.eduduel.app.net.room.ConnectError
import id.eduduel.app.net.room.ConnectStatus
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.net.transport.BtAction
import id.eduduel.app.net.transport.BtDevice
import id.eduduel.app.net.transport.LanController
import id.eduduel.app.net.transport.LanRoom
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.InfoCard
import id.eduduel.app.presentation.components.LoadingState
import id.eduduel.app.presentation.components.PrimaryButton
import id.eduduel.app.presentation.components.ScrollColumn
import id.eduduel.app.presentation.navigation.LocalAppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun connectErrorText(e: ConnectError): String = when (e) {
    ConnectError.PERMISSION -> stringResource(R.string.error_permission)
    ConnectError.BLUETOOTH_OFF -> stringResource(R.string.error_bt_off)
    ConnectError.CONNECT_FAILED -> stringResource(R.string.error_connect_failed)
    ConnectError.LISTEN_FAILED -> stringResource(R.string.error_listen_failed)
}

// ============================================================================ Buat Room

@Composable
fun CreateRoomScreen(onBack: () -> Unit, onLobby: () -> Unit) {
    val container = LocalAppContainer.current
    var bluetoothChosen by rememberSaveable { mutableStateOf(false) }
    AppScaffold(stringResource(R.string.room_create_title), onBack = { if (bluetoothChosen) bluetoothChosen = false else onBack() }) { padding ->
        ScrollColumn(padding) {
            if (!bluetoothChosen) {
                Text(stringResource(R.string.transport_title), style = MaterialTheme.typography.titleLarge)
                ChoiceCard(
                    Icons.Filled.Wifi, stringResource(R.string.transport_lan_title), stringResource(R.string.transport_lan_body),
                    MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        container.bankRepository.bankOrNull?.let { container.room.hostRoom(it, TransportKind.LAN) }
                        onLobby()
                    },
                    badge = stringResource(R.string.transport_recommended),
                )
                ChoiceCard(
                    Icons.Filled.Bluetooth, stringResource(R.string.transport_bt_title), stringResource(R.string.transport_bt_body),
                    MaterialTheme.colorScheme.tertiaryContainer, onClick = { bluetoothChosen = true },
                )
            } else {
                BluetoothGate(BtAction.HOST) {
                    LaunchedEffect(Unit) {
                        container.bankRepository.bankOrNull?.let { container.room.hostRoom(it, TransportKind.BLUETOOTH) }
                        onLobby()
                    }
                    LoadingState(stringResource(R.string.loading))
                }
            }
        }
    }
}

// ============================================================================ Gabung Room

@Composable
fun JoinRoomScreen(onBack: () -> Unit, onLobby: () -> Unit) {
    val coordinator = LocalAppContainer.current.room
    val leave = { coordinator.endSession(); onBack() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val (_, state) = rememberRoom()
    val status by coordinator.status.collectAsStateWithLifecycle()
    LaunchedEffect(state?.phase) { if (state?.phase == RoomPhase.LOBBY) onLobby() }
    BackHandler(onBack = leave)
    AppScaffold(stringResource(R.string.room_join_title), onBack = leave) { padding ->
        ScrollColumn(padding) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_wifi)) },
                    icon = { Icon(Icons.Filled.Wifi, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_bluetooth)) },
                    icon = { Icon(Icons.Filled.Bluetooth, null) })
            }
            JoinStatus(status, state?.phase, state?.failure, state?.transport)
            if (tab == 0) LanJoinContent(enabled = status !is ConnectStatus.Connecting)
            else BluetoothGate(BtAction.CONNECT_PAIRED) { BtJoinContent(enabled = status !is ConnectStatus.Connecting) }
        }
    }
}

@Composable
private fun JoinStatus(status: ConnectStatus, phase: RoomPhase?, failure: id.eduduel.app.net.room.RoomFailure?, transport: TransportKind?) {
    when {
        status is ConnectStatus.Connecting -> InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.join_connecting, status.target), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (transport == TransportKind.BLUETOOTH) Text(stringResource(R.string.join_connecting_bt_hint), style = MaterialTheme.typography.bodySmall)
        }
        status is ConnectStatus.Connected && phase == RoomPhase.HANDSHAKE -> InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.join_waiting_host), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        status is ConnectStatus.Error -> InfoCard { Text(connectErrorText(status.kind), color = MaterialTheme.colorScheme.error) }
        phase == RoomPhase.FAILED -> InfoCard { Text(failureText(failure), color = MaterialTheme.colorScheme.error) }
        else -> Unit
    }
}

private data class LanTarget(val host: String, val port: Int, val label: String)

@Composable
private fun LanJoinContent(enabled: Boolean) {
    val container = LocalAppContainer.current
    val rooms = remember { mutableStateListOf<LanRoom>() }
    var searching by remember { mutableStateOf(true) }
    var manual by rememberSaveable { mutableStateOf("") }
    var manualError by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableStateOf<LanTarget?>(null) }

    LaunchedEffect(Unit) {
        try {
            container.lan.discover().collect { list ->
                rooms.clear(); rooms.addAll(list)
            }
        } catch (_: Exception) {
        } finally {
            searching = false
        }
    }

    Text(stringResource(R.string.lan_join_hint), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.lan_rooms_title), style = MaterialTheme.typography.titleMedium)
    if (rooms.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (searching) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(stringResource(if (searching) R.string.lan_searching else R.string.lan_rooms_empty), style = MaterialTheme.typography.bodyMedium)
        }
        if (searching) Text(stringResource(R.string.lan_rooms_empty), style = MaterialTheme.typography.bodySmall)
    }
    rooms.forEach { r ->
        val press = id.eduduel.app.presentation.components.rememberPressSource()
        OutlinedCard(onClick = id.eduduel.app.presentation.components.playfulClick { target = LanTarget(r.host, r.port, r.hostNickname) },
            interactionSource = press, enabled = enabled,
            modifier = Modifier.fillMaxWidth().playfulPress(press)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.lan_room_of, r.hostNickname)) },
                supportingContent = { Text(r.host) },
                leadingContent = { Icon(Icons.Filled.MeetingRoom, null) },
            )
        }
    }

    Text(stringResource(R.string.lan_manual_title), style = MaterialTheme.typography.titleMedium)
    fun submitManual() {
        val parsed = LanController.parseAddress(manual)
        if (parsed == null) manualError = true else target = LanTarget(parsed.first, parsed.second, parsed.first)
    }
    OutlinedTextField(
        value = manual,
        onValueChange = { manual = it.filter { c -> c.isDigit() || c == '.' || c == ':' }.take(21); manualError = false },
        label = { Text(stringResource(R.string.lan_manual_label)) },
        placeholder = { Text("192.168.43.1") },
        isError = manualError,
        supportingText = if (manualError) ({ Text(stringResource(R.string.lan_manual_invalid)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { submitManual() }),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = ::submitManual, enabled = enabled && manual.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.lan_connect))
    }

    target?.let { t ->
        RoomCodeDialog(
            onDismiss = { target = null },
            onConfirm = { code ->
                target = null
                container.bankRepository.bankOrNull?.let { container.room.joinLan(it, t.host, t.port, code, t.label) }
            },
        )
    }
}

@Composable
private fun RoomCodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    fun submit() {
        if (code.length == 4 && code.all(Char::isDigit)) onConfirm(code) else error = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lan_code_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.lan_code_body))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(4); error = false },
                    label = { Text(stringResource(R.string.lan_code_label)) },
                    isError = error,
                    supportingText = if (error) ({ Text(stringResource(R.string.lan_code_invalid)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    textStyle = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = ::submit) { Text(stringResource(R.string.lan_connect)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun BtJoinContent(enabled: Boolean) {
    val container = LocalAppContainer.current
    val bt = container.bluetooth
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var paired by remember { mutableStateOf(bt.bondedDevices()) }
    val found = remember { mutableStateListOf<BtDevice>() }
    var scanning by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanAsked by rememberSaveable { mutableStateOf(false) }
    var showScanRationale by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        paired = bt.bondedDevices()
        onPauseOrDispose { scanJob?.cancel(); scanning = false }
    }

    fun startScan() {
        scanJob?.cancel()
        found.clear()
        scanning = true
        scanJob = scope.launch {
            try {
                bt.discover().collect { d -> if (found.none { it.address == d.address } && paired.none { it.address == d.address }) found += d }
            } catch (_: Exception) {
            } finally {
                scanning = false
            }
        }
    }
    val scanPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scanAsked = true
        if (bt.hasPermissions(BtAction.SCAN)) startScan()
    }
    fun connect(d: BtDevice) {
        scanJob?.cancel()
        container.bankRepository.bankOrNull?.let { container.room.joinBluetooth(it, d) }
    }

    Text(stringResource(R.string.join_paired_title), style = MaterialTheme.typography.titleMedium)
    if (paired.isEmpty()) Text(stringResource(R.string.join_paired_empty), style = MaterialTheme.typography.bodyMedium)
    paired.forEach { d -> DeviceRow(d, enabled) { connect(d) } }

    OutlinedButton(
        onClick = { if (bt.hasPermissions(BtAction.SCAN)) startScan() else showScanRationale = true },
        enabled = !scanning && enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null); Spacer(Modifier.width(8.dp))
        Text(if (scanning) stringResource(R.string.join_scanning) else stringResource(R.string.join_scan))
    }
    if (showScanRationale) {
        val missing = bt.missingPermissions(BtAction.SCAN)
        val permanentlyDenied = scanAsked && activity != null && missing.none { activity.shouldShowRequestPermissionRationale(it) }
        PermissionCard(
            body = stringResource(if (isLegacyBluetoothPermissions) R.string.bt_perm_body_scan_legacy else R.string.bt_perm_body_scan),
            permanentlyDenied = permanentlyDenied,
            onGrant = { showScanRationale = false; scanPerm.launch(missing.toTypedArray()) },
            onOpenSettings = { activity?.openAppSettings() },
        )
    }
    if (scanning || found.isNotEmpty() || scanJob != null) {
        Text(stringResource(R.string.join_found_title), style = MaterialTheme.typography.titleMedium)
        if (!scanning && found.isEmpty()) Text(stringResource(R.string.join_found_empty), style = MaterialTheme.typography.bodyMedium)
        found.forEach { d -> DeviceRow(d, enabled) { connect(d) } }
    }
}

@Composable
private fun DeviceRow(d: BtDevice, enabled: Boolean, onClick: () -> Unit) {
    val press = id.eduduel.app.presentation.components.rememberPressSource()
    OutlinedCard(onClick = id.eduduel.app.presentation.components.playfulClick(onClick),
        interactionSource = press, enabled = enabled, modifier = Modifier.fillMaxWidth().playfulPress(press)) {
        ListItem(
            headlineContent = { Text(d.name ?: stringResource(R.string.device_unnamed)) },
            supportingContent = { Text(if (d.bonded) stringResource(R.string.device_paired) else stringResource(R.string.device_new)) },
            leadingContent = { Icon(if (d.bonded) Icons.Filled.BluetoothConnected else Icons.Filled.PhoneAndroid, null) },
        )
    }
}

/** Dipakai Lobi: tombol agar HP Host terlihat oleh Tamu Bluetooth yang belum pernah dipasangkan. */
@Composable
fun rememberDiscoverableLauncher(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return {
        runCatching {
            launcher.launch(
                android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                        id.eduduel.app.net.transport.BluetoothController.DISCOVERABLE_SECONDS),
            )
        }
    }
}

@Composable
fun RetryHostButton(onRetry: () -> Unit) {
    PrimaryButton(stringResource(R.string.retry), onRetry)
}
