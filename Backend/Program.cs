using FirebaseAdmin;
using FirebaseAdmin.Messaging;
using Google.Apis.Auth.OAuth2;
using Google.Cloud.Firestore; 




var builder = WebApplication.CreateBuilder(args);

builder.Services.AddOpenApi();
// ---------------------------------------------------------------
// Forbindelse til Firebase (FCM) og Firestore - samme nøglefil
// ---------------------------------------------------------------
FirebaseApp.Create(new AppOptions
{
    Credential = GoogleCredential.FromFile("service-account.json")
});

var db = new FirestoreDbBuilder
{
    ProjectId = "fcm-app-ce9b2", // Project settings -> General -> Project ID
    CredentialsPath = "service-account.json"
}.Build();

builder.Services.AddSingleton(db);
builder.Services.AddHostedService<InaktivBrugerService>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi(); // beskrivelsen af dit API på /openapi/v1.json
    app.UseSwaggerUI(o => o.SwaggerEndpoint("/openapi/v1.json", "Backend"));
}

app.MapGet("/", () => "Backend kører");

// ---------------------------------------------------------------
// POST /users - appen registrerer sig (ved opstart og i onNewToken)
// Dokument-id = installations-id, så tokenet kan skifte uden dubletter
// ---------------------------------------------------------------
app.MapPost("/users", async (RegisterUser r) =>
{
    await db.Collection("users").Document(r.UserId).SetAsync(new Dictionary<string, object>
    {
        ["token"] = r.Token,
        ["topics"] = r.Topics,
        ["lastActive"] = Timestamp.GetCurrentTimestamp().ToDateTime(),
        ["updatedAt"] = Timestamp.GetCurrentTimestamp().ToDateTime()
    }, SetOptions.MergeAll);

    return Results.Ok();
});

// ---------------------------------------------------------------
// POST /users/{userId}/active - appen siger "jeg er blevet åbnet"
// ---------------------------------------------------------------
app.MapPost("/users/{userId}/active", async (string userId) =>
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

// ---------------------------------------------------------------
// POST /articles - AUTOMATISERING 1 (hændelsesbaseret)
// Ny artikel gemmes og sendes straks som data message til topic'et
// ---------------------------------------------------------------
app.MapPost("/articles", async (NewArticle a) =>
{
    var doc = await db.Collection("articles").AddAsync(new Dictionary<string, object>
    {
        ["title"] = a.Title,
        ["body"] = a.Body,
        ["category"] = a.Category,
        ["createdAt"] = Timestamp.GetCurrentTimestamp()
    });

    var messageId = await FirebaseMessaging.DefaultInstance.SendAsync(new Message
    {
        Topic = a.Category, // topic-navne må kun indeholde a-z, 0-9 og - _ . ~ %
        Data = new Dictionary<string, string>
        {
            ["type"] = "article",
            ["articleId"] = doc.Id,
            ["title"] = a.Title,
            ["category"] = a.Category
        },
        Android = new AndroidConfig { Priority = Priority.High }
    });

    await NotifikationsLog.Gem(db, "article", topic: a.Category, userId: null, messageId);
    return Results.Ok(new { articleId = doc.Id, messageId });
});

// ---------------------------------------------------------------
// GET /articles/{id} - appen henter artiklen, når brugeren trykker
// ---------------------------------------------------------------
app.MapGet("/articles/{id}", async (string id) =>
{
    var snap = await db.Collection("articles").Document(id).GetSnapshotAsync();
    if (!snap.Exists) return Results.NotFound();

    return Results.Ok(new
    {
        id = snap.Id,
        title = snap.GetValue<string>("title"),
        body = snap.GetValue<string>("body"),
        category = snap.GetValue<string>("category")
    });
});

// ---------------------------------------------------------------
// POST /send - kun til test: send til ét bestemt token
// ---------------------------------------------------------------
app.MapPost("/send", async (SendRequest r) =>
{
    var id = await FirebaseMessaging.DefaultInstance.SendAsync(new Message
    {
        Token = r.Token,
        Notification = new Notification { Title = r.Title, Body = r.Body }
    });
    return Results.Ok(id);
});

app.Run();

// ===============================================================
// AUTOMATISERING 2 (adfærdsbaseret)
// Kører i baggrunden og sender en personlig påmindelse til brugere,
// der ikke har åbnet appen i et stykke tid - med frekvensloft.
// ===============================================================
class InaktivBrugerService(FirestoreDb db, ILogger<InaktivBrugerService> logger) : BackgroundService
{
    // Demo-værdier i minutter. I en rigtig app ville det være dage.
    static readonly TimeSpan Interval = TimeSpan.FromMinutes(1);
    static readonly TimeSpan InaktivEfter = TimeSpan.FromMinutes(2);
    static readonly TimeSpan Frekvensloft = TimeSpan.FromMinutes(5);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await TjekBrugere();
            }
            catch (Exception e)
            {
                logger.LogError(e, "Fejl i InaktivBrugerService");
            }

            await Task.Delay(Interval, stoppingToken);
        }
    }

    async Task TjekBrugere()
    {
        var nu = DateTime.UtcNow;
        var graense = Timestamp.FromDateTime(nu - InaktivEfter);

        // Firestore filtrerer på ét felt (lastActive) ...
        var inaktive = await db.Collection("users")
            .WhereLessThan("lastActive", graense)
            .GetSnapshotAsync();

        foreach (var bruger in inaktive.Documents)
        {
            // ... og frekvensloftet tjekkes her i C#
            if (bruger.TryGetValue<Timestamp>("lastNotifiedAt", out var sidst) &&
                nu - sidst.ToDateTime() < Frekvensloft)
            {
                continue;
            }

            // Personalisering: brug brugerens egne topics i teksten
            var tekst = "Se de seneste artikler i appen";
            if (bruger.TryGetValue<List<string>>("topics", out var topics) && topics.Count > 0)
            {
                tekst = $"Se de seneste artikler om {string.Join(" og ", topics)}";
            }

            try
            {
                var id = await FirebaseMessaging.DefaultInstance.SendAsync(new Message
                {
                    Token = bruger.GetValue<string>("token"),
                    Notification = new Notification
                    {
                        Title = "Der er nyt til dig",
                        Body = tekst
                    }
                });

                await bruger.Reference.UpdateAsync("lastNotifiedAt", Timestamp.GetCurrentTimestamp());
                await NotifikationsLog.Gem(db, "reminder", topic: null, userId: bruger.Id, id);
                logger.LogInformation("Påmindelse sendt til {Id}", bruger.Id);
            }
            catch (FirebaseMessagingException e) when (e.MessagingErrorCode == MessagingErrorCode.Unregistered)
            {
                // Tokenet er ugyldigt (fx appen er afinstalleret) - ryd op
                await bruger.Reference.DeleteAsync();
                logger.LogInformation("Slettede bruger {Id} med ugyldigt token", bruger.Id);
            }
        }
    }
}

// Log over alle afsendte notifikationer (bruges i testmatricen)
static class NotifikationsLog
{
    public static Task Gem(FirestoreDb db, string type, string? topic, string? userId, string messageId) =>
        db.Collection("notifications").AddAsync(new Dictionary<string, object?>
        {
            ["type"] = type,
            ["topic"] = topic,
            ["userId"] = userId,
            ["fcmMessageId"] = messageId,
            ["sentAt"] = Timestamp.GetCurrentTimestamp()
        });
}

record RegisterUser(string UserId, string Token, List<string> Topics);
record NewArticle(string Title, string Body, string Category);
record SendRequest(string Token, string Title, string Body);