namespace Backend.Models;

// Data der sendes, når en ny artikel oprettes.
public record NewArticle(string Title, string Body, string Category, string? ImageUrl = null);
