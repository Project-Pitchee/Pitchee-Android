package io.rovly.pitchee.feature.more

import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.feature.piano.PianoKeysScreen
import io.rovly.pitchee.feature.spectrum.RealtimeSpectrumScreen
import io.rovly.pitchee.feature.voicehealth.VoiceHealthScreen
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.ScreenTransition

private enum class MorePage {
    MENU,
    SPECTRUM,
    PIANO,
    VOICE_HEALTH,
}

private val VoiceHealthTint = PitcheeColorTokens.InsightTeal
private val SpectrumTint = PitcheeColorTokens.Masculine
private val PianoTint = PitcheeColorTokens.Feminine

@Composable
internal fun MoreScreen() {
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
            )
            MorePage.VOICE_HEALTH -> VoiceHealthScreen(
                onBack = { navigateTo(MorePage.MENU) },
            )
            MorePage.SPECTRUM -> RealtimeSpectrumScreen(
                onBack = { navigateTo(MorePage.MENU) },
            )
            MorePage.PIANO -> PianoKeysScreen(
                onBack = { selectedOrdinal = MorePage.MENU.ordinal },
            )
        }
    }
}

@Composable
private fun MoreMenu(
    onOpenVoiceHealth: () -> Unit,
    onOpenSpectrum: () -> Unit,
    onOpenPiano: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            ScreenTitle(title = stringResource(R.string.more_title))
            Spacer(Modifier.size(18.dp))
            MoreLinkCard(
                title = stringResource(R.string.spectrum_title),
                description = stringResource(R.string.more_spectrum_description),
                iconRes = R.drawable.ic_spectrum,
                tint = SpectrumTint,
                onClick = onOpenSpectrum,
            )
            Spacer(Modifier.size(12.dp))
            MoreLinkCard(
                title = stringResource(R.string.more_piano_title),
                description = stringResource(R.string.more_piano_description),
                iconRes = R.drawable.ic_piano,
                tint = PianoTint,
                onClick = onOpenPiano,
            )
            Spacer(Modifier.size(12.dp))
            MoreLinkCard(
                title = stringResource(R.string.more_voice_health_title),
                description = stringResource(R.string.more_voice_health_description),
                iconRes = R.drawable.ic_voice_health,
                tint = VoiceHealthTint,
                onClick = onOpenVoiceHealth,
            )
        }
    }
}

@Composable
private fun MoreLinkCard(
    title: String,
    description: String,
    iconRes: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = MaterialTheme.shapes.medium,
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
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}
