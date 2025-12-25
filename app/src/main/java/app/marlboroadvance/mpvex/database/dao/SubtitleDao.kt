package app.marlboroadvance.mpvex.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.marlboroadvance.mpvex.database.entities.SubtitleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitles WHERE videoTitle = :videoTitle ORDER BY timestamp ASC")
    fun getSubtitlesForVideo(videoTitle: String): Flow<List<SubtitleEntity>>

    @Query("SELECT * FROM subtitles WHERE videoTitle = :videoTitle ORDER BY timestamp ASC")
    suspend fun getSubtitlesForVideoSync(videoTitle: String): List<SubtitleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitle(subtitle: SubtitleEntity)
    
    @Query("DELETE FROM subtitles WHERE videoTitle = :videoTitle AND subtitlePath = :path")
    suspend fun deleteSubtitle(videoTitle: String, path: String)
}
