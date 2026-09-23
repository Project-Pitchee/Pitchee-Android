package io.rovly.pitchee.ui

import io.rovly.pitchee.R
import io.rovly.pitchee.data.PitcheeResult
import kotlin.math.max
import kotlin.math.min

enum class ScoreRuleState {
    CONTINUOUS,
    CAPPED,
    BOOSTED,
    F0_UNAVAILABLE,
}

internal data class ScoreInsight(
    val ruleName: LocalizedText,
    val ruleDescription: LocalizedText,
    val ruleImpact: LocalizedText,
    val ruleState: ScoreRuleState,
    val hasBottleneck: Boolean,
    val bottleneckTitle: LocalizedText?,
    val bottleneckDescription: LocalizedText?,
    val bottleneckMetric: ScoreMetric?,
) {
    companion object {
        fun from(result: PitcheeResult): ScoreInsight {
            val (ruleName, ruleDescription, ruleImpact, ruleState) = ruleSummary(result)
            val bottleneck = findBottleneck(result)
            return ScoreInsight(
                ruleName = ruleName,
                ruleDescription = ruleDescription,
                ruleImpact = ruleImpact,
                ruleState = ruleState,
                hasBottleneck = bottleneck != null,
                bottleneckTitle = bottleneck?.title,
                bottleneckDescription = bottleneck?.description,
                bottleneckMetric = bottleneck?.metric,
            )
        }

        private fun ruleSummary(result: PitcheeResult): RuleSummary {
            val composite = result.composite
            return when (composite.rule) {
                "f0_unavailable" -> RuleSummary(
                    ruleName = LocalizedText(R.string.insight_rule_f0_unavailable),
                    ruleDescription = LocalizedText(R.string.insight_desc_f0_unavailable),
                    ruleImpact = LocalizedText(R.string.insight_impact_timbre),
                    ruleState = ScoreRuleState.F0_UNAVAILABLE,
                )
                "pass_boost" -> RuleSummary(
                    ruleName = LocalizedText(R.string.insight_rule_qualified_boost),
                    ruleDescription = LocalizedText(R.string.insight_desc_qualified_boost),
                    ruleImpact = if (composite.boosted) {
                        LocalizedText(
                            R.string.insight_impact_boost_format,
                            listOf(composite.finalScore - composite.baseScore),
                        )
                    } else {
                        LocalizedText(R.string.insight_impact_boost_met)
                    },
                    ruleState = ScoreRuleState.BOOSTED,
                )
                "high_f0_stylized_cap" -> cappedRule(
                    name = LocalizedText(R.string.insight_rule_high_f0_low_naturalness),
                    cap = composite.cap,
                    description = LocalizedText(
                        R.string.insight_desc_high_f0_low_naturalness,
                    ),
                    result = result,
                )
                "low_f0_natural_cap" -> cappedRule(
                    name = LocalizedText(R.string.insight_rule_low_f0),
                    cap = composite.cap,
                    description = LocalizedText(R.string.insight_desc_low_f0),
                    result = result,
                )
                "low_f0_stylized_cap" -> cappedRule(
                    name = LocalizedText(R.string.insight_rule_low_f0_low_naturalness),
                    cap = composite.cap,
                    description = LocalizedText(
                        R.string.insight_desc_low_f0_low_naturalness,
                    ),
                    result = result,
                )
                "high_f0_male_cap" -> cappedRule(
                    name = LocalizedText(R.string.insight_rule_timbre_cap),
                    cap = composite.cap,
                    description = LocalizedText(R.string.insight_desc_timbre_cap),
                    result = result,
                )
                else -> RuleSummary(
                    ruleName = LocalizedText(R.string.insight_rule_continuous),
                    ruleDescription = LocalizedText(R.string.insight_desc_continuous),
                    ruleImpact = LocalizedText(R.string.insight_impact_no_cap_boost),
                    ruleState = ScoreRuleState.CONTINUOUS,
                )
            }
        }

        private fun cappedRule(
            name: LocalizedText,
            cap: Double?,
            description: LocalizedText,
            result: PitcheeResult,
        ): RuleSummary {
            val reduction = result.composite.baseScore - result.composite.finalScore
            val capText = cap?.let { "%.0f".format(it) } ?: "--"
            return RuleSummary(
                ruleName = name,
                ruleDescription = description,
                ruleImpact = if (result.composite.limited && reduction > 0.05) {
                    LocalizedText(
                        R.string.insight_impact_cap_reduced_format,
                        listOf(capText, reduction),
                    )
                } else {
                    LocalizedText(
                        R.string.insight_impact_cap_unchanged_format,
                        listOf(capText),
                    )
                },
                ruleState = ScoreRuleState.CAPPED,
            )
        }

        private fun findBottleneck(result: PitcheeResult): Bottleneck? =
            when (result.composite.rule) {
                "f0_unavailable" -> Bottleneck(
                    title = LocalizedText(R.string.insight_rule_f0_unavailable),
                    description = LocalizedText(
                        R.string.insight_desc_bottleneck_f0_unavailable,
                    ),
                    metric = ScoreMetric.F0,
                    value = 0.0,
                )
                "high_f0_stylized_cap" -> Bottleneck(
                    title = LocalizedText(R.string.insight_bottleneck_naturalness),
                    description = LocalizedText(
                        R.string.insight_desc_bottleneck_naturalness,
                    ),
                    metric = ScoreMetric.NATURALNESS,
                    value = result.naturalness.score,
                )
                "low_f0_natural_cap" -> Bottleneck(
                    title = LocalizedText(R.string.insight_bottleneck_f0),
                    description = LocalizedText(R.string.insight_desc_bottleneck_f0),
                    metric = ScoreMetric.F0,
                    value = result.f0.meanHz ?: 0.0,
                )
                "low_f0_stylized_cap" -> Bottleneck(
                    title = LocalizedText(R.string.insight_bottleneck_f0_naturalness),
                    description = LocalizedText(
                        R.string.insight_desc_bottleneck_f0_naturalness,
                    ),
                    metric = ScoreMetric.NATURALNESS,
                    value = result.naturalness.score,
                )
                "high_f0_male_cap" -> Bottleneck(
                    title = LocalizedText(R.string.insight_bottleneck_timbre),
                    description = LocalizedText(R.string.insight_desc_bottleneck_timbre),
                    metric = ScoreMetric.TIMBRE,
                    value = result.vfp.standardScore,
                )
                "pass_boost" -> null
                else -> continuousBottleneck(result)
            }

        private fun continuousBottleneck(result: PitcheeResult): Bottleneck? {
            val standard = result.vfp.standardScore
            val naturalness = result.naturalness.score
            val f0 = result.f0.meanHz
            val f0Score = f0?.let {
                min(100.0, max(0.0, (it - 110.0) / 90.0 * 100.0))
            }
            val issues = buildList {
                if (f0Score == null) {
                    add(
                        Bottleneck(
                            title = LocalizedText(R.string.insight_rule_f0_unavailable),
                            description = LocalizedText(
                                R.string.insight_desc_continuous_f0_unavailable,
                            ),
                            metric = ScoreMetric.F0,
                            value = 0.0,
                        )
                    )
                } else if (f0 <= 165.0) {
                    add(
                        Bottleneck(
                            title = LocalizedText(R.string.insight_bottleneck_f0),
                            description = LocalizedText(
                                R.string.insight_desc_continuous_f0,
                            ),
                            metric = ScoreMetric.F0,
                            value = f0Score,
                        )
                    )
                }
                if (standard < 50.0) {
                    add(
                        Bottleneck(
                            title = LocalizedText(R.string.insight_bottleneck_timbre),
                            description = LocalizedText(
                                R.string.insight_desc_continuous_timbre,
                            ),
                            metric = ScoreMetric.TIMBRE,
                            value = standard,
                        )
                    )
                }
                if (naturalness < 50.0) {
                    add(
                        Bottleneck(
                            title = LocalizedText(R.string.insight_bottleneck_naturalness),
                            description = LocalizedText(
                                R.string.insight_desc_continuous_naturalness,
                            ),
                            metric = ScoreMetric.NATURALNESS,
                            value = naturalness,
                        )
                    )
                }
            }
            return issues.minByOrNull { it.value }
        }
    }
}

private data class Bottleneck(
    val title: LocalizedText,
    val description: LocalizedText,
    val metric: ScoreMetric,
    val value: Double,
)

private data class RuleSummary(
    val ruleName: LocalizedText,
    val ruleDescription: LocalizedText,
    val ruleImpact: LocalizedText,
    val ruleState: ScoreRuleState,
)
