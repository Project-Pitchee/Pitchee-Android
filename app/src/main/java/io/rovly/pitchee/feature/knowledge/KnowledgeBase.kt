package io.rovly.pitchee.feature.knowledge

import android.content.Context
import androidx.annotation.StringRes
import io.rovly.pitchee.R
import io.rovly.pitchee.ui.common.ScoreMetric
import space.pitchee.core.PitcheeScoreProfile

internal enum class KnowledgeCategory(
    @param:StringRes val titleRes: Int,
) {
    RULES(R.string.knowledge_category_rules),
    PRACTICE(R.string.knowledge_category_practice),
}

internal enum class KnowledgeArticleKey(
    val category: KnowledgeCategory,
    val relativePath: String,
) {
    CONTINUOUS_NO_PASS(
        KnowledgeCategory.RULES,
        "rules/01-feminization-continuous-nopass.md",
    ),
    CONTINUOUS_PASS(
        KnowledgeCategory.RULES,
        "rules/02-feminization-continuous-pass.md",
    ),
    PASS_BOOST(
        KnowledgeCategory.RULES,
        "rules/03-pass-boost.md",
    ),
    HIGH_F0_STYLIZED(
        KnowledgeCategory.RULES,
        "rules/04-high-f0-stylized-cap.md",
    ),
    LOW_F0_NATURAL(
        KnowledgeCategory.RULES,
        "rules/05-low-f0-natural-cap.md",
    ),
    LOW_F0_STYLIZED(
        KnowledgeCategory.RULES,
        "rules/06-low-f0-stylized-cap.md",
    ),
    HIGH_F0_TIMBRE(
        KnowledgeCategory.RULES,
        "rules/07-high-f0-male-cap.md",
    ),
    TIMBRE_GUIDE(
        KnowledgeCategory.PRACTICE,
        "practice/01-vfp.md",
    ),
    NATURALNESS_GUIDE(
        KnowledgeCategory.PRACTICE,
        "practice/02-naturalness.md",
    ),
    PITCH_GUIDE(
        KnowledgeCategory.PRACTICE,
        "practice/03-low-f0.md",
    ),
}

internal enum class KnowledgeContentLanguage(
    val assetFolder: String?,
) {
    CHINESE(null),
    ENGLISH("en"),
    ;

    companion object {
        fun fromLanguageCode(languageCode: String): KnowledgeContentLanguage =
            if (languageCode.startsWith("zh", ignoreCase = true)) CHINESE else ENGLISH
    }
}

internal data class KnowledgeArticle(
    val key: KnowledgeArticleKey,
    val title: String,
    val summary: String,
    val markdownBody: String,
    val relatedArticles: List<RelatedKnowledgeArticle>,
)

internal data class RelatedKnowledgeArticle(
    val label: String,
    val key: KnowledgeArticleKey,
)

internal object KnowledgeBaseCatalog {
    val all: List<KnowledgeArticleKey> = KnowledgeArticleKey.entries

    fun forCategory(category: KnowledgeCategory): List<KnowledgeArticleKey> =
        all.filter { it.category == category }

    fun forRule(
        scoreProfile: PitcheeScoreProfile,
        rule: String,
        finalScore: Double,
    ): KnowledgeArticleKey? {
        if (scoreProfile == PitcheeScoreProfile.MASCULINIZATION) return null
        return when (rule) {
            "continuous" -> if (finalScore >= 60.0) {
                KnowledgeArticleKey.CONTINUOUS_PASS
            } else {
                KnowledgeArticleKey.CONTINUOUS_NO_PASS
            }
            "pass_boost" -> KnowledgeArticleKey.PASS_BOOST
            "high_f0_stylized_cap" -> KnowledgeArticleKey.HIGH_F0_STYLIZED
            "low_f0_natural_cap" -> KnowledgeArticleKey.LOW_F0_NATURAL
            "low_f0_stylized_cap" -> KnowledgeArticleKey.LOW_F0_STYLIZED
            "high_f0_male_cap" -> KnowledgeArticleKey.HIGH_F0_TIMBRE
            else -> null
        }
    }

