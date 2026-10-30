package io.rovly.pitchee.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.preferences.CorePreferences
import io.rovly.pitchee.data.preferences.PitchChartStyle
import io.rovly.pitchee.data.preferences.PitchDisplayPreferences
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.SegmentedChoiceRow
import io.rovly.pitchee.ui.theme.LocalPitcheeThemeController
import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import io.rovly.pitchee.ui.theme.PitcheeThemePalette
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

private enum class SettingsPage {
    MENU,
    LANGUAGE,
    THEME,
    VOICE_GOAL,
    PASSAGES,
    ADVANCED,
    UPDATES,
    ABOUT,
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
private fun AdvancedSettingsCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "advanced-chevron",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Surface(
                onClick = onToggle,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_advanced),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_advanced_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 260f),
                    expandFrom = Alignment.Top,
                ),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 18.dp,
                    ),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(18.dp))
                    content()
                }
            }
        }
    }
}


@Composable
internal fun SettingsScreen(
    updateState: UpdateUiState,
    autoCheckUpdates: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
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
    var selectedPageOrdinal by rememberSaveable {
        mutableIntStateOf(SettingsPage.MENU.ordinal)
    }
    val selectedPage = SettingsPage.entries[selectedPageOrdinal]
    val uriHandler = LocalUriHandler.current
    val repositoryUrl = stringResource(R.string.settings_repository_url)
    val pitcheeCoreUrl = stringResource(R.string.settings_pitchee_core_url)
    val lvyZhanUrl = stringResource(R.string.settings_author_lvy_url)
    val ryoFengUrl = stringResource(R.string.settings_author_ryo_url)
    val pageTitle = stringResource(
        when (selectedPage) {
            SettingsPage.LANGUAGE -> R.string.settings_language
            SettingsPage.THEME -> R.string.settings_theme
            SettingsPage.VOICE_GOAL -> R.string.settings_voice_goal
            SettingsPage.PASSAGES -> R.string.settings_passages
            SettingsPage.ADVANCED -> R.string.settings_advanced
            SettingsPage.UPDATES -> R.string.settings_updates_title
            SettingsPage.ABOUT -> R.string.settings_about
            SettingsPage.MENU -> R.string.settings_title
        },
    )

    BackHandler(enabled = selectedPage != SettingsPage.MENU) {
        selectedPageOrdinal = SettingsPage.MENU.ordinal
    }

    if (selectedPage == SettingsPage.MENU) {
        SettingsMenuPage(
            onOpenLanguage = {
                selectedPageOrdinal = SettingsPage.LANGUAGE.ordinal
            },
            onOpenTheme = {
                selectedPageOrdinal = SettingsPage.THEME.ordinal
            },
            onOpenVoiceGoal = {
                selectedPageOrdinal = SettingsPage.VOICE_GOAL.ordinal
            },
            onOpenPassages = {
                selectedPageOrdinal = SettingsPage.PASSAGES.ordinal
            },
            onOpenAdvanced = {
                selectedPageOrdinal = SettingsPage.ADVANCED.ordinal
            },
            onOpenUpdates = {
                selectedPageOrdinal = SettingsPage.UPDATES.ordinal
            },
            onOpenAbout = {
                selectedPageOrdinal = SettingsPage.ABOUT.ordinal
            },
            onBack = onBack,
        )
        return
    }

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
            ScreenTitle(
                title = pageTitle,
                onBack = { selectedPageOrdinal = SettingsPage.MENU.ordinal },
            )
        if (selectedPage == SettingsPage.LANGUAGE) {
            Spacer(Modifier.height(24.dp))
            LanguageSelector(
                selectedTag = AppLanguage.selectedTag(context),
                onSelected = { AppLanguage.set(context, it) },
            )
        }
        if (selectedPage == SettingsPage.THEME) {
            Spacer(Modifier.height(24.dp))
            ThemeSelector()
        }
        if (selectedPage == SettingsPage.VOICE_GOAL) {
            Spacer(Modifier.height(24.dp))
            VoiceGoalSelector(
                selected = voicePreference,
                onSelected = { option ->
                    voicePreference = option
                    voicePreferences.setPreference(option)
                },
            )
        }
        if (selectedPage == SettingsPage.ADVANCED) {
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
        }
        if (selectedPage == SettingsPage.PASSAGES) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
                label = { Text(stringResource(R.string.passage_source_custom)) },
                minLines = 4,
                maxLines = 4,
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
        }
        if (selectedPage == SettingsPage.UPDATES) {
        Spacer(Modifier.height(24.dp))
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
        }
        if (selectedPage == SettingsPage.ABOUT) {
            Spacer(Modifier.height(24.dp))
            AboutSection(
                versionName = versionName,
                repositoryUrl = repositoryUrl,
                pitcheeCoreUrl = pitcheeCoreUrl,
                lvyZhanUrl = lvyZhanUrl,
                ryoFengUrl = ryoFengUrl,
                onOpenUrl = uriHandler::openUri,
            )
        }
        }
    }
}

