package app.marlboroadvance.mpvex.utils.media

/**
 * High-accuracy media filename parser inspired by kahari-parser (GizmoH2o/kahari-parser).
 *
 * Extracts structured metadata from messy release filenames:
 * - Title (cleaned and normalized)
 * - Season number
 * - Episode number
 * - Episode title
 * - Year
 * - Media type (tv / movie)
 *
 * Handles:
 * - Regular TV series:     Dexter.S01E02.1080p.BluRay.mkv → "Dexter" S01E02
 * - Regular movies:        The.Dark.Knight.2008.1080p.BluRay.mkv → "The Dark Knight" (2008)
 * - Scene release formats: Jujutsu.Kaisen.S02E05.1080p.WEBRip.x265-PSA.mkv → "Jujutsu Kaisen" S02E05
 * - Anime naming:          [SubsPlease] Frieren - 01 (1080p) [ABCD1234].mkv → "Frieren" E01
 * - Japanese seasons:      San no Shou → Season 3
 * - Multi-episode:         S01E01E02 or S01E01-E03 → S01E01
 * - Episode with title:    Breaking.Bad.S05E16.Felina.720p.mkv → "Breaking Bad" S05E16 "Felina"
 * - Cross-format episode:  1x02, EP05, Episode 5, #05 patterns
 */

data class ParsedMediaInfo(
    val title: String,
    val year: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val type: String // "movie" or "tv"
)

object MediaInfoParser {

    // ── Japanese season numbers ──────────────────────────────────────────────────
    private val JAPANESE_NUMBERS = mapOf(
        "ichi" to 1, "ni" to 2, "san" to 3, "yon" to 4, "shi" to 4,
        "go" to 5, "roku" to 6, "nana" to 7, "shichi" to 7,
        "hachi" to 8, "kyuu" to 9, "ku" to 9, "juu" to 10
    )

    // ── Known release groups ─────────────────────────────────────────────────────
    private val RELEASE_GROUPS = setOf(
        "YTS", "RARBG", "EVO", "iExTV", "FGT", "PSA", "Batch",
        "HorribleSubs", "SubsPlease", "Erai-raws", "ASW", "Judas",
        "EMBER", "GHOSTS", "ION10", "SPARKS", "AMIABLE", "GECKOS",
        "YIFY", "ShAaNiG", "USURY", "STUTTERSHIT", "DDR", "QxR",
        "NTb", "NTG", "CAKES", "FLUX", "MZABI", "EDITH",
        "GalaxyRG", "MkvCage", "Joy", "BonsaiHD", "TrollHD",
        "NOGRP", "Pahe", "Tigole", "PHOCiS", "RUSTED", "DSNP",
        "PECULATE", "SuccessfulCrab", "PLAY", "HONE", "KOGI",
        "DEMAND", "RARBG", "SPARKS", "AMIABLE", "FGT", "ROVERS"
    )

    // ── Metadata keywords (noise) ────────────────────────────────────────────────
    private val VIDEO_CODECS = setOf(
        "h264", "h265", "x264", "x265", "hevc", "av1", "avc", "mpeg4",
        "divx", "xvid", "vp9", "vp8"
    )

    private val AUDIO_CODECS = setOf(
        "aac", "flac", "mp3", "opus", "ac3", "dts", "eac3", "truehd",
        "atmos", "lpcm", "pcm", "vorbis", "ogg"
    )

    private val SOURCE_TAGS = setOf(
        "bluray", "bdrip", "brrip", "dvdrip", "hdrip", "webdl",
        "webrip", "hdtv", "cam", "tc", "hdcam",
        "hdts", "dvdscr", "dvdr", "pdtv", "sdtv", "tvrip", "r5",
        "amzn", "nf", "hulu", "atvp", "pcok", "hmax",
        "crater", "hbo", "stan", "pmtp"
    )

    private val RESOLUTION_TAGS = setOf(
        "480p", "480i", "576p", "576i", "720p", "720i",
        "1080p", "1080i", "2160p", "4k", "8k", "uhd"
    )

    private val SCENE_TAGS = setOf(
        "proper", "repack", "remux", "extended", "unrated", "imax",
        "theatrical", "directors", "internal", "limited",
        "remastered", "uncut", "complete"
    )

