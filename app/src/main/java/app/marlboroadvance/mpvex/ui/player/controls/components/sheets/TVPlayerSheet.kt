package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

@Composable
fun TVPlayerSheet(
  subtitleTracks: ImmutableList<TrackNode>,
  audioTracks: ImmutableList<TrackNode>,
  currentSpeed: Float,
  speedPresets: List<Float>,
  currentAspect: VideoAspect,
  onSelectSubtitle: (Int) -> Unit,
  onSelectAudio: (TrackNode) -> Unit,
  onSpeedChange: (Float) -> Unit,
  onAspectChange: (VideoAspect) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val subtitleListState = rememberLazyListState()
  val audioListState = rememberLazyListState()
  val speedListState = rememberLazyListState()
  val aspectListState = rememberLazyListState()

  // Handle BACK button press - close immediately
  BackHandler {
    onDismissRequest()
  }

  // Full-screen dialog overlay
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = false,
      usePlatformDefaultWidth = false,
    ),
  ) {
    // Semi-transparent background
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.85f)),
      contentAlignment = Alignment.Center,
    ) {
      // Main content surface
      Surface(
        modifier = Modifier
          .fillMaxWidth(0.95f)
          .fillMaxHeight(0.85f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.large),
        ) {
          // Header
          Text(
            "Player Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
          )

          // Four-column layout
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
          ) {
            // Left Column - Subtitles
            Column(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            ) {
              // Subtitle Header
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
              ) {
                androidx.compose.material3.Icon(
                  Icons.Default.Subtitles,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                  "Subtitles",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }

              // Subtitle List
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                  ),
              ) {
                LazyColumn(
                  state = subtitleListState,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.small),
                  verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                ) {
                  itemsIndexed(subtitleTracks) { index, track ->
                    TrackRow(
                      title = track.lang?.ifBlank { "Track ${track.id}" } ?: "Track ${track.id}",
                      isSelected = (track.mainSelection?.toInt() ?: -1) > -1,
                      onClick = {
                        onSelectSubtitle(track.id)
                      },
                    )
                  }

                  if (subtitleTracks.isEmpty()) {
                    item {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(MaterialTheme.spacing.large),
                        contentAlignment = Alignment.Center,
                      ) {
                        Text(
                          "No subtitle tracks available",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          textAlign = TextAlign.Center,
                        )
                      }
                    }
                  }
                }
              }
            }

            // Second Column - Audio
            Column(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            ) {
              // Audio Header
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
              ) {
                androidx.compose.material3.Icon(
                  Icons.Default.Headphones,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                  "Audio",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }

              // Audio List
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                  ),
              ) {
                LazyColumn(
                  state = audioListState,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.small),
                  verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                ) {
                  itemsIndexed(audioTracks) { index, track ->
                    TrackRow(
                      title = track.lang?.ifBlank { "Track ${track.id}" } ?: "Track ${track.id}",
                      isSelected = track.isSelected,
                      onClick = {
                        onSelectAudio(track)
                      },
                    )
                  }

                  if (audioTracks.isEmpty()) {
                    item {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(MaterialTheme.spacing.large),
                        contentAlignment = Alignment.Center,
                      ) {
                        Text(
                          "No audio tracks available",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          textAlign = TextAlign.Center,
                        )
                      }
                    }
                  }
                }
              }
            }

            // Third Column - Speed
            Column(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            ) {
              // Speed Header
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
              ) {
                androidx.compose.material3.Icon(
                  Icons.Default.Speed,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                  "Speed",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }

              // Speed List
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                  ),
              ) {
                LazyColumn(
                  state = speedListState,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.small),
                  verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                ) {
                  itemsIndexed(speedPresets) { index, speed ->
                    TrackRow(
                      title = "${speed}x",
                      isSelected = currentSpeed == speed,
                      onClick = {
                        onSpeedChange(speed)
                      },
                    )
                  }
                }
              }
            }

            // Fourth Column - Aspect Ratio
            Column(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            ) {
              // Aspect Ratio Header
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
              ) {
                androidx.compose.material3.Icon(
                  Icons.Default.AspectRatio,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                  "Aspect Ratio",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }

              // Aspect Ratio List
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                  ),
              ) {
                LazyColumn(
                  state = aspectListState,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.small),
                  verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                ) {
                  item {
                    TrackRow(
                      title = stringResource(VideoAspect.Fit.titleRes),
                      isSelected = currentAspect == VideoAspect.Fit,
                      onClick = {
                        onAspectChange(VideoAspect.Fit)
                      },
                    )
                  }
                  item {
                    TrackRow(
                      title = stringResource(VideoAspect.Crop.titleRes),
                      isSelected = currentAspect == VideoAspect.Crop,
                      onClick = {
                        onAspectChange(VideoAspect.Crop)
                      },
                    )
                  }
                  item {
                    TrackRow(
                      title = stringResource(VideoAspect.Stretch.titleRes),
                      isSelected = currentAspect == VideoAspect.Stretch,
                      onClick = {
                        onAspectChange(VideoAspect.Stretch)
                      },
                    )
                  }
                }
              }
            }
          }

          // Footer hint
          Text(
            "Press BACK to close • Use D-PAD to navigate",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = MaterialTheme.spacing.small),
          )
        }
      }
    }
  }
}

@Composable
private fun TrackRow(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val backgroundColor = if (isSelected) {
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
  } else {
    Color.Transparent
  }

  val textColor = MaterialTheme.colorScheme.onSurface

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp)
      .background(backgroundColor, shape = MaterialTheme.shapes.small)
      .clickable(onClick = onClick)
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(
      selected = isSelected,
      onClick = null,
    )
    Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
    Text(
      title,
      color = textColor,
      fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      fontSize = 16.sp,
      modifier = Modifier.weight(1f),
      maxLines = 2,
    )
  }
}
