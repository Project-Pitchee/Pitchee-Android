package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import io.rovly.pitchee.data.model.FeminineTimeline
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.feature.analysis.AnalysisExportButton
import io.rovly.pitchee.feature.analysis.PreviousMetrics
import io.rovly.pitchee.feature.analysis.RecordUiState
import io.rovly.pitchee.feature.analysis.RecordViewModel
import io.rovly.pitchee.feature.analysis.RecordedAudioTimeline
import io.rovly.pitchee.feature.analysis.RecordingStage
import io.rovly.pitchee.feature.analysis.ScoreResultContent
import io.rovly.pitchee.feature.analysis.ScoreRulesContent
import io.rovly.pitchee.feature.dashboard.DashboardScreen
import io.rovly.pitchee.feature.dashboard.DashboardScrollPosition
import io.rovly.pitchee.feature.more.MoreScreen
import io.rovly.pitchee.feature.onboarding.OnboardingScreen
import io.rovly.pitchee.feature.pitch.RealtimePitchScreen
import io.rovly.pitchee.feature.settings.SettingsScreen
import io.rovly.pitchee.feature.settings.VoicePreferencePreferences
import io.rovly.pitchee.ui.common.SwipeBackContainer
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.ScreenTransition
import io.rovly.pitchee.update.ApkInstaller
import io.rovly.pitchee.update.GitHubRelease
import io.rovly.pitchee.update.UpdateUiState
import io.rovly.pitchee.update.UpdateViewModel
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch

private val MAX_CONTENT_WIDTH = 1040.dp

private enum class PitcheeDestination(
    val labelRes: Int,
    val iconRes: Int,
) {
    DASHBOARD(R.string.nav_dashboard, R.drawable.ic_dashboard),
    PITCH(R.string.nav_pitch, R.drawable.ic_pitch_analysis),
    ANALYSIS(R.string.nav_analysis, R.drawable.ic_model_analysis),
    MORE(R.string.nav_more, R.drawable.ic_more),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}

