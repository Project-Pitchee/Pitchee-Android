package io.rovly.pitchee.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.feature.knowledge.KnowledgeBaseScreen
import io.rovly.pitchee.feature.piano.PianoKeysScreen
import io.rovly.pitchee.feature.settings.SettingsScreen
import io.rovly.pitchee.feature.spectrum.RealtimeSpectrumScreen
import io.rovly.pitchee.feature.voicehealth.VoiceHealthScreen
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.ScreenTransition
import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import io.rovly.pitchee.update.UpdateUiState

private enum class MorePage {
    MENU,
    SPECTRUM,
    PIANO,
    VOICE_HEALTH,
    KNOWLEDGE_BASE,
    SETTINGS,
}

private val VoiceHealthTint = PitcheeColorTokens.InsightTeal
private val PianoTint = PitcheeColorTokens.Feminine

@Composable
internal fun MoreScreen(
    updateState: UpdateUiState,
    autoCheckUpdates: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onSpectrumChromeChange: (Boolean) -> Unit = {},
) {
    var selectedOrdinal by rememberSaveable { mutableIntStateOf(MorePage.MENU.ordinal) }
    var zStep by remember { mutableIntStateOf(0) }
    val page = MorePage.entries[selectedOrdinal]
    fun navigateTo(target: MorePage) {
        zStep += 1
        selectedOrdinal = target.ordinal
    }
    ScreenTransition(
        targetState = page,
        forward = page != MorePage.MENU,
        contentZIndex = if (page != MorePage.MENU) zStep.toFloat() else -zStep.toFloat(),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        label = "more-page",
    ) { current ->
        when (current) {
            MorePage.MENU -> MoreMenu(
                onOpenVoiceHealth = { navigateTo(MorePage.VOICE_HEALTH) },
                onOpenSpectrum = { navigateTo(MorePage.SPECTRUM) },
                onOpenPiano = { navigateTo(MorePage.PIANO) },
                onOpenKnowledgeBase = { navigateTo(MorePage.KNOWLEDGE_BASE) },
                onOpenSettings = { navigateTo(MorePage.SETTINGS) },
            )
            MorePage.VOICE_HEALTH -> VoiceHealthScreen(
                onBack = { navigateTo(MorePage.MENU) },
            )
            MorePage.SPECTRUM -> RealtimeSpectrumScreen(
                onBack = { navigateTo(MorePage.MENU) },
                onSpectrumChromeChange = onSpectrumChromeChange,
            )
            MorePage.PIANO -> PianoKeysScreen(
                onBack = { selectedOrdinal = MorePage.MENU.ordinal },
            )
            MorePage.KNOWLEDGE_BASE -> KnowledgeBaseScreen(
                onBack = { navigateTo(MorePage.MENU) },
            )
            MorePage.SETTINGS -> SettingsScreen(
                updateState = updateState,
                autoCheckUpdates = autoCheckUpdates,
                onAutoCheckChange = onAutoCheckChange,
                onCheckUpdates = onCheckUpdates,
                onBack = { navigateTo(MorePage.MENU) },
            )
        }
    }
}

@Composable
private fun MoreMenu(
    onOpenVoiceHealth: () -> Unit,
    onOpenSpectrum: () -> Unit,
    onOpenPiano: () -> Unit,
    onOpenKnowledgeBase: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            ScreenTitle(title = stringResource(R.string.more_title))
            Spacer(Modifier.height(18.dp))
            SpectrumFeatureCard(onClick = onOpenSpectrum)
            Spacer(Modifier.height(12.dp))
            KnowledgeFeatureCard(onClick = onOpenKnowledgeBase)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoreCompactCard(
                    title = stringResource(R.string.more_piano_title),
                    iconRes = R.drawable.ic_piano,
                    tint = PianoTint,
                    onClick = onOpenPiano,
                    modifier = Modifier
                        .weight(1f)
                        .height(224.dp),
                )
                MoreCompactCard(
                    title = stringResource(R.string.more_voice_health_title),
                    iconRes = R.drawable.ic_voice_health,
                    tint = VoiceHealthTint,
                    onClick = onOpenVoiceHealth,
                    modifier = Modifier
                        .weight(1f)
                        .height(224.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            MoreLinkRow(
                title = stringResource(R.string.settings_title),
                iconRes = R.drawable.ic_settings,
                onClick = onOpenSettings,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KnowledgeFeatureCard(onClick: () -> Unit) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.secondary
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(204.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.18f),
                        contentColor = accentColor,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_book),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.10f),
                        contentColor = contentColor,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.knowledge_base_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun MoreLinkRow(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(25.dp),
            )
            Spacer(Modifier.size(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun SpectrumFeatureCard(onClick: () -> Unit) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(204.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                        contentColor = accentColor,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_spectrum),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.10f),
                        contentColor = contentColor,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.spectrum_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun MoreCompactCard(
    title: String,
    iconRes: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.16f),
                    contentColor = tint,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}
