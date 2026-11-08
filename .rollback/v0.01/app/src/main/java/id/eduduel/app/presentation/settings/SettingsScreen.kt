package id.eduduel.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.BuildConfig
import id.eduduel.app.R
import id.eduduel.app.data.bank.BankState
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.data.settings.SettingsRepository
import id.eduduel.app.presentation.components.AppScaffold
import id.eduduel.app.presentation.components.InfoCard
import id.eduduel.app.presentation.components.ScrollColumn
import id.eduduel.app.presentation.navigation.LocalAppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.settings
    val settings by repo.settings.collectAsStateWithLifecycle(AppSettings())
    val bankState by container.bankRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val nameCleared = stringResource(R.string.settings_clear_name_done)
    val historyCleared = stringResource(R.string.settings_clear_history_done)

    AppScaffold(stringResource(R.string.settings_title), onBack, snackbarHostState = snackbar) { padding ->
        ScrollColumn(padding) {
            Header(stringResource(R.string.settings_text_size))
            val labels = listOf(R.string.settings_text_small, R.string.settings_text_normal, R.string.settings_text_large, R.string.settings_text_xlarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRepository.TEXT_SCALES.forEachIndexed { i, scale ->
                    FilterChip(
                        selected = settings.textScale == scale,
                        onClick = { scope.launch { repo.setTextScale(scale) } },
                        label = { Text(stringResource(labels[i])) },
                        modifier = Modifier.heightIn(min = 40.dp),
                    )
                }
            }
            HorizontalDivider()
            ToggleRow(stringResource(R.string.settings_sound), stringResource(R.string.settings_sound_body), settings.soundEnabled) {
                scope.launch { repo.setSound(it) }
            }
            ToggleRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_body), settings.hapticsEnabled) {
                scope.launch { repo.setHaptics(it) }
            }
            HorizontalDivider()
            Header(stringResource(R.string.settings_data))
            ToggleRow(stringResource(R.string.settings_remember_name), stringResource(R.string.settings_remember_name_body), settings.rememberLastNickname) {
                scope.launch { repo.setRememberNickname(it) }
            }
            OutlinedButton(
                onClick = { scope.launch { repo.clearLastNickname(); snackbar.showSnackbar(nameCleared) } },
                enabled = settings.lastNickname != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_clear_name)) }
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_clear)) }
            HorizontalDivider()
            Header(stringResource(R.string.settings_about))
            InfoCard {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.logo_duelmu),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(96.dp),
                )
                Text(stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME))
                (bankState as? BankState.Ready)?.bank?.let { b ->
                    Text(stringResource(R.string.settings_bank_version, b.versionName, b.questions.size))
                    Text(stringResource(R.string.settings_bank_hash, b.bankSha256.take(16)), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.settings_offline_note), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_privacy)) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { container.history.clear(); snackbar.showSnackbar(historyCleared) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    AppScaffold(stringResource(R.string.privacy_title), onBack) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