    private val LANGUAGE_TAGS = setOf(
        "english", "french", "german", "spanish", "hindi",
        "multi", "dual", "dubbed", "subbed", "engsub", "vostfr",
        "vf", "ita", "jpn", "kor", "chi", "rus", "ara",
        "por", "pol", "tur", "dut", "swe", "nor", "dan", "fin",
        "hun", "cze", "gre", "rom", "heb", "tha", "ind", "vie"
    )

    private val MISC_NOISE = setOf(
        "10bit", "8bit", "hdr", "hdr10", "dolby",
        "vision", "sdr", "bt709", "bt2020", "hlg", "pq",
        "batch", "dvd", "uncensored", "censored",
        "horriblesubs", "subsplease"
    )

    private val FILE_EXTENSIONS = setOf(
        "mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v",
        "mpg", "mpeg", "m2ts", "vob", "ogm", "rmvb",
        "srt", "zip", "rar", "7z"
    )

    // Combine all noise into one set for quick lookup
    private val ALL_NOISE: Set<String> by lazy {
        (VIDEO_CODECS + AUDIO_CODECS + SOURCE_TAGS + RESOLUTION_TAGS +
                SCENE_TAGS + LANGUAGE_TAGS + MISC_NOISE + FILE_EXTENSIONS)
    }

    // ── Regex patterns ───────────────────────────────────────────────────────────

    // Season-Episode: S01E02, S1E2, s01e02 — also captures multi-episode S01E01E02
    private val SEASON_EPISODE_REGEX = Regex("""[Ss](\d{1,2})[Ee](\d{1,4})""")

    // Cross-format: 1x02 format
    private val CROSS_FORMAT_REGEX = Regex("""\b(\d{1,2})[xX](\d{1,4})\b""")

    // EP marker: EP05, Ep5 — with word boundary to avoid matching inside words
    private val EP_MARKER_REGEX = Regex("""\b[Ee][Pp](\d{1,4})\b""")

    // Episode word: Episode 5, EPISODE 05
    private val EPISODE_WORD_REGEX = Regex("""\b[Ee]pisode\s*(\d{1,4})\b""")

    // Season word: Season 3, SEASON 3
    private val SEASON_WORD_REGEX = Regex("""\b[Ss]eason\s*(\d{1,2})\b""")

    // Year: 1990-2029
    private val YEAR_REGEX = Regex("""\b(19|20)\d{2}\b""")

    // Hash episode: #05
    private val HASH_EPISODE_REGEX = Regex("""#(\d{1,4})""")

    // File size: 700MB, 1.2 GB
    private val FILESIZE_REGEX = Regex("""\b\d+\.?\d*\s*[MmGg][Bb]\b""")

    // Bitrate: 4500kbps
    private val BITRATE_REGEX = Regex("""\b\d+\s*[KkMm]bps\b""")

    // Audio channels: 5.1, 7.1, 2.0 — also DDP5.1, DD5.1, AAC2.0
    private val AUDIO_CHANNEL_REGEX = Regex("""\b(?:DDP?|AAC|DD\+?)?\.?([257])\.([01])\b""", RegexOption.IGNORE_CASE)

    // Resolution number: 1080p, 720p
    private val RESOLUTION_NUM_REGEX = Regex("""\b\d{3,4}[pPiI]\b""")

    // Japanese season: San no Shou
    private val JAPANESE_SEASON_REGEX = Regex("""(\w+)\s+no\s+[Ss]hou""", RegexOption.IGNORE_CASE)

    // WEB-DL special pattern (common in scene releases)
    private val WEB_DL_REGEX = Regex("""\bWEB[-.]?DL\b""", RegexOption.IGNORE_CASE)

    // H.264 / H.265 with dot notation
    private val H_CODEC_REGEX = Regex("""\b[Hh]\.?26[45]\b""")

    // DTS-HD MA and similar compound audio tags
    private val COMPOUND_AUDIO_REGEX = Regex("""\b(?:DTS[-.]?HD(?:[-.]?MA)?|TrueHD|DD\+?|DDP)\b""", RegexOption.IGNORE_CASE)

    // ── Main parse function ──────────────────────────────────────────────────────

