package app.sfsakhawat999.mpvrex.domain.subtitle

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Subdl.com API service
 */
interface SubdlApiService {
  /**
   * Search for subtitles
   */
  @GET("api/v1/subtitles")
  suspend fun searchSubtitles(
    @Query("api_key") apiKey: String,
    @Query("film_name") filmName: String? = null,
    @Query("file_name") fileName: String? = null,
    @Query("languages") languages: String? = "EN",
    @Query("subs_per_page") subsPerPage: Int = 30,
    @Query("hi") hearingImpaired: Int? = null,
    @Query("type") type: String? = null,
  ): SubdlSearchResponse
}
