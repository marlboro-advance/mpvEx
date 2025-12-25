package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "subtitles")
@Serializable
data class SubtitleEntity(
    val videoTitle: String,
    val subtitlePath: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