@Composable
private fun SettingsMenuPage(
    onOpenLanguage: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenVoiceGoal: () -> Unit,
    onOpenPassages: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: (() -> Unit)?,
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
            ScreenTitle(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
            )
            Spacer(Modifier.height(20.dp))
            SettingsMenuCard(
                title = stringResource(R.string.settings_language),
                iconRes = R.drawable.ic_language,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onOpenLanguage,
            )
            Spacer(Modifier.height(10.dp))
            SettingsMenuCard(
                title = stringResource(R.string.settings_theme),
                iconRes = R.drawable.ic_palette,
                tint = MaterialTheme.colorScheme.tertiary,
                onClick = onOpenTheme,
            )
            Spacer(Modifier.height(10.dp))
            SettingsMenuCard(
                title = stringResource(R.string.settings_voice_goal),
                iconRes = R.drawable.ic_voice_goal,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onOpenVoiceGoal,
            )
            Spacer(Modifier.height(10.dp))
            SettingsMenuCard(
                title = stringResource(R.string.settings_passages),
                iconRes = R.drawable.ic_book,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onOpenPassages,
            )
            Spacer(Modifier.height(10.dp))
            SettingsMenuCard(
                title = stringResource(R.string.settings_advanced),
                iconRes = R.drawable.ic_tune,
                tint = MaterialTheme.colorScheme.tertiary,
                onClick = onOpenAdvanced,
            )
            Spacer(Modifier.height(10.dp))
            SettingsMenuCard(
                title = stringResource(R.string.settings_updates_title),
                iconRes = R.drawable.ic_system_update,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onOpenUpdates,
            )
            Spacer(Modifier.height(10.dp))
            SettingsMenuCard(
                title = stringResource(R.string.settings_about),
                iconRes = R.drawable.ic_info,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onOpenAbout,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsMenuCard(
    title: String,
    iconRes: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = tint.copy(alpha = 0.14f),
                contentColor = tint,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
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
private fun VoiceGoalSelector(
    selected: VoicePreference,
    onSelected: (VoicePreference) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VoiceGoalCard(
            title = stringResource(R.string.voice_profile_feminine_title),
            description = stringResource(R.string.settings_voice_goal_feminine_desc),
            frequencyLabel = "F0 > 165 Hz",
            tint = PitcheeColorTokens.Feminine,
            selected = selected == VoicePreference.FEMININE,
            rising = true,
            onClick = { onSelected(VoicePreference.FEMININE) },
        )
        VoiceGoalCard(
            title = stringResource(R.string.voice_profile_masculine_title),
            description = stringResource(R.string.settings_voice_goal_masculine_desc),
            frequencyLabel = "F0 < 165 Hz",
            tint = PitcheeColorTokens.Masculine,
            selected = selected == VoicePreference.MASCULINE,
            rising = false,
            onClick = { onSelected(VoicePreference.MASCULINE) },
        )
    }
}

@Composable
private fun VoiceGoalCard(
    title: String,
    description: String,
    frequencyLabel: String,
    tint: Color,
    selected: Boolean,
    rising: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) {
            tint.copy(alpha = 0.17f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.BottomCenter),
            ) {
                val path = Path()
                if (rising) {
                    path.moveTo(size.width * 0.52f, size.height * 0.78f)
                    path.cubicTo(
                        size.width * 0.63f,
                        size.height * 0.80f,
                        size.width * 0.72f,
                        size.height * 0.42f,
                        size.width * 0.83f,
                        size.height * 0.46f,
                    )
                    path.cubicTo(
                        size.width * 0.91f,
                        size.height * 0.49f,
                        size.width * 0.94f,
                        size.height * 0.22f,
                        size.width,
                        size.height * 0.18f,
                    )
                } else {
                    path.moveTo(size.width * 0.52f, size.height * 0.24f)
                    path.cubicTo(
                        size.width * 0.63f,
                        size.height * 0.20f,
                        size.width * 0.72f,
                        size.height * 0.60f,
                        size.width * 0.83f,
                        size.height * 0.55f,
                    )
                    path.cubicTo(
                        size.width * 0.92f,
                        size.height * 0.51f,
                        size.width * 0.95f,
                        size.height * 0.78f,
                        size.width,
                        size.height * 0.82f,
                    )
                }
                drawPath(
                    path = path,
                    color = tint.copy(alpha = if (selected) 0.72f else 0.28f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = tint,
                            contentColor = if (rising) {
                                PitcheeColorTokens.FeminineContent
                            } else {
                                PitcheeColorTokens.MasculineContent
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = frequencyLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector() {
    val controller = LocalPitcheeThemeController.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PitcheeThemePalette.entries.forEach { palette ->
            val selected = controller.palette == palette
            Surface(
                onClick = { controller.select(palette) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeSwatch(palette.lightPrimary)
                        ThemeSwatch(palette.lightSecondary)
                        ThemeSwatch(palette.lightTertiary)
                    }
                    Spacer(Modifier.size(16.dp))
                    Text(
                        text = stringResource(palette.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(color: Color) {
    Surface(
        modifier = Modifier.size(26.dp),
        shape = CircleShape,
        color = color,
    ) {}
}

@Composable
private fun LanguageSelector(
    selectedTag: String,
    onSelected: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            AppLanguage.supported.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { onSelected(option.tag) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedTag == option.tag,
                        onClick = { onSelected(option.tag) },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selectedTag == option.tag) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    versionName: String,
    repositoryUrl: String,
    pitcheeCoreUrl: String,
    lvyZhanUrl: String,
    ryoFengUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.pitchee_logo),
            contentDescription = null,
            modifier = Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.settings_version_format, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.settings_authors),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    AuthorRow(
        name = "Lvy Zhan",
        profileUrl = lvyZhanUrl,
        avatarRes = R.drawable.author_lvy_zhan,
        onClick = { onOpenUrl(lvyZhanUrl) },
    )
    AuthorRow(
        name = "Ryo Feng",
        profileUrl = ryoFengUrl,
        avatarRes = R.drawable.author_ryo_feng,
        onClick = { onOpenUrl(ryoFengUrl) },
    )
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.settings_repository),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
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
        modifier = Modifier.clickable { onOpenUrl(repositoryUrl) },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.settings_open_source),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    SETTINGS_LICENSES.forEach { license ->
        Text(
            text = stringResource(license),
            modifier = Modifier.padding(vertical = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = pitcheeCoreUrl,
        modifier = Modifier.clickable { onOpenUrl(pitcheeCoreUrl) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AuthorCard(
    name: String,
    profileUrl: String,
    avatarRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(142.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Image(
                painter = painterResource(avatarRes),
                contentDescription = name,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "@${profileUrl.substringAfterLast('/')}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OpenSourceCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    pitcheeCoreUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "licenses-chevron",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Surface(
                onClick = onToggle,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_open_source),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_open_source_count,
                            SETTINGS_LICENSES.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 280f),
                    expandFrom = Alignment.Top,
                ),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_open_source_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SETTINGS_LICENSES.forEach { license ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(5.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                            ) {}
                            Spacer(Modifier.size(9.dp))
                            Text(
                                text = stringResource(license),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pitcheeCoreUrl.removePrefix("https://"),
                        modifier = Modifier.clickable { onOpenUrl(pitcheeCoreUrl) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private val SETTINGS_LICENSES = listOf(
    R.string.settings_license_pitchee_core,
    R.string.settings_license_swift_f0,
    R.string.settings_license_silero_vad,
    R.string.settings_license_speechbrain,
    R.string.settings_license_ncnn,
    R.string.settings_license_androidx,
    R.string.settings_license_markwon,
    R.string.settings_license_material_icons,
    R.string.settings_license_pitchee_articles,
)

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
