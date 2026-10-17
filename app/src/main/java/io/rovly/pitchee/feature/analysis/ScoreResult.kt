package io.rovly.pitchee.feature.analysis

import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.rovly.pitchee.R
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.ui.common.resolve
import io.rovly.pitchee.ui.common.LocalizedText
import io.rovly.pitchee.ui.common.ScoreMetric
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.pitchee.core.PitcheeScoreProfile

private const val SCORE_SOUND_SEQUENCE_MILLIS = 350f
private const val SCORE_POINTS_PER_TICK = 3f
private const val MAX_SCORE_SOUND_TICKS = 20

private val DeepRed = PitcheeColorTokens.Danger
private val DeepGreen = PitcheeColorTokens.Success
private val DeepRedContainer = PitcheeColorTokens.DangerContainer
private val DeepGreenContainer = PitcheeColorTokens.SuccessContainer
private val DeepRedContainerDark = PitcheeColorTokens.DangerContainerDark
private val DeepGreenContainerDark = PitcheeColorTokens.SuccessContainerDark
private val DeepRedContentDark = PitcheeColorTokens.DangerContentDark
private val DeepGreenContentDark = PitcheeColorTokens.SuccessContentDark

@Composable
private fun ruleGreenContent(): Color =
    if (isSystemInDarkTheme()) DeepGreenContentDark else DeepGreen

@Composable
private fun ruleRedContent(): Color =
    if (isSystemInDarkTheme()) DeepRedContentDark else DeepRed

@Composable
private fun ruleGreenContainer(): Color =
    if (isSystemInDarkTheme()) DeepGreenContainerDark else DeepGreenContainer

@Composable
private fun ruleRedContainer(): Color =
    if (isSystemInDarkTheme()) DeepRedContainerDark else DeepRedContainer

@Composable
internal fun ScoreResultContent(
    result: PitcheeResult,
    previousScore: Double? = null,
    previousMetrics: PreviousMetrics? = null,
    animateScore: Boolean = true,
    onOpenRules: () -> Unit = {},
    audioPlayer: (@Composable () -> Unit)? = null,
) {
    val languageCode = LocalConfiguration.current.locales[0].language
    val insight = remember(result, languageCode) { ScoreInsight.from(result) }
    val score = result.composite.finalScore.coerceIn(0.0, 100.0)
    val scoreSoundPlayer = rememberScoreSoundPlayer()

    AnimatedScoreIndexChart(
        targetScore = score,
        previousScore = previousScore,
        animate = animateScore,
        scoreProfile = result.scoreProfile,
        onScoreTick = scoreSoundPlayer::play,
        modifier = Modifier.fillMaxWidth(),
    )
    audioPlayer?.let {
        Spacer(Modifier.height(18.dp))
        it()
        Spacer(Modifier.height(12.dp))
    }
    if (insight.hasBottleneck) {
        BottleneckCard(insight, onOpenRules)
        Spacer(Modifier.height(12.dp))
    } else if (score > 60.0) {
        PassCard(onOpenRules)
        Spacer(Modifier.height(12.dp))
    }
    MetricsCard(
        result = result,
        insight = insight,
        previousMetrics = previousMetrics,
        animate = animateScore,
        languageCode = languageCode,
    )
}

