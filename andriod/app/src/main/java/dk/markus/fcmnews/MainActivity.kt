package dk.markus.fcmnews

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dk.markus.fcmnews.ui.theme.FcmNewsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var articles by mutableStateOf(listOf<Article>())
    private var isLoading by mutableStateOf(false)
    private var loadFailed by mutableStateOf(false)
    private var selectedTopic by mutableStateOf<String?>(null)
    private var openedArticleId by mutableStateOf<String?>(null)
    private var article by mutableStateOf<Article?>(null)
    private var articleLoadFailed by mutableStateOf(false)
    private var topics by mutableStateOf(setOf<String>())
    private var notificationsAllowed by mutableStateOf(true)

    // Spørger brugeren om tilladelse til notifikationer.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "Notification permission granted: $granted")
        notificationsAllowed = granted
    }

    // Starter appen og viser skærmen.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        topics = UserSync.getTopics(this)

        handleIntent(intent)

        lifecycleScope.launch { UserSync.register(this@MainActivity) }

        setContent {
            FcmNewsTheme {
                BackHandler(enabled = openedArticleId != null) { closeArticle() }

                if (openedArticleId != null) {
                    ArticleDetailScreen(
                        article = article,
                        loadFailed = articleLoadFailed,
                        onBack = ::closeArticle
                    )
                } else {
                    NewsScreen(
                        articles = selectedTopic?.let { topic ->
                            articles.filter { it.category == topic }
                        } ?: articles,
                        selectedTopic = selectedTopic,
                        subscribedTopics = topics,
                        isLoading = isLoading,
                        loadFailed = loadFailed,
                        showPermissionCard = !notificationsAllowed,
                        onTopicSelected = { selectedTopic = it },
                        onBellClick = { topic -> onTopicChanged(topic, topic !in topics) },
                        onRefresh = ::loadArticles,
                        onArticleClick = ::openArticle,
                        onAllowNotifications = {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }
        }
    }

    // Opdaterer tilladelse, aktivitet og artikler, når appen åbnes.
    override fun onResume() {
        super.onResume()
        notificationsAllowed = hasNotificationPermission()
        lifecycleScope.launch { UserSync.markActive() }
        loadArticles()
    }

    // Håndterer tryk på en notifikation, mens appen kører.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Henter artiklen som notifikationen henviser til.
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_ARTICLE_ID) ?: return
        Log.d(TAG, "Opened from notification, articleId: $id")
        openedArticleId = id
        article = articles.find { it.id == id }
        articleLoadFailed = false

        lifecycleScope.launch {
            try {
                article = Api.service.getArticle(id)
            } catch (e: Exception) {
                Log.e(TAG, "Could not load article $id", e)
                articleLoadFailed = article == null
            }
        }
    }

    // Henter de nyeste artikler fra backenden.
    private fun loadArticles() {
        if (isLoading) return
        lifecycleScope.launch {
            isLoading = true
            try {
                articles = Api.service.getArticles()
                loadFailed = false
            } catch (e: Exception) {
                Log.e(TAG, "Could not load articles", e)
                loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    // Åbner en artikel fra listen.
    private fun openArticle(selected: Article) {
        openedArticleId = selected.id
        article = selected
        articleLoadFailed = false
    }

    // Lukker artiklen og går tilbage til listen.
    private fun closeArticle() {
        openedArticleId = null
        article = null
    }

    // Gemmer brugerens valg af topic.
    private fun onTopicChanged(topic: String, enabled: Boolean) {
        UserSync.setTopic(this, topic, enabled)
        topics = UserSync.getTopics(this)
        lifecycleScope.launch { UserSync.register(this@MainActivity) }

        val name = topic.replaceFirstChar { it.uppercase() }
        val message = if (enabled) "Du får nu notifikationer om $name"
        else "Du får ikke længere notifikationer om $name"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Tjekker om appen må vise notifikationer.
    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val TAG = "FCM"
    }
}
