package app.marlboroadvance.mpvex.utils.subtitles

import android.content.Context

/**
 * Interface for subtitle providers.
 * Implement this interface to add new subtitle sources.
 * 
 * To add a new provider:
 * 1. Create a new class implementing SubtitleProvider
 * 2. Implement search() to return subtitle results from your source
 * 3. Implement getDownloadUrl() to resolve the actual download link
 * 4. Register the provider in SubtitleRepository
 */
interface SubtitleProvider {
    /**
     * Unique identifier for this provider
     */
    val providerId: String
    
    /**
     * Display name for this provider
     */
    val displayName: String
    
    /**
     * Base URL for this provider (for reference)
     */
    val baseUrl: String
    
    /**
     * Search for subtitles matching the query
     * @param query The search query (usually movie/show name)
     * @return List of matching subtitle results
     */
    suspend fun search(query: String): List<SubtitleSearchResult>
    
    /**
     * Get the actual download URL for a subtitle
     * @param detailsUrl The details/info page URL from search results
     * @param preferredLanguage Preferred language code (e.g., "en", "hi")
     * @return Direct download URL for the subtitle file, or null if not found
     */
    suspend fun getDownloadUrl(detailsUrl: String, preferredLanguage: String? = null): String?
    
    /**
     * Download a subtitle to the device
     * @param context Android context
     * @param detailsUrl The details/info page URL from search results
     * @param movieName Name of the movie/show (for folder organization)
     * @param subtitleName Name for the subtitle file
     * @param preferredLanguage Preferred language code
     * @return Download ID from DownloadManager, or -1 if failed
     */
    suspend fun download(
        context: Context,
        detailsUrl: String,
        movieName: String,
        subtitleName: String,
        preferredLanguage: String? = null
    ): Long
}
