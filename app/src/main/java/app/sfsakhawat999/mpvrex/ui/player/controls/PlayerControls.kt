package app.sfsakhawat999.mpvrex.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.sfsakhawat999.mpvrex.ui.theme.controlColor
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.sfsakhawat999.mpvrex.R
import app.sfsakhawat999.mpvrex.preferences.AppearancePreferences
import app.sfsakhawat999.mpvrex.preferences.AudioPreferences
import app.sfsakhawat999.mpvrex.preferences.PlayerPreferences
import app.sfsakhawat999.mpvrex.preferences.preference.collectAsState
import app.sfsakhawat999.mpvrex.preferences.preference.deleteAndGet
import app.sfsakhawat999.mpvrex.preferences.preference.minusAssign
import app.sfsakhawat999.mpvrex.preferences.preference.plusAssign
import app.sfsakhawat999.mpvrex.ui.player.Decoder.Companion.getDecoderFromValue
import app.sfsakhawat999.mpvrex.ui.player.Panels
import app.sfsakhawat999.mpvrex.ui.player.PlayerUpdates
import app.sfsakhawat999.mpvrex.ui.player.PlayerViewModel
import app.sfsakhawat999.mpvrex.ui.player.Sheets
import app.sfsakhawat999.mpvrex.ui.player.controls.components.BrightnessSlider
import app.sfsakhawat999.mpvrex.ui.player.controls.components.ControlsButton
import app.sfsakhawat999.mpvrex.ui.player.controls.components.MultipleSpeedPlayerUpdate
import app.sfsakhawat999.mpvrex.ui.player.controls.components.SeekbarWithTimers
import app.sfsakhawat999.mpvrex.ui.player.controls.components.TextPlayerUpdate
import app.sfsakhawat999.mpvrex.ui.player.controls.components.VolumeSlider
import app.sfsakhawat999.mpvrex.ui.player.controls.components.sheets.toFixed
import app.sfsakhawat999.mpvrex.ui.theme.playerRippleConfiguration
import app.sfsakhawat999.mpvrex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject
import kotlin.math.abs

@Suppress("CompositionLocalAllowlist")
val LocalPlayerButtonsClickEvent = staticCompositionLocalOf { {} }

