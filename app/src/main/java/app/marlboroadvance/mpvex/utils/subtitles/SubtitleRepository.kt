package app.marlboroadvance.mpvex.utils.subtitles

import android.content.Context
import android.util.Log
import app.marlboroadvance.mpvex.utils.subtitles.providers.SubtitleCatProvider

/**
 * Central repository for subtitle operations.
 * Manages multiple subtitle providers and provides a unified API.
 * 
 * Usage:
 * ```
 * val repository = SubtitleRepository()
 * val results = repository.search("Movie Name")
 * val downloadId = repository.download(context, result, movieName, preferredLanguage)
 * ```
 * 
 * To add a new provider:
 * 1. Create a class implementing SubtitleProvider in the providers/ folder
 * 2. Add an instance to the `providers` list below
 */
class SubtitleRepository {
    
    companion object {
        private const val TAG = "SubtitleRepository"
    }
    
    /**
     * List of all available subtitle providers.
     * Add new providers here to enable them.
     */
    private val providers: List<SubtitleProvider> = listOf(
        SubtitleCatProvider(),
        // Add more providers here:
        // OpenSubtitlesProvider(),
        // Subscene Provider(),
        // etc.
    )
    
    /**
     * Get a specific provider by ID
     */
    fun getProvider(providerId: String): SubtitleProvider? {
        return providers.find { it.providerId == providerId }
    }
    
    /**
     * Get all available providers
     */
    fun getAllProviders(): List<SubtitleProvider> = providers
    
    /**
     * Search for subtitles across all providers
     * @param query Search query
     * @return Combined list of results from all providers
     */
    suspend fun search(query: String): List<SubtitleSearchResult> {
        Log.d(TAG, "Searching all providers for: $query")
        
        val allResults = mutableListOf<SubtitleSearchResult>()
        
        for (provider in providers) {
            try {
                val results = provider.search(query)
                Log.d(TAG, "${provider.displayName}: Found ${results.size} results")
                allResults.addAll(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching ${provider.displayName}: ${e.message}", e)
            }
        }
        
        return allResults
    }
    
    /**
     * Search using a specific provider
     */
    suspend fun searchWithProvider(providerId: String, query: String): List<SubtitleSearchResult> {
        val provider = getProvider(providerId)
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        
        return provider.search(query)
    }
    
    /**
     * Download a subtitle using the default/first provider
     */
    suspend fun download(
        context: Context,
        detailsUrl: String,
        movieName: String,
        subtitleName: String,
        preferredLanguage: String? = null
    ): Long {
        // Use the first provider (SubtitleCat) by default
        val provider = providers.firstOrNull()
            ?: return -1L
        
        return provider.download(context, detailsUrl, movieName, subtitleName, preferredLanguage)
    }
    
    /**
     * Download using a specific provider
     */
    suspend fun downloadWithProvider(
        providerId: String,
        context: Context,
        detailsUrl: String,
        movieName: String,
        subtitleName: String,
        preferredLanguage: String? = null
    ): Long {
        val provider = getProvider(providerId)
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        
        return provider.download(context, detailsUrl, movieName, subtitleName, preferredLanguage)
    }
    
    /**
     * Get the download URL for a subtitle
     */
    suspend fun getDownloadUrl(
        detailsUrl: String,
        preferredLanguage: String? = null
    ): String? {
        val provider = providers.firstOrNull() ?: return null
        return provider.getDownloadUrl(detailsUrl, preferredLanguage)
    }
}