@Composable
internal fun ScoreIndexChart(
    score: Double,
    scoreProfile: PitcheeScoreProfile = PitcheeScoreProfile.FEMINIZATION,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(304.dp),
    ) {
        val targetVisual = score.coerceIn(0.0, 112.0)
        val oppositeVisual = (100.0 - score).coerceIn(0.0, 112.0)
        val feminineVisual = if (scoreProfile == PitcheeScoreProfile.MASCULINIZATION) {
            oppositeVisual
        } else {
            targetVisual
        }
        val masculineVisual = if (scoreProfile == PitcheeScoreProfile.MASCULINIZATION) {
            targetVisual
        } else {
            oppositeVisual
        }
        val feminineScore = feminineVisual.coerceAtMost(100.0)
        val masculineScore = masculineVisual.coerceAtMost(100.0)
        val rawFeminineSize = 76f + feminineVisual.toFloat() * 1.76f
        val rawMasculineSize = 76f + masculineVisual.toFloat() * 1.76f
        val rawFeminineTravel = cornerTravel(feminineScore).value
        val rawMasculineTravel = cornerTravel(masculineScore).value
        val rawFeminineCenterY = -rawFeminineTravel * 0.82f
        val rawMasculineCenterY = rawMasculineTravel * 0.82f
        val rawGroupWidth = maxOf(
            rawFeminineTravel + rawFeminineSize / 2f,
            -rawMasculineTravel + rawMasculineSize / 2f,
        ) - minOf(
            rawFeminineTravel - rawFeminineSize / 2f,
            -rawMasculineTravel - rawMasculineSize / 2f,
        )
        val rawGroupHeight = maxOf(
            rawFeminineCenterY + rawFeminineSize / 2f,
            rawMasculineCenterY + rawMasculineSize / 2f,
        ) - minOf(
            rawFeminineCenterY - rawFeminineSize / 2f,
            rawMasculineCenterY - rawMasculineSize / 2f,
        )
        val targetGroupWidth = (maxWidth.value - 40f).coerceAtLeast(0f)
        val horizontalScale = (targetGroupWidth / rawGroupWidth).coerceAtLeast(1f)
        val verticalScale = (288f / rawGroupHeight).coerceAtLeast(1f)
        val groupScale = minOf(horizontalScale, verticalScale)
        val feminineSize = (rawFeminineSize * groupScale).dp
        val masculineSize = (rawMasculineSize * groupScale).dp
        val feminineTravel = rawFeminineTravel * groupScale
        val masculineTravel = rawMasculineTravel * groupScale
        val feminineCenterX = feminineTravel
        val feminineCenterY = -feminineTravel * 0.82f
        val masculineCenterX = -masculineTravel
        val masculineCenterY = masculineTravel * 0.82f
        val groupMinX = minOf(
            feminineCenterX - feminineSize.value / 2f,
            masculineCenterX - masculineSize.value / 2f,
        )
        val groupMaxX = maxOf(
            feminineCenterX + feminineSize.value / 2f,
            masculineCenterX + masculineSize.value / 2f,
        )
        val groupMinY = minOf(
            feminineCenterY - feminineSize.value / 2f,
            masculineCenterY - masculineSize.value / 2f,
        )
        val groupMaxY = maxOf(
            feminineCenterY + feminineSize.value / 2f,
            masculineCenterY + masculineSize.value / 2f,
        )
        val groupOffsetX = -(groupMinX + groupMaxX) / 2f
        val groupOffsetY = -(groupMinY + groupMaxY) / 2f

        Box(Modifier.fillMaxSize()) {
            ScoreIndexCircle(
                score = masculineScore,
                color = PitcheeColorTokens.Masculine,
                contentColor = PitcheeColorTokens.MasculineContent,
                size = masculineSize,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = (masculineCenterX + groupOffsetX).dp,
                        y = (masculineCenterY + groupOffsetY).dp,
                    ),
            )
            ScoreIndexCircle(
                score = feminineScore,
                color = PitcheeColorTokens.Feminine,
                contentColor = PitcheeColorTokens.FeminineContent,
                size = feminineSize,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = (feminineCenterX + groupOffsetX).dp,
                        y = (feminineCenterY + groupOffsetY).dp,
                    ),
            )
        }
    }
}

@Composable
private fun PassCard(onOpenRules: () -> Unit) {
    InsightCard(
        label = stringResource(R.string.score_pass_label),
        title = stringResource(R.string.score_pass_title),
        description = stringResource(R.string.score_pass_desc),
        containerColor = ruleGreenContainer(),
        contentColor = ruleGreenContent(),
        actionContainerColor = DeepGreen,
        actionContentColor = Color.White,
        onOpenRules = onOpenRules,
    )
}

@Composable
private fun InsightCard(
    label: String,
    title: String,
    description: String,
    containerColor: Color,
    contentColor: Color,
    actionContainerColor: Color,
    actionContentColor: Color,
    onOpenRules: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            Spacer(Modifier.height(8.dp))
            ResultActionButton(
                text = stringResource(R.string.score_view_rules),
                onClick = onOpenRules,
                containerColor = actionContainerColor,
                contentColor = actionContentColor,
            )
        }
    }
}

