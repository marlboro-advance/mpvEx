package app.marlboroadvance.mpvex.utils.subtitles.providers

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import app.marlboroadvance.mpvex.utils.subtitles.SubtitleProvider
import app.marlboroadvance.mpvex.utils.subtitles.SubtitleSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Subtitle provider for SubtitleCat.com
 * 
 * Website structure:
 * - Search: https://www.subtitlecat.com/index.php?search=QUERY
 * - Results: Links with href starting with "subs/" ending in .html
 * - Download: .srt links on the details page
 */
class SubtitleCatProvider : SubtitleProvider {
    
    override val providerId = "subtitlecat"
    override val displayName = "SubtitleCat"
    override val baseUrl = "https://www.subtitlecat.com"
    
    companion object {
        private const val TAG = "SubtitleCatProvider"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val TIMEOUT_MS = 8000 // 8 seconds - faster timeout for responsiveness
    }
    
    override suspend fun search(query: String): List<SubtitleSearchResult> = withContext(Dispatchers.IO) {
        val searchUrl = "$baseUrl/index.php?search=${Uri.encode(query)}"
        val results = mutableListOf<SubtitleSearchResult>()
        
        try {
            Log.d(TAG, "Searching: $searchUrl")
            
            val doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()
            
            // Select links that point to subtitle pages
            val elements = doc.select("a[href^=subs/]")
            Log.d(TAG, "Found ${elements.size} subtitle links")
            
            for (element in elements) {
                val title = element.text().trim()
                var href = element.attr("href")
                
                // Build full URL
                if (!href.startsWith("http")) {
                    href = if (href.startsWith("/")) "$baseUrl$href" else "$baseUrl/$href"
                }
                
                // Only include valid subtitle pages
                if (title.isNotBlank() && href.endsWith(".html")) {
                    results.add(
                        SubtitleSearchResult(
                            title = title,
                            detailsUrl = href,
                            provider = displayName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
        }
        
        // Remove duplicates based on URL
        return@withContext results.distinctBy { it.detailsUrl }
    }
    
    override suspend fun getDownloadUrl(detailsUrl: String, preferredLanguage: String?): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting download URL from: $detailsUrl")
            
            val doc = Jsoup.connect(detailsUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()
            
            // Get all links and filter for .srt files
            val allLinks = doc.select("a[href]")
            val srtLinks = allLinks.filter { 
                val href = it.attr("href")
                href.contains(".srt", ignoreCase = true)
            }
            
            Log.d(TAG, "Found ${srtLinks.size} SRT links on page")
            
            if (srtLinks.isNotEmpty()) {
                // Try to find preferred language first
                var targetLink = if (!preferredLanguage.isNullOrBlank()) {
                    srtLinks.firstOrNull { 
                        val href = it.attr("href").lowercase()
                        href.contains(preferredLanguage.lowercase()) || 
                        href.contains("-${preferredLanguage.lowercase()}.") 
                    }
                } else null
                
                // Fallback to English if preferred not found
                if (targetLink == null) {
                    targetLink = srtLinks.firstOrNull { 
                        val href = it.attr("href").lowercase()
                        href.contains("english") || href.contains("-en.")
                    }
                }
                
                // Fallback to first available
                val finalLinkElem = targetLink ?: srtLinks.firstOrNull()
                var finalLink = finalLinkElem?.attr("href")
                
                if (finalLink != null) {
                    if (!finalLink.startsWith("http")) {
                        finalLink = "$baseUrl/${finalLink.removePrefix("/")}"
                    }
                    Log.d(TAG, "Found SRT URL: $finalLink")
                    return@withContext finalLink
                }
            }
            
            // Fallback: look for "Download" button
            val downloadLink = allLinks.firstOrNull { 
                it.text().contains("Download", ignoreCase = true) && 
                it.attr("href").contains("download", ignoreCase = true) 
            }
            if (downloadLink != null) {
                var link = downloadLink.attr("href")
                if (!link.startsWith("http")) {
                    link = "$baseUrl/${link.removePrefix("/")}"
                }
                Log.d(TAG, "Found download button URL: $link")
                return@withContext link
            }
            
            Log.w(TAG, "No SRT links found on page: $detailsUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download URL: ${e.message}", e)
        }
        
        return@withContext null
    }
    
    override suspend fun download(
        context: Context,
        detailsUrl: String,
        movieName: String,
        subtitleName: String,
        preferredLanguage: String?
    ): Long = withContext(Dispatchers.IO) {
        try {
            // Resolve the actual .srt link from the details page
            val srtUrl = getDownloadUrl(detailsUrl, preferredLanguage)
            
            if (srtUrl.isNullOrBlank()) {
                Log.e(TAG, "Could not find .srt link for: $detailsUrl")
                return@withContext -1L
            }
            
            val request = DownloadManager.Request(Uri.parse(srtUrl))
            
            // Clean names for filesystem
            val cleanMovieName = movieName
                .replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .trim()
            val cleanFileName = if (subtitleName.endsWith(".srt", ignoreCase = true)) {
                subtitleName
            } else {
                "$subtitleName.srt"
            }
            
            request.setTitle("Downloading Subtitle")
            request.setDescription(cleanFileName)
            request.addRequestHeader("User-Agent", USER_AGENT)
            request.addRequestHeader("Referer", detailsUrl)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            // Save to "Download/Subtitles/{MovieName}/{SubtitleName}" folder
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, 
                "Subtitles/$cleanMovieName/$cleanFileName"
            )
            
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            
            Log.d(TAG, "Download started - ID: $downloadId, File: $cleanFileName, URL: $srtUrl")
            return@withContext downloadId
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            return@withContext -1L
        }
    }
}
