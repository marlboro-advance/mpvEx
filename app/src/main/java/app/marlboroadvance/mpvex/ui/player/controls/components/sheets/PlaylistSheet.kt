package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Video.Thumbnails
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.ui.theme.spacing
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
  val path: String = "", // Video path for thumbnail loading
  val duration: String = "", // Duration in formatted string (e.g., "10:30")
  val resolution: String = "", // Resolution (e.g., "1920x1080")
)

/**
 * LRU (Least Recently Used) cache for Bitmap thumbnails with a maximum size limit.
 * This prevents memory issues when dealing with large playlists (100+ videos).
 */
class LRUBitmapCache(private val maxSize: Int) {
  private val cache = LinkedHashMap<String, Bitmap?>(maxSize + 1, 1f, true)

  operator fun get(key: String): Bitmap? = synchronized(this) { cache[key] }

  operator fun set(key: String, value: Bitmap?) = synchronized(this) {
    cache[key] = value
    if (cache.size > maxSize) {
      // Remove the least recently used item
      cache.remove(cache.keys.firstOrNull())
    }
  }

  fun containsKey(key: String): Boolean = synchronized(this) { cache.containsKey(key) }

  fun clear() = synchronized(this) { cache.clear() }
}

/**
 * Loads a thumbnail from MediaStore cache (much faster than generating new thumbnails).
 * Falls back to null if no cached thumbnail exists (in which case a placeholder will be shown).
 */
private fun loadMediaStoreThumbnail(context: Context, uri: Uri): Bitmap? {
  return try {
    when (uri.scheme) {
      // For content:// URIs, we need to find the video ID first
      "content" -> {
        val path = uri.path
        if (path != null) {
          // Extract video ID from path if it's a MediaStore URI
          val videoId = extractVideoId(uri, context)
          if (videoId != null) {
            Thumbnails.getThumbnail(
              context.contentResolver,
              videoId,
              Thumbnails.MINI_KIND,
              null
            )
          } else {
            null
          }
        } else {
          null
        }
      }
      // For file:// URIs, try to find the corresponding MediaStore entry
      "file" -> {
        val filePath = uri.path ?: return null
        val projection = arrayOf(
          MediaStore.Video.Media._ID
        )
        val selection = "${MediaStore.Video.Media.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        context.contentResolver.query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          selectionArgs,
          null
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val videoId = cursor.getLong(idColumn)
            Thumbnails.getThumbnail(
              context.contentResolver,
              videoId,
              Thumbnails.MINI_KIND,
              null
            )
          } else {
            null
          }
        }
      }
      else -> null
    }
  } catch (e: Exception) {
    // Fallback with placeholder if thumbnail loading fails
    null
  }
}

/**
 * Extracts the video ID from a content:// URI.
 */
private fun extractVideoId(uri: Uri, context: Context): Long? {
  return try {
    val path = uri.path ?: return null
    // Extract ID from path like /external/video/media/123
    val idString = path.substringAfterLast('/').toLongOrNull() ?: return null

    // Verify this ID exists in MediaStore
    val projection = arrayOf(MediaStore.Video.Media._ID)
    val selection = "${MediaStore.Video.Media._ID} = ?"
    val selectionArgs = arrayOf(idString.toString())

    context.contentResolver.query(
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      projection,
      selection,
      selectionArgs,
      null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        cursor.getLong(idColumn)
      } else {
        null
      }
    }
  } catch (e: Exception) {
    null
  }
}

@Composable
fun PlaylistSheet(
  playlist: ImmutableList<PlaylistItem>,
  onDismissRequest: () -> Unit,
  onItemClick: (PlaylistItem) -> Unit,
  totalCount: Int = playlist.size,
  isM3UPlaylist: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  // Use theme colors dynamically
  val accentColor = MaterialTheme.colorScheme.primary

  // Thumbnail cache with LRU eviction - limited size to prevent memory issues with large playlists
  val thumbnailCache by remember {
    mutableStateOf(LRUBitmapCache(maxSize = 50))
  }

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
          text = "$totalCount items",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    track = { item ->
      PlaylistTrack(
        item = item,
        context = context,
        thumbnailCache = thumbnailCache as LRUBitmapCache,
        onClick = {
          onItemClick(item)
        },
        skipThumbnail = isM3UPlaylist,
      )
    },
  )
}

@Composable
fun PlaylistTrack(
  item: PlaylistItem,
  context: Context,
  thumbnailCache: LRUBitmapCache,
  onClick: () -> Unit,
  skipThumbnail: Boolean = false,
  modifier: Modifier = Modifier,
) {
  // Use theme colors dynamically
  val accentColor = MaterialTheme.colorScheme.primary
  val accentSecondary = MaterialTheme.colorScheme.tertiary
  val successColor = MaterialTheme.colorScheme.tertiary

  // Thumbnail state - uses cache to persist across recompositions
  val videoPath = item.path.ifBlank { item.uri.toString() }
  var thumbnail by remember(videoPath) {
    mutableStateOf(thumbnailCache[videoPath])
  }

  // Load thumbnail asynchronously for all items (not just visible ones)
  // Skip thumbnail loading for M3U playlists (network streams)
  LaunchedEffect(videoPath) {
    if (!skipThumbnail && thumbnail == null && !thumbnailCache.containsKey(videoPath)) {
      withContext(Dispatchers.IO) {
        try {
          val bmp = loadMediaStoreThumbnail(context, item.uri)
          thumbnail = bmp
          thumbnailCache[videoPath] = bmp
        } catch (e: Exception) {
          thumbnailCache[videoPath] = null
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

        // Video number badge in top-left with better visibility
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .background(
              color = Color.Black.copy(alpha = 0.7f),
              shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Text(
            text = "${item.index + 1}",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            ),
            color = Color.White,
          )
        }
      }

      // Title and info
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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

        // Duration and resolution chips
        if (item.duration.isNotEmpty() || item.resolution.isNotEmpty()) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (item.duration.isNotEmpty()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(4.dp),
              ) {
                Text(
                  text = item.duration,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                  ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            if (item.resolution.isNotEmpty()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(4.dp),
              ) {
                Text(
                  text = item.resolution,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                  ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
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
