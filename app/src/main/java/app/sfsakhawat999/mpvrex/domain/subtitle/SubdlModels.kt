package app.sfsakhawat999.mpvrex.domain.subtitle

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from Subdl search API
 */
@JsonClass(generateAdapter = true)
data class SubdlSearchResponse(
  val status: Boolean,
  val results: List<SubdlResult>? = null,
  val subtitles: List<SubdlSubtitle>? = null,
  val error: String? = null,
)

/**
 * Movie/TV show result
 */
@JsonClass(generateAdapter = true)
data class SubdlResult(
  @Json(name = "imdb_id") val imdbId: String? = null,
  @Json(name = "tmdb_id") val tmdbId: Int? = null,
  val type: String? = null, // "movie" or "tv"
  val name: String? = null,
  @Json(name = "sd_id") val sdId: Int? = null,
  @Json(name = "first_air_date") val firstAirDate: String? = null,
  val year: Int? = null,
)

/**
 * Subtitle entry from Subdl
 */
@JsonClass(generateAdapter = true)
data class SubdlSubtitle(
  @Json(name = "sd_id") val sdId: Int? = null,
  @Json(name = "type") val type: String? = null,
  val name: String? = null,
  @Json(name = "release_name") val releaseName: String? = null,
  val lang: String? = null,
  val author: String? = null,
  val url: String, // This is the download URL
  @Json(name = "season_number") val seasonNumber: Int? = null,
  @Json(name = "episode_number") val episodeNumber: Int? = null,
  val comment: String? = null,
  @Json(name = "hi") val hearingImpaired: Boolean? = false,
  val rating: Double? = null,
  @Json(name = "download_count") val downloadCount: Int? = 0,
  @Json(name = "full_season") val fullSeason: Boolean? = false,
)
