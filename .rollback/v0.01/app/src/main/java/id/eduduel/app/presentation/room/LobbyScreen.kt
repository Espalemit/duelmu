package id.eduduel.app.presentation.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.R
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.Subject
import id.eduduel.app.net.protocol.MAX_ROOM_PLAYERS
import id.eduduel.app.net.room.ActiveRoom
import id.eduduel.app.net.room.ConnectStatus
import id.eduduel.app.net.room.LanHostInfo
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.RoomPlayer
import id.eduduel.app.net.room.RoomRole
import id.eduduel.app.net.room.RoomState
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.net.transport.LocalAddress
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.ConfigSummary
import id.eduduel.app.presentation.components.InfoCard
import id.eduduel.app.presentation.components.LoadingState
import id.eduduel.app.presentation.components.PrimaryButton
import id.eduduel.app.presentation.components.RoleBadge
import id.eduduel.app.presentation.components.ScrollColumn
import id.eduduel.app.presentation.components.SubjectPicker
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme
import id.eduduel.app.presentation.theme.LocalFeedbackColors
import kotlinx.coroutines.launch

@Composable
fun LobbyScreen(onExit: () -> Unit, onMatchStarted: () -> Unit) {
    val container = LocalAppContainer.current
    val coordinator = container.room
    val (room, state) = rememberRoom()
    val status by coordinator.status.collectAsStateWithLifecycle()
    val lanHost by coordinator.lanHost.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var kickTarget by remember { mutableStateOf<RoomPlayer?>(null) }
    val leave = { coordinator.endSession(); onExit() }
    val makeDiscoverable = rememberDiscoverableLauncher()

    LaunchedEffect(room) { if (room == null) onExit() }
    LaunchedEffect(state?.phase) { if (state?.inMatch == true) onMatchStarted() }
    RoomNotices(state, snackbar)
    BackHandler { confirmLeave = true }

    AppScaffold(stringResource(R.string.lobby_title), onBack = { confirmLeave = true }, snackbarHostState = snackbar) { padding ->
        val st = state
        if (st == null || room == null) {
            LoadingState(stringResource(R.string.loading))
            return@AppScaffold
        }
        ScrollColumn(padding) {
            if (st.phase == RoomPhase.FAILED || st.phase == RoomPhase.CANCELLED) {
                InfoCard {
                    Text(stringResource(R.string.fail_title), style = MaterialTheme.typography.titleLarge)
                    Text(failureText(st.failure ?: id.eduduel.app.net.room.RoomFailure.HOST_LEFT))
                    PrimaryButton(stringResource(R.string.go_home), leave)
                }
                return@ScrollColumn
            }
            val err = status as? ConnectStatus.Error
            if (err != null && st.role == RoomRole.HOST) {
                InfoCard {
                    Text(connectErrorText(err.kind), color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = {
                        container.bankRepository.bankOrNull?.let { coordinator.hostRoom(it, st.transport, st.config) }
                    })
                }
            }
            LobbyContent(
                state = st,
                bank = container.bankRepository.bankOrNull,
                lanHost = lanHost,
                bluetoothName = remember(st.transport) { if (st.transport == TransportKind.BLUETOOTH) container.bluetooth.localName() else null },
                onRefreshIp = coordinator::refreshLanAddresses,
                onMakeDiscoverable = makeDiscoverable,
                onConfigChange = { cfg -> (room as? ActiveRoom.Host)?.session?.updateConfig(cfg) },
                onRename = { renaming = true },
                onReady = { (room as? ActiveRoom.Guest)?.session?.setReady(!st.me.ready) },
                onStart = { (room as? ActiveRoom.Host)?.session?.startMatch() },
                onKick = { kickTarget = it },
                onLeave = { confirmLeave = true },
            )
        }
    }
    if (renaming && state != null) {
        RenameDialog(
            initial = state.me.nickname,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                coordinator.setNickname(name)
                scope.launch { container.settings.saveLastNickname(name) }
            },
        )
    }
    kickTarget?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.lobby_kick),
            body = stringResource(R.string.lobby_kick_confirm, p.nickname),
            confirm = stringResource(R.string.lobby_kick),
            onConfirm = { kickTarget = null; (room as? ActiveRoom.Host)?.session?.kick(p.sessionId) },
            onDismiss = { kickTarget = null },
        )
    }
    if (confirmLeave) {
        val host = state?.role == RoomRole.HOST
        ConfirmDialog(
            title = stringResource(if (host) R.string.lobby_close_room else R.string.lobby_leave),
            body = stringResource(if (host) R.string.lobby_close_body else R.string.lobby_leave_body),
            confirm = stringResource(R.string.leave),
            onConfirm = { confirmLeave = false; leave() },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
fun LobbyContent(
    state: RoomState,
    bank: QuestionBank?,
    lanHost: LanHostInfo?,
    bluetoothName: String?,
    onRefreshIp: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onConfigChange: (MatchConfig) -> Unit,
    onRename: () -> Unit,
    onReady: () -> Unit,
    onStart: () -> Unit,
    onKick: (RoomPlayer) -> Unit,
    onLeave: () -> Unit,
) {
    val isHost = state.role == RoomRole.HOST
    RoomInfoCard(state, lanHost, bluetoothName, onRefreshIp, onMakeDiscoverable)

    Text(stringResource(R.string.lobby_players_title, state.players.size, MAX_ROOM_PLAYERS), style = MaterialTheme.typography.titleLarge)
    state.players.forEach { p ->
        PlayerCard(p, isMe = p.sessionId == state.me.sessionId, canKick = isHost && !p.isHost && state.transport != TransportKind.DEMO, onKick = { onKick(p) })
    }
    repeat((MAX_ROOM_PLAYERS - state.players.size).coerceAtLeast(0)) { EmptySlot() }

    OutlinedButton(onClick = onRename, enabled = state.canEditName, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.change_name))
    }
    if (!state.canEditName) Text(stringResource(R.string.lobby_change_name_locked), style = MaterialTheme.typography.bodySmall)

    Text(stringResource(R.string.lobby_rules_title), style = MaterialTheme.typography.titleLarge)
    if (isHost) {
        Text(stringResource(R.string.lobby_rules_host_hint), style = MaterialTheme.typography.bodyMedium)
        SubjectPicker(bank, state.config, onConfigChange, enabled = state.phase == RoomPhase.LOBBY)
    } else {
        Text(stringResource(R.string.lobby_rules_guest_hint), style = MaterialTheme.typography.bodyMedium)
        InfoCard { ConfigSummary(bank, state.config) }
    }

    if (isHost) {
        val guests = state.guests
        PrimaryButton(stringResource(R.string.lobby_start), onStart, enabled = state.canStart)
        when {
            guests.isEmpty() -> Text(stringResource(R.string.lobby_start_need_players), style = MaterialTheme.typography.bodyMedium)
            !state.canStart -> Text(stringResource(R.string.lobby_start_need_ready, guests.count { it.ready }, guests.size), style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        val ready = state.me.ready
        Button(
            onClick = onReady,
            enabled = state.phase == RoomPhase.LOBBY,
            colors = if (ready) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary) else ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(if (ready) R.string.lobby_cancel_ready else R.string.lobby_press_ready), style = MaterialTheme.typography.titleMedium)
        }
        if (ready) Text(stringResource(R.string.lobby_waiting_host_start), style = MaterialTheme.typography.bodyMedium)
    }
    TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (isHost) R.string.lobby_close_room else R.string.lobby_leave))
    }
}

