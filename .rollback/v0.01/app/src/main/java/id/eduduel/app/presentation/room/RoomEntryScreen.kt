package id.eduduel.app.presentation.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.eduduel.app.R
import id.eduduel.app.core.NicknameValidation
import id.eduduel.app.core.NicknameValidator
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.NicknameField
import id.eduduel.app.presentation.components.PrimaryButton
import id.eduduel.app.presentation.components.ScrollColumn
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Langkah wajib sebelum SETIAP pertandingan: isi/edit nickname, lalu pilih Buat Room atau Gabung.
 * Nama terakhir (DataStore lokal) hanya nilai awal dan selalu dapat diedit.
 */
@Composable
fun RoomEntryScreen(onBack: () -> Unit, onCreate: () -> Unit, onJoin: () -> Unit, onDemo: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showError by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        container.room.endSession() // pertandingan baru selalu dimulai bersih
        if (name == null) name = container.settings.settings.first().lastNickname.orEmpty()
    }
    AppScaffold(stringResource(R.string.home_duel_title), onBack) { padding ->
        ScrollColumn(padding) {
            val current = name ?: ""
            if (step == 0) {
                NameStep(
                    value = current,
                    onValueChange = { name = it; showError = false },
                    showError = showError,
                    onContinue = {
                        when (val v = NicknameValidator.validate(current)) {
                            is NicknameValidation.Valid -> {
                                name = v.value
                                container.room.setNickname(v.value)
                                scope.launch { container.settings.saveLastNickname(v.value) }
                                step = 1
                            }
                            is NicknameValidation.Invalid -> showError = true
                        }
                    },
                )
            } else {
                RoleStep(
                    nickname = current,
                    onEditName = { step = 0 },
                    onCreate = onCreate,
                    onJoin = onJoin,
                    onDemo = {
                        container.bankRepository.bankOrNull?.let { container.room.startDemo(it) }
                        onDemo()
                    },
                )
            }
        }
    }
}

@Composable
fun NameStep(value: String, onValueChange: (String) -> Unit, showError: Boolean, onContinue: () -> Unit) {
    Text(stringResource(R.string.duel_name_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.duel_name_body), style = MaterialTheme.typography.bodyMedium)
    NicknameField(value, onValueChange, showError, onDone = onContinue)
    PrimaryButton(stringResource(R.string.duel_name_continue), onContinue)
}

@Composable
fun RoleStep(nickname: String, onEditName: () -> Unit, onCreate: () -> Unit, onJoin: () -> Unit, onDemo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.duel_playing_as), style = MaterialTheme.typography.labelLarge)
                Text(nickname, style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = onEditName) {
                Icon(Icons.Filled.Edit, null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.change_name))
            }
        }
    }
    ChoiceCard(Icons.Filled.AddCircle, stringResource(R.string.room_create_title), stringResource(R.string.room_create_body),
        MaterialTheme.colorScheme.primaryContainer, onCreate)
    ChoiceCard(Icons.Filled.Groups, stringResource(R.string.room_join_title), stringResource(R.string.room_join_body),
        MaterialTheme.colorScheme.tertiaryContainer, onJoin)
    Text(stringResource(R.string.room_role_note), style = MaterialTheme.typography.bodySmall)
    ChoiceCard(Icons.Filled.SmartToy, stringResource(R.string.room_demo_title), stringResource(R.string.room_demo_body),
        MaterialTheme.colorScheme.surfaceContainerHigh, onDemo)
}

@Composable
fun ChoiceCard(icon: ImageVector, title: String, body: String, color: Color, onClick: () -> Unit, badge: String? = null) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null) id.eduduel.app.presentation.components.RoleBadge(badge, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                }
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NameStepPreview() {
    EduDuelTheme { Column(Modifier.padding(16.dp)) { NameStep("R", {}, showError = true, onContinue = {}) } }
}

@Preview(showBackground = true)
@Composable
private fun RoleStepPreview() {
    EduDuelTheme { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { RoleStep("Rina ⚡", {}, {}, {}, {}) } }
}
