package io.rovly.pitchee.feature.knowledge

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.rovly.pitchee.R
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.ScreenTransition
import io.rovly.pitchee.ui.common.SwipeBackContainer
import kotlin.math.abs

internal const val KNOWLEDGE_LIBRARY_ROUTE = "library"

internal fun KnowledgeArticleKey.routeToken(): String = "article:$name"

internal fun knowledgeArticleFromRoute(route: String): KnowledgeArticleKey? =
    route.removePrefix("article:").takeIf { route.startsWith("article:") }?.let { name ->
        runCatching { KnowledgeArticleKey.valueOf(name) }.getOrNull()
    }

@Composable
internal fun KnowledgeBaseScreen(
    initialArticleKey: KnowledgeArticleKey? = null,
    onBack: () -> Unit,
) {
    val articlePath = rememberSaveable(
        initialArticleKey,
        saver = listSaver(
            save = { path -> path.map { it.name } },
            restore = { names ->
                names.mapNotNull { name ->
                    runCatching { KnowledgeArticleKey.valueOf(name) }.getOrNull()
                }.toMutableStateList()
            },
        ),
    ) {
        mutableStateListOf<KnowledgeArticleKey>().apply {
            initialArticleKey?.let(::add)
        }
    }
    var navigatingForward by remember { mutableStateOf(true) }
    var zStep by remember { mutableFloatStateOf(1f) }
    val selectedArticle = articlePath.lastOrNull()

    fun openArticle(key: KnowledgeArticleKey) {
        if (key == selectedArticle) return
        zStep = abs(zStep) + 1f
        navigatingForward = true
        articlePath.add(key)
    }

    fun closeCurrentPage() {
        if (articlePath.isEmpty() || (articlePath.size == 1 && initialArticleKey != null)) {
            onBack()
        } else {
            zStep = -abs(zStep) - 1f
            navigatingForward = false
            articlePath.removeAt(articlePath.lastIndex)
        }
    }

    BackHandler(onBack = ::closeCurrentPage)
    SwipeBackContainer(enabled = true, onBack = ::closeCurrentPage) {
        ScreenTransition(
            targetState = selectedArticle?.name,
            forward = navigatingForward,
            contentZIndex = if (navigatingForward) abs(zStep) else -abs(zStep),
            modifier = Modifier.fillMaxSize(),
            label = "knowledge-base-page",
        ) { articleName ->
            if (articleName == null) {
                KnowledgeBaseHome(
                    onBack = onBack,
                    onOpenArticle = ::openArticle,
                )
            } else {
                val key = runCatching { KnowledgeArticleKey.valueOf(articleName) }.getOrNull()
                if (key == null) {
                    KnowledgeMissingArticle(onBack = ::closeCurrentPage)
                } else {
                    KnowledgeArticlePage(
                        articleKey = key,
                        onBack = ::closeCurrentPage,
                        onOpenArticle = ::openArticle,
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeBaseHome(
    onBack: () -> Unit,
    onOpenArticle: (KnowledgeArticleKey) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenTitle(
                    title = stringResource(R.string.knowledge_base_title),
                    onBack = onBack,
                )
                Spacer(Modifier.height(12.dp))
            }
            KnowledgeCategory.entries.forEach { category ->
                item(key = "header-${category.name}") {
                    Text(
                        text = stringResource(category.titleRes),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(
                    items = KnowledgeBaseCatalog.forCategory(category),
                    key = { it.name },
                ) { articleKey ->
                    KnowledgeArticleRow(
                        articleKey = articleKey,
                        onClick = { onOpenArticle(articleKey) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = {
                        uriHandler.openUri(
                            "https://github.com/Project-Pitchee/Articles",
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.knowledge_article_source))
                }
            }
        }
    }
}

@Composable
private fun KnowledgeArticleRow(
    articleKey: KnowledgeArticleKey,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val articleLanguage = remember(locale) {
        KnowledgeContentLanguage.fromLanguageCode(locale.language)
    }
    val article = remember(articleKey, articleLanguage) {
        KnowledgeArticleLoader.load(context.applicationContext, articleKey, articleLanguage)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = article?.title ?: stringResource(R.string.knowledge_article_load_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!article?.summary.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun KnowledgeArticlePage(
    articleKey: KnowledgeArticleKey,
    onBack: () -> Unit,
    onOpenArticle: (KnowledgeArticleKey) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val articleLanguage = remember(locale) {
        KnowledgeContentLanguage.fromLanguageCode(locale.language)
    }
    val article = remember(articleKey, articleLanguage) {
        KnowledgeArticleLoader.load(context.applicationContext, articleKey, articleLanguage)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (article == null) {
            KnowledgeMissingArticle(onBack = onBack)
            return@Surface
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            ScreenTitle(title = article.title, onBack = onBack)
            Spacer(Modifier.height(18.dp))
            MarkdownArticleBody(markdown = article.markdownBody)
            if (article.relatedArticles.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.knowledge_related_reading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                article.relatedArticles.forEach { related ->
                    Surface(
                        onClick = { onOpenArticle(related.key) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 14.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = related.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MarkdownArticleBody(markdown: String) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val markwon = remember(context, textColor) { Markwon.create(context) }

    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setHintTextColor(secondaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.35f)
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            markwon.setMarkdown(textView, markdown)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KnowledgeMissingArticle(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        ScreenTitle(
            title = stringResource(R.string.knowledge_base_title),
            onBack = onBack,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_book),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.knowledge_article_load_failed),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.knowledge_article_load_failed_desc),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}
