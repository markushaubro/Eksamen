using Backend.Models;
using Backend.Services;
using Google.Cloud.Firestore;

namespace Backend.Endpoints;

public static class ArticleEndpoints
{
    public static void MapArticleEndpoints(this IEndpointRouteBuilder app)
    {
        // Gemmer en artikel og sender en besked til dens emne.
        app.MapPost("/articles", async (NewArticle a, FirestoreDb db) =>
        {
            var (articleId, messageId) = await ArticlePublisher.Publish(db, a);
            return Results.Ok(new { articleId, messageId });
        });

        // Henter de nyeste artikler.
        app.MapGet("/articles", async (FirestoreDb db) =>
        {
            var snaps = await db.Collection("articles")
                .OrderByDescending("createdAt")
                .Limit(50)
                .GetSnapshotAsync();

            return Results.Ok(snaps.Documents.Select(ToArticle));
        });

        // Henter en artikel ud fra dens id.
        app.MapGet("/articles/{id}", async (string id, FirestoreDb db) =>
        {
            var snap = await db.Collection("articles").Document(id).GetSnapshotAsync();
            if (!snap.Exists) return Results.NotFound();

            return Results.Ok(ToArticle(snap));
        });
    }

    // Laver et Firestore-dokument om til det format, appen modtager.
    private static object ToArticle(DocumentSnapshot snap) => new
    {
        id = snap.Id,
        title = snap.GetValue<string>("title"),
        body = snap.GetValue<string>("body"),
        category = snap.GetValue<string>("category"),
        imageUrl = snap.TryGetValue<string>("imageUrl", out var imageUrl) ? imageUrl : null,
        createdAt = snap.TryGetValue<Timestamp>("createdAt", out var createdAt)
            ? createdAt.ToDateTime()
            : (DateTime?)null
    };
}