    fun parse(fileName: String): ParsedMediaInfo {
        if (fileName.isBlank()) {
            return ParsedMediaInfo(title = "", type = "movie")
        }

        // Step 1: Extract S01E02 / 1x02 patterns before any modification
        val seMatch = SEASON_EPISODE_REGEX.find(fileName)
        val crossMatch = CROSS_FORMAT_REGEX.find(fileName)
        val epWordMatch = EPISODE_WORD_REGEX.find(fileName)
        val seasonWordMatch = SEASON_WORD_REGEX.find(fileName)
        val epMarkerMatch = EP_MARKER_REGEX.find(fileName)

        var season: Int? = null
        var episode: Int? = null

        // Priority 1: S01E02 format (handles regular TV like Dexter.S01E02 and anime alike)
        if (seMatch != null) {
            season = seMatch.groupValues[1].toIntOrNull()
            episode = seMatch.groupValues[2].toIntOrNull()
        }
        // Priority 2: 1x02 format
        else if (crossMatch != null) {
            season = crossMatch.groupValues[1].toIntOrNull()
            episode = crossMatch.groupValues[2].toIntOrNull()
        }
        // Priority 3: "Episode 5" format
        else if (epWordMatch != null) {
            episode = epWordMatch.groupValues[1].toIntOrNull()
        }
        // Priority 4: EP05 format
        else if (epMarkerMatch != null) {
            episode = epMarkerMatch.groupValues[1].toIntOrNull()
        }

        // Season from "Season X" format (e.g., "Attack on Titan Season 3 Episode 12")
        if (season == null && seasonWordMatch != null) {
            season = seasonWordMatch.groupValues[1].toIntOrNull()
        }

        // Step 2: Extract year (from original filename)
        // Be careful not to grab episode numbers as years — skip if year is part of S01E2020 etc.
        val yearMatch = findYear(fileName, seMatch, crossMatch)
        val year = yearMatch?.value

        // Step 3: Japanese season detection
        if (season == null) {
            season = extractJapaneseSeason(fileName)
        }

        // Step 4: Determine the title boundary
        // For S01E02 / 1x02 / Episode X patterns: title is everything before the marker
        // For movies: title is everything before the year
        val titleBoundary = findTitleBoundary(
            fileName, seMatch, crossMatch, epWordMatch,
            seasonWordMatch, epMarkerMatch, yearMatch
        )

        // Step 5: Extract and clean the title
        var cleanTitle = if (titleBoundary != null && titleBoundary > 0) {
            val prefix = fileName.substring(0, titleBoundary)
            cleanRawTitle(prefix)
        } else {
            cleanRawTitle(stripBracketsAndExtension(fileName))
        }

        // Step 6: Attempt to extract episode title (text after episode marker)
        // e.g., "Breaking.Bad.S05E16.Felina.720p.mkv" → episodeTitle = "Felina"
        var episodeTitle: String? = null
        val episodeEndIndex = getEpisodeEndIndex(seMatch, crossMatch, epWordMatch, epMarkerMatch)
        if (episodeEndIndex != null && episodeEndIndex < fileName.length) {
            val afterEpisode = fileName.substring(episodeEndIndex)
            val candidateEpTitle = cleanRawTitle(afterEpisode)
            if (candidateEpTitle.isNotBlank() && candidateEpTitle != cleanTitle) {
                episodeTitle = candidateEpTitle
            }
        }

        // Step 7: Try to detect dash-separated episode for anime-style naming
        // Pattern: "Title - 08" or "Title - 08 - Episode Name"
        // Only if no episode was found yet AND no year-only movie pattern
        if (episode == null && !(year != null && season == null)) {
            val dashEpResult = detectDashEpisode(fileName)
            if (dashEpResult != null) {
                episode = dashEpResult.first
                if (season == null) season = 1
                if (dashEpResult.second != null) {
                    val candidate = cleanRawTitle(dashEpResult.second!!)
                    if (candidate.isNotBlank() && candidate != cleanTitle) {
                        episodeTitle = candidate
                    }
                }
                // Recalculate the title as the part before the dash-episode
                val dashTitleResult = detectDashTitleBoundary(fileName)
                if (dashTitleResult != null && dashTitleResult.isNotBlank()) {
                    val candidateTitle = cleanRawTitle(dashTitleResult)
                    if (candidateTitle.isNotBlank()) {
                        cleanTitle = candidateTitle
                    }
                }
            }
        }

        // Step 8: Default season to 1 if episode is found but no season
        if (episode != null && season == null) {
            season = 1
        }

        // Step 9: Remove Japanese season phrase from title if season was extracted
        if (season != null) {
            cleanTitle = cleanTitle
                .replace(Regex("""\w+\s+no\s+[Ss]hou""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        // Step 10: Remove "Season X" from the title if season was already extracted
        if (season != null) {
            cleanTitle = cleanTitle
                .replace(Regex("""\b[Ss]eason\s*\d+\b"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        // Step 11: Final cleanup
        cleanTitle = finalCleanup(cleanTitle)

        // Step 12: Determine type
        val type = when {
            season != null || episode != null -> "tv"
            else -> "movie"
        }

        return ParsedMediaInfo(
            title = cleanTitle,
            year = year,
            season = season,
            episode = episode,
            episodeTitle = episodeTitle,
            type = type
        )
    }

    // ── Helper: Find year without matching inside episode patterns ────────────────

    private fun findYear(
        fileName: String,
        seMatch: MatchResult?,
        crossMatch: MatchResult?
    ): MatchResult? {
        val yearCandidates = YEAR_REGEX.findAll(fileName).toList()
        if (yearCandidates.isEmpty()) return null

        // Filter out any year that overlaps with S01E02 or 1x02 match ranges
        val excludeRanges = listOfNotNull(seMatch?.range, crossMatch?.range)
        return yearCandidates.firstOrNull { yearResult ->
            excludeRanges.none { range ->
                yearResult.range.first in range || yearResult.range.last in range
            }
        }
    }

    // ── Helper: Detect "Title - 08 - Episode Name" anime pattern ─────────────────

    private fun detectDashEpisode(fileName: String): Pair<Int, String?>? {
        // Match: " - 08 - " or " - 08." or " - 08 ("
        val match = Regex("""\s+-\s+(\d{1,4})\s*(?:-\s*(.+))?""").find(fileName) ?: return null
        val ep = match.groupValues[1].toIntOrNull() ?: return null

        // Guard: don't match resolution/year-like numbers
        if (ep in setOf(480, 720, 1080, 2160)) return null
        if (ep in 1900..2100) return null
        if (ep > 1999) return null

        val epTitle = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        return ep to epTitle
    }

    private fun detectDashTitleBoundary(fileName: String): String? {
        val match = Regex("""\s+-\s+\d{1,4}""").find(fileName) ?: return null
        return if (match.range.first > 0) fileName.substring(0, match.range.first) else null
    }

    // ── Helper: Japanese season extraction ───────────────────────────────────────

    private fun extractJapaneseSeason(text: String): Int? {
        val match = JAPANESE_SEASON_REGEX.find(text) ?: return null
        val word = match.groupValues[1].lowercase()
        return JAPANESE_NUMBERS[word]
    }

    // ── Helper: Find title boundary ──────────────────────────────────────────────

    private fun findTitleBoundary(
        fileName: String,
        seMatch: MatchResult?,
        crossMatch: MatchResult?,
        epWordMatch: MatchResult?,
        seasonWordMatch: MatchResult?,
        epMarkerMatch: MatchResult?,
        yearMatch: MatchResult?
    ): Int? {
        // For TV shows: title ends at the earliest season/episode marker
        // For movies: title ends at the year
        val candidates = mutableListOf<Int>()

        // Episode/season markers (highest priority for boundary)
        seMatch?.range?.first?.let { candidates.add(it) }
        crossMatch?.range?.first?.let { candidates.add(it) }
        epWordMatch?.range?.first?.let { candidates.add(it) }
        epMarkerMatch?.range?.first?.let { candidates.add(it) }

        // If season word comes before episode word, use season as boundary
        seasonWordMatch?.range?.first?.let { seasonStart ->
            // Only use season as boundary if no earlier episode marker exists
            if (candidates.isEmpty() || seasonStart < (candidates.minOrNull() ?: Int.MAX_VALUE)) {
                candidates.add(seasonStart)
            }
        }

        // Year as boundary — only if no episode/season markers found,
        // or if year comes after the title but before metadata
        if (candidates.isEmpty() && yearMatch != null) {
            candidates.add(yearMatch.range.first)
        }

        return candidates.minOrNull()
    }

    // ── Helper: Get end index of episode marker ──────────────────────────────────

    private fun getEpisodeEndIndex(
        seMatch: MatchResult?,
        crossMatch: MatchResult?,
        epWordMatch: MatchResult?,
        epMarkerMatch: MatchResult?
    ): Int? {
        // Pick the one that was actually used for episode detection (highest priority first)
        return when {
            seMatch != null -> seMatch.range.last + 1
            crossMatch != null -> crossMatch.range.last + 1
            epWordMatch != null -> epWordMatch.range.last + 1
            epMarkerMatch != null -> epMarkerMatch.range.last + 1
            else -> null
        }
    }

    // ── Helper: Strip brackets and extension ─────────────────────────────────────

    private fun stripBracketsAndExtension(text: String): String {
        return text
            .replace(Regex("""%5B.*?%5D""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\[.*?]"""), " ")
            .replace(Regex("""\(.*?\)"""), " ")
            .replace(Regex("""【.*?】"""), " ")
            .replace(Regex("""（.*?）"""), " ")
            .replace(Regex("""\.(${FILE_EXTENSIONS.joinToString("|")})$""", RegexOption.IGNORE_CASE), "")
    }

    // ── Helper: Clean raw title string ───────────────────────────────────────────

    private fun cleanRawTitle(raw: String): String {
        var title = raw
            // Remove brackets and their content
            .replace(Regex("""%5B.*?%5D""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\[.*?]"""), " ")
            .replace(Regex("""\(.*?\)"""), " ")
            .replace(Regex("""【.*?】"""), " ")
            .replace(Regex("""（.*?）"""), " ")
            // Remove file extension
            .replace(Regex("""\.(${FILE_EXTENSIONS.joinToString("|")})$""", RegexOption.IGNORE_CASE), "")
            // Replace delimiters (dots & underscores → spaces, preserve hyphens for now)
            .replace(Regex("""[._]"""), " ")
            // Remove version suffixes (v2, v3, etc.)
            .replace(Regex("""\b[Vv]\d+\b"""), " ")
            // Remove file size patterns (700MB, 1.2 GB)
            .replace(FILESIZE_REGEX, " ")
            // Remove bitrate patterns (4500kbps)
            .replace(BITRATE_REGEX, " ")
            // Remove audio channel patterns (5.1, 7.1, DDP5.1, DD5.1)
            .replace(AUDIO_CHANNEL_REGEX, " ")
            // Remove WEB-DL pattern
            .replace(WEB_DL_REGEX, " ")
            // Remove H.264 / H.265 with dot
            .replace(H_CODEC_REGEX, " ")
            // Remove compound audio tags (DTS-HD MA, TrueHD, DD+, DDP)
            .replace(COMPOUND_AUDIO_REGEX, " ")

        // Remove all noise tags (with word boundaries)
        ALL_NOISE.forEach { tag ->
            title = title.replace(Regex("""\b${Regex.escape(tag)}\b""", RegexOption.IGNORE_CASE), " ")
        }

        // Remove S01E02 patterns from title
        title = title.replace(SEASON_EPISODE_REGEX, " ")

        // Remove cross-format episode patterns (1x02)
        title = title.replace(CROSS_FORMAT_REGEX, " ")

        // Remove EP marker patterns
        title = title.replace(EP_MARKER_REGEX, " ")

        // Remove episode word patterns
        title = title.replace(EPISODE_WORD_REGEX, " ")

        // Remove season word patterns
        title = title.replace(SEASON_WORD_REGEX, " ")

        // Remove release groups
        RELEASE_GROUPS.forEach { group ->
            title = title.replace(Regex("""\b${Regex.escape(group)}\b""", RegexOption.IGNORE_CASE), " ")
        }

        // Remove trailing group after dash (e.g., "-PSA", "-DEMAND")
        title = title.replace(Regex("""\s*-\s*[A-Z0-9]{2,10}$"""), " ")

        // Remove year pattern
        title = title.replace(YEAR_REGEX, " ")

        // Remove CRC32 hashes (8-char hex)
        title = title.replace(Regex("""\b[A-Fa-f0-9]{8}\b"""), " ")

        // Remove standalone resolution numbers (1080p, 720p)
        title = title.replace(RESOLUTION_NUM_REGEX, " ")

        // Remove hash episode markers
        title = title.replace(HASH_EPISODE_REGEX, " ")

        // Final cleanup
        return finalCleanup(title)
    }

    // ── Helper: Final cleanup ────────────────────────────────────────────────────

    private fun finalCleanup(title: String): String {
        return title
            // Collapse multiple spaces
            .replace(Regex("""\s+"""), " ")
            // Remove leading/trailing separators and whitespace
            .replace(Regex("""^[\s\-_.]+"""), "")
            .replace(Regex("""[\s\-_.]+$"""), "")
            // Remove trailing dash left over from group removal
            .replace(Regex("""\s*-\s*$"""), "")
            .trim()
    }
}
