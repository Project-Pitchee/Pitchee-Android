package io.rovly.pitchee.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.feature.settings.VoicePreference

@Composable
internal fun OnboardingScreen(
    onFinished: (VoicePreference) -> Unit,
) {
    var selectedRaw by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = VoicePreference.fromRaw(selectedRaw)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Text(
                text = "PITCHEE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                PreferencePage(
                    selected = selected,
                    onSelected = { selectedRaw = it.rawValue },
                )
            }

            Button(
                onClick = {
                    selected?.let(onFinished)
                },
                enabled = selected != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_finish_setup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PreferencePage(
    selected: VoicePreference?,
    onSelected: (VoicePreference) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        ScreenTitle(
            title = stringResource(R.string.onboarding_preferences_title),
            subtitle = stringResource(R.string.onboarding_preferences_subtitle),
            titleStyle = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(34.dp))
        Text(
            text = stringResource(R.string.onboarding_preferences_prompt),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        VoicePreference.entries.forEach { option ->
            VoicePreferenceCard(
                option = option,
                selected = selected == option,
                onClick = { onSelected(option) },
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun VoicePreferenceCard(
    option: VoicePreference,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = stringResource(
        when (option) {
            VoicePreference.MASCULINE -> R.string.voice_profile_masculine_title
            VoicePreference.FEMININE -> R.string.voice_profile_feminine_title
        },
    )
    val description = stringResource(
        when (option) {
            VoicePreference.MASCULINE -> R.string.voice_profile_masculine_description
            VoicePreference.FEMININE -> R.string.voice_profile_feminine_description
        },
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 14.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
        }
    }
}
