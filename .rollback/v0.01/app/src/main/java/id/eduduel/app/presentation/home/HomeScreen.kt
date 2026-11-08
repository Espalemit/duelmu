package id.eduduel.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.R
import id.eduduel.app.data.bank.BankState
import id.eduduel.app.presentation.components.LoadingState
import id.eduduel.app.presentation.components.MessageState
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme

@Composable
fun HomeScreen(onSolo: () -> Unit, onDuel: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val bankState by container.bankRepository.state.collectAsStateWithLifecycle()
    HomeContent(bankState, onSolo, onDuel, onHistory, onSettings, onRetry = container.bankRepository::reload)
}

@Composable
fun HomeContent(
    bankState: BankState,
    onSolo: () -> Unit,
    onDuel: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Logo aplikasi (kickstart/Duelmu Logo.png) menggantikan judul teks; dibaca TalkBack sebagai nama aplikasi.
        Image(
            painter = painterResource(R.drawable.logo_duelmu),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally).semantics { heading() },
        )
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.home_guest_note), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        when (bankState) {
            BankState.Loading -> LoadingState(stringResource(R.string.bank_loading), Modifier.heightIn(max = 240.dp))
            is BankState.Error -> MessageState(
                stringResource(R.string.bank_error_title),
                stringResource(R.string.bank_error_body, bankState.message),
                isError = true,
                action = { androidx.compose.material3.Button(onClick = onRetry) { Text(stringResource(R.string.retry)) } },
            )
            is BankState.Ready -> {
                val ready = true
                HomeCard(Icons.Filled.School, stringResource(R.string.home_solo_title), stringResource(R.string.home_solo_body),
                    MaterialTheme.colorScheme.secondaryContainer, ready, onSolo)
                HomeCard(Icons.Filled.Groups, stringResource(R.string.home_duel_title), stringResource(R.string.home_duel_body),
                    MaterialTheme.colorScheme.primaryContainer, ready, onDuel)
            }
        }
        HomeCard(Icons.Filled.History, stringResource(R.string.home_history_title), stringResource(R.string.home_history_body),
            MaterialTheme.colorScheme.surfaceContainerHigh, true, onHistory)
        HomeCard(Icons.Filled.Settings, stringResource(R.string.home_settings_title), stringResource(R.string.home_settings_body),
            MaterialTheme.colorScheme.surfaceContainerHigh, true, onSettings)
        if (bankState is BankState.Ready) {
            Text(
                stringResource(R.string.home_bank_info, bankState.bank.versionName, bankState.bank.questions.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeCard(icon: ImageVector, title: String, body: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    EduDuelTheme { HomeContent(BankState.Loading, {}, {}, {}, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeErrorDarkPreview() {
    EduDuelTheme(darkTheme = true) { HomeContent(BankState.Error("hash tidak cocok"), {}, {}, {}, {}) }
}
