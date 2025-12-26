package app.marlboroadvance.mpvex.utils.subtitles

/**
 * Data class representing a subtitle search result.
 */
data class SubtitleSearchResult(
    val title: String,
    val detailsUrl: String,
    val language: String? = null,
    val format: String? = null,
    val provider: String = "Unknown"
)
