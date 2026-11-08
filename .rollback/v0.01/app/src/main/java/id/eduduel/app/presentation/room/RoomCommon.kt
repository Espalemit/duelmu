package id.eduduel.app.presentation.room

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.R
import id.eduduel.app.core.NicknameValidation
import id.eduduel.app.core.NicknameValidator
import id.eduduel.app.net.room.ActiveRoom
import id.eduduel.app.net.room.RoomFailure
import id.eduduel.app.net.room.RoomNotice
import id.eduduel.app.net.room.RoomPlayer
import id.eduduel.app.net.room.RoomState
import id.eduduel.app.net.room.Standing
import id.eduduel.app.net.transport.AdapterState
import id.eduduel.app.net.transport.BtAction
import id.eduduel.app.presentation.components.InfoCard
import id.eduduel.app.presentation.components.MessageState
import id.eduduel.app.presentation.components.NicknameField
import id.eduduel.app.presentation.components.PrimaryButton
import id.eduduel.app.presentation.components.RoleBadge
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme

/** Room aktif + state-nya (null bila tidak ada room). */
@Composable
fun rememberRoom(): Pair<ActiveRoom?, RoomState?> {
    val coordinator = LocalAppContainer.current.room
    val room by coordinator.room.collectAsStateWithLifecycle()
    val state: State<RoomState?> = room?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<RoomState?>(null) }
    return room to state.value
}

@Composable
fun failureText(f: RoomFailure?): String = when (f) {
    RoomFailure.BANK_MISMATCH -> stringResource(R.string.fail_bank_mismatch)
    RoomFailure.PROTOCOL_MISMATCH -> stringResource(R.string.fail_protocol)
    RoomFailure.ROOM_FULL -> stringResource(R.string.fail_room_full)
    RoomFailure.MATCH_IN_PROGRESS -> stringResource(R.string.fail_in_progress)
    RoomFailure.WRONG_ROOM_CODE -> stringResource(R.string.fail_wrong_code)
    RoomFailure.INVALID_SETUP -> stringResource(R.string.fail_setup)
    RoomFailure.INCONSISTENT_RESULT -> stringResource(R.string.fail_inconsistent)
    RoomFailure.HANDSHAKE_TIMEOUT -> stringResource(R.string.fail_timeout)
    RoomFailure.CONNECTION_LOST -> stringResource(R.string.fail_connection)
    RoomFailure.HOST_LEFT -> stringResource(R.string.fail_host_left)
    RoomFailure.KICKED -> stringResource(R.string.fail_kicked)
    RoomFailure.BLUETOOTH_OFF -> stringResource(R.string.fail_bt_off)
    RoomFailure.RECONNECT_FAILED -> stringResource(R.string.fail_reconnect)
    RoomFailure.RESUME_REJECTED -> stringResource(R.string.fail_resume)
    RoomFailure.NOT_ENOUGH_PLAYERS -> stringResource(R.string.fail_not_enough)
    RoomFailure.PROTOCOL_ERROR, null -> stringResource(R.string.fail_generic)
}

@Composable
fun noticeText(n: RoomNotice): String = when (n) {
    is RoomNotice.PlayerJoined -> stringResource(R.string.notice_joined, n.name)
    is RoomNotice.PlayerLeft -> stringResource(R.string.notice_left, n.name)
    is RoomNotice.PlayerDisconnected -> stringResource(R.string.notice_disconnected, n.name)
    is RoomNotice.PlayerReconnected -> stringResource(R.string.notice_reconnected, n.name)
    is RoomNotice.PlayerRenamed -> stringResource(R.string.notice_renamed, n.oldName, n.newName)
    is RoomNotice.NameInvalid -> stringResource(R.string.notice_name_invalid, n.fallback)
    RoomNotice.ConfigChanged -> stringResource(R.string.notice_config_changed)
    RoomNotice.Resumed -> stringResource(R.string.notice_resumed)
}

/** Tampilkan pemberitahuan room sebagai snackbar (sekali per [RoomState.noticeId]). */
@Composable
fun RoomNotices(state: RoomState?, snackbar: SnackbarHostState) {
    val notice = state?.notice
    val text = notice?.let { noticeText(it) }
    LaunchedEffect(state?.noticeId) { text?.let { snackbar.showSnackbar(it) } }
}

