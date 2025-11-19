package app.marlboroadvance.mpvex.ui.player.controls

import androidx.activity.compose.LocalActivity
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
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.preferences.preference.deleteAndGet
import app.marlboroadvance.mpvex.preferences.preference.plusAssign
import app.marlboroadvance.mpvex.preferences.preference.minusAssign
import app.marlboroadvance.mpvex.ui.player.Decoder.Companion.getDecoderFromValue
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerUpdates
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.controls.components.BrightnessSlider
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.MultipleSpeedPlayerUpdate
import app.marlboroadvance.mpvex.ui.player.controls.components.SeekbarWithTimers
import app.marlboroadvance.mpvex.ui.player.controls.components.TextPlayerUpdate
import app.marlboroadvance.mpvex.ui.player.controls.components.VolumeSlider
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.toFixed
import app.marlboroadvance.mpvex.ui.theme.controlColor
import app.marlboroadvance.mpvex.ui.theme.playerRippleConfiguration
import app.marlboroadvance.mpvex.ui.theme.spacing
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
  val isSpeedNonOne by remember(playbackSpeed) {
    derivedStateOf { abs((playbackSpeed ?: 1f) - 1f) > 0.001f }
  }
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

  val topRightControlsPref by appearancePreferences.topRightControls.collectAsState()
  val bottomRightControlsPref by appearancePreferences.bottomRightControls.collectAsState()
  val bottomLeftControlsPref by appearancePreferences.bottomLeftControls.collectAsState()
  val portraitBottomControlsPref by appearancePreferences.portraitBottomControls.collectAsState()

  val (topRightButtons, bottomRightButtons, bottomLeftButtons) =
    remember(
      topRightControlsPref,
      bottomRightControlsPref,
      bottomLeftControlsPref,
    ) {
      val usedButtons = mutableSetOf<app.marlboroadvance.mpvex.preferences.PlayerButton>()
      val topR = appearancePreferences.parseButtons(topRightControlsPref, usedButtons)
      val bottomR = appearancePreferences.parseButtons(bottomRightControlsPref, usedButtons)
      val bottomL = appearancePreferences.parseButtons(bottomLeftControlsPref, usedButtons)
      listOf(topR, bottomR, bottomL)
    }

  val portraitBottomButtons = remember(portraitBottomControlsPref) {
    appearancePreferences.parseButtons(portraitBottomControlsPref, mutableSetOf())
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
    LocalPlayerButtonsClickEvent provides { },
    LocalContentColor provides Color.White,
  ) {
    CompositionLocalProvider(
      LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
      val configuration = LocalConfiguration.current
      val isPortrait by remember(configuration) {
        derivedStateOf { configuration.orientation == ORIENTATION_PORTRAIT }
      }

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
            )
            .padding(horizontal = MaterialTheme.spacing.medium),
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

        val activity = LocalActivity.current as PlayerActivity
        val aspect by playerPreferences.videoAspect.collectAsState()
        val currentZoom by viewModel.videoZoom.collectAsState()

        val rawMediaTitle by MPVLib.propString["media-title"].collectAsState()
        val mediaTitle by remember(rawMediaTitle, activity) {
          derivedStateOf {
            rawMediaTitle?.takeIf { it.isNotBlank() }
              ?: activity.getTitleForControls()
          }
        }

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
              } + fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) {
                if (swapVolumeAndBrightness) -it else it
              } + fadeOut(playerControlsExitAnimationSpec())
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
              } + fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) {
                if (swapVolumeAndBrightness) it else -it
              } + fadeOut(playerControlsExitAnimationSpec())
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
          visible = controlsShown && areControlsLocked,
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
              val buttonShadow =
                Brush.radialGradient(
                  0.0f to Color.Black.copy(alpha = 0.3f),
                  0.7f to Color.Transparent,
                  1.0f to Color.Transparent,
                )

              if (viewModel.hasPlaylistSupport()) {
                androidx.compose.foundation.layout.Row(
                  horizontalArrangement = Arrangement.spacedBy(24.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Surface(
                    modifier =
                      Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(
                          enabled = viewModel.hasPrevious(),
                          onClick = { if (viewModel.hasPrevious()) viewModel.playPrevious() },
                        )
                        .then(
                          if (hideBackground) {
                            Modifier.background(brush = buttonShadow, shape = CircleShape)
                          } else {
                            Modifier
                          },
                        ),
                    shape = CircleShape,
                    color =
                      if (!hideBackground) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                      } else {
                        Color.Transparent
                      },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    border =
                      if (!hideBackground) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                      } else {
                        null
                      },
                  ) {
                    Icon(
                      imageVector = Icons.Default.SkipPrevious,
                      contentDescription = "Previous",
                      tint =
                        if (viewModel.hasPrevious()) {
                          if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
                        } else {
                          if (hideBackground) {
                            controlColor.copy(alpha = 0.38f)
                          } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                          }
                        },
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.small),
                    )
                  }

                  Surface(
                    modifier =
                      Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable(interaction, ripple(), onClick = viewModel::pauseUnpause)
                        .then(
                          if (hideBackground) {
                            Modifier.background(brush = buttonShadow, shape = CircleShape)
                          } else {
                            Modifier
                          },
                        ),
                    shape = CircleShape,
                    color =
                      if (!hideBackground) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                      } else {
                        Color.Transparent
                      },
                    contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    border =
                      if (!hideBackground) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                      } else {
                        null
                      },
                  ) {
                    Image(
                      painter = rememberAnimatedVectorPainter(icon, paused == false),
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.medium),
                      contentDescription = null,
                      colorFilter = ColorFilter.tint(LocalContentColor.current),
                    )
                  }

                  Surface(
                    modifier =
                      Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(
                          enabled = viewModel.hasNext(),
                          onClick = { if (viewModel.hasNext()) viewModel.playNext() },
                        )
                        .then(
                          if (hideBackground) {
                            Modifier.background(brush = buttonShadow, shape = CircleShape)
                          } else {
                            Modifier
                          },
                        ),
                    shape = CircleShape,
                    color =
                      if (!hideBackground) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                      } else {
                        Color.Transparent
                      },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    border =
                      if (!hideBackground) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                      } else {
                        null
                      },
                  ) {
                    Icon(
                      imageVector = Icons.Default.SkipNext,
                      contentDescription = "Next",
                      tint =
                        if (viewModel.hasNext()) {
                          if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
                        } else {
                          if (hideBackground) {
                            controlColor.copy(alpha = 0.38f)
                          } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                          }
                        },
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.small),
                    )
                  }
                }
              } else {
                Surface(
                  modifier =
                    Modifier
                      .size(64.dp)
                      .clip(CircleShape)
                      .clickable(interaction, ripple(), onClick = viewModel::pauseUnpause)
                      .then(
                        if (hideBackground) {
                          Modifier.background(brush = buttonShadow, shape = CircleShape)
                        } else {
                          Modifier
                        },
                      ),
                  shape = CircleShape,
                  color =
                    if (!hideBackground) {
                      MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                    } else {
                      Color.Transparent
                    },
                  contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
                  tonalElevation = 2.dp,
                  shadowElevation = 0.dp,
                  border =
                    if (!hideBackground) {
                      BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    } else {
                      null
                    },
                ) {
                  Image(
                    painter = rememberAnimatedVectorPainter(icon, paused == false),
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(MaterialTheme.spacing.medium),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(LocalContentColor.current),
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
              bottom.linkTo(parent.bottom, if (isPortrait) spacing.larger else spacing.small)
              start.linkTo(parent.start, spacing.small)
              end.linkTo(parent.end, spacing.small)
            },
        ) {
          val invertDuration by playerPreferences.invertDuration.collectAsState()
          val useWavySeekbar by playerPreferences.useWavySeekbar.collectAsState()

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
            useWavySeekbar = useWavySeekbar,
          )
        }

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
              top.linkTo(parent.top, if (isPortrait) spacing.extraLarge else spacing.small)
              start.linkTo(parent.start, spacing.small)
              if (isPortrait) {
                width = Dimension.fillToConstraints
                end.linkTo(parent.end, spacing.small)
              } else {
                width = Dimension.fillToConstraints
                end.linkTo(topRightControls.start, spacing.extraSmall)
              }
            },
        ) {
          if (isPortrait) {
            TopPlayerControlsPortrait(
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              onBackPress = onBackPress,
              viewModel = viewModel,
            )
          } else {
            TopLeftPlayerControlsLandscape(
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              onBackPress = onBackPress,
              viewModel = viewModel,
            )
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked && !isPortrait,
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
              top.linkTo(parent.top, spacing.small)
              end.linkTo(parent.end, spacing.small)
            },
        ) {
          TopRightPlayerControlsLandscape(
            buttons = topRightButtons,
            chapters = chapters,
            currentChapter = currentChapter,
            isSpeedNonOne = isSpeedNonOne,
            currentZoom = currentZoom,
            aspect = aspect,
            mediaTitle = mediaTitle,
            hideBackground = hideBackground,
            decoder = decoder,
            playbackSpeed = playbackSpeed ?: 1f,
            onBackPress = onBackPress,
            onOpenSheet = onOpenSheet,
            onOpenPanel = onOpenPanel,
            viewModel = viewModel,
            activity = activity,
          )
        }

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
              bottom.linkTo(seekbar.top, spacing.small)
              if (isPortrait) {
                start.linkTo(parent.start, spacing.small)
                end.linkTo(parent.end, spacing.small)
                width = Dimension.fillToConstraints
              } else {
                end.linkTo(parent.end, spacing.small)
              }
            },
        ) {
          if (isPortrait) {
            BottomPlayerControlsPortrait(
              buttons = portraitBottomButtons,
              chapters = chapters,
              currentChapter = currentChapter,
              isSpeedNonOne = isSpeedNonOne,
              currentZoom = currentZoom,
              aspect = aspect,
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              decoder = decoder,
              playbackSpeed = playbackSpeed ?: 1f,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              onOpenPanel = onOpenPanel,
              viewModel = viewModel,
              activity = activity,
            )
          } else {
            BottomRightPlayerControlsLandscape(
              buttons = bottomRightButtons,
              chapters = chapters,
              currentChapter = currentChapter,
              isSpeedNonOne = isSpeedNonOne,
              currentZoom = currentZoom,
              aspect = aspect,
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              decoder = decoder,
              playbackSpeed = playbackSpeed ?: 1f,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              onOpenPanel = onOpenPanel,
              viewModel = viewModel,
              activity = activity,
            )
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked && !isPortrait,
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
              bottom.linkTo(seekbar.top, spacing.small)
              start.linkTo(parent.start, spacing.small)
              width = Dimension.fillToConstraints
              end.linkTo(bottomRightControls.start, spacing.small)
            },
        ) {
          BottomLeftPlayerControlsLandscape(
            buttons = bottomLeftButtons,
            chapters = chapters,
            currentChapter = currentChapter,
            isSpeedNonOne = isSpeedNonOne,
            currentZoom = currentZoom,
            aspect = aspect,
            mediaTitle = mediaTitle,
            hideBackground = hideBackground,
            decoder = decoder,
            playbackSpeed = playbackSpeed ?: 1f,
            onBackPress = onBackPress,
            onOpenSheet = onOpenSheet,
            onOpenPanel = onOpenPanel,
            viewModel = viewModel,
            activity = activity,
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
      panelShown = panel,
      onDismissRequest = { onOpenPanel(Panels.None) },
    )
  }
}