@Composable
private fun RoomInfoCard(state: RoomState, lanHost: LanHostInfo?, bluetoothName: String?, onRefreshIp: () -> Unit, onMakeDiscoverable: () -> Unit) {
    val isHost = state.role == RoomRole.HOST
    when (state.transport) {
        TransportKind.DEMO -> InfoCard { Text(stringResource(R.string.lobby_demo_note), style = MaterialTheme.typography.bodyMedium) }
        TransportKind.LAN -> if (isHost) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.lan_host_code, state.roomCode ?: "----"), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.lan_host_code_hint), style = MaterialTheme.typography.bodySmall)
                    LanAddresses(lanHost?.addresses.orEmpty(), lanHost?.port, onRefreshIp)
                    Text(stringResource(R.string.lan_host_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            state.hostLabel?.let { InfoCard { Text(stringResource(R.string.lobby_connected_to, it), style = MaterialTheme.typography.bodyMedium) } }
        }
        TransportKind.BLUETOOTH -> InfoCard {
            Text(stringResource(R.string.lobby_bt_note), style = MaterialTheme.typography.titleMedium)
            if (isHost) {
                Text(stringResource(R.string.bt_host_name, bluetoothName ?: stringResource(R.string.host_unknown_device_name)))
                OutlinedButton(onClick = onMakeDiscoverable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.PersonAddAlt, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.host_make_discoverable, id.eduduel.app.net.transport.BluetoothController.DISCOVERABLE_SECONDS))
                }
                Text(stringResource(R.string.host_discoverable_note), style = MaterialTheme.typography.bodySmall)
            } else {
                state.hostLabel?.let { Text(stringResource(R.string.lobby_connected_to, it)) }
            }
        }
    }
}