/** Papan skor ringkas untuk 2–4 pemain, urut peringkat. */
@Composable
fun ScoreBoard(standings: List<Standing>, meId: String, roundLabel: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (roundLabel != null) Text(roundLabel, style = MaterialTheme.typography.labelLarge)
            standings.forEach { st -> ScoreRow(st, isMe = st.player.sessionId == meId) }
        }
    }
}

@Composable
private fun ScoreRow(st: Standing, isMe: Boolean) {
    val you = stringResource(R.string.you)
    val off = stringResource(R.string.lobby_disconnected)
    val desc = buildString {
        append("#${st.rank} ${st.player.nickname}")
        if (isMe) append(" ($you)")
        append(", ${st.total} poin")
        if (!st.player.connected) append(", $off")
    }
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${st.rank}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(36.dp))
        Text(
            st.player.nickname + if (isMe) " ($you)" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (!st.player.connected) RoleBadge(off, MaterialTheme.colorScheme.errorContainer)
        Text("${st.total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Dialog Ubah Nama (dipakai di lobi sebelum SIAP). */
@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var showError by rememberSaveable { mutableStateOf(false) }
    fun submit() {
        when (val v = NicknameValidator.validate(value)) {
            is NicknameValidation.Valid -> onConfirm(v.value)
            is NicknameValidation.Invalid -> showError = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_dialog_title)) },
        text = { NicknameField(value, { value = it; showError = false }, showError, onDone = ::submit) },
        confirmButton = { TextButton(onClick = ::submit) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Gerbang izin & status Bluetooth. Meminta izin hanya yang dibutuhkan aksi tersebut dan versi
 * Android ini; menangani penolakan permanen (arahkan ke Pengaturan) dan pencabutan (cek ulang saat resume).
 */
@Composable
fun BluetoothGate(action: BtAction, content: @Composable () -> Unit) {
    val bt = LocalAppContainer.current.bluetooth
    val activity = LocalActivity.current
    var missing by remember { mutableStateOf(bt.missingPermissions(action)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val adapter by bt.adapterState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(action) {
        missing = bt.missingPermissions(action)
        bt.refreshState()
        onPauseOrDispose { }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        missing = bt.missingPermissions(action)
        asked = true
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { bt.refreshState() }

    when {
        !bt.isSupported -> MessageState(stringResource(R.string.fail_title), stringResource(R.string.bt_unsupported), isError = true)
        missing.isNotEmpty() -> {
            val permanentlyDenied = asked && activity != null &&
                missing.none { activity.shouldShowRequestPermissionRationale(it) }
            PermissionCard(
                body = stringResource(if (action == BtAction.SCAN) R.string.bt_perm_body_scan else R.string.bt_perm_body_connect),
                permanentlyDenied = permanentlyDenied,
                onGrant = { permLauncher.launch(missing.toTypedArray()) },
                onOpenSettings = { activity?.openAppSettings() },
            )
        }
        adapter != AdapterState.ON -> InfoCard {
            Text(stringResource(R.string.bt_off_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.bt_off_body))
            PrimaryButton(stringResource(R.string.bt_turn_on), onClick = {
                runCatching { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            })
        }
        else -> content()
    }
}

@Composable
fun PermissionCard(body: String, permanentlyDenied: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    InfoCard {
        Text(stringResource(R.string.bt_perm_title), style = MaterialTheme.typography.titleLarge)
        Text(body)
        if (permanentlyDenied) {
            Text(stringResource(R.string.bt_perm_denied), color = MaterialTheme.colorScheme.error)
            PrimaryButton(stringResource(R.string.bt_open_settings), onOpenSettings)
        } else {
            PrimaryButton(stringResource(R.string.bt_perm_grant), onGrant)
        }
    }
}

fun Activity.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

val isLegacyBluetoothPermissions: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

@Preview(showBackground = true)
@Composable
private fun ScoreBoardPreview() {
    val st = listOf(
        Standing(RoomPlayer("a", "Rina ⚡", true), 1240, 5, 1),
        Standing(RoomPlayer("b", "Budi", false), 980, 4, 2),
        Standing(RoomPlayer("c", "Sari", false, connected = false), 980, 4, 2),
        Standing(RoomPlayer("d", "Joko", false), 300, 1, 4),
    )
    EduDuelTheme { ScoreBoard(st, "b", "Soal 7 dari 10") }
}
