package app.marlboroadvance.mpvex.repository.wyzie

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WyzieSearchRepository(
    private val context: Context,
    private val service: WyzieSearchService,
    private val preferences: SubtitlesPreferences
) {
    suspend fun search(
        query: String, // IMDb/TMDb ID or Title
        season: Int? = null,
        episode: Int? = null
    ): Result<List<WyzieSubtitle>> = withContext(Dispatchers.IO) {
        try {
            var searchId = query

            // If query is not an ID (doesn't start with tt and isn't numeric), try to resolve via TMDb
            val isImdb = query.startsWith("tt", ignoreCase = true)
            val isTmdb = query.all { it.isDigit() }

            if (!isImdb && !isTmdb) {
                Log.d("WyzieSearchRepository", "Query '$query' is not an ID, resolving via TMDb...")
                val tmdbResults = service.tmdbSearch(query)
                if (tmdbResults.isNotEmpty()) {
                    // Use the first result (most relevant)
                    searchId = tmdbResults[0].id.toString()
                    Log.d("WyzieSearchRepository", "Resolved '$query' to TMDb ID: $searchId (${tmdbResults[0].title})")
                } else {
                    return@withContext Result.failure(Exception("Could not find media ID for '$query'"))
                }
            }

            // Fetch selected languages from preferences
            val selectedLangs = preferences.subdlLanguages.get() // Reuse existing lang preference for now
            val languages = if (selectedLangs.isNotEmpty() && !selectedLangs.contains("all")) {
                selectedLangs.joinToString(",").lowercase()
            } else null

            val sources = preferences.wyzieSources.get()
            val sourceParam = if (sources.isEmpty() || sources.contains("all")) "all" else sources.joinToString(",").lowercase()
            
            val formats = preferences.wyzieFormats.get()
            val formatParam = if (formats.isNotEmpty() && !formats.contains("all")) {
                formats.joinToString(",").lowercase()
            } else null
            
            val encodings = preferences.wyzieEncodings.get()
            val encodingParam = if (encodings.isNotEmpty() && !encodings.contains("all")) {
                encodings.joinToString(",").lowercase()
            } else null
            
            val hearingImpaired = preferences.wyzieHearingImpaired.get()

            Log.d("WyzieSearchRepository", "Searching for ID: $searchId, Season: $season, Episode: $episode, Languages: $languages, Sources: $sourceParam, Formats: $formatParam, Encodings: $encodingParam, HI: $hearingImpaired")
            
            val results = service.searchSubtitles(
                id = searchId,
                season = season,
                episode = episode,
                language = languages,
                format = formatParam,
                encoding = encodingParam,
                source = sourceParam,
                hi = if (hearingImpaired) true else null
            )
            
            // Sort results: Meaningful names (matching movie title heuristics) first
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
            Log.e("WyzieSearchRepository", "Search failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun download(
        subtitle: WyzieSubtitle,
        mediaTitle: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val response = service.downloadSubtitle(subtitle.url)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code}"))
            }

            val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty body"))
            
            // The Wyzie API serves the subtitle directly (unzipped if autoUnzip was used)
            // We can infer the extension from the URL or headers, or default to SRT
            val fileNameFromUrl = subtitle.url.substringAfterLast("/", "subtitle.srt").substringBefore("?")
            val extension = fileNameFromUrl.substringAfterLast(".", "srt")
            
            val saveFolderUri = preferences.subtitleSaveFolder.get()
            val sanitizedTitle = MediaInfoParser.parse(mediaTitle).title
            val fileName = "${subtitle.displayName}_${subtitle.displayLanguage}.$extension"

            if (saveFolderUri.isNotBlank()) {
                val parentDir = DocumentFile.fromTreeUri(context, Uri.parse(saveFolderUri))
                if (parentDir != null && parentDir.exists()) {
                    var movieDir = parentDir.findFile(sanitizedTitle)
                    if (movieDir == null || !movieDir.isDirectory) {
                        movieDir = parentDir.createDirectory(sanitizedTitle)
                    }
                    
                    if (movieDir != null) {
                        var subFile = movieDir.findFile(fileName)
                        if (subFile == null) {
                            subFile = movieDir.createFile("application/octet-stream", fileName)
                        }
                        
                        if (subFile != null) {
                            context.contentResolver.openOutputStream(subFile.uri)?.use { 
                                it.write(bytes)
                            }
                            return@withContext Result.success(subFile.uri)
                        }
                    }
                }
            }

            // Fallback to internal storage
            val internalMoviesDir = File(context.getExternalFilesDir(null), "Movies")
            val movieDir = File(internalMoviesDir, sanitizedTitle)
            if (!movieDir.exists()) movieDir.mkdirs()
            
            val file = File(movieDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            
            Result.success(Uri.fromFile(file))
        } catch (e: Exception) {
            Log.e("WyzieSearchRepository", "Download failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSubtitleFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("WyzieSearchRepository", "Attempting to delete subtitle: $uri")
            val file = if (uri.scheme == "content") {
                DocumentFile.fromSingleUri(context, uri)
            } else {
                val path = uri.path ?: uri.toString()
                DocumentFile.fromFile(java.io.File(path))
            }
            
            if (file == null || !file.exists()) {
                Log.w("WyzieSearchRepository", "Subtitle file does not exist: $uri")
                return@withContext false
            }
            
            val deleted = file.delete()
            Log.d("WyzieSearchRepository", "Deletion result for $uri: $deleted")
            
            // Cleanup empty folders in the background
            if (deleted) {
                val saveFolderUri = preferences.subtitleSaveFolder.get()
                if (saveFolderUri.isNotBlank()) {
                    cleanupEmptyFolders(Uri.parse(saveFolderUri))
                }
            }
            
            deleted
        } catch (e: Exception) {
            Log.e("WyzieSearchRepository", "Failed to delete subtitle: ${e.message}")
            false
        }
    }

    private fun cleanupEmptyFolders(saveFolderUri: Uri) {
        try {
            val root = DocumentFile.fromTreeUri(context, saveFolderUri) ?: return
            root.listFiles().forEach { movieDir ->
                if (movieDir.isDirectory && (movieDir.listFiles()?.isEmpty() == true)) {
                    Log.d("WyzieSearchRepository", "Deleting empty folder: ${movieDir.name}")
                    movieDir.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("WyzieSearchRepository", "Folder cleanup failed", e)
        }
    }
}
