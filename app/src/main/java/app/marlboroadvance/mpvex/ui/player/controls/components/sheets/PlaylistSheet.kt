package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.FastThumbnails
import kotlinx.collections.immutable.ImmutableList

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlaylistItem(
  val uri: Uri,
  val title: String,
  val index: Int,
  val isPlaying: Boolean,
  val progressPercent: Float = 0f, // 0-100, progress of video watched
  val isWatched: Boolean = false,  // True if video is fully watched (100%)
  val hasSubtitles: Boolean = false, // True if subtitles are available
  val path: String = "", // Video path for thumbnail loading
  val resolution: String = "", // Video quality e.g. "1080p", "720p"
)

@Composable
fun PlaylistSheet(
  playlist: ImmutableList<PlaylistItem>,
  onDismissRequest: () -> Unit,
  onItemClick: (PlaylistItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Use theme colors dynamically
  val accentColor = MaterialTheme.colorScheme.primary
  
  // Scroll state for the playlist
  val lazyListState = rememberLazyListState()
  
  // Find the currently playing item index - tracks changes in playlist items
  val playingItemIndex by remember {
    derivedStateOf {
      playlist.indexOfFirst { it.isPlaying }
    }
  }
  
  // Scroll to the currently playing item when the playing item changes or when sheet opens
  LaunchedEffect(playingItemIndex) {
    if (playingItemIndex >= 0) {
      lazyListState.animateScrollToItem(playingItemIndex)
    }
  }

  GenericTracksSheet(
    playlist,
    onDismissRequest = onDismissRequest,
    lazyListState = lazyListState,
    modifier = modifier.padding(vertical = MaterialTheme.spacing.smaller),
    header = {
      // Header showing current playlist info
      val currentItem = playlist.find { it.isPlaying }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            horizontal = MaterialTheme.spacing.medium,
            vertical = MaterialTheme.spacing.small,
          ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        if (currentItem != null) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = "Now Playing",
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = accentColor,
            ),
          )
          Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = "${playlist.size} items",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    track = { item ->
      PlaylistTrack(
        item = item,
        onClick = {
          onItemClick(item)
          // Auto-dismiss when selecting a different video
          if (!item.isPlaying) {
            onDismissRequest()
          }
        },
      )
    },
  )
}

@Composable
fun PlaylistTrack(
  item: PlaylistItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Use theme colors dynamically
  val accentColor = MaterialTheme.colorScheme.primary
  val accentSecondary = MaterialTheme.colorScheme.tertiary
  val successColor = MaterialTheme.colorScheme.tertiary

  // Thumbnail state
  var thumbnail by remember(item.uri) { mutableStateOf<Bitmap?>(null) }

  // Load thumbnail asynchronously
  LaunchedEffect(item.uri, item.path) {
    if (thumbnail == null) {
      withContext(Dispatchers.IO) {
        try {
          val videoPath = item.path.ifBlank { item.uri.toString() }
          // Generate thumbnail at 3 seconds, 256px dimension
          val bmp = FastThumbnails.generateAsync(videoPath, 3.0, 256)
          thumbnail = bmp
        } catch (e: Exception) {
          // Silently fail - will show placeholder
        }
      }
    }
  }

  val borderModifier = if (item.isPlaying) {
    Modifier.border(
      width = 2.dp,
      brush = Brush.linearGradient(listOf(accentColor, accentSecondary)),
      shape = RoundedCornerShape(12.dp),
    )
  } else {
    Modifier
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(
        horizontal = MaterialTheme.spacing.medium,
        vertical = MaterialTheme.spacing.extraSmall,
      )
      .clip(RoundedCornerShape(12.dp))
      .then(borderModifier)
      .clickable(onClick = onClick),
    color = if (item.isPlaying) {
      MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
    } else {
      Color.Transparent
    },
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.smaller),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      // Thumbnail with simple background, episode number, and progress
      Box(
        modifier = Modifier
          .width(100.dp)
          .height(56.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        // Show actual thumbnail or fallback icon
        thumbnail?.let { bmp ->
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Thumbnail",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
          )
        } ?: run {
          // Movie icon as fallback placeholder
          Icon(
            imageVector = Icons.Outlined.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp),
          )
        }
        
        // Episode number badge in bottom-left
        Box(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(4.dp)
            .background(
              color = accentColor,
              shape = RoundedCornerShape(3.dp),
            )
            .size(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "${item.index + 1}",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 8.sp,
            ),
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }

        // Video quality badge in top-left
        if (item.resolution.isNotBlank()) {
          Box(
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(4.dp)
              .background(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                shape = RoundedCornerShape(3.dp),
              )
              .padding(horizontal = 4.dp, vertical = 1.dp),
          ) {
            Text(
              text = item.resolution,
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
              ),
              color = Color.White,
            )
          }
        }

        // Subtitle indicator badge in bottom-right
        if (item.hasSubtitles) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(4.dp)
              .background(
                color = accentColor,
                shape = RoundedCornerShape(3.dp),
              )
              .size(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.ClosedCaption,
              contentDescription = "Subtitles available",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(10.dp),
            )
          }
        }

        // Playing overlay indicator
        if (item.isPlaying) {
          Box(
            modifier = Modifier
              .matchParentSize()
              .background(
                Brush.radialGradient(
                  listOf(
                    accentColor.copy(alpha = 0.3f),
                    Color.Transparent,
                  )
                )
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(28.dp),
            )
          }
        }
      }

      // Title and info
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (item.isPlaying) FontWeight.Bold else FontWeight.Normal,
            color = if (item.isPlaying) {
              accentColor
            } else if (item.isWatched) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      // Status badges
      when {
        item.isPlaying -> {
          Surface(
            color = accentColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(16.dp),
          ) {
            Text(
              text = "Playing",
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
              ),
            )
          }
        }

      }
    }
  }
}
