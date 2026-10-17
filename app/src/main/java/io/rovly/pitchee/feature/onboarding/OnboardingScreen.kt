package io.rovly.pitchee.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
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
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedRaw by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = VoicePreference.fromRaw(selectedRaw)

    BackHandler(enabled = page > 0) {
        page--
    }

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
                Spacer(Modifier.weight(1f))
                PageIndicator(
                    currentPage = page,
                    pageCount = 2,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                if (page == 0) {
                    WelcomePage()
                } else {
                    PreferencePage(
                        selected = selected,
                        onSelected = { selectedRaw = it.rawValue },
                        onBack = { page-- },
                    )
                }
            }

            Button(
                onClick = {
                    if (page == 0) {
                        page = 1
                    } else {
                        selected?.let(onFinished)
                    }
                },
                enabled = page == 0 || selected != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(
                        if (page == 0) {
                            R.string.onboarding_start_setup
                        } else {
                            R.string.onboarding_finish_setup
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        ScreenTitle(
            title = stringResource(R.string.onboarding_welcome_title),
            subtitle = stringResource(R.string.onboarding_welcome_subtitle),
            titleStyle = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(32.dp))
        OnboardingArtwork()
        Spacer(Modifier.height(30.dp))
        OnboardingFeature(
            iconRes = R.drawable.ic_pitch_analysis,
            title = stringResource(R.string.onboarding_welcome_feature_trend_title),
            description = stringResource(R.string.onboarding_welcome_feature_trend_description),
        )
        Spacer(Modifier.height(16.dp))
        OnboardingFeature(
            iconRes = R.drawable.ic_check,
            title = stringResource(R.string.onboarding_welcome_feature_privacy_title),
            description = stringResource(R.string.onboarding_welcome_feature_privacy_description),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingArtwork() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 24.dp),
            ) {
                val heights = listOf(0.28f, 0.58f, 0.88f, 0.43f, 0.72f, 1f, 0.5f, 0.8f, 0.35f, 0.66f, 0.94f, 0.4f)
                val gap = size.width / (heights.size * 2f)
                heights.forEachIndexed { index, fraction ->
                    val barWidth = gap * 0.72f
                    val x = gap * (index * 2f + 0.5f)
                    val barHeight = size.height * fraction
                    drawRoundRect(
                        color = if (index % 3 == 0) {
                            primaryColor
                        } else {
                            primaryColor.copy(alpha = 0.28f)
                        },
                        topLeft = Offset(x, (size.height - barHeight) / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
                    )
                }
            }
            Text(
                text = stringResource(R.string.onboarding_welcome_badge),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        CircleShape,
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingFeature(
    iconRes: Int,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreferencePage(
    selected: VoicePreference?,
    onSelected: (VoicePreference) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        ScreenTitle(
            title = stringResource(R.string.onboarding_preferences_title),
            subtitle = stringResource(R.string.onboarding_preferences_subtitle),
            onBack = onBack,
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

@Composable
private fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(
                        width = if (index == currentPage) 24.dp else 7.dp,
                        height = 7.dp,
                    )
                    .background(
                        color = if (index == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
