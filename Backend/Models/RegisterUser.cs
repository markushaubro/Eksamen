namespace Backend.Models;

// Data der sendes, når en bruger registreres eller opdateres.
public record RegisterUser(string UserId, string Token, List<string> Topics);
