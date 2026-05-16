package app.gyrolet.mpvrx.ui.browser.cards

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.thumbnail.ThumbnailRepository
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Immutable
data class VideoCardUiConfig(
  val unlimitedNameLines: Boolean,
  val showThumbnails: Boolean,
  val showSizeChip: Boolean,
  val showResolutionChip: Boolean,
  val showFramerateInResolution: Boolean,
  val showProgressBar: Boolean,
  val showDateChip: Boolean,
  val showUnplayedOldVideoLabel: Boolean,
  val unplayedOldVideoDays: Int,
)

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
  isWatched: Boolean = false,
  onThumbClick: () -> Unit = {},
  isGridMode: Boolean = false,
  gridColumns: Int = 1,
  showSubtitleIndicator: Boolean = true,
  overrideShowSizeChip: Boolean? = null,
  overrideShowResolutionChip: Boolean? = null,
  useFolderNameStyle: Boolean = false,
  allowThumbnailGeneration: Boolean = true,
  uiConfig: VideoCardUiConfig? = null,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val resolvedUiConfig =
    uiConfig ?: VideoCardUiConfig(
      unlimitedNameLines = appearancePreferences.unlimitedNameLines.collectAsState().value,
      showThumbnails = browserPreferences.showVideoThumbnails.collectAsState().value,
      showSizeChip = browserPreferences.showSizeChip.collectAsState().value,
      showResolutionChip = browserPreferences.showResolutionChip.collectAsState().value,
      showFramerateInResolution = browserPreferences.showFramerateInResolution.collectAsState().value,
      showProgressBar = browserPreferences.showProgressBar.collectAsState().value,
      showDateChip = browserPreferences.showDateChip.collectAsState().value,
      showUnplayedOldVideoLabel = appearancePreferences.showUnplayedOldVideoLabel.collectAsState().value,
      unplayedOldVideoDays = appearancePreferences.unplayedOldVideoDays.collectAsState().value,
    )
  val maxLines = if (resolvedUiConfig.unlimitedNameLines) Int.MAX_VALUE else 2

  val showThumbnails = resolvedUiConfig.showThumbnails
  val showFramerateInResolution = resolvedUiConfig.showFramerateInResolution
  val showProgressBar = resolvedUiConfig.showProgressBar
  val showDateChip = resolvedUiConfig.showDateChip
  val showUnplayedOldVideoLabel = resolvedUiConfig.showUnplayedOldVideoLabel
  val unplayedOldVideoDays = resolvedUiConfig.unplayedOldVideoDays
  val selectionInset = 2.dp
  val selectionContainerColor =
    if (isSelected) {
      MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
    } else {
      Color.Transparent
    }

  // Use override parameters if provided, otherwise use preferences
  val showSizeChip = overrideShowSizeChip ?: resolvedUiConfig.showSizeChip
  val showResolutionChip = overrideShowResolutionChip ?: resolvedUiConfig.showResolutionChip

  val cardShape = RoundedCornerShape(16.dp)

  Card(
    modifier = modifier
      .then(
        if (isGridMode) Modifier.fillMaxWidth() else Modifier.fillMaxWidth()
      )
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
      ),
    shape = cardShape,
    colors = CardDefaults. cardColors(containerColor = Color. Transparent),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      if (isSelected) {
        Box(
          modifier =
            Modifier
              .matchParentSize()
              .padding(selectionInset)
              .clip(cardShape)
              .background(selectionContainerColor),
        )
      }

      if (isGridMode) {
        // GRID LAYOUT - Vertical arrangement
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalAlignment = if (gridColumns == 1) Alignment.Start else Alignment.CenterHorizontally,
        ) {
        val thumbnailRepository = koinInject<ThumbnailRepository>()
        val thumbWidthDp = 160.dp
        val aspect = 16f / 9f
        val thumbWidthPx = with(LocalDensity.current) { thumbWidthDp.roundToPx() }
        val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

        val thumbnailKey =
          remember(video.id, video.dateModified, video.size, thumbWidthPx, thumbHeightPx) {
            thumbnailRepository.thumbnailKey(video, thumbWidthPx, thumbHeightPx)
          }

        var thumbnail by remember(thumbnailKey) {
          mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx))
        }

        // Update thumbnail when the repository emits that this key became ready (folder prefetch or any other source).
        LaunchedEffect(thumbnailKey) {
          thumbnailRepository.thumbnailReadyKeys
            .filter { it == thumbnailKey }
            .collect {
              thumbnail = thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx)
            }
        }

        // Optional immediate generation (used on screens that don't run folder-wide sequential generation).
        LaunchedEffect(thumbnailKey, allowThumbnailGeneration, showThumbnails) {
          if (thumbnail == null && showThumbnails) {
            thumbnail =
              withContext(Dispatchers.IO) {
                if (allowThumbnailGeneration) {
                  thumbnailRepository.getThumbnail(video, thumbWidthPx, thumbHeightPx)
                } else {
                  thumbnailRepository.getCachedThumbnail(video, thumbWidthPx, thumbHeightPx)
                }
              }
          }
        }

        // Thumbnail
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
              onClick = onThumbClick,
              onLongClick = onLongClick,
            ),
          contentAlignment = Alignment.Center,
        ) {
          if (showThumbnails) {
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
          } else {
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
            // Check if video is recently modified (within threshold days)
            val currentTime = System.currentTimeMillis()
            val videoAge = currentTime - (video.dateModified * 1000) // dateModified is in seconds
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

          // Duration overlay
          Box(
            modifier = Modifier
              .align(Alignment. BottomEnd)
              .padding(6.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(Color.Black.copy(alpha = 0.65f))
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = video. durationFormatted,
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
            )
          }

          // Progress bar
          if (progressPercentage != null && showProgressBar) {
            Box(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp),
            ) {
              Box(modifier = Modifier.matchParentSize().background(Color.Black. copy(alpha = 0.6f)))
              Box(
                modifier = Modifier
                  .fillMaxHeight()
                  . fillMaxWidth(progressPercentage)
                  .background(MaterialTheme.colorScheme.primary),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title below thumbnail
        Text(
          text = video.displayName,
          style = if (useFolderNameStyle) {
            MaterialTheme.typography.titleSmall
          } else {
            if (gridColumns == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall
          }.let { baseStyle ->
            if (isRecentlyPlayed) baseStyle.copy(fontStyle = FontStyle.Italic) else baseStyle
          },
          color = if (isRecentlyPlayed) {
            MaterialTheme.colorScheme.tertiary 
          } else if (isWatched) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          } else {
            MaterialTheme.colorScheme.onSurface
          },
          maxLines = maxLines,
          overflow = TextOverflow. Ellipsis,
          textAlign = if (useFolderNameStyle) {
            TextAlign.Center
          } else {
            if (gridColumns == 1) TextAlign.Start else TextAlign.Center
          },
        )
        if (gridColumns == 1) {
          Spacer(modifier = Modifier.height(4.dp))
          FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
          ) {
            if (showSubtitleIndicator) {
              if (video.hasEmbeddedSubtitles && video.subtitleCodec.isNotBlank()) {
                video.subtitleCodec.split(" ").forEach { codec ->
                  Text(
                    text = codec,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                      .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp),
                      )
                      .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                }
              }
            }
            if (showSizeChip && video.sizeFormatted != "0 B" && video.sizeFormatted != "--") {
              Text(
                video.sizeFormatted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                  .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(8.dp),
                  )
                  .padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }

            val fpsOnly = video.resolution.substringAfter("@", "")
            val hasFps = fpsOnly.isNotEmpty()
            
            if (showResolutionChip) {
              if (video.resolution != "--") {
                 val displayResolution = if (showFramerateInResolution) {
                   video.resolution
                 } else {
                   video.resolution.substringBefore("@")
                 }
                
                 Text(
                   displayResolution,
                   style = MaterialTheme.typography.labelSmall,
                   modifier = Modifier
                     .background(
                       MaterialTheme.colorScheme.surfaceContainerHigh,
                       RoundedCornerShape(8.dp),
                     )
                     .padding(horizontal = 8.dp, vertical = 4.dp),
                   color = MaterialTheme.colorScheme.onSurface,
                 )
              }
            } else if (showFramerateInResolution && hasFps) {
                 Text(
                   "$fpsOnly FPS",
                   style = MaterialTheme.typography.labelSmall,
                   modifier = Modifier
                     .background(
                       MaterialTheme.colorScheme.surfaceContainerHigh,
                       RoundedCornerShape(8.dp),
                     )
                     .padding(horizontal = 8.dp, vertical = 4.dp),
                   color = MaterialTheme.colorScheme.onSurface,
                 )
            }
            
            if (showDateChip && video.dateModified > 0) {
              Text(
                formatDate(video.dateModified),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
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
      } else {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
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
            thumbnailRepository.thumbnailKey(video, thumbWidthPx, thumbHeightPx)
          }

        // Try to get from memory cache immediately (synchronous, no flicker)
        var thumbnail by remember(thumbnailKey) {
          mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx))
        }

        // Update thumbnail when the repository emits that this key became ready (folder prefetch or any other source).
        LaunchedEffect(thumbnailKey) {
          thumbnailRepository.thumbnailReadyKeys
            .filter { it == thumbnailKey }
            .collect {
              thumbnail = thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx)
            }
        }

        // Optional immediate generation (used on screens that don't run folder-wide sequential generation).
        LaunchedEffect(thumbnailKey, allowThumbnailGeneration, showThumbnails) {
          if (thumbnail == null && showThumbnails) {
            thumbnail =
              withContext(Dispatchers.IO) {
                if (allowThumbnailGeneration) {
                  thumbnailRepository.getThumbnail(video, thumbWidthPx, thumbHeightPx)
                } else {
                  thumbnailRepository.getCachedThumbnail(video, thumbWidthPx, thumbHeightPx)
                }
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
              .combinedClickable(
                onClick = onThumbClick,
                onLongClick = onLongClick,
              ),
          contentAlignment = Alignment.Center,
        ) {
          if (showThumbnails) {
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
          } else {
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
            // Check if video is recently modified (within threshold days)
            val currentTime = System.currentTimeMillis()
            val videoAge = currentTime - (video.dateModified * 1000) // dateModified is in seconds
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
            style = if (useFolderNameStyle) {
              MaterialTheme.typography.titleMedium
            } else {
              MaterialTheme.typography.titleSmall
            }.let { baseStyle ->
              if (isRecentlyPlayed) baseStyle.copy(fontStyle = FontStyle.Italic) else baseStyle
            },
            color = if (isRecentlyPlayed) {
              MaterialTheme.colorScheme.tertiary 
            } else if (isWatched) {
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            } else {
              MaterialTheme.colorScheme.onSurface
            },
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(4.dp))
          FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
          ) {
            if (showSubtitleIndicator) {
              if (video.hasEmbeddedSubtitles && video.subtitleCodec.isNotBlank()) {
                video.subtitleCodec.split(" ").forEach { codec ->
                  Text(
                    text = codec,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                      .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp),
                      )
                      .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                }
              }
            }
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
            // Resolution and Framerate logic (List view)
            val fpsOnly = video.resolution.substringAfter("@", "")
            val hasFps = fpsOnly.isNotEmpty()

            if (showResolutionChip) {
              if (video.resolution != "--") {
                val displayResolution = if (showFramerateInResolution) {
                  video.resolution
                } else {
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
            } else if (showFramerateInResolution && hasFps) {
              // Resolution is hidden, but framerate is enabled -> show only framerate
              Text(
                "$fpsOnly FPS",
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
            
            if (showDateChip && video.dateModified > 0) {
              Text(
                formatDate(video.dateModified),
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
  }
}

private fun formatDate(timestampSeconds: Long): String {
  val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
  return sdf.format(java.util.Date(timestampSeconds * 1000))
}




