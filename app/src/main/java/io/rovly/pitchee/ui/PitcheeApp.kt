package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import io.rovly.pitchee.data.FeminineTimeline
import io.rovly.pitchee.data.PitcheeResult
import io.rovly.pitchee.update.ApkInstaller
import io.rovly.pitchee.update.GitHubRelease
import io.rovly.pitchee.update.UpdateUiState
import io.rovly.pitchee.update.UpdateViewModel
import java.io.File
import space.pitchee.core.PitcheePhase
import kotlinx.coroutines.launch

private enum class PitcheeDestination(
    val labelRes: Int,
    val iconRes: Int,
) {
    DASHBOARD(R.string.nav_dashboard, R.drawable.ic_dashboard),
    PITCH(R.string.nav_pitch, R.drawable.ic_pitch_analysis),
    SPECTRUM(R.string.nav_spectrum, R.drawable.ic_spectrum),
    ANALYSIS(R.string.nav_analysis, R.drawable.ic_model_analysis),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
    SCORE_RULES(R.string.nav_score_test, R.drawable.ic_model_analysis),
}

@Preview
@Composable
fun PitcheeApp() {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
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
    val debugBuild = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val destinations = remember(debugBuild) {
        PitcheeDestination.entries.filter { it != PitcheeDestination.SCORE_RULES || debugBuild }
    }
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

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
    ) { innerPadding ->
        SwipeBackContainer(
            enabled = destinations[selectedIndex] != PitcheeDestination.DASHBOARD,
            onBack = { selectedIndex = 0 },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (destinations[selectedIndex]) {
                PitcheeDestination.DASHBOARD -> DashboardScreen(
                    refreshKey = selectedIndex,
                    scrollPosition = dashboardScrollPosition,
                )
                PitcheeDestination.PITCH -> RealtimePitchScreen()
                PitcheeDestination.SPECTRUM -> RealtimeSpectrumScreen()
                PitcheeDestination.ANALYSIS -> RecordAnalysisScreen()
                PitcheeDestination.SETTINGS -> SettingsScreen(
                    updateState = updateState,
                    autoCheckUpdates = autoCheckUpdates,
                    onAutoCheckChange = updateViewModel::setAutoCheck,
                    onCheckUpdates = { updateViewModel.checkForUpdates(manual = true) },
                )
                PitcheeDestination.SCORE_RULES -> ScoreRulesPage(
                    result = remember { demoRuleResult() },
                    previousScore = 52.0,
                    previousMetrics = PreviousMetrics(
                        standardScore = 70.0,
                        naturalnessScore = 75.0,
                        meanF0Hz = 150.0,
                    ),
                    onBack = null,
                )
            }
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
        showingRules = false
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
                    onBack = { showingRules = false },
                ) {
                    AnimatedContent(
                        targetState = showingRules,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        transitionSpec = {
                            if (targetState) {
                                slideInHorizontally(
                                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 280f),
                                    initialOffsetX = { it / 2 },
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                                    targetOffsetX = { -it / 3 },
                                )
                            } else {
                                slideInHorizontally(
                                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 280f),
                                    initialOffsetX = { -it / 3 },
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                                    targetOffsetX = { it / 2 },
                                )
                            }
                        },
                        label = "score-rules-page",
                    ) { rulesVisible ->
                        if (rulesVisible) {
                            ScoreRulesPage(
                                result = current.result,
                                previousScore = current.previousScore,
                                previousMetrics = current.previousMetrics,
                                onBack = { showingRules = false },
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
                                    ScoreResultContent(
                                        result = current.result,
                                        previousScore = current.previousScore,
                                        previousMetrics = current.previousMetrics,
                                        animateScore = animateScore,
                                        onOpenRules = { showingRules = true },
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
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(loc("返回结果", "Back to result"))
                }
                Spacer(Modifier.height(8.dp))
            }
            ScreenHeader(
                title = loc("评分规则", "Scoring rules"),
                subtitle = loc(
                    "结合本次指标查看综合分的计算过程",
                    "See how the final score is calculated from the current metrics",
                ),
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

private fun demoRuleResult(): PitcheeResult = PitcheeResult.fromJson(
    """
    {
      "schema_version": 2,
      "model_version": "preview",
      "audio": {
        "source_sample_rate": 48000,
        "source_channels": 1,
        "input_seconds": 8.0,
        "analyzed_seconds": 8.0
      },
      "vad": {
        "segment_count": 1,
        "speech_seconds": 7.2,
        "silero_segment_count": 1,
        "discarded_breath_like_count": 0,
        "trimmed_segment_count": 0,
        "segments": [{
          "start_seconds": 0.2,
          "end_seconds": 7.4,
          "speech_start_seconds": 0.0,
          "speech_end_seconds": 7.2
        }]
      },
      "f0": {
        "window_seconds": 0.05,
        "mean_hz": 158.0,
        "standard_deviation_hz": 12.0,
        "voiced_frame_count": 600,
        "voiced_window_count": 60,
        "windows": []
      },
      "vfp": {
        "vfp_standard_score": 76.0,
        "window_count": 4,
        "window_duration_seconds": 1.515,
        "windows": []
      },
      "naturalness": {
        "score": 68.0,
        "window_count": 4,
        "window_duration_seconds": 1.515,
        "windows": []
      },
      "composite": {
        "base_score": 72.0,
        "final_score": 59.0,
        "cap": 59.0,
        "rule": "low_f0_natural_cap",
        "limited": true,
        "boosted": false
      }
    }
    """.trimIndent(),
)

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
private fun ScreenHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
