package io.rovly.pitchee.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.preferences.CorePreferences
import io.rovly.pitchee.data.preferences.PitchChartStyle
import io.rovly.pitchee.data.preferences.PitchDisplayPreferences
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.SegmentedChoiceRow
import io.rovly.pitchee.update.UpdateUiState
import kotlin.math.roundToInt

/**
 * Slider position -> milliseconds. The left end allows the longest silence
 * ([PitchDisplayPreferences.MAX_GAP_MS]), the right end the shortest
 * ([PitchDisplayPreferences.MIN_GAP_MS]).
 */
private fun gapThresholdMsOf(position: Float): Int {
    val span = (PitchDisplayPreferences.MAX_GAP_MS - PitchDisplayPreferences.MIN_GAP_MS).toFloat()
    val raw = PitchDisplayPreferences.MAX_GAP_MS - position.coerceIn(0f, 1f) * span
    return raw.roundToInt()
        .coerceIn(PitchDisplayPreferences.MIN_GAP_MS, PitchDisplayPreferences.MAX_GAP_MS)
}

private fun gapSensitivityOf(gapMs: Int): Float {
    val span = (PitchDisplayPreferences.MAX_GAP_MS - PitchDisplayPreferences.MIN_GAP_MS).toFloat()
    return (
        (PitchDisplayPreferences.MAX_GAP_MS - gapMs).toFloat() / span
        ).coerceIn(0f, 1f)
}

@Composable
private fun ThresholdSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}