@Preview
@Composable
fun PitcheeApp() {
    val context = LocalContext.current
    val voicePreferences = remember(context) {
        VoicePreferencePreferences(context.applicationContext)
    }
    var onboardingCompleted by rememberSaveable {
        mutableStateOf(voicePreferences.hasCompletedOnboarding())
    }
    if (!onboardingCompleted) {
        OnboardingScreen { preference ->
            voicePreferences.completeOnboarding(preference)
            onboardingCompleted = true
        }
        return
    }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val updateViewModel: UpdateViewModel = viewModel(
        factory = remember { UpdateViewModel.factory(context.applicationContext) },
    )
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val autoCheckUpdates by updateViewModel.autoCheck.collectAsStateWithLifecycle()
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }
    var pendingDownloadRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val file = pendingInstallFile
        pendingInstallFile = null
        if (file != null && ApkInstaller.canInstall(context)) {
            runCatching { ApkInstaller.install(context, file) }
            updateViewModel.markInstallLaunched()
        }
        val release = pendingDownloadRelease
        pendingDownloadRelease = null
        if (release != null && ApkInstaller.canInstall(context)) {
            updateViewModel.download(release)
        }
    }

    fun startUpdate(release: GitHubRelease) {
        updateViewModel.hideUpdate()
        if (ApkInstaller.canInstall(context)) {
            updateViewModel.download(release)
        } else {
            pendingDownloadRelease = release
            ApkInstaller.requestInstallPermission(context)
        }
    }
    val destinations = remember { PitcheeDestination.entries }
    val dashboardScrollPosition = remember { DashboardScrollPosition() }

    LaunchedEffect(Unit) {
        updateViewModel.checkOnLaunch()
    }

    LaunchedEffect(updateState) {
        val ready = updateState as? UpdateUiState.ReadyToInstall ?: return@LaunchedEffect
        if (ApkInstaller.canInstall(context)) {
            runCatching { ApkInstaller.install(context, ready.file) }
            updateViewModel.markInstallLaunched()
        } else {
            pendingInstallFile = ready.file
            ApkInstaller.requestInstallPermission(context)
        }
    }

    when (val state = updateState) {
        is UpdateUiState.Available -> {
            var snoozeForWeek by remember(state.release.tagName) {
                mutableStateOf(false)
            }
            AlertDialog(
                onDismissRequest = { updateViewModel.dismissUpdate(snoozeForWeek) },
                title = { Text(stringResource(R.string.update_found_title)) },
                text = {
                    Column {
                        Text(state.release.displayName)
                        if (state.release.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(state.release.notes.take(600))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clickable { snoozeForWeek = !snoozeForWeek },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = snoozeForWeek,
                                onCheckedChange = { snoozeForWeek = it },
                            )
                            Text(stringResource(R.string.update_snooze_week))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (snoozeForWeek) updateViewModel.snoozeUpdatesForWeek()
                            startUpdate(state.release)
                        },
                    ) {
                        Text(stringResource(R.string.update_download))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { updateViewModel.dismissUpdate(snoozeForWeek) },
                    ) {
                        Text(stringResource(R.string.update_later))
                    }
                },
            )
        }

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading_title)) },
            text = {
                Column {
                    Text(state.release.displayName)
                    Spacer(Modifier.height(12.dp))
                    val progress = state.progress
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {},
        )

        else -> Unit
    }

    val layoutDirection = LocalLayoutDirection.current
    val screenContent: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The pitch chart paints its own background, so that tab runs under
            // the status bar. Everything else stays inset as before.
            val fullBleedTop = destinations[selectedIndex] == PitcheeDestination.PITCH
            val contentPadding = if (fullBleedTop) {
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding(),
                )
            } else {
                innerPadding
            }
            SwipeBackContainer(
                enabled = destinations[selectedIndex] != PitcheeDestination.DASHBOARD,
                onBack = { selectedIndex = 0 },
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(contentPadding),
            ) {
                when (destinations[selectedIndex]) {
                    PitcheeDestination.DASHBOARD -> DashboardScreen(
                        refreshKey = selectedIndex,
                        scrollPosition = dashboardScrollPosition,
                    )
                    PitcheeDestination.PITCH -> RealtimePitchScreen()
                    PitcheeDestination.MORE -> MoreScreen()
                    PitcheeDestination.ANALYSIS -> RecordAnalysisScreen()
                    PitcheeDestination.SETTINGS -> SettingsScreen(
                        updateState = updateState,
                        autoCheckUpdates = autoCheckUpdates,
                        onAutoCheckChange = updateViewModel::setAutoCheck,
                        onCheckUpdates = { updateViewModel.checkForUpdates(manual = true) },
                    )
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        NavigationRailItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            icon = {
                                Icon(
                                    painter = painterResource(destination.iconRes),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
                Scaffold { innerPadding -> screenContent(innerPadding) }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                icon = {
                                    Icon(
                                        painter = painterResource(destination.iconRes),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                },
                content = screenContent,
            )
        }
    }

}

@Composable
private fun RecordAnalysisScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val factory = remember { RecordViewModel.factory(context.applicationContext) }
    val viewModel: RecordViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionDeniedMessage = stringResource(R.string.record_permission_denied)
    var showingRules by rememberSaveable { mutableStateOf(false) }
    // Same idea as the dashboard: keep a strictly ordered zIndex so a pop right
    // after a pop cannot tie with the outgoing page.
    var rulesZStep by remember { mutableFloatStateOf(1f) }

    fun setRulesVisible(visible: Boolean) {
        if (visible == showingRules) return
        val magnitude = abs(rulesZStep) + 1f
        rulesZStep = if (visible) magnitude else -magnitude
        showingRules = visible
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(permissionDeniedMessage)
            }
        }
    }

    fun startRecording() {
        setRulesVisible(false)
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val errorMessage = (state as? RecordUiState.Error)?.message
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbarHostState.showSnackbar(errorMessage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val current = state) {
            is RecordUiState.Success -> {
                SwipeBackContainer(
                    enabled = showingRules,
                    onBack = { setRulesVisible(false) },
                ) {
                    ScreenTransition(
                        targetState = showingRules,
                        forward = showingRules,
                        contentZIndex = rulesZStep,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        label = "score-rules-page",
                    ) { rulesVisible ->
                        if (rulesVisible) {
                            ScoreRulesPage(
                                result = current.result,
                                previousScore = current.previousScore,
                                previousMetrics = current.previousMetrics,
                                onBack = { setRulesVisible(false) },
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                val animateScore = remember(current.scoreAnimationToken) {
                                    viewModel.consumeScoreAnimation(current.scoreAnimationToken)
                                }
                                val playbackTimeline = remember(
                                    current.result,
                                    current.audio.durationSeconds,
                                ) {
                                    FeminineTimeline.from(
                                        result = current.result,
                                        durationSeconds = current.audio.durationSeconds,
                                    )
                                }
                                ScreenColumn {
                                    val resultTimestamp = remember(current.result) {
                                        System.currentTimeMillis()
                                    }
                                    ScreenTitle(
                                        title = stringResource(R.string.analysis_result_title),
                                        trailingContent = {
                                            AnalysisExportButton(
                                                result = current.result,
                                                audio = current.audio,
                                                timestampMillis = resultTimestamp,
                                            )
                                        },
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    ScoreResultContent(
                                        result = current.result,
                                        previousScore = current.previousScore,
                                        previousMetrics = current.previousMetrics,
                                        animateScore = animateScore,
                                        onOpenRules = { setRulesVisible(true) },
                                    ) {
                                        RecordedAudioTimeline(
                                            audio = current.audio,
                                            f0Windows = current.result.f0.windows,
                                            segmentScores = current.segmentScores,
                                            metricsTimeline = playbackTimeline,
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(
                                        onClick = {
                                            viewModel.reset()
                                            startRecording()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    ) {
                                        Text(stringResource(R.string.recording_again))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is RecordUiState.Error -> {
                if (current.audio == null) {
                    ScreenColumn {
                        ErrorCard(current.message)
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                viewModel.reset()
                                startRecording()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(stringResource(R.string.recording_again))
                        }
                    }
                } else {
                    ScreenColumn {
                        RecordedAudioTimeline(
                            audio = current.audio,
                        )
                        Spacer(Modifier.height(20.dp))
                        ErrorCard(current.message)
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                viewModel.reset()
                                startRecording()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(stringResource(R.string.recording_again))
                        }
                    }
                }
            }
            else -> {
                RecordingStage(
                    state = current,
                    onStart = {
                        viewModel.reset()
                        startRecording()
                    },
                    onStop = viewModel::stopAndAnalyze,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ScoreRulesPage(
    result: PitcheeResult,
    previousScore: Double?,
    previousMetrics: PreviousMetrics?,
    onBack: (() -> Unit)?,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    SwipeBackContainer(
        enabled = onBack != null,
        onBack = { onBack?.invoke() },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            ScreenColumn {
            ScreenTitle(
                title = stringResource(R.string.score_rules_title),
                subtitle = stringResource(R.string.score_rules_subtitle),
                onBack = onBack,
            )
            Spacer(Modifier.height(20.dp))
            ScoreRulesContent(
                result = result,
                previousScore = previousScore,
                previousMetrics = previousMetrics,
            )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.analysis_failed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        content = content,
    )
}