@Composable
private fun LanAddresses(addresses: List<LocalAddress>, port: Int?, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (addresses.isEmpty()) {
                Text(stringResource(R.string.lan_host_no_ip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            addresses.forEach { a ->
                val ip = if (port != null && port != id.eduduel.app.net.transport.LanController.DEFAULT_PORT) "${a.ip}:$port" else a.ip
                val shown = if (a.looksLikeHotspot) stringResource(R.string.lan_host_ip_hotspot, ip) else ip
                Text(stringResource(R.string.lan_host_ip, shown), style = MaterialTheme.typography.titleMedium)
            }
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.lan_refresh_ip)) }
    }
}

@Composable
private fun PlayerCard(p: RoomPlayer, isMe: Boolean, canKick: Boolean, onKick: () -> Unit) {
    val fb = LocalFeedbackColors.current
    val statusText = when {
        !p.connected -> stringResource(R.string.lobby_disconnected)
        p.isHost || p.ready -> stringResource(R.string.lobby_ready)
        else -> stringResource(R.string.lobby_not_ready)
    }
    val ready = p.connected && (p.isHost || p.ready)
    val roleText = stringResource(if (p.isHost) R.string.role_host else R.string.role_guest)
    val you = stringResource(R.string.you)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(p.nickname, if (isMe) you else null, roleText, statusText).joinToString(", ")
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isMe) RoleBadge(you)
                    RoleBadge(roleText, MaterialTheme.colorScheme.primaryContainer)
                }
                Text(p.nickname, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (ready) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty, null,
                    tint = if (ready) fb.correct else MaterialTheme.colorScheme.outline)
                Text(statusText, style = MaterialTheme.typography.labelMedium)
            }
            if (canKick) {
                IconButton(onClick = onKick) {
                    Icon(Icons.Filled.PersonRemove, contentDescription = stringResource(R.string.lobby_kick) + " " + p.nickname)
                }
            }
        }
    }
}

@Composable
private fun EmptySlot() {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.lobby_empty_slot),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun LobbyHostLanPreview() {
    val me = RoomPlayer("h", "Rina ⚡", isHost = true, ready = true)
    val st = RoomState(
        role = RoomRole.HOST, transport = TransportKind.LAN, me = me, phase = RoomPhase.LOBBY, roomCode = "4821",
        players = listOf(me, RoomPlayer("b", "Budi", false, ready = true), RoomPlayer("c", "Sari", false, ready = false)),
        config = MatchConfig(Subject.MATEMATIKA),
    )
    EduDuelTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LobbyContent(st, null, LanHostInfo(47_830, listOf(LocalAddress("192.168.43.1", "ap0"))), null, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