@Composable
private fun ResultActionButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun rememberScoreSoundPlayer(): ScoreSoundPlayer {
    val context = LocalContext.current
    val player = remember(context) { ScoreSoundPlayer(context) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@Composable
internal fun AnimatedScoreIndexChart(
    targetScore: Double,
    previousScore: Double?,
    animate: Boolean = true,
    scoreProfile: PitcheeScoreProfile = PitcheeScoreProfile.FEMINIZATION,
    onScoreTick: (score: Float, pitchScore: Float) -> Unit = { _, _ -> },
    animationKey: Any? = Unit,
    modifier: Modifier = Modifier,
) {
    val target = targetScore.coerceIn(0.0, 100.0).toFloat()
    val start = if (animate) {
        (previousScore ?: 0.0).coerceIn(0.0, 100.0).toFloat()
    } else {
        target
    }
    val animatedScore = remember { Animatable(start) }
    val currentOnScoreTick by rememberUpdatedState(onScoreTick)

    LaunchedEffect(target, start, animate, animationKey) {
        animatedScore.stop()
        animatedScore.snapTo(start)
        if (!animate) return@LaunchedEffect

        delay(500)
        currentOnScoreTick(start, start)

        val scoreDelta = target - start
        if (abs(scoreDelta) <= 0.01f) return@LaunchedEffect

        val tickCount = min(
            MAX_SCORE_SOUND_TICKS,
            max(2, ceil(abs(scoreDelta) / SCORE_POINTS_PER_TICK).toInt() + 1),
        )
        val pitchStep = scoreDelta / (tickCount - 1)
        val baseIntervalMillis = SCORE_SOUND_SEQUENCE_MILLIS / (tickCount - 1)
        val scoreUpdates = launch {
            repeat(tickCount) { index ->
                val currentScore = animatedScore.value
                val pitchScore = start + pitchStep * index
                currentOnScoreTick(currentScore, pitchScore)

                if (index < tickCount - 1) {
                    val progress = (index + 1).toFloat() / (tickCount - 1).toFloat()
                    val cadenceFactor = 0.8f + 0.6f * progress * progress
                    delay((baseIntervalMillis * cadenceFactor).roundToLong().coerceAtLeast(1L))
                }
            }
        }
        try {
            animatedScore.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = 900,
                    easing = FastOutSlowInEasing,
                ),
            )
            scoreUpdates.join()
        } finally {
            scoreUpdates.cancel()
        }
    }

    ScoreIndexChart(
        score = animatedScore.value.toDouble(),
        scoreProfile = scoreProfile,
        modifier = modifier,
    )
}

private fun cornerTravel(score: Double): Dp {
    val lowScoreFactor = (1.0 - score.coerceIn(0.0, 100.0) / 100.0).toFloat()
    return (22f + lowScoreFactor * 42f).dp
}

@Composable
private fun ScoreIndexCircle(
    score: Double,
    color: Color,
    contentColor: Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = color,
        contentColor = contentColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "%.1f%%".format(score),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = TextUnit.Unspecified,
                    lineHeight = TextUnit.Unspecified,
                ),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = scoreFontCap(score),
                    stepSize = 0.5.sp,
                ),
            )
        }
    }
}

private fun scoreFontCap(score: Double): TextUnit {
    val progress = (score.coerceIn(0.0, 100.0) / 100.0).toFloat()
    return (34f + 18f * sqrt(progress)).sp
}

