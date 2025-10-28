package app.marlboroadvance.mpvex.ui.browser.cards

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun VideoCard(
  video: Video,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isRecentlyPlayed: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
) {
  val preferences = koinInject<AppearancePreferences>()
  val unlimitedNameLines by preferences.unlimitedNameLines.collectAsState()
  val maxLines = if (unlimitedNameLines) Int.MAX_VALUE else 2

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(
            if (isSelected) {
              MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            } else {
              Color.Transparent
            },
          ).padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val thumbnailRepository = koinInject<ThumbnailRepository>()
      // Rectangular thumbnail (16:9) with fixed width; height derives from aspect ratio
      val thumbWidthDp = 128.dp
      val aspect = 16f / 9f
      val thumbWidthPx = with(LocalDensity.current) { thumbWidthDp.roundToPx() }
      val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

      // Load thumbnail keyed by identity + content changes
      val thumbnail by produceState<Bitmap?>(
        initialValue = null,
        key1 = video.id,
        key2 = video.dateModified,
        key3 = video.size,
      ) {
        value = thumbnailRepository.getThumbnail(video, thumbWidthPx, thumbHeightPx)
      }

      Box(
        modifier =
          Modifier
            .width(thumbWidthDp)
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        if (thumbnail != null) {
          Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = "Thumbnail",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
          )
        } else {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }

        // Duration timestamp overlay at bottom-right of the thumbnail
        Box(
          modifier =
            Modifier
              .align(Alignment.BottomEnd)
              .padding(6.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(Color.Black.copy(alpha = 0.65f))
              .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
          Text(
            text = video.durationFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
          )
        }
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(
        modifier = Modifier.weight(1f),
      ) {
        Text(
          video.displayName,
          style = MaterialTheme.typography.titleSmall,
          color = if (isRecentlyPlayed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
          maxLines = maxLines,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row {
          Text(
            video.sizeFormatted,
            style = MaterialTheme.typography.labelSmall,
            modifier =
              Modifier
                .background(
                  MaterialTheme.colorScheme.surfaceContainerHigh,
                  RoundedCornerShape(8.dp),
                ).padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
