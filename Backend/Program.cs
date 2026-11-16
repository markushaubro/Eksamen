using Backend.Configuration;
using Backend.Endpoints;
using Backend.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddOpenApi();
builder.Services.AddFirebase();
builder.Services.AddHostedService<InactiveUserService>();

var app = builder.Build();

// Bruges til at teste API med swagger.
app.MapOpenApi();
app.UseSwaggerUI(o => o.SwaggerEndpoint("/openapi/v1.json", "Backend"));

app.MapGet("/", () => "Backend is running");

app.MapUserEndpoints();
app.MapArticleEndpoints();

app.Run();
