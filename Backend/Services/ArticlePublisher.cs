using Backend.Models;
using FirebaseAdmin.Messaging;
using Google.Cloud.Firestore;

namespace Backend.Services;

// Gemmer en artikel i Firestore og sender en besked til dens emne.
public static class ArticlePublisher
{
    public static async Task<(string ArticleId, string MessageId)> Publish(FirestoreDb db, NewArticle a)
    {
        var fields = new Dictionary<string, object>
        {
            ["title"] = a.Title,
            ["body"] = a.Body,
            ["category"] = a.Category,
            ["createdAt"] = Timestamp.GetCurrentTimestamp()
        };
        if (!string.IsNullOrWhiteSpace(a.ImageUrl)) fields["imageUrl"] = a.ImageUrl;

        var doc = await db.Collection("articles").AddAsync(fields);

        var messageId = await FirebaseMessaging.DefaultInstance.SendAsync(new Message
        {
            Topic = a.Category,
            Data = new Dictionary<string, string>
            {
                ["type"] = "article",
                ["articleId"] = doc.Id,
                ["title"] = a.Title,
                ["category"] = a.Category
            },
            Android = new AndroidConfig { Priority = Priority.High }
        });

        await NotificationLog.Save(db, "article", topic: a.Category, userId: null, messageId);
        return (doc.Id, messageId);
    }
}
