package app.marlboroadvance.mpvex.repository.wyzie

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder

@Serializable
data class WyzieSubtitle(
    val id: String? = null,
    val url: String,
    val flagUrl: String? = null,
    val format: String? = null,
    val encoding: String? = null,
    val display: String? = null,
    val language: String? = null,
    val media: String? = null,
    val isHearingImpaired: Boolean = false,
    val source: String? = null,
    val release: String? = null,
    val releases: List<String> = emptyList(),
    val origin: String? = null,
    val fileName: String? = null
) {
    val displayName: String get() = fileName ?: release ?: media ?: "Unknown Subtitle"
    val displayLanguage: String get() = display ?: language ?: "Unknown"
}

@Serializable
data class WyzieSearchResponse(
    val results: List<WyzieSubtitle> = emptyList()
)

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

object WyzieSources {
    val ALL = mapOf(
        "all" to "All",
        "subdl" to "SubDL",
        "subf2m" to "Subf2m",
        "opensubtitles" to "OpenSubtitles",
        "podnapisi" to "Podnapisi",
        "gestdown" to "Gestdown",
        "animetosho" to "AnimeTosho"
    )
}

object WyzieFormats {
    val ALL = mapOf(
        "srt" to "SRT",
        "ass" to "ASS",
        "ssa" to "SSA",
        "vtt" to "VTT",
        "sub" to "SUB"
    )
}

object WyzieEncodings {
    val ALL = mapOf(
        "utf-8" to "UTF-8",
        "cp1252" to "Windows-1252",
        "iso-8859-1" to "ISO-8859-1",
        "iso-8859-2" to "ISO-8859-2"
    )
}

object WyzieLanguages {
    val ALL = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
        "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "zh" to "Chinese",
        "ja" to "Japanese", "ko" to "Korean", "ar" to "Arabic", "hi" to "Hindi",
        "bn" to "Bengali", "pa" to "Punjabi", "jv" to "Javanese", "vi" to "Vietnamese",
        "te" to "Telugu", "mr" to "Marathi", "ta" to "Tamil", "ur" to "Urdu",
        "tr" to "Turkish", "pl" to "Polish", "uk" to "Ukrainian", "nl" to "Dutch",
        "el" to "Greek", "hu" to "Hungarian", "sv" to "Swedish", "cs" to "Czech",
        "ro" to "Romanian", "da" to "Danish", "fi" to "Finnish", "no" to "Norwegian",
        "he" to "Hebrew", "id" to "Indonesian", "ms" to "Malay", "th" to "Thai",
        "fa" to "Persian", "sk" to "Slovak", "bg" to "Bulgarian", "hr" to "Croatian",
        "sr" to "Serbian", "sl" to "Slovenian", "et" to "Estonian", "lv" to "Latvian",
        "lt" to "Lithuanian", "af" to "Afrikaans", "sq" to "Albanian", "am" to "Amharic",
        "hy" to "Armenian", "az" to "Azerbaijani", "eu" to "Basque", "be" to "Belarusian",
        "bs" to "Bosnian", "ca" to "Catalan", "cy" to "Welsh", "eo" to "Esperanto",
        "ga" to "Irish", "gl" to "Galician", "ka" to "Georgian", "gu" to "Gujarati",
        "ht" to "Haitian Creole", "is" to "Icelandic", "kn" to "Kannada", "kk" to "Kazakh",
        "km" to "Khmer", "ky" to "Kyrgyz", "lo" to "Lao", "mk" to "Macedonian",
        "mg" to "Malagasy", "mt" to "Maltese", "mi" to "Maori", "mn" to "Mongolian",
        "ne" to "Nepali", "ps" to "Pashto", "si" to "Sinhala", "sw" to "Swahili",
        "tg" to "Tajik", "tt" to "Tatar", "uz" to "Uzbek", "yi" to "Yiddish",
        "yo" to "Yoruba", "zu" to "Zulu"
    )
    val SORTED = ALL.toList().sortedBy { it.second }.toMap()
}

class WyzieSearchRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val preferences: SubtitlesPreferences
) {
    private val baseUrl = "https://sub.wyzie.ru"

    suspend fun search(
        query: String,
        season: Int? = null,
        episode: Int? = null
    ): Result<List<WyzieSubtitle>> = withContext(Dispatchers.IO) {
        try {
            var searchId = query
            if (!query.startsWith("tt", ignoreCase = true) && !query.all { it.isDigit() }) {
                val tmdbResults = tmdbSearch(query)
                if (tmdbResults.isNotEmpty()) {
                    searchId = tmdbResults[0].id.toString()
                } else {
                    return@withContext Result.failure(Exception("Could not find media ID for '$query'"))
                }
            }

            val selectedLangs = preferences.subdlLanguages.get()
            val languages = if (selectedLangs.isNotEmpty() && !selectedLangs.contains("all")) {
                selectedLangs.joinToString(",").lowercase()
            } else null

            val sources = preferences.wyzieSources.get()
            val sourceParam = if (sources.isEmpty() || sources.contains("all")) "all" else sources.joinToString(",").lowercase()
            
            val formats = preferences.wyzieFormats.get()
            val formatParam = if (formats.isNotEmpty() && !formats.contains("all")) formats.joinToString(",").lowercase() else null
            
            val encodings = preferences.wyzieEncodings.get()
            val encodingParam = if (encodings.isNotEmpty() && !encodings.contains("all")) encodings.joinToString(",").lowercase() else null
            
            val hearingImpaired = preferences.wyzieHearingImpaired.get()

            val results = fetchSubtitles(
                id = searchId,
                season = season,
                episode = episode,
                language = languages,
                format = formatParam,
                encoding = encodingParam,
                source = sourceParam,
                hi = if (hearingImpaired) true else null
            )
            
            val sortedResults = results.sortedWith(compareByDescending<WyzieSubtitle> { sub ->
                val name = sub.displayName.lowercase()
                val q = query.lowercase()
                var score = 0
                if (name.contains(q)) score += 100
                if (name.contains("720p") || name.contains("1080p") || name.contains("2160p")) score += 50
                if (name.contains("web-dl") || name.contains("webrip") || name.contains("bluray")) score += 40
                if (name.contains("yify") || name.contains("sparks") || name.contains("rarbg")) score += 30
                score
            }.thenByDescending { it.displayName.length })

            Result.success(sortedResults)
        } catch (e: Exception) {
            Log.e("WyzieSearchRepository", "Search failed", e)
            Result.failure(e)
        }
    }

    private fun fetchSubtitles(
        id: String,
        season: Int? = null,
        episode: Int? = null,
        language: String? = null,
        format: String? = null,
        encoding: String? = null,
        source: String = "all",
        hi: Boolean? = null
    ): List<WyzieSubtitle> {
        val url = StringBuilder("$baseUrl/search?id=$id")
            .apply {
                season?.let { append("&season=$it") }
                episode?.let { append("&episode=$it") }
                language?.let { append("&language=$it") }
                format?.let { append("&format=$it") }
                encoding?.let { append("&encoding=$it") }
                append("&source=$source&unzip=true")
                hi?.let { append("&hi=$it") }
            }.toString()

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Search failed: ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            return json.decodeFromString(body)
        }
    }

    suspend fun download(subtitle: WyzieSubtitle, mediaTitle: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url(subtitle.url).build()).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Download failed: ${response.code}"))

            val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty body"))
            val urlExtension = subtitle.url.substringAfterLast("/", "").substringBefore("?").substringAfterLast(".", "")
            val extension = subtitle.format?.lowercase() ?: urlExtension.takeIf { it.isNotEmpty() } ?: "srt"
            
            val saveFolderUri = preferences.subtitleSaveFolder.get()
            val sanitizedTitle = MediaInfoParser.parse(mediaTitle).title
            val fileName = "${subtitle.displayName}_${subtitle.displayLanguage}.$extension"

            if (saveFolderUri.isNotBlank()) {
                val parentDir = DocumentFile.fromTreeUri(context, Uri.parse(saveFolderUri))
                if (parentDir?.exists() == true) {
                    var movieDir = parentDir.findFile(sanitizedTitle) ?: parentDir.createDirectory(sanitizedTitle)
                    if (movieDir != null) {
                        val subFile = movieDir.findFile(fileName) ?: movieDir.createFile("application/octet-stream", fileName)
                        if (subFile != null) {
                            context.contentResolver.openOutputStream(subFile.uri)?.use { it.write(bytes) }
                            return@withContext Result.success(subFile.uri)
                        }
                    }
                }
            }

            val internalMoviesDir = File(context.getExternalFilesDir(null), "Movies")
            val movieDir = File(internalMoviesDir, sanitizedTitle).apply { if (!exists()) mkdirs() }
            val file = File(movieDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            Result.success(Uri.fromFile(file))
        } catch (e: Exception) {
            Log.e("WyzieSearchRepository", "Download failed", e)
            Result.failure(e)
        }
    }

    private fun tmdbSearch(query: String): List<WyzieTmdbResult> {
        val url = "$baseUrl/api/tmdb/search?q=${URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TMDb search failed: ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            return json.decodeFromString<WyzieTmdbResponse>(body).results
        }
    }

    suspend fun deleteSubtitleFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = if (uri.scheme == "content") DocumentFile.fromSingleUri(context, uri) else DocumentFile.fromFile(File(uri.path ?: uri.toString()))
            if (file == null || !file.exists()) return@withContext false
            val deleted = file.delete()
            if (deleted) {
                preferences.subtitleSaveFolder.get().takeIf { it.isNotBlank() }?.let { cleanupEmptyFolders(Uri.parse(it)) }
            }
            deleted
        } catch (e: Exception) {
            Log.e("WyzieSearchRepository", "Delete failed", e)
            false
        }
    }

    private fun cleanupEmptyFolders(saveFolderUri: Uri) {
        try {
            val root = DocumentFile.fromTreeUri(context, saveFolderUri) ?: return
            root.listFiles().forEach { if (it.isDirectory && it.listFiles()?.isEmpty() == true) it.delete() }
        } catch (e: Exception) {
            Log.e("WyzieSearchRepository", "Cleanup failed", e)
        }
    }
}
