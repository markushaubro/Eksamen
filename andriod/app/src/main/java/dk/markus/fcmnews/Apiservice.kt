package dk.markus.fcmnews

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Endepunkter i backenden.
interface ApiService {

    // Registrerer en bruger eller opdaterer brugerens oplysninger.
    @POST("users")
    suspend fun registerUser(@Body body: RegisterUserRequest): Response<Unit>

    // Opdaterer tidspunktet for brugerens seneste aktivitet.
    @POST("users/{userId}/active")
    suspend fun markActive(@Path("userId") userId: String): Response<Unit>

    // Henter de nyeste artikler.
    @GET("articles")
    suspend fun getArticles(): List<Article>

    // Henter en artikel ud fra dens id.
    @GET("articles/{id}")
    suspend fun getArticle(@Path("id") id: String): Article
}

data class RegisterUserRequest(
    val userId: String,
    val token: String,
    val topics: List<String>
)

data class Article(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val imageUrl: String?,
    val createdAt: String?
)

// Opretter forbindelsen til backenden.
object Api {
    private const val BASE_URL = "http://localhost:5231/"

    val service: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
