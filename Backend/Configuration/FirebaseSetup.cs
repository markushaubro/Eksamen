using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using Google.Cloud.Firestore;

namespace Backend.Configuration;

public static class FirebaseSetup
{
    const string ProjectId = "fcm-app-ce9b2";

    // Initialiserer Firebase og registrerer Firestore, så den kan bruges i endpoints og services.
    public static IServiceCollection AddFirebase(this IServiceCollection services)
    {
        var credentialsJson = File.ReadAllText("service-account.json");

        FirebaseApp.Create(new AppOptions
        {
            Credential = GoogleCredential.FromJson(credentialsJson)
        });

        var db = new FirestoreDbBuilder
        {
            ProjectId = ProjectId,
            JsonCredentials = credentialsJson
        }.Build();

        services.AddSingleton(db);
        return services;
    }
}