@Composable
internal fun SettingsScreen(
    updateState: UpdateUiState,
    autoCheckUpdates: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val context = LocalContext.current
    val languageCode = LocalConfiguration.current.locales[0].language
    val preferences = remember(context) { PassagePreferences(context) }
    val voicePreferences = remember(context) { VoicePreferencePreferences(context) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0" }
    }
    var voicePreference by remember {
        mutableStateOf(voicePreferences.preference())
    }
    var source by remember { mutableStateOf(preferences.source()) }
    var officialIndex by remember { mutableIntStateOf(preferences.officialIndex()) }
    var customText by remember { mutableStateOf(preferences.customText()) }
    var customSaved by remember { mutableStateOf(false) }
    val corePreferences = remember(context) { CorePreferences(context) }
    val pitchDisplayPreferences = remember(context) { PitchDisplayPreferences(context) }
    var pitchStyle by remember { mutableStateOf(pitchDisplayPreferences.style()) }
    var threads by remember { mutableIntStateOf(corePreferences.threads()) }
    var minF0Hz by remember { mutableStateOf(corePreferences.minF0Hz()) }
    var maxF0Hz by remember { mutableStateOf(corePreferences.maxF0Hz()) }
    var minConfidence by remember { mutableStateOf(corePreferences.minConfidence()) }
    var gapSensitivity by remember {
        mutableStateOf(gapSensitivityOf(pitchDisplayPreferences.gapThresholdMs()))
    }
    var f0Rate by remember { mutableIntStateOf(pitchDisplayPreferences.f0SampleRate()) }
    val language = if (languageCode.startsWith("en")) "en" else "zh"
    val uriHandler = LocalUriHandler.current
    val repositoryUrl = stringResource(R.string.settings_repository_url)
    val pitcheeCoreUrl = stringResource(R.string.settings_pitchee_core_url)
    val lvyZhanUrl = stringResource(R.string.settings_author_lvy_url)
    val ryoFengUrl = stringResource(R.string.settings_author_ryo_url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        ScreenTitle(title = stringResource(R.string.settings_title))
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        SegmentedChoiceRow(
            options = listOf(AppLanguage.LANGUAGE_ZH, AppLanguage.LANGUAGE_EN),
            selected = if (language == "en") AppLanguage.LANGUAGE_EN else AppLanguage.LANGUAGE_ZH,
            onSelected = { AppLanguage.set(context, it) },
            label = { tag ->
                stringResource(
                    if (tag == AppLanguage.LANGUAGE_ZH) {
                        R.string.language_chinese
                    } else {
                        R.string.language_english
                    },
                )
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_voice_goal),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        SegmentedChoiceRow(
            options = VoicePreference.entries,
            selected = voicePreference,
            onSelected = { option ->
                voicePreference = option
                voicePreferences.setPreference(option)
            },
            label = { option ->
                stringResource(
                    when (option) {
                        VoicePreference.FEMININE -> R.string.voice_profile_feminine_title
                        VoicePreference.MASCULINE -> R.string.voice_profile_masculine_title
                    },
                )
            },
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_f0_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = {
                    corePreferences.reset()
                    threads = corePreferences.threads()
                    minF0Hz = corePreferences.minF0Hz()
                    maxF0Hz = corePreferences.maxF0Hz()
                    minConfidence = corePreferences.minConfidence()
                    pitchDisplayPreferences.resetGapThresholdMs()
                    gapSensitivity = gapSensitivityOf(pitchDisplayPreferences.gapThresholdMs())
                    pitchDisplayPreferences.resetF0SampleRate()
                    f0Rate = pitchDisplayPreferences.f0SampleRate()
                },
            ) {
                Text(stringResource(R.string.settings_f0_reset))
            }
        }
        Spacer(Modifier.height(8.dp))
        SegmentedChoiceRow(
            options = PitchChartStyle.entries,
            selected = pitchStyle,
            onSelected = { style ->
                pitchStyle = style
                pitchDisplayPreferences.setStyle(style)
            },
            label = { style ->
                stringResource(
                    when (style) {
                        PitchChartStyle.CURVE -> R.string.settings_f0_chart_curve
                        PitchChartStyle.DOTS -> R.string.settings_f0_chart_dots
                    },
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        ThresholdSlider(
            label = stringResource(R.string.settings_f0_min),
            valueLabel = stringResource(R.string.settings_f0_hz, minF0Hz.roundToInt()),
            value = minF0Hz,
            range = CorePreferences.MIN_F0_HZ..(CorePreferences.MAX_F0_HZ - CorePreferences.F0_GAP_HZ),
            steps = 0,
            onValueChange = {
                val rounded = it.roundToInt().toFloat()
                minF0Hz = rounded
                corePreferences.setMinF0Hz(rounded)
            },
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_f0_max),
            valueLabel = stringResource(R.string.settings_f0_hz, maxF0Hz.roundToInt()),
            value = maxF0Hz,
            range = CorePreferences.MIN_F0_HZ..CorePreferences.MAX_F0_HZ,
            steps = 0,
            onValueChange = {
                val rounded = it.roundToInt().toFloat()
                maxF0Hz = rounded
                corePreferences.setMaxF0Hz(rounded)
            },
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_f0_confidence),
            valueLabel = stringResource(R.string.settings_f0_ratio, minConfidence),
            value = minConfidence,
            range = CorePreferences.MIN_CONFIDENCE..CorePreferences.MAX_CONFIDENCE,
            steps = 0,
            onValueChange = {
                minConfidence = it
                corePreferences.setMinConfidence(it)
            },
        )
        // Reads as sensitivity: further right means a shorter silence is enough to
        // break the trace, so the millisecond value shown goes down.
        ThresholdSlider(
            label = stringResource(R.string.settings_f0_gap),
            valueLabel = stringResource(
                R.string.settings_f0_ms,
                gapThresholdMsOf(gapSensitivity),
            ),
            value = gapSensitivity,
            range = 0f..1f,
            steps = 0,
            onValueChange = { value ->
                gapSensitivity = value
                pitchDisplayPreferences.setGapThresholdMs(gapThresholdMsOf(value))
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_performance_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        ThresholdSlider(
            label = stringResource(R.string.settings_f0_sample_rate),
            valueLabel = stringResource(R.string.settings_f0_rate_value, f0Rate),
            value = f0Rate.toFloat(),
            range = PitchDisplayPreferences.MIN_F0_RATE.toFloat()..
                PitchDisplayPreferences.MAX_F0_RATE.toFloat(),
            steps = 0,
            onValueChange = { value ->
                val rounded = value.roundToInt()
                f0Rate = rounded
                pitchDisplayPreferences.setF0SampleRate(rounded)
            },
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_threads),
            valueLabel = threads.toString(),
            value = threads.toFloat(),
            range = CorePreferences.MIN_THREADS.toFloat()..CorePreferences.maxThreads().toFloat(),
            steps = (CorePreferences.maxThreads() - CorePreferences.MIN_THREADS - 1).coerceAtLeast(0),
            onValueChange = {
                val rounded = it.roundToInt()
                threads = rounded
                corePreferences.setThreads(rounded)
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_passages),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        SegmentedChoiceRow(
            options = PassageSource.entries,
            selected = source,
            onSelected = { option ->
                source = option
                preferences.setSource(option)
                if (option == PassageSource.CUSTOM) customSaved = false
            },
            label = { option ->
                stringResource(
                    when (option) {
                        PassageSource.OFFICIAL -> R.string.passage_source_official
                        PassageSource.CUSTOM -> R.string.passage_source_custom
                    },
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        if (source == PassageSource.OFFICIAL) {
            readingPassages.forEachIndexed { index, passage ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            officialIndex = index
                            preferences.setOfficialIndex(index)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = officialIndex == index,
                        onClick = {
                            officialIndex = index
                            preferences.setOfficialIndex(index)
                        },
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            text = passage.title(context),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = passage.text(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        } else {
            TextField(
                value = customText,
                onValueChange = {
                    customText = it
                    customSaved = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.passage_source_custom)) },
                minLines = 4,
                maxLines = 10,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    preferences.setCustomText(customText)
                    customText = preferences.customText()
                    customSaved = true
                },
                enabled = customText.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.save_passage))
            }
            val savedCustomText = preferences.customText()
            if (customSaved || (savedCustomText.isNotBlank() && customText == savedCustomText)) {
                Text(
                    text = stringResource(R.string.custom_passage_active),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_app),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_current_version),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onCheckUpdates,
                enabled = updateState !is UpdateUiState.Checking &&
                    updateState !is UpdateUiState.Downloading,
            ) {
                if (updateState is UpdateUiState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.settings_check_updates))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_updates),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_auto_updates_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoCheckUpdates,
                onCheckedChange = onAutoCheckChange,
            )
        }
        when (val state = updateState) {
            is UpdateUiState.UpToDate -> UpdateStatusText(
                stringResource(R.string.update_up_to_date),
            )
            is UpdateUiState.Available -> UpdateStatusText(
                stringResource(R.string.update_available, state.release.displayName),
            )
            is UpdateUiState.Downloading -> UpdateStatusText(
                stringResource(R.string.update_downloading),
            )
            is UpdateUiState.ReadyToInstall -> UpdateStatusText(
                stringResource(R.string.update_ready),
            )
            is UpdateUiState.Error -> UpdateStatusText(
                text = state.message,
                isError = true,
            )
            UpdateUiState.Idle,
            UpdateUiState.Checking,
            -> Unit
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_authors),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        AuthorRow(
            name = "Lvy Zhan",
            profileUrl = lvyZhanUrl,
            avatarRes = R.drawable.author_lvy_zhan,
            onClick = { uriHandler.openUri(lvyZhanUrl) },
        )
        Spacer(Modifier.height(10.dp))
        AuthorRow(
            name = "Ryo Feng",
            profileUrl = ryoFengUrl,
            avatarRes = R.drawable.author_ryo_feng,
            onClick = { uriHandler.openUri(ryoFengUrl) },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_repository),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_repository_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = repositoryUrl,
            modifier = Modifier.clickable { uriHandler.openUri(repositoryUrl) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_open_source),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_open_source_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        listOf(
            R.string.settings_license_pitchee_core,
            R.string.settings_license_swift_f0,
            R.string.settings_license_silero_vad,
            R.string.settings_license_speechbrain,
            R.string.settings_license_ncnn,
            R.string.settings_license_androidx,
            R.string.settings_license_markwon,
            R.string.settings_license_material_icons,
        ).forEach { license ->
            Text(
                text = stringResource(license),
                modifier = Modifier.padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = pitcheeCoreUrl,
            modifier = Modifier.clickable { uriHandler.openUri(pitcheeCoreUrl) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AuthorRow(
    name: String,
    profileUrl: String,
    avatarRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(avatarRes),
            contentDescription = name,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = profileUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UpdateStatusText(
    text: String,
    isError: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
