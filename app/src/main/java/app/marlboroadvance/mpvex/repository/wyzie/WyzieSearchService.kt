package app.marlboroadvance.mpvex.repository.wyzie

import android.util.Log
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.net.URLEncoder

@Serializable
data class WyzieTmdbResult(
    val id: Int,
    val mediaType: String,
    val title: String,
    val releaseYear: String? = null
)

@Serializable
data class WyzieTmdbResponse(
    val results: List<WyzieTmdbResult>
)

class WyzieSearchService(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val baseUrl = "https://sub.wyzie.ru"

    fun searchSubtitles(
        id: String, // IMDb or TMDb ID
        season: Int? = null,
        episode: Int? = null,
        language: String? = null, // Comma-separated ISO codes
        format: String? = null, // Comma-separated formats
        encoding: String? = null, // Comma-separated encodings
        source: String = "all",
        hi: Boolean? = null
    ): List<WyzieSubtitle> {
        val urlBuilder = StringBuilder("$baseUrl/search")
        urlBuilder.append("?id=$id")
        season?.let { urlBuilder.append("&season=$it") }
        episode?.let { urlBuilder.append("&episode=$it") }
        language?.let { urlBuilder.append("&language=$it") }
        format?.let { urlBuilder.append("&format=$it") }
        encoding?.let { urlBuilder.append("&encoding=$it") }
        urlBuilder.append("&source=$source")
        urlBuilder.append("&unzip=true")
        hi?.let { urlBuilder.append("&hi=$it") }

        val url = urlBuilder.toString()
        Log.d("WyzieSearchService", "Constructed URL: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("WyzieSearchService", "Search failed: ${response.code}, $errorBody")
                throw IOException("Search failed with code ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("Empty body")
            Log.d("WyzieSearchService", "Search response: $responseBody")
            
            return try {
                json.decodeFromString<List<WyzieSubtitle>>(responseBody)
            } catch (e: Exception) {
                Log.e("WyzieSearchService", "Failed to parse JSON: ${e.message}", e)
                throw IOException("JSON parsing error: ${e.message}")
            }
        }
    }

    fun downloadSubtitle(url: String): Response {
        Log.d("WyzieSearchService", "Downloading from: $url")
        val request = Request.Builder()
            .url(url)
            .build()
        return client.newCall(request).execute()
    }

    fun tmdbSearch(query: String): List<WyzieTmdbResult> {
        val url = "$baseUrl/api/tmdb/search?q=${URLEncoder.encode(query, "UTF-8")}"
        Log.d("WyzieSearchService", "TMDb Search URL: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("WyzieSearchService", "TMDb search failed: ${response.code}, $errorBody")
                throw IOException("TMDb search failed with code ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty body")
            return try {
                json.decodeFromString<WyzieTmdbResponse>(responseBody).results
            } catch (e: Exception) {
                Log.e("WyzieSearchService", "Failed to parse TMDb JSON: ${e.message}", e)
                throw IOException("TMDb JSON parsing error: ${e.message}")
            }
        }
    }
}
