package app.marlboroadvance.mpvex.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

data class SubtitleItem(
    val title: String,
    val detailsUrl: String
)

object SubtitleManager {
    private const val BASE_URL = "https://www.subtitlecat.com"

    /**
     * 1. SEARCH METHOD
     * Searches the website and returns a list of subtitles (Title + Link).
     */
    suspend fun searchSubtitles(query: String): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val searchUrl = "$BASE_URL/index.php?search=$query"
        val results = mutableListOf<SubtitleItem>()

        try {
            // Connect to the search page
            val doc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").get()
            
            // The search results are usually links containing "subs/"
            // We select 'a' tags that have 'href' starting with 'subs/'
            val elements = doc.select("a[href^=subs/]")
            Log.d("SubtitleManager", "Found ${elements.size} subtitle links")

            for (element in elements) {
                val title = element.text()
                var href = element.attr("href")

                // Fix relative URLs if necessary
                if (!href.startsWith("http")) {
                    href = if (href.startsWith("/")) BASE_URL + href else "$BASE_URL/$href"
                }

                // Filter out empty or irrelevant links (basic filtering)
                if (title.isNotBlank() && href.endsWith(".html")) {
                    results.add(SubtitleItem(title, href))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Remove duplicates based on URL
        return@withContext results.distinctBy { it.detailsUrl }
    }

    /**
     * 2. PARSE DOWNLOAD LINK METHOD
     * Visits the specific subtitle page to find the actual .srt download link.
     * Returns the first valid .srt URL found (usually the English one if available).
     */
    suspend fun getSrtLink(detailsUrl: String, preferredLanguage: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(detailsUrl).userAgent("Mozilla/5.0").get()
            
            // Get all links and filter for .srt files
            val allLinks = doc.select("a[href]")
            val srtLinks = allLinks.filter { 
                val href = it.attr("href")
                href.endsWith(".srt", ignoreCase = true) || href.contains(".srt", ignoreCase = true)
            }
            
            if (srtLinks.isNotEmpty()) {
                // Try to find preferred language first
                var targetLink = if (!preferredLanguage.isNullOrBlank()) {
                     srtLinks.firstOrNull { 
                         it.attr("href").contains(preferredLanguage, ignoreCase = true) || 
                         it.attr("href").contains("-$preferredLanguage.", ignoreCase = true) 
                     }
                } else null

                // Fallback to English if preferred not found
                if (targetLink == null) {
                    targetLink = srtLinks.firstOrNull { 
                        it.attr("href").contains("English", ignoreCase = true) || 
                        it.attr("href").contains("-en.", ignoreCase = true) 
                    }
                }

                // Fallback to first available
                var finalLinkElem = targetLink ?: srtLinks.firstOrNull()
                var finalLink = finalLinkElem?.attr("href")

                if (finalLink != null) {
                    if (!finalLink.startsWith("http")) {
                        val cleanLink = finalLink.removePrefix("/")
                        finalLink = "$BASE_URL/$cleanLink"
                    }
                    return@withContext finalLink
                }
            } else {
                 Log.e("SubtitleManager", "No SRT links found on page: $detailsUrl")
                 // Add fallback: look for "Download" text if no direct .srt link is found (some sites use redirects)
                 val downloadLink = allLinks.firstOrNull { it.text().contains("Download", ignoreCase = true) && it.attr("href").contains("download", ignoreCase = true) }
                 if (downloadLink != null) {
                     var link = downloadLink.attr("href")
                     if (!link.startsWith("http")) {
                        link = "$BASE_URL/${link.removePrefix("/")}"
                     }
                     return@withContext link
                 }
            }
        } catch (e: Exception) {
            Log.e("SubtitleManager", "Error parsing SRT link: ${e.message}")
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * 3. DOWNLOAD METHOD
     * Visits the page to get the real link, then uses Android DownloadManager.
     */
    suspend fun downloadSubtitle(
        context: Context, 
        detailsUrl: String, 
        movieName: String, 
        subtitleName: String,
        preferredLanguage: String? = null
    ): Long = withContext(Dispatchers.IO) {
        try {
            // Resolve the actual .srt link from the details page
            val srtUrl = getSrtLink(detailsUrl, preferredLanguage)
            
            if (srtUrl.isNullOrBlank()) {
                Log.e("SubtitleManager", "Could not find .srt link for: $detailsUrl")
                return@withContext -1L
            }

            val request = DownloadManager.Request(Uri.parse(srtUrl))
            
            // Clean names
            val cleanMovieName = movieName.replace("/", "_").trim()
            val cleanFileName = if (subtitleName.endsWith(".srt")) subtitleName else "$subtitleName.srt"

            request.setTitle("Downloading Subtitle")
            request.setDescription(cleanFileName)
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            request.addRequestHeader("Referer", detailsUrl)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            // Save to "Download/Subtitles/{MovieName}/{SubtitleName}" folder
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Subtitles/$cleanMovieName/$cleanFileName")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            
            Log.d("SubtitleManager", "Download started for: $cleanFileName from $srtUrl with ID: $downloadId")
            return@withContext downloadId
            
        } catch (e: Exception) {
            Log.e("SubtitleManager", "Error downloading subtitle: ${e.message}")
            e.printStackTrace()
            return@withContext -1L
        }
    }

    /**
     * 4. DIRECT DOWNLOAD METHOD
     * Use this when you already have the direct .srt link (e.g. from OpenSubtitles API).
     */
    suspend fun downloadDirectSubtitle(
        context: Context, 
        directUrl: String, 
        movieName: String, 
        subtitleName: String
    ): Long = withContext(Dispatchers.IO) {
        try {
            if (directUrl.isBlank()) return@withContext -1L

            val request = DownloadManager.Request(Uri.parse(directUrl))
            
            // Clean names
            val cleanMovieName = movieName.replace("/", "_").trim()
            val cleanFileName = if (subtitleName.endsWith(".srt")) subtitleName else "$subtitleName.srt"

            request.setTitle("Downloading Subtitle")
            request.setDescription(cleanFileName)
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            // Save to "Download/Subtitles/{MovieName}/{SubtitleName}" folder
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Subtitles/$cleanMovieName/$cleanFileName")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            
            Log.d("SubtitleManager", "Direct Download started for: $cleanFileName from $directUrl with ID: $downloadId")
            return@withContext downloadId
        } catch (e: Exception) {
            Log.e("SubtitleManager", "Error direct downloading subtitle: ${e.message}")
            e.printStackTrace()
            return@withContext -1L
        }
    }
}