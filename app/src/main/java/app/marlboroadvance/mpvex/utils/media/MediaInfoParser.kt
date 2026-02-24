package app.marlboroadvance.mpvex.utils.media

data class MediaInfo(
    val title: String,
    val year: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val type: String // movie or tv
)

object MediaInfoParser {
    private val YEAR_REGEX = Regex("""\b(19|20)\d{2}\b""")
    private val SEASON_EPISODE_REGEX = Regex("""[Ss](\d{1,2})[Ee](\d{1,2})""", RegexOption.IGNORE_CASE)
    private val EPISODE_ONLY_REGEX = Regex("""\b\d{1,2}x(\d{1,2})\b""", RegexOption.IGNORE_CASE)
    
    // Derived from the Lua script ignore list
    private val IGNORE_TAGS = listOf(
        "1080p", "720p", "480p", "2160p", "4k", "8k", "x264", "x265", "h264", "h265",
        "web-dl", "webrip", "bluray", "aac", "ac3", "dts", "opus", "flac",
        "proper", "repack", "engsub", "uncensored", "mkv", "mp4", "ember", 
        "horriblesubs", "subsplease", "10bit", "8bit", "hevc", "avc", "hdr", "remux",
        "dual", "audio", "vostfr", "multi"
    )

    fun parse(fileName: String): MediaInfo {
        // 1. Remove brackets and their content first (like [Ember] or (2024))
        var workingName = fileName
            .replace(Regex("""%5B.*?%5D""", RegexOption.IGNORE_CASE), " ") // URL encoded brackets
            .replace(Regex("""\[.*?\]"""), " ")
            .replace(Regex("""\(.*?\)"""), " ")
            .replace(Regex("""【.*?】"""), " ")
            .replace(Regex("""（.*?）"""), " ")

        // 2. Extract Season/Episode info
        val seasonEpisodeMatch = SEASON_EPISODE_REGEX.find(fileName)
        val season = seasonEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
        val episode = seasonEpisodeMatch?.groupValues?.get(2)?.toIntOrNull()
            ?: EPISODE_ONLY_REGEX.find(fileName)?.groupValues?.get(1)?.toIntOrNull()

        // 3. Clean delimiters and extension
        var cleanTitle = workingName
            .replace(Regex("""\.(srt|zip|mp4|mkv|avi|mov|ts|m4v)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[._\-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // 4. Remove Year (if matched as a standalone word)
        val yearMatch = YEAR_REGEX.find(cleanTitle)
        val year = yearMatch?.value
        if (year != null) {
            cleanTitle = cleanTitle.replace(Regex("""\b$year\b"""), "").trim()
        }

        // 5. Aggressively remove ignore tags
        IGNORE_TAGS.forEach { tag ->
            cleanTitle = cleanTitle.replace(Regex("""\b$tag\b""", RegexOption.IGNORE_CASE), "")
        }

        // 6. Final cleanup of double spaces and trailing symbols
        cleanTitle = cleanTitle
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""^[\s\-_\.]+"""), "")
            .replace(Regex("""[\s\-_\.]+$"""), "")
            .trim()

        // 7. If we matched a season/episode, the title usually ends before that match
        if (seasonEpisodeMatch != null) {
            val originalIndex = fileName.indexOf(seasonEpisodeMatch.value)
            if (originalIndex > 0) {
                // Try to find a cleaner version of the prefix
                val prefix = fileName.substring(0, originalIndex)
                val sanitizedPrefix = parse(prefix).title // Recursive call for the prefix
                if (sanitizedPrefix.isNotBlank()) {
                    cleanTitle = sanitizedPrefix
                }
            }
        }

        return MediaInfo(
            title = cleanTitle,
            year = year,
            season = season,
            episode = episode,
            type = if (season != null || episode != null) "tv" else "movie"
        )
    }
}
