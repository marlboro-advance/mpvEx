package app.marlboroadvance.mpvex.repository.wyzie

import kotlinx.serialization.Serializable

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