@Composable
internal fun ScoreRulesContent(
    result: PitcheeResult,
    previousScore: Double?,
    previousMetrics: PreviousMetrics?,
) {
    val languageCode = LocalConfiguration.current.locales[0].language
    val insight = remember(result, languageCode) { ScoreInsight.from(result) }
    val currentRule = result.composite.rule
    val masculineProfile = result.scoreProfile == PitcheeScoreProfile.MASCULINIZATION
    val commonFormulas = if (masculineProfile) {
        masculineCommonScoreFormulas
    } else {
        commonScoreFormulas
    }
    val ruleDocs = if (masculineProfile) masculineScoringRuleDocs else scoringRuleDocs
    val currentRuleColor = if (insight.hasBottleneck) ruleRedContent() else ruleGreenContent()
    var otherRulesExpanded by rememberSaveable(currentRule) { mutableStateOf(false) }
    val currentRuleDoc = ruleDocs.firstOrNull { it.key == currentRule }
    val otherRuleDocs = ruleDocs.filterNot { it.key == currentRule }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.score_final_score),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%.1f".format(result.composite.finalScore),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            ScoreDelta(
                previousScore = previousScore,
                currentScore = result.composite.finalScore,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.score_current_metrics),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        scoreIndicators(result, previousMetrics).forEach { indicator ->
            IndicatorRow(indicator)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(10.dp))
        CommonFormulaSection(
            formulas = commonFormulas,
            variablesRes = if (masculineProfile) {
                R.string.score_formula_variables_masculine
            } else {
                R.string.score_formula_variables
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.score_current_rule),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = insight.ruleDescription.resolve(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        currentRuleDoc?.let { rule ->
            RuleDetails(
                rule = rule,
                isCurrent = true,
                accentColor = currentRuleColor,
                )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { otherRulesExpanded = !otherRulesExpanded }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.score_other_rules),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.score_other_rules_count, otherRuleDocs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (otherRulesExpanded) "−" else "+",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(
            visible = otherRulesExpanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 280f),
            ),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
            ),
        ) {
            Column {
                otherRuleDocs.forEachIndexed { index, rule ->
                    RuleDetails(rule = rule, isCurrent = false)
                    if (index != otherRuleDocs.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreDelta(
    previousScore: Double?,
    currentScore: Double,
) {
    val delta = previousScore?.let { currentScore - it }
    val (arrow, color) = when {
        delta == null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0.05 -> "↑" to ruleGreenContent()
        delta < -0.05 -> "↓" to ruleRedContent()
        else -> "→" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(R.string.score_compare_previous),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = delta?.let { "$arrow ${"%.1f".format(abs(it))}" }
                ?: stringResource(R.string.score_first_test),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun CommonFormulaSection(
    formulas: List<String>,
    variablesRes: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.score_base_formulas),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.score_base_formulas_note),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FormulaView(formulas.map(::latexBlock))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(variablesRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
        )
    }
}

@Composable
private fun IndicatorRow(indicator: ScoreIndicator) {
    val containerColor = if (indicator.passed) {
        ruleGreenContainer()
    } else {
        ruleRedContainer()
    }
    val contentColor = if (indicator.passed) {
        ruleGreenContent()
    } else {
        ruleRedContent()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (indicator.passed) R.drawable.ic_check else R.drawable.ic_close,
                    ),
                    contentDescription = indicator.tier.resolve(),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = indicator.label.resolve(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = indicator.value.resolve(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        val delta = indicator.comparison?.let { it.current - it.previous }
        val comparisonText = when {
            delta == null -> stringResource(R.string.score_vs_last_none)
            delta > 0.05 -> stringResource(R.string.score_vs_last_up, formatComparison(abs(delta), indicator.comparison.unit))
            delta < -0.05 -> stringResource(R.string.score_vs_last_down, formatComparison(abs(delta), indicator.comparison.unit))
            else -> stringResource(R.string.score_vs_last_same, formatComparison(0.0, indicator.comparison.unit))
        }
        val comparisonColor = when {
            delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
            delta > 0.05 -> ruleGreenContent()
            delta < -0.05 -> ruleRedContent()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = indicator.tier.resolve(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = comparisonText,
                style = MaterialTheme.typography.labelSmall,
                color = comparisonColor,
            )
        }
    }
}

private data class ScoreIndicator(
    val label: LocalizedText,
    val value: LocalizedText,
    val passed: Boolean,
    val tier: LocalizedText,
    val comparison: ScoreComparison?,
)

private data class ScoreComparison(
    val current: Double,
    val previous: Double,
    val unit: String = "",
)

private fun formatComparison(value: Double, unit: String): String =
    if (unit == " Hz") "%.0f%s".format(value, unit) else "%.1f%s".format(value, unit)

private fun scoreIndicators(
    result: PitcheeResult,
    previousMetrics: PreviousMetrics?,
): List<ScoreIndicator> {
    if (result.scoreProfile == PitcheeScoreProfile.MASCULINIZATION) {
        return masculineScoreIndicators(result, previousMetrics)
    }
    val standard = result.vfp.standardScore
    val naturalness = result.naturalness.score
    val f0 = result.f0.meanHz
    val rule = result.composite.rule
    val standardLimited = when (rule) {
        "high_f0_male_cap" -> true
        "continuous" -> standard < 50.0
        else -> false
    }
    val naturalnessLimited = when (rule) {
        "high_f0_stylized_cap", "low_f0_stylized_cap" -> true
        "continuous" -> naturalness < 50.0
        else -> false
    }
    val f0Limited = when (rule) {
        "f0_unavailable", "low_f0_natural_cap", "low_f0_stylized_cap" -> true
        "continuous" -> f0 == null || f0 <= 165.0
        else -> false
    }
    val standardTier = scoreTier(standard, !standardLimited)
    val naturalnessTier = scoreTier(naturalness, !naturalnessLimited)
    val f0Score = f0?.let { ((it - 110.0) / 90.0 * 100.0).coerceIn(0.0, 100.0) } ?: 0.0
    val f0Tier = scoreTier(f0Score, !f0Limited)
    return listOf(
        ScoreIndicator(
            label = LocalizedText(R.string.score_label_timbre),
            value = LocalizedText(R.string.score_value_score, listOf(standard)),
            passed = !standardLimited,
            tier = standardTier,
            comparison = previousMetrics?.let {
                ScoreComparison(standard, it.standardScore)
            },
        ),
        ScoreIndicator(
            label = LocalizedText(R.string.score_label_naturalness),
            value = LocalizedText(R.string.score_value_score, listOf(naturalness)),
            passed = !naturalnessLimited,
            tier = naturalnessTier,
            comparison = previousMetrics?.let {
                ScoreComparison(naturalness, it.naturalnessScore)
            },
        ),
        ScoreIndicator(
            label = LocalizedText(R.string.score_label_mean_f0),
            value = f0?.let {
                LocalizedText(R.string.score_value_hz, listOf(it))
            } ?: LocalizedText(R.string.score_value_not_detected),
            passed = !f0Limited,
            tier = f0Tier,
            comparison = if (f0 != null && previousMetrics?.meanF0Hz != null) {
                ScoreComparison(f0, previousMetrics.meanF0Hz, " Hz")
            } else {
                null
            },
        ),
    ).sortedBy { it.passed }
}

private fun masculineScoreIndicators(
    result: PitcheeResult,
    previousMetrics: PreviousMetrics?,
): List<ScoreIndicator> {
    val standard = (100.0 - result.vfp.standardScore).coerceIn(0.0, 100.0)
    val naturalness = result.naturalness.score
    val f0 = result.f0.meanHz
    val standardLimited = standard < 50.0
    val f0Limited = f0 == null || f0 >= 165.0
    val f0Score = f0?.let {
        ((200.0 - it) / 90.0 * 100.0).coerceIn(0.0, 100.0)
    } ?: 0.0
    return listOf(
        ScoreIndicator(
            label = LocalizedText(R.string.score_label_timbre),
            value = LocalizedText(R.string.score_value_score, listOf(standard)),
            passed = !standardLimited,
            tier = scoreTier(standard, !standardLimited),
            comparison = previousMetrics?.let {
                ScoreComparison(standard, (100.0 - it.standardScore).coerceIn(0.0, 100.0))
            },
        ),
        ScoreIndicator(
            label = LocalizedText(R.string.score_label_naturalness),
            value = LocalizedText(R.string.score_value_score, listOf(naturalness)),
            passed = true,
            tier = scoreTier(naturalness, passed = true),
            comparison = previousMetrics?.let {
                ScoreComparison(naturalness, it.naturalnessScore)
            },
        ),
        ScoreIndicator(
            label = LocalizedText(R.string.score_label_mean_f0),
            value = f0?.let {
                LocalizedText(R.string.score_value_hz, listOf(it))
            } ?: LocalizedText(R.string.score_value_not_detected),
            passed = !f0Limited,
            tier = scoreTier(f0Score, !f0Limited),
            comparison = if (f0 != null && previousMetrics?.meanF0Hz != null) {
                ScoreComparison(f0, previousMetrics.meanF0Hz, " Hz")
            } else {
                null
            },
        ),
    ).sortedBy { it.passed }
}

private fun scoreTier(
    score: Double,
    passed: Boolean,
): LocalizedText {
    if (!passed) return LocalizedText(R.string.score_tier_limiter)
    return when (score.coerceAtLeast(50.0)) {
        in 50.0..<60.0 -> LocalizedText(R.string.score_tier_fair)
        in 60.0..<70.0 -> LocalizedText(R.string.score_tier_good)
        in 70.0..<80.0 -> LocalizedText(R.string.score_tier_great)
        else -> LocalizedText(R.string.score_tier_excellent)
    }
}

@Composable
private fun RuleDetails(
    rule: ScoringRuleDoc,
    isCurrent: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        val title = stringResource(rule.titleRes)
        val guidance = stringResource(rule.guidanceRes)
        val condition = stringResource(rule.conditionRes)
        val handling = stringResource(rule.handlingRes)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) {
                    accentColor
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (isCurrent) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.score_rule_current),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = guidance,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.score_rule_condition),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = condition,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.score_rule_formula),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FormulaView(rule.formulas.map(::latexBlock))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.score_rule_result),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = handling,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FormulaView(formulas: List<String>) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary
    val formulaTextSize = with(density) { 17.sp.toPx() }
    val markwon = remember(context, textColor, formulaTextSize) {
        Markwon.builder(context)
            .usePlugin(
                JLatexMathPlugin.create(
                    formulaTextSize,
                    JLatexMathPlugin.BuilderConfigure { builder ->
                        builder.theme()
                            .textColor(textColor)
                            .blockTextColor(textColor)
                            .blockFitCanvas(true)
                            .blockPadding(JLatexMathTheme.Padding.symmetric(6, 12))
                    },
                ),
            )
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = accentColor,
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        formulas.forEachIndexed { index, formula ->
            AndroidView(
                factory = { viewContext ->
                    TextView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        setTextColor(textColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        setLineSpacing(0f, 1.15f)
                        includeFontPadding = false
                        setPadding(0, 0, 0, 0)
                    }
                },
                update = { textView -> markwon.setMarkdown(textView, formula) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (index != formulas.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun latexBlock(body: String): String {
    val lines = body.lines().filter { it.isNotBlank() }
    val content = when {
        lines.size == 1 -> lines.single()
        body.contains("\\begin{") -> body
        else -> "\\begin{gathered}\n${lines.joinToString(" \\\\\n")}\n\\end{gathered}"
    }
    return "\$\$\n$content\n\$\$"
}

private data class ScoringRuleDoc(
    val key: String,
    val titleRes: Int,
    val guidanceRes: Int,
    val conditionRes: Int,
    val formulas: List<String>,
    val handlingRes: Int,
)

private val commonScoreFormulas = listOf(
    """\mathrm{Standard} = \mathrm{TimbreScore}""",
    """\mathrm{Naturalness} \in [0,100]""",
    """F_0 = \overline{f_0}""",
    """S_r = \frac{\mathrm{Standard}}{100}""",
    """\begin{aligned}n &= \frac{\mathrm{Naturalness} - 40}{50} \\ N_r &= \min(1,\max(0,n))\end{aligned}""",
    """F_{0r} = \min\left(1, \max\left(0, \frac{F_0 - 110}{90}\right)\right)""",
    """\begin{aligned}\mathrm{Base} &= 100 \times \bigl(0.50\,S_r + 0.20\,N_r \\ &\quad + 0.15\,F_{0r} + 0.15\,S_r\,N_r\,F_{0r}\bigr)\end{aligned}""",
    """\mathrm{Base} \xrightarrow{\mathrm{rule}} \mathrm{Final}""",
)

private val masculineCommonScoreFormulas = listOf(
    """\mathrm{Standard} = 100 - \mathrm{TimbreScore}""",
    """\mathrm{Naturalness} \in [0,100]""",
    """F_0 = \overline{f_0}""",
    """S_r = \frac{\mathrm{Standard}}{100}""",
    """\begin{aligned}n &= \frac{\mathrm{Naturalness} - 40}{50} \\ N_r &= \min(1,\max(0,n))\end{aligned}""",
    """F_{0r} = \min\left(1, \max\left(0, \frac{200 - F_0}{90}\right)\right)""",
    """\begin{aligned}\mathrm{Base} &= 100 \times \bigl(0.50\,S_r + 0.20\,N_r \\ &\quad + 0.15\,F_{0r} + 0.15\,S_r\,N_r\,F_{0r}\bigr)\end{aligned}""",
    """\mathrm{Base} \xrightarrow{\mathrm{rule}} \mathrm{Final}""",
)

private val scoringRuleDocs = listOf(
    ScoringRuleDoc(
        key = "continuous",
        titleRes = R.string.rule_doc_continuous_title,
        guidanceRes = R.string.rule_doc_continuous_guidance,
        conditionRes = R.string.rule_doc_continuous_condition,
        formulas = listOf("""\mathrm{Final} = \mathrm{Base}"""),
        handlingRes = R.string.rule_doc_continuous_handling,
    ),
    ScoringRuleDoc(
        key = "pass_boost",
        titleRes = R.string.rule_doc_boost_title,
        guidanceRes = R.string.rule_doc_boost_guidance,
        conditionRes = R.string.rule_doc_boost_condition,
        formulas = listOf(
            """s_F = \frac{F_0 - 165}{25}""",
            """s_N = \frac{\mathrm{Naturalness} - 80}{20}""",
            """s_S = \frac{\mathrm{Standard} - 50}{30}""",
            """\mathrm{strength} = \min(s_F, s_N, s_S, 1)""",
            """\mathrm{promoted} = 60 + 40 \times \mathrm{strength}""",
            """\mathrm{Final} = \max(\mathrm{Base}, \mathrm{promoted})""",
        ),
        handlingRes = R.string.rule_doc_boost_handling,
    ),
    ScoringRuleDoc(
        key = "high_f0_stylized_cap",
        titleRes = R.string.rule_doc_high_f0_stylized_title,
        guidanceRes = R.string.rule_doc_high_f0_stylized_guidance,
        conditionRes = R.string.rule_doc_high_f0_stylized_condition,
        formulas = listOf("""\mathrm{Final} = \min(\mathrm{Base}, 30)"""),
        handlingRes = R.string.rule_doc_high_f0_stylized_handling,
    ),
    ScoringRuleDoc(
        key = "low_f0_natural_cap",
        titleRes = R.string.rule_doc_low_f0_natural_title,
        guidanceRes = R.string.rule_doc_low_f0_natural_guidance,
        conditionRes = R.string.rule_doc_low_f0_natural_condition,
        formulas = listOf("""\mathrm{Final} = \min(\mathrm{Base}, 59)"""),
        handlingRes = R.string.rule_doc_low_f0_natural_handling,
    ),
    ScoringRuleDoc(
        key = "low_f0_stylized_cap",
        titleRes = R.string.rule_doc_low_f0_stylized_title,
        guidanceRes = R.string.rule_doc_low_f0_stylized_guidance,
        conditionRes = R.string.rule_doc_low_f0_stylized_condition,
        formulas = listOf("""\mathrm{Final} = \min(\mathrm{Base}, 20)"""),
        handlingRes = R.string.rule_doc_low_f0_stylized_handling,
    ),
    ScoringRuleDoc(
        key = "high_f0_male_cap",
        titleRes = R.string.rule_doc_high_f0_male_title,
        guidanceRes = R.string.rule_doc_high_f0_male_guidance,
        conditionRes = R.string.rule_doc_high_f0_male_condition,
        formulas = listOf("""\mathrm{Final} = \min(\mathrm{Base}, 59)"""),
        handlingRes = R.string.rule_doc_high_f0_male_handling,
    ),
    ScoringRuleDoc(
        key = "f0_unavailable",
        titleRes = R.string.rule_doc_f0_unavailable_title,
        guidanceRes = R.string.rule_doc_f0_unavailable_guidance,
        conditionRes = R.string.rule_doc_f0_unavailable_condition,
        formulas = listOf("""\mathrm{Final} = \mathrm{Standard}"""),
        handlingRes = R.string.rule_doc_f0_unavailable_handling,
    ),
)

private val masculineScoringRuleDocs = listOf(
    ScoringRuleDoc(
        key = "continuous",
        titleRes = R.string.rule_doc_masculine_continuous_title,
        guidanceRes = R.string.rule_doc_masculine_continuous_guidance,
        conditionRes = R.string.rule_doc_masculine_continuous_condition,
        formulas = listOf("""\mathrm{Final} = \mathrm{Base}"""),
        handlingRes = R.string.rule_doc_masculine_continuous_handling,
    ),
)

@Composable
private fun BottleneckCard(
    insight: ScoreInsight,
    onOpenRules: () -> Unit,
) {
    val containerColor = if (insight.hasBottleneck) {
        ruleRedContainer()
    } else {
        ruleGreenContainer()
    }
    val contentColor = if (insight.hasBottleneck) {
        ruleRedContent()
    } else {
        ruleGreenContent()
    }

    InsightCard(
        label = stringResource(R.string.score_main_bottleneck),
        title = insight.bottleneckTitle?.resolve().orEmpty(),
        description = insight.bottleneckDescription?.resolve().orEmpty(),
        containerColor = containerColor,
        contentColor = contentColor,
        actionContainerColor = if (insight.hasBottleneck) DeepRed else DeepGreen,
        actionContentColor = Color.White,
        onOpenRules = onOpenRules,
    )
}

@Composable
private fun MetricsCard(
    result: PitcheeResult,
    insight: ScoreInsight,
    previousMetrics: PreviousMetrics?,
    animate: Boolean,
    languageCode: String,
) {
    val cardColor = MaterialTheme.colorScheme.tertiaryContainer
    val toggleColor = MaterialTheme.colorScheme.tertiary
    val toggleContentColor = MaterialTheme.colorScheme.onTertiary
    val masculineProfile = result.scoreProfile == PitcheeScoreProfile.MASCULINIZATION
    val currentStandard = if (masculineProfile) {
        100.0 - result.vfp.standardScore
    } else {
        result.vfp.standardScore
    }
    val currentNaturalness = result.naturalness.score
    val currentF0 = result.f0.meanHz
    val previousStandard = previousMetrics?.standardScore?.let {
        if (masculineProfile) 100.0 - it else it
    } ?: 0.0
    val previousNaturalness = previousMetrics?.naturalnessScore ?: 0.0
    val previousF0 = previousMetrics?.meanF0Hz ?: 0.0
    val standardAnimation = remember { Animatable(previousStandard.toFloat()) }
    val naturalnessAnimation = remember { Animatable(previousNaturalness.toFloat()) }
    val f0Animation = remember { Animatable(previousF0.toFloat()) }
    val compareInteraction = remember { MutableInteractionSource() }
    val comparing by compareInteraction.collectIsPressedAsState()
    val comparisonActive = comparing && previousMetrics != null

    LaunchedEffect(result, previousMetrics, animate) {
        standardAnimation.stop()
        naturalnessAnimation.stop()
        f0Animation.stop()
        standardAnimation.snapTo(previousStandard.toFloat())
        naturalnessAnimation.snapTo(previousNaturalness.toFloat())
        f0Animation.snapTo(previousF0.toFloat())
        if (!animate) {
            standardAnimation.snapTo(currentStandard.toFloat())
            naturalnessAnimation.snapTo(currentNaturalness.toFloat())
            f0Animation.snapTo((currentF0 ?: 0.0).toFloat())
            return@LaunchedEffect
        }

        delay(500)
        val metricSpring = spring(
            dampingRatio = 0.62f,
            stiffness = 180f,
            visibilityThreshold = 0.01f,
        )
        launch {
            standardAnimation.animateTo(
                targetValue = currentStandard.toFloat(),
                animationSpec = metricSpring,
            )
        }
        launch {
            naturalnessAnimation.animateTo(
                targetValue = currentNaturalness.toFloat(),
                animationSpec = metricSpring,
            )
        }
        launch {
            f0Animation.animateTo(
                targetValue = (currentF0 ?: 0.0).toFloat(),
                animationSpec = metricSpring,
            )
        }
    }

    val standardProgress = if (comparisonActive) {
        (previousStandard / 100.0).toFloat()
    } else {
        (standardAnimation.value / 100f).coerceIn(0f, 1f)
    }
    val naturalnessProgress = if (comparisonActive) {
        (previousNaturalness / 100.0).toFloat()
    } else {
        (naturalnessAnimation.value / 100f).coerceIn(0f, 1f)
    }
    val f0ProgressValue = if (comparisonActive) previousF0 else f0Animation.value.toDouble()
    val f0Progress = (
        f0ProgressScore(f0ProgressValue, result.scoreProfile) / 100.0
        ).toFloat()
    val standardForColor =
        if (comparisonActive) previousStandard else standardAnimation.value.toDouble()
    val naturalnessForColor =
        if (comparisonActive) previousNaturalness else naturalnessAnimation.value.toDouble()
    val f0ForColor = f0ProgressScore(f0ProgressValue, result.scoreProfile)

    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = cardColor,
            ),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.score_metrics_toggle),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = {},
                        interactionSource = compareInteraction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = toggleColor,
                            contentColor = toggleContentColor,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.score_compare),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    MetricRow(
                        label = stringResource(R.string.score_label_timbre),
                        value = if (comparisonActive) {
                            "%.1f".format(previousStandard)
                        } else {
                            "%.1f".format(currentStandard)
                        },
                        progress = standardProgress,
                        highlighted = insight.bottleneckMetric == ScoreMetric.TIMBRE,
                        score = standardForColor,
                    )
                    Spacer(Modifier.height(14.dp))
                    MetricRow(
                        label = stringResource(R.string.score_label_naturalness),
                        value = if (comparisonActive) {
                            "%.1f".format(previousNaturalness)
                        } else {
                            "%.1f".format(currentNaturalness)
                        },
                        progress = naturalnessProgress,
                        highlighted = insight.bottleneckMetric == ScoreMetric.NATURALNESS,
                        score = naturalnessForColor,
                    )
                    Spacer(Modifier.height(14.dp))
                    MetricRow(
                        label = stringResource(R.string.score_label_mean_f0),
                        value = if (comparisonActive) {
                            previousMetrics?.meanF0Hz?.let { "%.0f Hz".format(it) } ?: stringResource(R.string.score_value_not_detected)
                        } else {
                            currentF0?.let { "%.0f Hz".format(it) } ?: stringResource(R.string.score_value_not_detected)
                        },
                        progress = f0Progress,
                        highlighted = insight.bottleneckMetric == ScoreMetric.F0,
                        score = f0ForColor,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (masculineProfile) {
                                R.string.score_f0_range_note_masculine
                            } else {
                                R.string.score_f0_range_note
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (comparing && previousMetrics == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.score_no_previous_metrics),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun f0ProgressScore(
    f0Hz: Double,
    scoreProfile: PitcheeScoreProfile = PitcheeScoreProfile.FEMINIZATION,
): Double = if (scoreProfile == PitcheeScoreProfile.MASCULINIZATION) {
    min(100.0, max(0.0, (200.0 - f0Hz) / 90.0 * 100.0))
} else {
    min(100.0, max(0.0, (f0Hz - 110.0) / 90.0 * 100.0))
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    progress: Float,
    highlighted: Boolean,
    score: Double,
) {
    val color = feminineScoreColor(score)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(9.dp)) { drawCircle(color) }
                Spacer(Modifier.width(7.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (highlighted) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.18f),
                        contentColor = color,
                    ) {
                        Text(
                            text = stringResource(R.string.score_lowest),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.14f),
        )
    }
}