    fun forMetric(metric: ScoreMetric): KnowledgeArticleKey = when (metric) {
        ScoreMetric.TIMBRE -> KnowledgeArticleKey.TIMBRE_GUIDE
        ScoreMetric.NATURALNESS -> KnowledgeArticleKey.NATURALNESS_GUIDE
        ScoreMetric.F0 -> KnowledgeArticleKey.PITCH_GUIDE
    }

    fun fromArticleLink(link: String): KnowledgeArticleKey? {
        val normalized = link
            .substringBefore('#')
            .substringBefore('?')
            .removePrefix("./")
            .removePrefix("../")
            .removePrefix("../")
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val category = parts.first()
        val fileName = parts.last()
        return all.firstOrNull { it.relativePath == "$category/$fileName" }
    }
}

internal object KnowledgeArticleLoader {
    private val cache = mutableMapOf<Pair<KnowledgeArticleKey, KnowledgeContentLanguage>, KnowledgeArticle>()

    @Synchronized
    fun load(
        context: Context,
        key: KnowledgeArticleKey,
        language: KnowledgeContentLanguage,
    ): KnowledgeArticle? {
        val cacheKey = key to language
        cache[cacheKey]?.let { return it }
        return runCatching {
            val markdown = context.assets
                .open(key.assetPath(language))
                .bufferedReader()
                .use { it.readText() }
            parseKnowledgeMarkdown(key, markdown).also { cache[cacheKey] = it }
        }.getOrNull()
    }
}

internal fun KnowledgeArticleKey.assetPath(language: KnowledgeContentLanguage): String =
    buildString {
        append("knowledge/")
        language.assetFolder?.let {
            append(it)
            append('/')
        }
        append(relativePath)
    }

internal fun parseKnowledgeMarkdown(
    key: KnowledgeArticleKey,
    rawMarkdown: String,
): KnowledgeArticle {
    val normalized = rawMarkdown.replace("\r\n", "\n").trim()
    val lines = normalized.lines()
    val titleIndex = lines.indexOfFirst { it.startsWith("# ") }
    val title = if (titleIndex >= 0) {
        lines[titleIndex].removePrefix("# ").trim()
    } else {
        key.name
    }
    val bodyLines = if (titleIndex >= 0) {
        lines.filterIndexed { index, _ -> index != titleIndex }
    } else {
        lines
    }
    val relatedArticles = mutableListOf<RelatedKnowledgeArticle>()
    val contentLines = bodyLines.filterNot { line ->
        val isRelatedReadingLine = line.contains("相关阅读") ||
            line.contains("Related reading", ignoreCase = true)
        if (!isRelatedReadingLine) return@filterNot false
        RELATED_LINK_PATTERN.findAll(line).forEach { match ->
            val label = match.groupValues[1]
            val keyForLink = KnowledgeBaseCatalog.fromArticleLink(match.groupValues[2])
            if (keyForLink != null) {
                relatedArticles += RelatedKnowledgeArticle(label, keyForLink)
            }
        }
        true
    }
    val summary = markdownPlainText(contentLines)
    return KnowledgeArticle(
        key = key,
        title = title.ifBlank { key.name },
        summary = summary,
        markdownBody = contentLines.joinToString("\n").trim(),
        relatedArticles = relatedArticles.distinctBy { it.key },
    )
}

private val RELATED_LINK_PATTERN = Regex("""\[([^]]+)]\(([^)]+)\)""")

private fun markdownPlainText(lines: List<String>): String =
    lines.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "---" }
        .map { it.replace(Regex("""^#{1,6}\s*"""), "") }
        .map { it.replace(Regex("""^([-*+]|\d+\.)\s+"""), "") }
        .map { it.replace(Regex("""^>\s*"""), "") }
        .joinToString(" ")
        .replace(Regex("""!\[([^]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""\[([^]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""[`*_]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
