using Backend.Models;
using Google.Cloud.Firestore;

namespace Backend.Endpoints;

public static class UserEndpoints
{
    public static void MapUserEndpoints(this IEndpointRouteBuilder app)
    {
        // Registrerer en bruger eller opdaterer brugerens oplysninger.
        app.MapPost("/users", async (RegisterUser r, FirestoreDb db) =>
        {
            await db.Collection("users").Document(r.UserId).SetAsync(new Dictionary<string, object>
            {
                ["token"] = r.Token,
                ["topics"] = r.Topics,
                ["lastActive"] = Timestamp.GetCurrentTimestamp(),
                ["updatedAt"] = Timestamp.GetCurrentTimestamp()
            }, SetOptions.MergeAll);

            return Results.Ok();
        });

        // Opdaterer tidspunktet for brugerens seneste aktivitet.
        app.MapPost("/users/{userId}/active", async (string userId, FirestoreDb db) =>
        {
            try
            {
                await db.Collection("users").Document(userId)
                    .UpdateAsync("lastActive", Timestamp.GetCurrentTimestamp());
                return Results.Ok();
            }
            catch (Grpc.Core.RpcException e) when (e.StatusCode == Grpc.Core.StatusCode.NotFound)
            {
                return Results.NotFound();
            }
        });
    }
}
