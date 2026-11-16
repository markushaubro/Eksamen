package dk.markus.fcmnews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Viser forsiden med emnebjælken og listen af artikler.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    articles: List<Article>,
    selectedTopic: String?,
    subscribedTopics: Set<String>,
    isLoading: Boolean,
    loadFailed: Boolean,
    showPermissionCard: Boolean,
    onTopicSelected: (String?) -> Unit,
    onBellClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (Article) -> Unit,
    onAllowNotifications: () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("FcmNews") })
                TopicBar(selectedTopic, subscribedTopics, onTopicSelected, onBellClick)
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showPermissionCard) {
                    item { NotificationPermissionCard(onAllowNotifications) }
                }
                if (loadFailed) {
                    item { InfoText("Kunne ikke hente artikler. Træk ned for at prøve igen.") }
                } else if (articles.isEmpty() && !isLoading) {
                    item { InfoText("Der er ingen artikler endnu.") }
                }
                items(articles, key = { it.id }) { article ->
                    ArticleCard(article, onClick = { onArticleClick(article) })
                }
            }
        }
    }
}

// Viser emnerne man kan vælge imellem og en klokke til at abonnere på det valgte emne.
@Composable
private fun TopicBar(
    selectedTopic: String?,
    subscribedTopics: Set<String>,
    onTopicSelected: (String?) -> Unit,
    onBellClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedTopic == null,
                    onClick = { onTopicSelected(null) },
                    label = { Text("Alle") }
                )
            }
            items(UserSync.availableTopics) { topic ->
                FilterChip(
                    selected = topic == selectedTopic,
                    onClick = { onTopicSelected(topic) },
                    label = { Text(topic.displayName()) },
                    leadingIcon = if (topic in subscribedTopics) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_bell),
                                contentDescription = "Abonnerer",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }

        if (selectedTopic != null) {
            val subscribed = selectedTopic in subscribedTopics
            IconButton(onClick = { onBellClick(selectedTopic) }) {
                Icon(
                    painterResource(if (subscribed) R.drawable.ic_bell else R.drawable.ic_bell_outline),
                    contentDescription = if (subscribed) {
                        "Stop abonnement på ${selectedTopic.displayName()}"
                    } else {
                        "Abonnér på ${selectedTopic.displayName()}"
                    },
                    tint = if (subscribed) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }
    }
}

// Viser en artikel i listen.
@Composable
private fun ArticleCard(article: Article, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            ArticleImage(
                article,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Column(Modifier.padding(16.dp)) {
                Text(
                    article.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    article.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Viser hele artiklen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(article: Article?, loadFailed: Boolean, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.category?.displayName() ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Tilbage")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            article != null -> Column(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                ArticleImage(
                    article,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                Column(Modifier.padding(16.dp)) {
                    Text(
                        article.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(article.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Text(article.body, style = MaterialTheme.typography.bodyLarge)
                }
            }

            loadFailed -> Box(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                InfoText("Kunne ikke hente artiklen.")
            }

            else -> Box(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ArticleImage(article: Article, modifier: Modifier) {
    if (article.imageUrl.isNullOrBlank()) {
        CategoryPlaceholder(article.category, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = article.imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) },
        error = { CategoryPlaceholder(article.category, Modifier.fillMaxSize()) }
    )
}

// Viser en farvet flade med emnets navn.
@Composable
private fun CategoryPlaceholder(category: String, modifier: Modifier) {
    Box(
        modifier.background(categoryColor(category)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            category.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }
}

// Viser en forklaring og en knap til at tillade notifikationer.
@Composable
private fun NotificationPermissionCard(onAllowClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Få besked om nye artikler", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAllowClick) {
                Text("Tillad notifikationer")
            }
        }
    }
}

// Viser en kort besked i midten af skærmen.
@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        textAlign = TextAlign.Center
    )
}

// Giver hvert emne sin egen farve.
private fun categoryColor(category: String): Color = when (category) {
    "sport" -> Color(0xFF2E7D32)
    "politik" -> Color(0xFF1565C0)
    "teknologi" -> Color(0xFF6A1B9A)
    else -> Color(0xFF455A64)
}

// Laver emnets navn med stort forbogstav.
private fun String.displayName(): String = replaceFirstChar { it.uppercase() }

// Laver teksten med emne og dato over artiklens titel.
private fun Article.label(): String =
    listOfNotNull(category.uppercase(), formatDate(createdAt)).joinToString(" · ")

// Formaterer artiklens dato til lokal tid.
private fun formatDate(createdAt: String?): String? {
    val instant = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
