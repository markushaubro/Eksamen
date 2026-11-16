using Google.Cloud.Firestore;

namespace Backend.Services;

// Gemmer oplysninger om en sendt notifikation.
public static class NotificationLog
{
    public static Task Save(FirestoreDb db, string type, string? topic, string? userId, string messageId) =>
        db.Collection("notifications").AddAsync(new Dictionary<string, object?>
        {
            ["type"] = type,
            ["topic"] = topic,
            ["userId"] = userId,
            ["fcmMessageId"] = messageId,
            ["sentAt"] = Timestamp.GetCurrentTimestamp()
        });
}
