package app.marlboroadvance.mpvex.ui.browser.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.utils.debouncedCombinedClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  progressPercentage: Float? = null,
  isOldAndUnplayed: Boolean = false,
  onThumbClick: () -> Unit = {},
  hasSubtitle: Boolean = false,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showUnplayedOldVideoLabel by appearancePreferences.showUnplayedOldVideoLabel.collectAsState()
  val unplayedOldVideoDays by appearancePreferences.unplayedOldVideoDays.collectAsState()
  val maxLines = if (unlimitedNameLines) Int.MAX_VALUE else 2

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .debouncedCombinedClickable(
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
          )
          .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val thumbnailRepository = koinInject<ThumbnailRepository>()
      // Rectangular thumbnail (16:9) with fixed width; height derives from aspect ratio
      val thumbWidthDp = 128.dp
      val aspect = 16f / 9f
      val thumbWidthPx = with(LocalDensity.current) { thumbWidthDp.roundToPx() }
      val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

      // Load thumbnail with optimized state management
      // Key includes video identity to prevent reloading same thumbnail
      val thumbnailKey =
        remember(video.id, video.dateModified, video.size, thumbWidthPx, thumbHeightPx) {
          "${video.id}_${video.dateModified}_${video.size}_${thumbWidthPx}_$thumbHeightPx"
        }

      // Try to get from memory cache immediately (synchronous, no flicker)
      var thumbnail by remember(thumbnailKey) {
        mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx))
      }

      // Only load if not already in memory - prevents reload on recomposition
      LaunchedEffect(thumbnailKey) {
        if (thumbnail == null) {
          thumbnail =
            withContext(Dispatchers.IO) {
              thumbnailRepository.getThumbnail(video, thumbWidthPx, thumbHeightPx)
            }
        }
      }

      Box(
        modifier =
          Modifier
            .width(thumbWidthDp)
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .debouncedCombinedClickable(
              onClick = onThumbClick,
              onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
      ) {
        thumbnail?.let {
          Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Thumbnail",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
          )
        } ?: run {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }

        // Show "NEW" label for recently added unplayed videos if enabled (top-left corner)
        // Like MX Player: show NEW for videos added within threshold days that haven't been played
        if (showUnplayedOldVideoLabel && isOldAndUnplayed) {
          // Check if video is recently added (within threshold days)
          val currentTime = System.currentTimeMillis()
          val videoAge = currentTime - (video.dateAdded * 1000) // dateAdded is in seconds
          val thresholdMillis = unplayedOldVideoDays * 24 * 60 * 60 * 1000L

          if (videoAge <= thresholdMillis) {
            Box(
              modifier =
                Modifier
                  .align(Alignment.TopStart)
                  .padding(6.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(Color(0xFFD32F2F)) // Warning red color
                  .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Text(
                text = stringResource(R.string.video_label_new),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
              )
            }
          }
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

        // Progress bar at bottom of thumbnail
        if (progressPercentage != null && showProgressBar) {
          Box(
            modifier =
              Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp),
          ) {
            // Background (unwatched portion)
            Box(
              modifier =
                Modifier
                  .matchParentSize()
                  .background(Color.Black.copy(alpha = 0.6f)),
            )
            // Progress (watched portion)
            Box(
              modifier =
                Modifier
                  .fillMaxHeight()
                  .fillMaxWidth(progressPercentage)
                  .background(MaterialTheme.colorScheme.primary),
            )
          }
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
          if (showSizeChip && video.sizeFormatted != "0 B" && video.sizeFormatted != "--") {
            Text(
              video.sizeFormatted,
              style = MaterialTheme.typography.labelSmall,
              modifier =
                Modifier
                  .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(8.dp),
                  )
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          if (showResolutionChip && video.resolution != "--") {
            if (showSizeChip && video.sizeFormatted != "0 B" && video.sizeFormatted != "--") {
              Spacer(modifier = Modifier.width(4.dp))
            }

            // Extract base resolution without FPS for display
            val displayResolution = if (showFramerateInResolution) {
              video.resolution
            } else {
              // Remove @fps part if present
              video.resolution.substringBefore("@")
            }

            Text(
              displayResolution,
              style = MaterialTheme.typography.labelSmall,
              modifier =
                Modifier
                  .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(8.dp),
                  )
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}