@OptIn(
  ExperimentalAnimationGraphicsApi::class,
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
@Suppress("CyclomaticComplexMethod", "ViewModelForwarding")
fun PlayerControls(
  viewModel: PlayerViewModel,
  onBackPress: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = MaterialTheme.spacing
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val playerPreferences = koinInject<PlayerPreferences>()
  val audioPreferences = koinInject<AudioPreferences>()
  val interactionSource = remember { MutableInteractionSource() }
  val controlsShown by viewModel.controlsShown.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekBarShown by viewModel.seekBarShown.collectAsState()
  val pausedForCache by MPVLib.propBoolean["paused-for-cache"].collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val duration by MPVLib.propInt["duration"].collectAsState()
  val position by MPVLib.propInt["time-pos"].collectAsState()
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()
  val gestureSeekAmount by viewModel.gestureSeekAmount.collectAsState()
  val doubleTapSeekAmount by viewModel.doubleTapSeekAmount.collectAsState()
  val showDoubleTapOvals by playerPreferences.showDoubleTapOvals.collectAsState()
  val showSeekTime by playerPreferences.showSeekTimeWhileSeeking.collectAsState()
  var isSeeking by remember { mutableStateOf(false) }
  var resetControls by remember { mutableStateOf(true) }
  val seekText by viewModel.seekText.collectAsState()
  val currentChapter by MPVLib.propInt["chapter"].collectAsState()
  val mpvDecoder by MPVLib.propString["hwdec-current"].collectAsState()
  val decoder by remember { derivedStateOf { getDecoderFromValue(mpvDecoder ?: "auto") } }
  val playerTimeToDisappear by playerPreferences.playerTimeToDisappear.collectAsState()
  val chapters by viewModel.chapters.collectAsState(persistentListOf())
  val onOpenSheet: (Sheets) -> Unit = {
    viewModel.sheetShown.update { _ -> it }
    if (it == Sheets.None) {
      viewModel.showControls()
    } else {
      viewModel.hideControls()
      viewModel.panelShown.update { Panels.None }
    }
  }
  val onOpenPanel: (Panels) -> Unit = {
    viewModel.panelShown.update { _ -> it }
    if (it == Panels.None) {
      viewModel.showControls()
    } else {
      viewModel.hideControls()
      viewModel.sheetShown.update { Sheets.None }
    }
  }

  LaunchedEffect(
    controlsShown,
    paused,
    isSeeking,
    resetControls,
  ) {
    if (controlsShown && paused == false && !isSeeking) {
      delay(playerTimeToDisappear.toLong())
      viewModel.hideControls()
    }
  }
  val transparentOverlay by animateFloatAsState(
    if (controlsShown && !areControlsLocked) .8f else 0f,
    animationSpec = playerControlsExitAnimationSpec(),
    label = "controls_transparent_overlay",
  )
  GestureHandler(
    viewModel = viewModel,
    interactionSource = interactionSource,
  )
  DoubleTapToSeekOvals(doubleTapSeekAmount, seekText, showDoubleTapOvals, showSeekTime, interactionSource)
  CompositionLocalProvider(
    LocalRippleConfiguration provides playerRippleConfiguration,
    LocalPlayerButtonsClickEvent provides { resetControls = !resetControls },
    LocalContentColor provides Color.White,
  ) {
    CompositionLocalProvider(
      LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
      ConstraintLayout(
        modifier =
          modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                Pair(0f, Color.Black),
                Pair(.2f, Color.Transparent),
                Pair(.7f, Color.Transparent),
                Pair(1f, Color.Black),
              ),
              alpha = transparentOverlay,
            ).padding(horizontal = MaterialTheme.spacing.medium),
      ) {
        val (topLeftControls, topRightControls) = createRefs()
        val (volumeSlider, brightnessSlider) = createRefs()
        val unlockControlsButton = createRef()
        val (bottomRightControls, bottomLeftControls) = createRefs()
        val playerPauseButton = createRef()
        val seekbar = createRef()
        val (playerUpdates) = createRefs()

        val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsState()
        val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsState()
        val brightness by viewModel.currentBrightness.collectAsState()
        val volume by viewModel.currentVolume.collectAsState()
        val mpvVolume by MPVLib.propInt["volume"].collectAsState()
        val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
        val reduceMotion by playerPreferences.reduceMotion.collectAsState()

        LaunchedEffect(volume, mpvVolume, isVolumeSliderShown) {
          delay(2000)
          if (isVolumeSliderShown) viewModel.isVolumeSliderShown.update { false }
        }
        LaunchedEffect(brightness, isBrightnessSliderShown) {
          delay(2000)
          if (isBrightnessSliderShown) viewModel.isBrightnessSliderShown.update { false }
        }
        AnimatedVisibility(
          isBrightnessSliderShown,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) {
                if (swapVolumeAndBrightness) -it else it
              } +
                fadeIn(
                  playerControlsEnterAnimationSpec(),
                )
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) {
                if (swapVolumeAndBrightness) -it else it
              } +
                fadeOut(
                  playerControlsExitAnimationSpec(),
                )
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(brightnessSlider) {
              if (swapVolumeAndBrightness) {
                start.linkTo(parent.start, spacing.medium)
              } else {
                end.linkTo(parent.end, spacing.medium)
              }
              top.linkTo(parent.top)
              bottom.linkTo(parent.bottom)
            },
        ) { BrightnessSlider(brightness, 0f..1f) }

        AnimatedVisibility(
          isVolumeSliderShown,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) {
                if (swapVolumeAndBrightness) it else -it
              } +
                fadeIn(
                  playerControlsEnterAnimationSpec(),
                )
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) {
                if (swapVolumeAndBrightness) it else -it
              } +
                fadeOut(
                  playerControlsExitAnimationSpec(),
                )
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(volumeSlider) {
              if (swapVolumeAndBrightness) {
                end.linkTo(parent.end, spacing.medium)
              } else {
                start.linkTo(parent.start, spacing.medium)
              }
              top.linkTo(parent.top)
              bottom.linkTo(parent.bottom)
            },
        ) {
          val boostCap by audioPreferences.volumeBoostCap.collectAsState()
          val displayVolumeAsPercentage by playerPreferences.displayVolumeAsPercentage.collectAsState()
          VolumeSlider(
            volume,
            mpvVolume = mpvVolume ?: 100,
            range = 0..viewModel.maxVolume,
            boostRange = if (boostCap > 0) 0..audioPreferences.volumeBoostCap.get() else null,
            displayAsPercentage = displayVolumeAsPercentage,
          )
        }
        val holdForMultipleSpeed by playerPreferences.holdForMultipleSpeed.collectAsState()
        val currentPlayerUpdate by viewModel.playerUpdate.collectAsState()
        val aspectRatio by playerPreferences.videoAspect.collectAsState()
        val videoZoom by viewModel.videoZoom.collectAsState()
        LaunchedEffect(currentPlayerUpdate, aspectRatio, videoZoom) {
          if (currentPlayerUpdate is PlayerUpdates.MultipleSpeed ||
            currentPlayerUpdate is PlayerUpdates.None
          ) {
            return@LaunchedEffect
          }
          delay(2000)
          viewModel.playerUpdate.update { PlayerUpdates.None }
        }
        AnimatedVisibility(
          currentPlayerUpdate !is PlayerUpdates.None,
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier =
            Modifier.constrainAs(playerUpdates) {
              linkTo(parent.start, parent.end)
              linkTo(parent.top, parent.bottom, bias = 0.2f)
            },
        ) {
          when (currentPlayerUpdate) {
            is PlayerUpdates.MultipleSpeed -> MultipleSpeedPlayerUpdate(currentSpeed = holdForMultipleSpeed)
            is PlayerUpdates.AspectRatio -> TextPlayerUpdate(stringResource(aspectRatio.titleRes))
            is PlayerUpdates.ShowText ->
              TextPlayerUpdate(
                (currentPlayerUpdate as PlayerUpdates.ShowText).value,
              )
            is PlayerUpdates.VideoZoom -> {
              val zoomPercentage = (videoZoom * 100).toInt()
              TextPlayerUpdate("Zoom: $zoomPercentage%")
            }
            else -> {}
          }
        }

        AnimatedVisibility(
          controlsShown && areControlsLocked,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier =
            Modifier.constrainAs(unlockControlsButton) {
              top.linkTo(parent.top, spacing.medium)
              start.linkTo(parent.start, spacing.medium)
            },
        ) {
          ControlsButton(
            Icons.Filled.Lock,
            onClick = { viewModel.unlockControls() },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          )
        }
        AnimatedVisibility(
          visible =
            (controlsShown && !areControlsLocked || gestureSeekAmount != null) || pausedForCache == true,
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier =
            Modifier.constrainAs(playerPauseButton) {
              end.linkTo(parent.absoluteRight)
              start.linkTo(parent.absoluteLeft)
              top.linkTo(parent.top)
              bottom.linkTo(parent.bottom)
            },
        ) {
          val showLoadingCircle by playerPreferences.showLoadingCircle.collectAsState()
          val icon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_play_to_pause)
          val interaction = remember { MutableInteractionSource() }
          when {
            gestureSeekAmount != null -> {
              Text(
                stringResource(
                  R.string.player_gesture_seek_indicator,
                  if (gestureSeekAmount!!.second >= 0) '+' else '-',
                  Utils.prettyTime(abs(gestureSeekAmount!!.second)),
                  Utils.prettyTime(gestureSeekAmount!!.first + gestureSeekAmount!!.second),
                ),
                style =
                  MaterialTheme.typography.headlineMedium.copy(
                    shadow = Shadow(Color.Black, blurRadius = 5f),
                  ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
              )
            }

            pausedForCache == true && showLoadingCircle -> {
              LoadingIndicator(
                modifier = Modifier.size(96.dp),
              )
            }

            controlsShown && !areControlsLocked -> {
              // Show playlist controls (previous, play/pause, next) if in playlist mode
              if (viewModel.hasPlaylistSupport()) {
                Row(
                  horizontalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                      .spacedBy(24.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  // Previous button
                  Surface(
                    modifier =
                      Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(
                          enabled = viewModel.hasPrevious(),
                          onClick = { if (viewModel.hasPrevious()) viewModel.playPrevious() },
                        ),
                    shape = CircleShape,
                    color =
                      if (!hideBackground) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(
                          alpha = 0.55f,
                        )
                      } else {
                        Color.Transparent
                      },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    border =
                      if (!hideBackground) {
                        BorderStroke(
                          1.dp,
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                      } else {
                        null
                      },
                  ) {
                    androidx.compose.material3.Icon(
                      imageVector = Icons.Default.SkipPrevious,
                      contentDescription = "Previous",
                      tint =
                        if (viewModel.hasPrevious()) {
                          MaterialTheme.colorScheme.onSurface
                        } else {
                          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                      modifier =
                        Modifier
                          .fillMaxSize()
                          .padding(MaterialTheme.spacing.small),
                    )
                  }

                  // Play/Pause button
                  Surface(
                    modifier =
                      Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable(
                          interaction,
                          ripple(),
                          onClick = viewModel::pauseUnpause,
                        ),
                    shape = CircleShape,
                    color =
                      if (!hideBackground) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(
                          alpha = 0.55f,
                        )
                      } else {
                        Color.Transparent
                      },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    border =
                      if (!hideBackground) {
                        BorderStroke(
                          1.dp,
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                      } else {
                        null
                      },
                  ) {
                    Image(
                      painter = rememberAnimatedVectorPainter(icon, paused == false),
                      modifier =
                        Modifier
                          .fillMaxSize()
                          .padding(MaterialTheme.spacing.medium),
                      contentDescription = null,
                    )
                  }

                  // Next button
                  Surface(
                    modifier =
                      Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(
                          enabled = viewModel.hasNext(),
                          onClick = { if (viewModel.hasNext()) viewModel.playNext() },
                        ),
                    shape = CircleShape,
                    color =
                      if (!hideBackground) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(
                          alpha = 0.55f,
                        )
                      } else {
                        Color.Transparent
                      },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    border =
                      if (!hideBackground) {
                        BorderStroke(
                          1.dp,
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                      } else {
                        null
                      },
                  ) {
                    androidx.compose.material3.Icon(
                      imageVector = Icons.Default.SkipNext,
                      contentDescription = "Next",
                      tint =
                        if (viewModel.hasNext()) {
                          MaterialTheme.colorScheme.onSurface
                        } else {
                          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                      modifier =
                        Modifier
                          .fillMaxSize()
                          .padding(MaterialTheme.spacing.small),
                    )
                  }
                }
              } else {
                // Single play/pause button for non-playlist mode
                Surface(
                  modifier =
                    Modifier
                      .size(64.dp)
                      .clip(CircleShape)
                      .clickable(
                        interaction,
                        ripple(),
                        onClick = viewModel::pauseUnpause,
                      ),
                  shape = CircleShape,
                  color =
                    if (!hideBackground) {
                      MaterialTheme.colorScheme.surfaceContainer.copy(
                        alpha = 0.55f,
                      )
                    } else {
                      Color.Transparent
                    },
                  contentColor = MaterialTheme.colorScheme.onSurface,
                  tonalElevation = 2.dp,
                  shadowElevation = 0.dp,
                  border =
                    if (!hideBackground) {
                      BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                      )
                    } else {
                      null
                    },
                ) {
                  Image(
                    painter = rememberAnimatedVectorPainter(icon, paused == false),
                    modifier =
                      Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.medium),
                    contentDescription = null,
                  )
                }
              }
            }
          }
        }
        AnimatedVisibility(
          visible = (controlsShown || seekBarShown) && !areControlsLocked,
          enter =
            if (!reduceMotion) {
              slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(seekbar) {
              bottom.linkTo(bottomLeftControls.top)
            },
        ) {
          val invertDuration by playerPreferences.invertDuration.collectAsState()

          SeekbarWithTimers(
            position = position?.toFloat() ?: 0f,
            duration = duration?.toFloat() ?: 0f,
            onValueChange = {
              isSeeking = true
              viewModel.seekTo(it.toInt())
            },
            onValueChangeFinished = {
              isSeeking = false
            },
            timersInverted = Pair(false, invertDuration),
            durationTimerOnCLick = { playerPreferences.invertDuration.set(!invertDuration) },
            positionTimerOnClick = {},
            chapters = chapters.toImmutableList(),
            paused = paused ?: false,
          )
        }
        val mediaTitle by MPVLib.propString["media-title"].collectAsState()
        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(topLeftControls) {
              top.linkTo(parent.top, spacing.medium)
              start.linkTo(parent.start)
              width = Dimension.fillToConstraints
              end.linkTo(topRightControls.start, spacing.medium)
            },
        ) {
          TopLeftPlayerControls(
            mediaTitle = mediaTitle ?: "",
            onBackClick = onBackPress,
            playlistInfo = viewModel.getPlaylistInfo(),
          )
        }
        // Top right controls
        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(topRightControls) {
              top.linkTo(parent.top, spacing.medium)
              end.linkTo(parent.end)
            },
        ) {
          val showChaptersButton by playerPreferences.showChaptersButton.collectAsState()
          TopRightPlayerControls(
            decoder = decoder,
            onDecoderClick = { viewModel.cycleDecoders() },
            onDecoderLongClick = { onOpenSheet(Sheets.Decoders) },
            isChaptersVisible = showChaptersButton && chapters.isNotEmpty(),
            onChaptersClick = { onOpenSheet(Sheets.Chapters) },
            onMoreClick = { onOpenSheet(Sheets.More) },
            onMoreLongClick = { onOpenPanel(Panels.VideoFilters) },
            // New parameters
            playbackSpeed = playbackSpeed ?: playerPreferences.defaultSpeed.get(),
            onPlaybackSpeedClick = {
              val currentSpeed = playbackSpeed ?: playerPreferences.defaultSpeed.get()
              val newSpeed = if (currentSpeed >= 2) 0.25f else currentSpeed + 0.25f
              MPVLib.setPropertyFloat("speed", newSpeed)
              playerPreferences.defaultSpeed.set(newSpeed)
            },
            onPlaybackSpeedLongClick = { onOpenSheet(Sheets.PlaybackSpeed) },
            onRotationClick = viewModel::cycleScreenRotations,
          )
        }
        // Bottom right controls (above seekbar)
        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(bottomRightControls) {
              bottom.linkTo(parent.bottom, spacing.medium)
              end.linkTo(parent.end)
            },
        ) {
          BottomRightPlayerControls(
            viewModel = viewModel,
          )
        }
        // Bottom left controls (above seekbar)
        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(bottomLeftControls) {
              bottom.linkTo(parent.bottom, spacing.medium)
              start.linkTo(parent.start)
              width = Dimension.fillToConstraints
              end.linkTo(bottomRightControls.start)
            },
        ) {
          val showChapterIndicator by playerPreferences.currentChaptersIndicator.collectAsState()
          BottomLeftPlayerControls(
            showChapterIndicator = showChapterIndicator,
            currentChapter = chapters.getOrNull(currentChapter ?: 0),
            onLockControls = viewModel::lockControls,
            onOpenSheet = onOpenSheet,
            // New parameters
            onAudioClick = { onOpenSheet(Sheets.AudioTracks) },
            onAudioLongClick = { onOpenPanel(Panels.AudioDelay) },
            onSubtitlesClick = { onOpenSheet(Sheets.SubtitleTracks) },
            onSubtitlesLongClick = { onOpenPanel(Panels.SubtitleSettings) },
          )
        }
      }
    }
    val sheetShown by viewModel.sheetShown.collectAsState()
    val subtitles by viewModel.subtitleTracks.collectAsState(persistentListOf())
    val audioTracks by viewModel.audioTracks.collectAsState(persistentListOf())
    val sleepTimerTimeRemaining by viewModel.remainingTime.collectAsState()
    val speedPresets by playerPreferences.speedPresets.collectAsState()
    PlayerSheets(
      viewModel = viewModel,
      sheetShown = sheetShown,
      subtitles = subtitles.toImmutableList(),
      onAddSubtitle = viewModel::addSubtitle,
      onSelectSubtitle = viewModel::selectSub,
      onRemoveSubtitle = viewModel::removeSubtitle,
      audioTracks = audioTracks.toImmutableList(),
      onAddAudio = viewModel::addAudio,
      onSelectAudio = {
        if (MPVLib.getPropertyInt("aid") == it.id) {
          MPVLib.setPropertyBoolean("aid", false)
        } else {
          MPVLib.setPropertyInt("aid", it.id)
        }
      },
      chapter = chapters.getOrNull(currentChapter ?: 0),
      chapters = chapters.toImmutableList(),
      onSeekToChapter = {
        MPVLib.setPropertyInt("chapter", it)
        viewModel.unpause()
      },
      decoder = decoder,
      onUpdateDecoder = { MPVLib.setPropertyString("hwdec", it.value) },
      speed = playbackSpeed ?: playerPreferences.defaultSpeed.get(),
      onSpeedChange = { MPVLib.setPropertyFloat("speed", it.toFixed(2)) },
      onMakeDefaultSpeed = { playerPreferences.defaultSpeed.set(it.toFixed(2)) },
      onAddSpeedPreset = { playerPreferences.speedPresets += it.toFixed(2).toString() },
      onRemoveSpeedPreset = { playerPreferences.speedPresets -= it.toFixed(2).toString() },
      onResetSpeedPresets = playerPreferences.speedPresets::delete,
      speedPresets = speedPresets.map { it.toFloat() }.sorted(),
      onResetDefaultSpeed = {
        MPVLib.setPropertyFloat("speed", playerPreferences.defaultSpeed.deleteAndGet().toFixed(2))
      },
      sleepTimerTimeRemaining = sleepTimerTimeRemaining,
      onStartSleepTimer = viewModel::startTimer,
      onOpenPanel = onOpenPanel,
      onShowSheet = onOpenSheet,
      onDismissRequest = { onOpenSheet(Sheets.None) },
    )
    val panel by viewModel.panelShown.collectAsState()
    PlayerPanels(
      viewModel = viewModel,
      panelShown = panel,
      onDismissRequest = { onOpenPanel(Panels.None) },
    )
  }
}

fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> =
  tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
  )

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> =
  tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
  )
