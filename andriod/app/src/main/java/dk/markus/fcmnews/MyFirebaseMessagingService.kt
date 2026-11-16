package dk.markus.fcmnews

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Modtager og viser notifikationer fra Firebase.
class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Kaldes når enheden får et nyt token.
    override fun onNewToken(token: String) {
        Log.d(TAG, "New token: $token")
        UserSync.resubscribeTopics(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            UserSync.register(applicationContext, token)
        }
    }

    // Modtager beskeder fra Firebase.
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "onMessageReceived called")
        Log.d(TAG, "Data: ${message.data}")
        Log.d(TAG, "Notification: ${message.notification?.title} - ${message.notification?.body}")

        when (message.data["type"]) {
            "article" -> showArticleNotification(message.data)
            else -> message.notification?.let { showSimpleNotification(it.title, it.body) }
        }
    }

    // Viser en notifikation om en ny artikel.
    private fun showArticleNotification(data: Map<String, String>) {
        val articleId = data["articleId"] ?: return
        val title = data["title"] ?: "Ny artikel"
        val category = data["category"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_ARTICLE_ID, articleId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            articleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        show("Ny artikel i $category", title, pendingIntent, articleId.hashCode())
    }

    // Viser en almindelig notifikation.
    private fun showSimpleNotification(title: String?, body: String?) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        show(title ?: "FcmNews", body ?: "", pendingIntent, System.currentTimeMillis().toInt())
    }

    // Viser notifikationen, hvis appen har tilladelse.
    private fun show(title: String, body: String, pendingIntent: PendingIntent, id: Int) {
        createChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS - notification not shown")
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(id, notification)
    }

    // Opretter kanalen til notifikationer.
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Artikler", NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val TAG = "FCM"
        const val CHANNEL_ID = "articles"
        const val EXTRA_ARTICLE_ID = "articleId"
    }
}
