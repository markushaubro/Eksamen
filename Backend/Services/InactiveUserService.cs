using FirebaseAdmin.Messaging;
using Google.Cloud.Firestore;

namespace Backend.Services;

// Sender påmindelser til inaktive brugere i baggrunden.
public class InactiveUserService(FirestoreDb db, ILogger<InactiveUserService> logger) : BackgroundService
{
    // Tidsintervaller for kontrol, inaktivitet og påmindelser.
    static readonly TimeSpan CheckInterval = TimeSpan.FromMinutes(1);
    static readonly TimeSpan InactiveAfter = TimeSpan.FromMinutes(2);
    static readonly TimeSpan FrequencyCap = TimeSpan.FromMinutes(5);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await CheckUsers();
            }
            catch (Exception e)
            {
                logger.LogError(e, "Error in InactiveUserService");
            }

            await Task.Delay(CheckInterval, stoppingToken);
        }
    }

    async Task CheckUsers()
    {
        var now = DateTime.UtcNow;
        var cutoff = Timestamp.FromDateTime(now - InactiveAfter);

        var inactiveUsers = await db.Collection("users")
            .WhereLessThan("lastActive", cutoff)
            .GetSnapshotAsync();

        foreach (var user in inactiveUsers.Documents)
        {
            if (user.TryGetValue<Timestamp>("lastNotifiedAt", out var lastNotified) &&
                now - lastNotified.ToDateTime() < FrequencyCap)
            {
                continue;
            }

            var body = "Check out the latest articles in the app";
            if (user.TryGetValue<List<string>>("topics", out var topics) && topics.Count > 0)
            {
                body = $"Check out the latest articles about {string.Join(" and ", topics)}";
            }

            try
            {
                var id = await FirebaseMessaging.DefaultInstance.SendAsync(new Message
                {
                    Token = user.GetValue<string>("token"),
                    Notification = new Notification
                    {
                        Title = "There's something new for you",
                        Body = body
                    }
                });

                await user.Reference.UpdateAsync("lastNotifiedAt", Timestamp.GetCurrentTimestamp());
                await NotificationLog.Save(db, "reminder", topic: null, userId: user.Id, id);
                logger.LogInformation("Reminder sent to {Id}", user.Id);
            }
            catch (FirebaseMessagingException e) when (
                e.MessagingErrorCode == MessagingErrorCode.Unregistered ||
                e.MessagingErrorCode == MessagingErrorCode.InvalidArgument)
            {
                // Fjerner brugeren, hvis tokenet ikke længere er gyldigt.
                await user.Reference.DeleteAsync();
                logger.LogInformation("Deleted user {Id} with invalid token", user.Id);
            }
            catch (Exception e)
            {
                // Fortsætter med de øvrige brugere, hvis afsendelsen fejler.
                logger.LogWarning(e, "Could not send reminder to {Id}", user.Id);
            }
        }
    }
}
