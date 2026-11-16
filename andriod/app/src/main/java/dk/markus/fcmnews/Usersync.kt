package dk.markus.fcmnews

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Holder brugerens emner og registrering i backenden opdateret.
object UserSync {
    private const val TAG = "FCM"
    private const val PREFS = "fcm_news_prefs"
    private const val KEY_TOPICS = "topics"

    // Emner som brugeren kan vælge.
    val availableTopics = listOf("sport", "politik", "teknologi")

    // Henter brugerens valgte emner.
    fun getTopics(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_TOPICS, emptySet())
            ?.filter { it in availableTopics }
            ?.toSet() ?: emptySet()

    // Gemmer valget og tilmelder eller afmelder emnet.
    fun setTopic(context: Context, topic: String, enabled: Boolean) {
        val updated = getTopics(context).toMutableSet().apply {
            if (enabled) add(topic) else remove(topic)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_TOPICS, updated)
            .apply()

        val messaging = FirebaseMessaging.getInstance()
        val task = if (enabled) messaging.subscribeToTopic(topic)
        else messaging.unsubscribeFromTopic(topic)

        task.addOnCompleteListener {
            val action = if (enabled) "Subscribed to" else "Unsubscribed from"
            Log.d(TAG, "$action '$topic': ${it.isSuccessful}")
        }
    }

    // Tilmelder brugeren sine emner igen efter et nyt token.
    fun resubscribeTopics(context: Context) {
        val messaging = FirebaseMessaging.getInstance()
        getTopics(context).forEach { topic ->
            messaging.subscribeToTopic(topic).addOnCompleteListener {
                Log.d(TAG, "Re-subscribed to '$topic': ${it.isSuccessful}")
            }
        }
    }

    // Registrerer brugeren i backenden.
    suspend fun register(context: Context, token: String? = null) {
        try {
            val currentToken = token ?: FirebaseMessaging.getInstance().token.awaitResult()
            val userId = FirebaseInstallations.getInstance().id.awaitResult()
            val response = Api.service.registerUser(
                RegisterUserRequest(userId, currentToken, getTopics(context).toList())
            )
            Log.d(TAG, "POST /users -> ${response.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not register user", e)
        }
    }

    // Opdaterer tidspunktet for brugerens seneste aktivitet.
    suspend fun markActive() {
        try {
            val userId = FirebaseInstallations.getInstance().id.awaitResult()
            val response = Api.service.markActive(userId)
            Log.d(TAG, "POST /users/$userId/active -> ${response.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not mark user active", e)
        }
    }
}

// Venter på resultatet af en Firebase-opgave.
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
