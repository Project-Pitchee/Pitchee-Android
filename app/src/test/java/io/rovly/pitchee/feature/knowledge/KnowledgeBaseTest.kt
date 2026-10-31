package io.rovly.pitchee.feature.knowledge

import io.rovly.pitchee.ui.common.ScoreMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.pitchee.core.PitcheeScoreProfile

class KnowledgeBaseTest {
    @Test
    fun mapsEveryImplementedFeminineRuleToAnArticle() {
        val rules = mapOf(
            "continuous" to KnowledgeArticleKey.CONTINUOUS_PASS,
            "pass_boost" to KnowledgeArticleKey.PASS_BOOST,
            "high_f0_stylized_cap" to KnowledgeArticleKey.HIGH_F0_STYLIZED,
            "low_f0_natural_cap" to KnowledgeArticleKey.LOW_F0_NATURAL,
            "low_f0_stylized_cap" to KnowledgeArticleKey.LOW_F0_STYLIZED,
            "high_f0_male_cap" to KnowledgeArticleKey.HIGH_F0_TIMBRE,
        )

        rules.forEach { (rule, article) ->
            assertEquals(
                article,
                KnowledgeBaseCatalog.forRule(
                    PitcheeScoreProfile.FEMINIZATION,
                    rule,
                    finalScore = 72.0,
                ),
            )
        }
    }

    @Test
    fun choosesTheContinuousArticleFromTheFinalScore() {
        assertEquals(
            KnowledgeArticleKey.CONTINUOUS_NO_PASS,
            KnowledgeBaseCatalog.forRule(
                PitcheeScoreProfile.FEMINIZATION,
                "continuous",
                finalScore = 59.9,
            ),
        )
        assertEquals(
            KnowledgeArticleKey.CONTINUOUS_PASS,
            KnowledgeBaseCatalog.forRule(
                PitcheeScoreProfile.FEMINIZATION,
                "continuous",
                finalScore = 60.0,
            ),
        )
    }

    @Test
    fun mapsMetricHeadingsToPracticeGuides() {
        assertEquals(
            KnowledgeArticleKey.TIMBRE_GUIDE,
            KnowledgeBaseCatalog.forMetric(ScoreMetric.TIMBRE),
        )
        assertEquals(
            KnowledgeArticleKey.NATURALNESS_GUIDE,
            KnowledgeBaseCatalog.forMetric(ScoreMetric.NATURALNESS),
        )
        assertEquals(
            KnowledgeArticleKey.PITCH_GUIDE,
            KnowledgeBaseCatalog.forMetric(ScoreMetric.F0),
        )
    }

    @Test
    fun parsesTitleSummaryAndBodyFromMarkdown() {
        val article = parseKnowledgeMarkdown(
            KnowledgeArticleKey.TIMBRE_GUIDE,
            """
                # 音色分低：先别硬夹

                音色分低，说明这段录音整体听起来还不够女性化。

                ## 怎么练
                正文。

                相关阅读：[自然度练习](../practice/02-naturalness.md)
            """.trimIndent(),
        )

        assertEquals("音色分低：先别硬夹", article.title)
        assertEquals(
            "音色分低，说明这段录音整体听起来还不够女性化。 怎么练 正文。",
            article.summary,
        )
        assertTrue(article.markdownBody.contains("正文。"))
        assertEquals(
            listOf(
                RelatedKnowledgeArticle(
                    "自然度练习",
                    KnowledgeArticleKey.NATURALNESS_GUIDE,
                ),
            ),
            article.relatedArticles,
        )
    }

    @Test
    fun resolvesRelatedReadingLinksToLocalArticles() {
        assertEquals(
            KnowledgeArticleKey.NATURALNESS_GUIDE,
            KnowledgeBaseCatalog.fromArticleLink(
                "../../practice/zh_cn/02-naturalness.md",
            ),
        )
        assertEquals(
            KnowledgeArticleKey.CONTINUOUS_PASS,
            KnowledgeBaseCatalog.fromArticleLink(
                "../../rules/en/02-feminization-continuous-pass.md",
            ),
        )
    }

    @Test
    fun selectsArticleLanguageFromTheAppLocale() {
        assertEquals(
            KnowledgeContentLanguage.CHINESE,
            KnowledgeContentLanguage.fromLanguageCode("zh"),
        )
        assertEquals(
            KnowledgeContentLanguage.CHINESE,
            KnowledgeContentLanguage.fromLanguageCode("zh-Hant"),
        )
        assertEquals(
            KnowledgeContentLanguage.ENGLISH,
            KnowledgeContentLanguage.fromLanguageCode("ja"),
        )
        assertEquals(
            "knowledge/rules/03-pass-boost.md",
            KnowledgeArticleKey.PASS_BOOST.assetPath(KnowledgeContentLanguage.CHINESE),
        )
        assertEquals(
            "knowledge/en/rules/03-pass-boost.md",
            KnowledgeArticleKey.PASS_BOOST.assetPath(KnowledgeContentLanguage.ENGLISH),
        )
    }

    @Test
    fun parsesEnglishRelatedReading() {
        val article = parseKnowledgeMarkdown(
            KnowledgeArticleKey.HIGH_F0_TIMBRE,
            """
                # High Pitch and Low Timbre Score

                Body text.

                Related reading: [Low timbre score](../../practice/en/01-vfp.md).
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                RelatedKnowledgeArticle(
                    "Low timbre score",
                    KnowledgeArticleKey.TIMBRE_GUIDE,
                ),
            ),
            article.relatedArticles,
        )
        assertTrue(!article.markdownBody.contains("Related reading"))
    }
}
