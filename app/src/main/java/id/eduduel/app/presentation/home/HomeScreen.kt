package id.eduduel.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.eduduel.app.R
import id.eduduel.app.data.bank.BankState
import id.eduduel.app.presentation.components.*
import id.eduduel.app.presentation.navigation.LocalAppContainer
import id.eduduel.app.presentation.theme.EduDuelTheme

@Composable
fun HomeScreen(onSolo: () -> Unit, onDuel: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val bankState by container.bankRepository.state.collectAsStateWithLifecycle()
    HomeContent(bankState, onSolo, onDuel, onHistory, onSettings, onRetry = container.bankRepository::reload)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeContent(
    bankState: BankState,
    onSolo: () -> Unit,
    onDuel: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit = {},
    motionEnabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val motion = rememberHomeMotion(motionEnabled)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp).widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeEntrance(0, motion, Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo_duelmu), null, Modifier.size(44.dp).graphicsLayer {
                    rotationZ = motion.wave() * 2f
                    translationY = motion.wave(0.5f) * 1.5.dp.toPx()
                })
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = cs.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.WifiOff, null, Modifier.size(14.dp), tint = cs.secondary)
                        Text(stringResource(R.string.play_offline), style = MaterialTheme.typography.labelMedium,
                            color = cs.onSecondaryContainer)
                    }
                }
            }
        }
        HomeEntrance(1, motion, Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().testTag("home_hero").clip(RoundedCornerShape(28.dp)).background(
                Brush.linearGradient(listOf(cs.primaryContainer, Color(0xFFFFF0DA))), RoundedCornerShape(28.dp),
            )) {
                HomeHeroBackdrop(motion, Modifier.matchParentSize())
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp).graphicsLayer {
                                rotationZ = motion.wave() * 12f
                            }, tint = cs.primary)
                            Text(stringResource(R.string.play_hero_eyebrow), style = MaterialTheme.typography.labelLarge,
                                color = cs.onPrimaryContainer)
                        }
                        FloatingTrophy(motion)
                    }
                    Text(stringResource(R.string.play_hero_title), style = MaterialTheme.typography.headlineLarge,
                        color = cs.onPrimaryContainer, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium,
                        color = cs.onPrimaryContainer)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoleBadge(stringResource(R.string.play_round_badge), cs.surface)
                        RoleBadge(stringResource(R.string.play_friends_badge), cs.surface)
                    }
                }
            }
        }
        HomeEntrance(2, motion) {
            Text(stringResource(R.string.play_choose_mode), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
        }
        when (bankState) {
            BankState.Loading -> LoadingState(stringResource(R.string.bank_loading), Modifier.heightIn(max = 180.dp))
            is BankState.Error -> MessageState(
                stringResource(R.string.bank_error_title), stringResource(R.string.bank_error_body, bankState.message),
                isError = true, action = { GameButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } },
            )
            is BankState.Ready -> {
                HomeEntrance(3, motion, Modifier.fillMaxWidth()) {
                    HomeCard(Icons.Filled.Groups, stringResource(R.string.home_duel_title), stringResource(R.string.home_duel_body),
                        cs.primaryContainer, cs.primary, onDuel, motion, 0f)
                }
                HomeEntrance(4, motion, Modifier.fillMaxWidth()) {
                    HomeCard(Icons.Filled.School, stringResource(R.string.home_solo_title), stringResource(R.string.home_solo_body),
                        cs.secondaryContainer, cs.secondary, onSolo, motion, 1.4f)
                }
            }
        }
        HomeEntrance(5, motion, Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UtilityCard(Icons.Filled.History, stringResource(R.string.home_history_title), cs.tertiaryContainer, onHistory, motion, 0f, Modifier.weight(1f))
                UtilityCard(Icons.Filled.Settings, stringResource(R.string.home_settings_title), cs.primaryContainer, onSettings, motion, 1.4f, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.VerifiedUser, null, Modifier.size(16.dp), tint = cs.secondary)
            Text(stringResource(R.string.home_guest_note), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (bankState is BankState.Ready) {
            Surface(color = cs.surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, cs.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalLibrary, null, Modifier.size(24.dp), tint = cs.primary)
                    Column {
                        Text(stringResource(R.string.play_bank_title, bankState.bank.questions.size), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.play_bank_subjects), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(icon: ImageVector, title: String, body: String, color: Color, accent: Color, onClick: () -> Unit, motion: HomeMotion, phaseOffset: Float) {
    val source = rememberPressSource()
    Card(
        onClick = playfulClick(onClick), interactionSource = source,
        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().playfulPress(source),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(54.dp).background(color, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(30.dp).graphicsLayer {
                    translationY = motion.wave(phaseOffset) * 2.5.dp.toPx()
                    rotationZ = motion.wave(phaseOffset) * 4f
                }, tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp).graphicsLayer {
                translationX = (motion.wave(phaseOffset) + 1f) * 1.5.dp.toPx()
            }, tint = accent)
        }
    }
}

@Composable
private fun UtilityCard(icon: ImageVector, title: String, color: Color, onClick: () -> Unit, motion: HomeMotion, phaseOffset: Float, modifier: Modifier = Modifier) {
    val source = rememberPressSource()
    Card(onClick = playfulClick(onClick), interactionSource = source,
        colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(22.dp),
        modifier = modifier.playfulPress(source)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(26.dp).graphicsLayer {
                rotationZ = motion.wave(phaseOffset) * 8f
            })
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true, heightDp = 950)
@Composable
private fun HomePreview() {
    EduDuelTheme { HomeContent(BankState.Loading, {}, {}, {}, {}, motionEnabled = false) }
}
