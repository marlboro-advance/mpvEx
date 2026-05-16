package app.gyrolet.mpvrx.ui.player.controls

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.controls.components.AnimatedPlayPauseIcon

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AdvancedPreferences
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.AudioPreferences
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.preferences.preference.deleteAndGet
import app.gyrolet.mpvrx.preferences.preference.plusAssign
import app.gyrolet.mpvrx.preferences.preference.minusAssign
import app.gyrolet.mpvrx.ui.player.ControlsAnimationStyle
import app.gyrolet.mpvrx.ui.player.Decoder.Companion.getDecoderFromValue
import app.gyrolet.mpvrx.ui.player.Panels
import app.gyrolet.mpvrx.ui.player.PlayerActivity
import app.gyrolet.mpvrx.ui.player.PlayerUpdates
import app.gyrolet.mpvrx.ui.player.PlayerViewModel
import app.gyrolet.mpvrx.ui.player.Sheets
import app.gyrolet.mpvrx.ui.player.VideoAspect
import app.gyrolet.mpvrx.ui.player.VideoOpenAnimation
import app.gyrolet.mpvrx.ui.player.VideoOpenAnimationOverlay
import app.gyrolet.mpvrx.ui.player.buildControlsEnterH
import app.gyrolet.mpvrx.ui.player.buildControlsEnterV
import app.gyrolet.mpvrx.ui.player.buildControlsExitH
import app.gyrolet.mpvrx.ui.player.buildControlsExitV
import app.gyrolet.mpvrx.ui.player.getTrackSelectionId
import app.gyrolet.mpvrx.ui.player.setTrackSelectionId
import app.gyrolet.mpvrx.ui.player.controls.components.BrightnessSlider
import app.gyrolet.mpvrx.ui.player.controls.components.CompactSpeedIndicator
import app.gyrolet.mpvrx.ui.player.controls.components.ControlsButton
import app.gyrolet.mpvrx.ui.player.controls.components.MultipleSpeedPlayerUpdate
import app.gyrolet.mpvrx.ui.player.controls.components.SeekPlayerUpdate
import app.gyrolet.mpvrx.ui.player.controls.components.SeekbarWithTimers
import app.gyrolet.mpvrx.ui.player.controls.components.SlideToUnlock
import app.gyrolet.mpvrx.ui.player.controls.components.SpeedControlSlider
import app.gyrolet.mpvrx.ui.player.controls.components.TextPlayerUpdate
import app.gyrolet.mpvrx.ui.player.controls.components.VolumeSlider
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.toFixed
import app.gyrolet.mpvrx.ui.theme.controlColor
import app.gyrolet.mpvrx.ui.theme.playerRippleConfiguration
import app.gyrolet.mpvrx.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

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
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
@Suppress("CyclomaticComplexMethod", "ViewModelForwarding")
fun PlayerControls(
  viewModel: PlayerViewModel,
  onBackPress: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = MaterialTheme.spacing
  val advancedPreferences = koinInject<AdvancedPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val playerPreferences = koinInject<PlayerPreferences>()
  val audioPreferences = koinInject<AudioPreferences>()
  val showSystemStatusBar by playerPreferences.showSystemStatusBar.collectAsState()
  val showSystemNavigationBar by playerPreferences.showSystemNavigationBar.collectAsState()
  val interactionSource = remember { MutableInteractionSource() }
  val controlsShown by viewModel.controlsShown.collectAsState()
  val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekBarShown by viewModel.seekBarShown.collectAsState()
  val pausedForCache by MPVLib.propBoolean["paused-for-cache"].collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val duration by MPVLib.propInt["duration"].collectAsState()
  val position by MPVLib.propInt["time-pos"].collectAsState()
  val precisePosition by viewModel.precisePosition.collectAsState()
  val preciseDuration by viewModel.preciseDuration.collectAsState()
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()
  val seekState by viewModel.seekState.collectAsState()
  val doubleTapSeekAmount = seekState.amount
  val showDoubleTapOvals by playerPreferences.showDoubleTapOvals.collectAsState()
  val showSeekTime by playerPreferences.showSeekTimeWhileSeeking.collectAsState()
  val safeAreaWindow by playerPreferences.safeAreaWindow.collectAsState()
  val safeAreaInsetModifier =
    if (safeAreaWindow) {
      Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    } else {
      Modifier
    }
  var isSeeking by remember { mutableStateOf(false) }
  var resetControlsTimestamp by remember { mutableStateOf(0L) }
  val seekText = seekState.text
  val currentChapter by MPVLib.propInt["chapter"].collectAsState()
  val mpvDecoder by MPVLib.propString["hwdec-current"].collectAsState()
  val decoder by remember { derivedStateOf { getDecoderFromValue(mpvDecoder ?: "auto") } }
  val isSpeedNonOne by remember(playbackSpeed) {
    derivedStateOf { abs((playbackSpeed ?: 1f) - 1f) > 0.001f }
  }
  val playerTimeToDisappear by playerPreferences.playerTimeToDisappear.collectAsState()
  val chapters by viewModel.chapters.collectAsState(persistentListOf())
  val skipSegments by viewModel.skipSegments.collectAsState(persistentListOf())
  val currentSkippableSegment by viewModel.currentSkippableSegment.collectAsState()
  val playlistMode by playerPreferences.playlistMode.collectAsState()
    val haptic = LocalHapticFeedback.current

    val customButtons by viewModel.customButtons.collectAsState()

  val abLoop by viewModel.abLoopState.collectAsState()
  val abLoopA = abLoop.a
  val abLoopB = abLoop.b

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
      val usedButtons = mutableSetOf<app.gyrolet.mpvrx.preferences.PlayerButton>()
      val topR = appearancePreferences.parseButtons(topRightControlsPref, usedButtons)
      val bottomR = appearancePreferences.parseButtons(bottomRightControlsPref, usedButtons)
      val bottomL = appearancePreferences.parseButtons(bottomLeftControlsPref, usedButtons)
      listOf(topR, bottomR, bottomL)
    }

  val portraitBottomButtons = remember(portraitBottomControlsPref) {
    appearancePreferences.parseButtons(portraitBottomControlsPref, mutableSetOf())
  }

  var isUnlockSliderDragging by remember { mutableStateOf(false) }

  LaunchedEffect(
    controlsShown,
    paused,
    isSeeking,
    resetControlsTimestamp,
    areControlsLocked,
    isUnlockSliderDragging,
  ) {
    if (controlsShown && paused == false && !isSeeking && !isUnlockSliderDragging) {
      // Use 2 second delay when controls are locked, otherwise use user preference
      val delayTime = if (areControlsLocked) 2000L else playerTimeToDisappear.toLong()
      delay(delayTime)
      viewModel.hideControls()
    }
  }

  val videoOpenAnim by playerPreferences.videoOpenAnimation.collectAsState()
  val videoOpenAnimState by viewModel.videoOpenAnimationState.collectAsState()
  val animSpeed by playerPreferences.animationSpeed.collectAsState()

  val transparentOverlay by animateFloatAsState(
    if (controlsShown && !areControlsLocked) .8f else 0f,
    animationSpec = playerControlsExitAnimationSpec(),
    label = "controls_transparent_overlay",
  )

  GestureHandler(
    viewModel = viewModel,
    interactionSource = interactionSource,
  )

  DoubleTapToSeekOvals(doubleTapSeekAmount, seekText, showDoubleTapOvals, showSeekTime, showSeekTime, interactionSource)

  Box(
    modifier = modifier.fillMaxSize(),
  ) {
    VideoOpenAnimationOverlay(
      style = videoOpenAnim,
      speedMultiplier = animSpeed,
      animationState = videoOpenAnimState,
    )
    if (statisticsPage == 6) {
      CustomStatsPageSixOverlay(
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .then(safeAreaInsetModifier)
            .padding(top = 16.dp, start = 14.dp),
      )
    }

    CompositionLocalProvider(
      LocalRippleConfiguration provides playerRippleConfiguration,
      LocalPlayerButtonsClickEvent provides { resetControlsTimestamp = System.currentTimeMillis() },
      LocalContentColor provides Color.White,
    ) {
      CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
      ) {
        val configuration = LocalConfiguration.current
        val isPortrait by remember(configuration) {
          derivedStateOf { configuration.orientation == ORIENTATION_PORTRAIT }
        }
        val density = LocalDensity.current
        var controlsLayoutHeightPx by remember { mutableStateOf(0) }
        var landscapeRightButtonsTopPx by remember { mutableStateOf<Int?>(null) }
        var portraitButtonsTopPx by remember { mutableStateOf<Int?>(null) }
        var bottomRightControlsTopPx by remember { mutableStateOf<Int?>(null) }

        ConstraintLayout(
          modifier =
            Modifier
              .fillMaxSize()
              .onSizeChanged { controlsLayoutHeightPx = it.height }
              .background(
                Brush.verticalGradient(
                  Pair(0f, Color.Black),
                  Pair(.4f, Color.Transparent),
                  Pair(.6f, Color.Transparent),
                  Pair(1f, Color.Black),
                ),
                alpha = transparentOverlay,
              )
              .then(safeAreaInsetModifier),
        ) {
        val (topLeftControls, topRightControls) = createRefs()
        val (volumeSlider, brightnessSlider) = createRefs()
        val unlockControlsButton = createRef()
        val (bottomRightControls, bottomLeftControls) = createRefs()
        val playerPauseButton = createRef()
        val skipSegmentChip = createRef()
        val seekbar = createRef()
        val (playerUpdates) = createRefs()
        val (customLeftButtonsRef, customRightButtonsRef) = createRefs()
        val customButtonsPortraitRef = createRef()

        val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsState()
        val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsState()
        val brightness by viewModel.currentBrightness.collectAsState()
        val volume by viewModel.currentVolume.collectAsState()
        val volumePercent by viewModel.currentVolumePercent.collectAsState()
        val mpvVolume by MPVLib.propInt["volume"].collectAsState()
        val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
        // Overlay visibility — Group 1
        val showVolumeGestureOverlay by playerPreferences.showVolumeGestureOverlay.collectAsState()
        val showBrightnessGestureOverlay by playerPreferences.showBrightnessGestureOverlay.collectAsState()
        val reduceMotion by playerPreferences.reduceMotion.collectAsState()
        val controlsAnimStyle by playerPreferences.controlsAnimStyle.collectAsState()
        val enterMs = (100 * animSpeed).toInt().coerceAtLeast(30)
        val exitMs  = (300 * animSpeed).toInt().coerceAtLeast(50)

        val activity = LocalActivity.current as PlayerActivity
        val aspect by viewModel.videoAspect.collectAsState()
        val currentZoom by viewModel.videoZoom.collectAsState()

        val rawMediaTitle by MPVLib.propString["media-title"].collectAsState()
        val mediaTitle by remember(rawMediaTitle, activity) {
          derivedStateOf {
            rawMediaTitle?.takeIf { it.isNotBlank() }
              ?: activity.getTitleForControls()
          }
        }

        // Slider display duration: 1000ms shown + 300ms exit animation = 1300ms total
        val sliderDisplayDuration = 1000L

        val volumeSliderTimestamp by viewModel.volumeSliderTimestamp.collectAsState()
        val brightnessSliderTimestamp by viewModel.brightnessSliderTimestamp.collectAsState()

        // Track timestamp to restart timer on every gesture event
        LaunchedEffect(volumeSliderTimestamp) {
          if (isVolumeSliderShown && volumeSliderTimestamp > 0) {
            delay(sliderDisplayDuration)
            viewModel.isVolumeSliderShown.update { false }
          }
        }

        LaunchedEffect(brightnessSliderTimestamp) {
          if (isBrightnessSliderShown && brightnessSliderTimestamp > 0) {
            delay(sliderDisplayDuration)
            viewModel.isBrightnessSliderShown.update { false }
          }
        }

        val areSlidersShown = isBrightnessSliderShown || isVolumeSliderShown
        val navigationBarsPadding =
          if (showSystemNavigationBar) {
            WindowInsets.navigationBars.asPaddingValues()
          } else {
            null
          }
        val navigationStartPaddingModifier =
          navigationBarsPadding?.let { navBarPadding ->
            Modifier.padding(
              start = navBarPadding.calculateLeftPadding(LayoutDirection.Ltr),
            )
          } ?: Modifier
        val navigationEndPaddingModifier =
          navigationBarsPadding?.let { navBarPadding ->
            Modifier.padding(
              end = navBarPadding.calculateRightPadding(LayoutDirection.Ltr),
            )
          } ?: Modifier
        val navigationHorizontalPaddingModifier =
          navigationBarsPadding?.let { navBarPadding ->
            Modifier.padding(
              start = navBarPadding.calculateLeftPadding(LayoutDirection.Ltr),
              end = navBarPadding.calculateRightPadding(LayoutDirection.Ltr),
            )
          } ?: Modifier
        val skipSegmentChipBottomOffset =
          (if (isPortrait) 104.dp else 88.dp) +
            (navigationBarsPadding?.calculateBottomPadding() ?: 0.dp)

        AnimatedVisibility(
          isBrightnessSliderShown && showBrightnessGestureOverlay,
          enter = buildControlsEnterH(
            controlsAnimStyle, reduceMotion, enterMs,
          ) { if (swapVolumeAndBrightness) -it else it },
          exit = buildControlsExitH(
            controlsAnimStyle, reduceMotion, exitMs,
          ) { if (swapVolumeAndBrightness) -it else it },
          modifier =
            Modifier.constrainAs(brightnessSlider) {
              if (swapVolumeAndBrightness) {
                start.linkTo(parent.start, if (isPortrait) spacing.large else spacing.extraLarge)
              } else {
                end.linkTo(parent.end, if (isPortrait) spacing.large else spacing.extraLarge)
              }
              top.linkTo(parent.top, spacing.larger)
              bottom.linkTo(parent.bottom, spacing.extraLarge)
            },
        ) { BrightnessSlider(brightness, 0f..1f) }

        AnimatedVisibility(
          isVolumeSliderShown && showVolumeGestureOverlay,
          enter = buildControlsEnterH(
            controlsAnimStyle, reduceMotion, enterMs,
          ) { if (swapVolumeAndBrightness) it else -it },
          exit = buildControlsExitH(
            controlsAnimStyle, reduceMotion, exitMs,
          ) { if (swapVolumeAndBrightness) it else -it },
          modifier =
            Modifier.constrainAs(volumeSlider) {
              if (swapVolumeAndBrightness) {
                end.linkTo(parent.end, if (isPortrait) spacing.large else spacing.extraLarge)
              } else {
                start.linkTo(parent.start, if (isPortrait) spacing.large else spacing.extraLarge)
              }
              top.linkTo(parent.top, spacing.larger)
              bottom.linkTo(parent.bottom, spacing.extraLarge)
            },
        ) {
          val boostCap by audioPreferences.volumeBoostCap.collectAsState()
          val displayVolumeAsPercentage by playerPreferences.displayVolumeAsPercentage.collectAsState()

          // Show if boost is allowed (boostCap > 0) OR if we are currently boosted (> 100)
          val currentBoost = (mpvVolume ?: 100) - 100
          val showBoost = boostCap > 0 || currentBoost > 0
          val effBoostCap = maxOf(boostCap, currentBoost)

          VolumeSlider(
            volume,
            volumePercentage = volumePercent,
            mpvVolume = mpvVolume ?: 100,
            range = 0..viewModel.maxVolume,
            boostRange = if (showBoost) 0..effBoostCap else null,
            displayAsPercentage = displayVolumeAsPercentage,
          )
        }

        val holdForMultipleSpeed by playerPreferences.holdForMultipleSpeed.collectAsState()
        val currentPlayerUpdate by viewModel.playerUpdate.collectAsState()
        val isTranslatingSub by viewModel.isTranslatingSub.collectAsState()
        val translationProgress by viewModel.translationProgress.collectAsState()
        val translationStatus by viewModel.translationStatus.collectAsState()
        val translatingTrackName by viewModel.translatingTrackName.collectAsState()
        val isRealtimeSubsActive by viewModel.isRealtimeSubsActive.collectAsState()
        val realtimeSubsLanguage by viewModel.realtimeSubsLanguage.collectAsState()
        val isGeneratingSubtitles by viewModel.isGeneratingSubtitles.collectAsState()
        val subtitleGenerationProgress by viewModel.subtitleGenerationProgress.collectAsState()
        val subtitleGenerationStatus by viewModel.subtitleGenerationStatus.collectAsState()

        // Overlay visibility — Groups 2 & 5
        val showHoldSpeedOverlay by playerPreferences.showHoldSpeedOverlay.collectAsState()
        val showAspectRatioOverlay by playerPreferences.showAspectRatioOverlay.collectAsState()
        val showZoomLevelOverlay by playerPreferences.showZoomLevelOverlay.collectAsState()
        val showRepeatShuffleOverlay by playerPreferences.showRepeatShuffleOverlay.collectAsState()
        val showActionFeedbackOverlay by playerPreferences.showActionFeedbackOverlay.collectAsState()

        // Determines whether the center action-pill should be visible for the current update.
        // Each update type is gated by its own toggle so the user can silence individual
        // categories without affecting the others or the underlying gesture behaviour.
        val shouldShowPlayerUpdate = when (currentPlayerUpdate) {
          is PlayerUpdates.MultipleSpeed,
          is PlayerUpdates.DynamicSpeedControl  -> showHoldSpeedOverlay
          is PlayerUpdates.AspectRatio           -> showAspectRatioOverlay
          is PlayerUpdates.VideoZoom             -> showZoomLevelOverlay
          is PlayerUpdates.RepeatMode,
          is PlayerUpdates.Shuffle               -> showRepeatShuffleOverlay
          is PlayerUpdates.ShowText              -> showActionFeedbackOverlay
          is PlayerUpdates.HorizontalSeek,
          is PlayerUpdates.FrameInfo             -> true   // Groups 3/4 — not in scope
          is PlayerUpdates.None                  -> false
        }
        val aspectRatio by viewModel.videoAspect.collectAsState()
        val currentAspectRatio by viewModel.currentAspectRatio.collectAsState()
        val videoZoom by viewModel.videoZoom.collectAsState()

        LaunchedEffect(currentPlayerUpdate, aspectRatio, videoZoom) {
          if (currentPlayerUpdate is PlayerUpdates.MultipleSpeed ||
            currentPlayerUpdate is PlayerUpdates.DynamicSpeedControl ||
            currentPlayerUpdate is PlayerUpdates.None
          ) {
            return@LaunchedEffect
          }
          delay(2000)
          viewModel.playerUpdate.update { PlayerUpdates.None }
        }

        AnimatedVisibility(
          shouldShowPlayerUpdate,
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier =
            Modifier
              .then(
                if (showSystemStatusBar) {
                  Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                  Modifier
                }
              )
              .constrainAs(playerUpdates) {
                linkTo(parent.start, parent.end)
                top.linkTo(parent.top, if (isPortrait) 104.dp else 64.dp)
              },
        ) {
          when (currentPlayerUpdate) {
            is PlayerUpdates.MultipleSpeed -> MultipleSpeedPlayerUpdate(currentSpeed = holdForMultipleSpeed)
            is PlayerUpdates.DynamicSpeedControl -> {
              val speedUpdate = currentPlayerUpdate as PlayerUpdates.DynamicSpeedControl
              val currentSpeed = speedUpdate.speed
              val showDynamicSpeedOverlay by playerPreferences.showDynamicSpeedOverlay.collectAsState()
              val shouldShowFull = speedUpdate.showFullOverlay
              var isCollapsed by remember { mutableStateOf(false) }

              LaunchedEffect(currentSpeed, shouldShowFull) {
                if (shouldShowFull) {
                  isCollapsed = false
                  delay(1500)
                  isCollapsed = true
                } else {
                  isCollapsed = true
                }
              }

              if (showDynamicSpeedOverlay) {
                if (isCollapsed) {
                  // Simple compact indicator
                  CompactSpeedIndicator(currentSpeed = currentSpeed)
                } else {
                  // Full speed control slider
                  SpeedControlSlider(currentSpeed = currentSpeed)
                }
              } else {
                // fallback, simple indicator
                CompactSpeedIndicator(currentSpeed = currentSpeed)
              }
            }
            is PlayerUpdates.AspectRatio -> {
              val customRatiosSet by playerPreferences.customAspectRatios.collectAsState()
              val displayText = if (currentAspectRatio > 0) {
                // Custom aspect ratio - try to find its label first
                val customLabel = customRatiosSet.firstNotNullOfOrNull { str ->
                  val parts = str.split("|")
                  if (parts.size == 2) {
                    val savedRatio = parts[1].toDoubleOrNull()
                    if (savedRatio != null && kotlin.math.abs(savedRatio - currentAspectRatio) < 0.01) {
                      parts[0] // Return the label
                    } else null
                  } else null
                }

                customLabel ?: run {
                  // No custom label found, use preset names or format as ratio
                  val ratio = currentAspectRatio
                  when {
                    kotlin.math.abs(ratio - 16.0/9.0) < 0.01 -> "16:9"
                    kotlin.math.abs(ratio - 4.0/3.0) < 0.01 -> "4:3"
                    kotlin.math.abs(ratio - 16.0/10.0) < 0.01 -> "16:10"
                    kotlin.math.abs(ratio - 21.0/9.0) < 0.01 -> "21:9"
                    kotlin.math.abs(ratio - 32.0/9.0) < 0.01 -> "32:9"
                    kotlin.math.abs(ratio - 1.0) < 0.01 -> "1:1"
                    kotlin.math.abs(ratio - 2.35) < 0.01 -> "2.35:1"
                    kotlin.math.abs(ratio - 2.39) < 0.01 -> "2.39:1"
                    else -> String.format("%.2f:1", ratio)
                  }
                }
              } else {
                // Standard mode (Fit/Crop/Stretch)
                stringResource(aspectRatio.titleRes)
              }
              TextPlayerUpdate(displayText)
            }
            is PlayerUpdates.ShowText ->
              TextPlayerUpdate(
                (currentPlayerUpdate as PlayerUpdates.ShowText).value,
                modifier = Modifier.widthIn(min = 120.dp),
              )

            is PlayerUpdates.VideoZoom -> {
              val zoomPercentage = (videoZoom * 100).toInt()
              TextPlayerUpdate(
                text = String.format("Zoom:%3d%%", zoomPercentage),
                modifier = Modifier.widthIn(min = 112.dp),
              )
            }

            is PlayerUpdates.HorizontalSeek -> {
              val seekUpdate = currentPlayerUpdate as PlayerUpdates.HorizontalSeek
              SeekPlayerUpdate(
                currentTime = seekUpdate.currentTime,
                seekDelta = "[${seekUpdate.seekDelta}]",
                modifier = Modifier.widthIn(min = 168.dp),
              )
            }

            is PlayerUpdates.RepeatMode -> {
              val mode = (currentPlayerUpdate as PlayerUpdates.RepeatMode).mode
              val text = when (mode) {
                app.gyrolet.mpvrx.ui.player.RepeatMode.OFF -> "Repeat: Off"
                app.gyrolet.mpvrx.ui.player.RepeatMode.ONE -> "Repeat: Current file"
                app.gyrolet.mpvrx.ui.player.RepeatMode.ALL -> {
                  if (playlistMode && viewModel.hasPlaylistSupport()) {
                    "Repeat: All playlist"
                  } else {
                    "Repeat: Current file"
                  }
                }
              }
              TextPlayerUpdate(text)
            }

            is PlayerUpdates.Shuffle -> {
              val enabled = (currentPlayerUpdate as PlayerUpdates.Shuffle).enabled
              val text = if (enabled) {
                if (playlistMode && viewModel.hasPlaylistSupport()) {
                  "Shuffle: On"
                } else {
                  "Shuffle: Not available"
                }
              } else {
                "Shuffle: Off"
              }
              TextPlayerUpdate(text)
            }

            is PlayerUpdates.FrameInfo -> {
              val frameInfo = (currentPlayerUpdate as PlayerUpdates.FrameInfo)
              val text = if (frameInfo.totalFrames > 0) {
                "Frame: ${frameInfo.currentFrame}/${frameInfo.totalFrames}"
              } else {
                "Frame: ${frameInfo.currentFrame}"
              }
              TextPlayerUpdate(text)
            }

            else -> {}
          }
        }



        val areButtonsVisible = controlsShown && !areControlsLocked && !areSlidersShown
        val leftCustomButtons = remember(customButtons) { customButtons.filter { it.isLeft } }
        val rightCustomButtons = remember(customButtons) { customButtons.filterNot { it.isLeft } }
        val showLandscapeLeftCustomButtons = areButtonsVisible && !isPortrait && leftCustomButtons.isNotEmpty()
        val showLandscapeRightCustomButtons = areButtonsVisible && !isPortrait && rightCustomButtons.isNotEmpty()
        val showPortraitCustomButtons = areButtonsVisible && isPortrait && customButtons.isNotEmpty()
        val customButtonsRowVerticalPadding = 2.dp
        val skipChipToButtonsSpacing = 2.dp
        val bottomRightControlsBottomOffset =
          if (bottomRightControlsTopPx != null && controlsLayoutHeightPx > 0) {
            with(density) {
              (controlsLayoutHeightPx - bottomRightControlsTopPx!!).toDp() +
                skipChipToButtonsSpacing
            }
          } else {
            skipSegmentChipBottomOffset
          }
        val skipChipBottomTarget =
          when {
            showLandscapeRightCustomButtons &&
              landscapeRightButtonsTopPx != null &&
              controlsLayoutHeightPx > 0 -> {
              with(density) {
                (controlsLayoutHeightPx - landscapeRightButtonsTopPx!!).toDp() +
                  skipChipToButtonsSpacing
              }
            }

            showPortraitCustomButtons &&
              portraitButtonsTopPx != null &&
              controlsLayoutHeightPx > 0 -> {
              with(density) {
                (controlsLayoutHeightPx - portraitButtonsTopPx!!).toDp() +
                  skipChipToButtonsSpacing
              }
            }

            else -> maxOf(skipSegmentChipBottomOffset, bottomRightControlsBottomOffset)
          }
        val skipChipBottomOffset by animateDpAsState(
          targetValue = skipChipBottomTarget,
          animationSpec = spring(),
          label = "skip_chip_bottom_offset",
        )

        AnimatedVisibility(
            visible = showLandscapeLeftCustomButtons,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = navigationStartPaddingModifier.constrainAs(customLeftButtonsRef) {
                start.linkTo(parent.start, spacing.large)
                bottom.linkTo(bottomRightControls.top, spacing.medium)
                width = Dimension.preferredWrapContent
                height = Dimension.wrapContent
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = customButtonsRowVerticalPadding)
                    .horizontalScroll(rememberScrollState())
            ) {
                leftCustomButtons.forEach { button ->
                    key(button.id) {
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = buttonInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButton(button.id)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButtonLongPress(button.id)
                                }
                            )
                    ) {
                        Text(
                            text = button.label,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .basicMarquee(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showLandscapeRightCustomButtons,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                navigationEndPaddingModifier
                    .constrainAs(customRightButtonsRef) {
                        end.linkTo(parent.end, spacing.large)
                        bottom.linkTo(bottomRightControls.top, spacing.medium)
                        width = Dimension.preferredWrapContent
                        height = Dimension.wrapContent
                    }
                    .onGloballyPositioned { coordinates ->
                        landscapeRightButtonsTopPx = coordinates.positionInParent().y.roundToInt()
                    }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = customButtonsRowVerticalPadding)
                    .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            ) {
                rightCustomButtons.forEach { button ->
                    key(button.id) {
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = buttonInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButton(button.id)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButtonLongPress(button.id)
                                }
                            )
                    ) {
                        Text(
                            text = button.label,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .basicMarquee(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPortraitCustomButtons,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                navigationHorizontalPaddingModifier
                    .constrainAs(customButtonsPortraitRef) {
                        start.linkTo(parent.start, spacing.large)
                        end.linkTo(parent.end, spacing.large)
                        bottom.linkTo(seekbar.top, spacing.small) // Reduced from medium
                        width = Dimension.fillToConstraints
                        height = Dimension.wrapContent
                    }
                    .onGloballyPositioned { coordinates ->
                        portraitButtonsTopPx = coordinates.positionInParent().y.roundToInt()
                    }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = customButtonsRowVerticalPadding)
                    .horizontalScroll(rememberScrollState())
            ) {
                customButtons.forEach { button ->
                    key(button.id) {
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = buttonInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButton(button.id)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButtonLongPress(button.id)
                                }
                            )
                    ) {
                        Text(
                            text = button.label,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .basicMarquee(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    }
                }
            }
        }

        AnimatedVisibility(
          visible = controlsShown && areControlsLocked,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier =
            Modifier
              .constrainAs(unlockControlsButton) {
                bottom.linkTo(parent.bottom, spacing.extraLarge)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
              },
        ) {
          SlideToUnlock(
            onUnlock = { viewModel.unlockControls() },
            onDraggingChanged = { isDragging -> isUnlockSliderDragging = isDragging },
          )
        }

        AnimatedVisibility(
          visible = !areControlsLocked && currentSkippableSegment != null,
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier =
            navigationEndPaddingModifier
              .constrainAs(skipSegmentChip) {
                end.linkTo(parent.end, spacing.large)
                bottom.linkTo(parent.bottom, skipChipBottomOffset)
              },
        ) {
          val segment = currentSkippableSegment ?: return@AnimatedVisibility
          val segmentColor = segment.type.accentColor
          val segmentSurfaceColor =
            Color(
              red = segmentColor.red * 0.30f,
              green = segmentColor.green * 0.30f,
              blue = segmentColor.blue * 0.30f,
              alpha = 0.88f,
            )
          val segmentBorderColor =
            Color(
              red = segmentColor.red * 0.72f,
              green = segmentColor.green * 0.72f,
              blue = segmentColor.blue * 0.72f,
              alpha = 0.96f,
            )
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = segmentSurfaceColor,
            border = BorderStroke(1.5.dp, segmentBorderColor),
            modifier =
              Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable {
                  resetControlsTimestamp = System.currentTimeMillis()
                  viewModel.skipActiveSegment()
                },
          ) {
            Text(
              text = segment.label,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = segmentColor.copy(alpha = 1f),
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier =
            Modifier.constrainAs(playerPauseButton) {
              end.linkTo(parent.absoluteRight)
              start.linkTo(parent.absoluteLeft)
              if (isPortrait) {
                bottom.linkTo(bottomRightControls.top, spacing.medium) // Reduced from large
              } else {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
              }
            },
        ) {
          val showLoadingCircle by playerPreferences.showLoadingCircle.collectAsState()
          val interaction = remember { MutableInteractionSource() }

          when {
            pausedForCache == true && showLoadingCircle -> {
              LoadingIndicator(
                modifier = Modifier.size(96.dp),
              )
            }

            else -> {
              val buttonShadow =
                Brush.radialGradient(
                  0.0f to Color.Black.copy(alpha = 0.3f),
                  0.7f to Color.Transparent,
                  1.0f to Color.Transparent,
                )

              if (playlistMode && viewModel.hasPlaylistSupport()) {
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
                          onClick = {
                            resetControlsTimestamp = System.currentTimeMillis()
                            if (viewModel.hasPrevious()) viewModel.playPrevious()
                          },
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
                    tonalElevation = 0.dp,
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
                        .clickable(interaction, ripple(), onClick = {
                          resetControlsTimestamp = System.currentTimeMillis()
                          viewModel.pauseUnpause()
                        })
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
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border =
                      if (!hideBackground) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                      } else {
                        null
                      },
                  ) {
                    AnimatedPlayPauseIcon(
                      isPlaying = paused == false,
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.medium),
                      tint = LocalContentColor.current,
                    )
                  }

                  Surface(
                    modifier =
                      Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(
                          enabled = viewModel.hasNext(),
                          onClick = {
                            resetControlsTimestamp = System.currentTimeMillis()
                            if (viewModel.hasNext()) viewModel.playNext()
                          },
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
                    tonalElevation = 0.dp,
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
                      .clickable(interaction, ripple(), onClick = {
                        resetControlsTimestamp = System.currentTimeMillis()
                        viewModel.pauseUnpause()
                      })
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
                  tonalElevation = 0.dp,
                  shadowElevation = 0.dp,
                  border =
                    if (!hideBackground) {
                      BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    } else {
                      null
                    },
                ) {
                  AnimatedPlayPauseIcon(
                    isPlaying = paused == false,
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(MaterialTheme.spacing.medium),
                    tint = LocalContentColor.current,
                  )
                }
              }
            }
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter = buildControlsEnterV(controlsAnimStyle, reduceMotion, enterMs) { it },
          exit  = buildControlsExitV(controlsAnimStyle, reduceMotion, exitMs) { it },
          modifier =
            Modifier
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(seekbar) {
                if (isPortrait) {
                  bottom.linkTo(playerPauseButton.top, spacing.medium)
                } else {
                  bottom.linkTo(parent.bottom, spacing.medium)
                }
                start.linkTo(parent.start, spacing.large)
                end.linkTo(parent.end, spacing.large)
              },
        ) {
          val invertDuration by playerPreferences.invertDuration.collectAsState()
          val seekbarStyle by appearancePreferences.seekbarStyle.collectAsState()

          SeekbarWithTimers(
            position = precisePosition,
            duration = if (preciseDuration > 0) preciseDuration else duration?.toFloat() ?: 0f,
            onValueChange = {
              isSeeking = true
              resetControlsTimestamp = System.currentTimeMillis()
              viewModel.seekTo(it.toInt())
            },
            onValueChangeFinished = {
              isSeeking = false
              resetControlsTimestamp = System.currentTimeMillis()
              viewModel.showControls()
            },
            timersInverted = Pair(false, invertDuration),
            durationTimerOnCLick = {
              resetControlsTimestamp = System.currentTimeMillis()
              playerPreferences.invertDuration.set(!invertDuration)
            },
            positionTimerOnClick = {},
            chapters = chapters.toImmutableList(),
            skipSegments = skipSegments.toImmutableList(),
            paused = paused ?: false,
            seekbarStyle = seekbarStyle,
            loopStart = abLoopA?.toFloat(),
            loopEnd = abLoopB?.toFloat(),
            isPortrait = isPortrait,
          )
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter = buildControlsEnterH(controlsAnimStyle, reduceMotion, enterMs) { -it },
          exit  = buildControlsExitH(controlsAnimStyle, reduceMotion, exitMs) { -it },
          modifier =
            Modifier
              .then(
                if (showSystemStatusBar) {
                  Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                  Modifier
                }
              )
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(topLeftControls) {
                top.linkTo(parent.top, if (isPortrait) spacing.extraLarge else spacing.small)
                start.linkTo(parent.start, spacing.large)
                if (isPortrait) {
                  width = Dimension.fillToConstraints
                  end.linkTo(parent.end, spacing.large)
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
              onOpenSheet = onOpenSheet,
              viewModel = viewModel,
              isTranslatingSub = isTranslatingSub,
              isRealtimeSubsActive = isRealtimeSubsActive,
              realtimeSubsLanguage = realtimeSubsLanguage,
              translationStatus = translationStatus,
              translatingTrackName = translatingTrackName,
            )
          } else {
            TopLeftPlayerControlsLandscape(
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              viewModel = viewModel,
              isTranslatingSub = isTranslatingSub,
              isRealtimeSubsActive = isRealtimeSubsActive,
              realtimeSubsLanguage = realtimeSubsLanguage,
              translationStatus = translationStatus,
              translatingTrackName = translatingTrackName,
            )
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked && !isPortrait,
          enter = buildControlsEnterH(controlsAnimStyle, reduceMotion, enterMs) { it },
          exit  = buildControlsExitH(controlsAnimStyle, reduceMotion, exitMs) { it },
          modifier =
            Modifier
              .then(
                if (showSystemStatusBar) {
                  Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                  Modifier
                }
              )
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(topRightControls) {
                top.linkTo(parent.top, spacing.small)
                end.linkTo(parent.end, spacing.large)
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
          visible = controlsShown && !areControlsLocked && !areSlidersShown,
          enter = buildControlsEnterH(controlsAnimStyle, reduceMotion, enterMs) { it },
          exit  = buildControlsExitH(controlsAnimStyle, reduceMotion, exitMs) { it },
          modifier =
            Modifier
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(bottomRightControls) {
                if (isPortrait) {
                  bottom.linkTo(parent.bottom, spacing.large) // Reduced from extraLarge
                  start.linkTo(parent.start, spacing.large)
                  end.linkTo(parent.end, spacing.large)
                  width = Dimension.fillToConstraints
                } else {
                  bottom.linkTo(seekbar.top, spacing.medium)
                  end.linkTo(parent.end, spacing.large)
                }
              }
              .onGloballyPositioned { coordinates ->
                bottomRightControlsTopPx = coordinates.positionInParent().y.roundToInt()
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
          visible = controlsShown && !areControlsLocked && !isPortrait && !areSlidersShown,
          enter = buildControlsEnterH(controlsAnimStyle, reduceMotion, enterMs) { -it },
          exit  = buildControlsExitH(controlsAnimStyle, reduceMotion, exitMs) { -it },
          modifier =
            Modifier
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(bottomLeftControls) {
                bottom.linkTo(seekbar.top, spacing.medium)
                start.linkTo(parent.start, spacing.large)
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
      onToggleSubtitle = viewModel::toggleSubtitle,
      isSubtitleSelected = viewModel::isSubtitleSelected,
      onRemoveSubtitle = viewModel::removeSubtitle,
      audioTracks = audioTracks.toImmutableList(),
      onAddAudio = viewModel::addAudio,
      onSelectAudio = {
        if (getTrackSelectionId("aid") == it.id) {
          setTrackSelectionId("aid", null)
        } else {
          setTrackSelectionId("aid", it.id)
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
      viewModel = viewModel,
      onDismissRequest = { onOpenPanel(Panels.None) },
    )
  }
}

private data class CustomStatsSnapshot(
  val fileName: String,
  val renderContext: String,
  val cache: String,
  val fps: String,
  val droppedFrames: String,
  val video: String,
  val audio: String,
  val cpuPercent: Float,
  val gpuEstimatePercent: Float,
  val networkText: String,
  val networkMbps: Float,
  val networkHistory: List<Float>,
  val batteryPercentText: String,
  val batteryRateText: String,
)

@Composable
private fun CustomStatsPageSixOverlay(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current.applicationContext
  val stats by produceState(
    initialValue =
      CustomStatsSnapshot(
        fileName = "--",
        renderContext = "--",
        cache = "--",
        fps = "--",
        droppedFrames = "--",
        video = "--",
        audio = "--",
        cpuPercent = 0f,
        gpuEstimatePercent = 0f,
        networkText = "0 KB/s",
        networkMbps = 0f,
        networkHistory = emptyList(),
        batteryPercentText = "--%",
        batteryRateText = "Unknown",
      ),
  ) {
    val history = ArrayDeque<Float>()
    var lastCpuMs   = runCatching { android.os.Process.getElapsedCpuTime() }.getOrDefault(0L)
    var lastTimeMs  = android.os.SystemClock.elapsedRealtime()
    // Track PREVIOUS cumulative counts so we can compute per-second DELTA rates.
    // Using raw cumulative totals in the bar gave ever-growing values that drifted
    // to 100% over time and never reflected the current rendering state.
    var lastDropped = 0
    var lastDelayed = 0

    while (true) {
      val fileName      = runCatching { MPVLib.getPropertyString("media-title") ?: "--" }.getOrDefault("--")
      val renderContext = runCatching { MPVLib.getPropertyString("current-vo")  ?: "--" }.getOrDefault("--")
      val cache         = runCatching { MPVLib.getPropertyString("demuxer-cache-duration") ?: "--" }.getOrDefault("--")
      val fps           = runCatching { MPVLib.getPropertyDouble("estimated-vf-fps")?.let { String.format("%.3f", it) } ?: "--" }.getOrDefault("--")
      val dropped       = runCatching { MPVLib.getPropertyInt("drop-frame-count")       ?: 0 }.getOrDefault(0)
      val delayed       = runCatching { MPVLib.getPropertyInt("vo-delayed-frame-count") ?: 0 }.getOrDefault(0)
      val videoCodec    = runCatching { MPVLib.getPropertyString("video-codec")      ?: "--" }.getOrDefault("--")
      val audioCodec    = runCatching { MPVLib.getPropertyString("audio-codec-name") ?: "--" }.getOrDefault("--")

      // ── App CPU % ──────────────────────────────────────────────────────────
      // getElapsedCpuTime() measures THIS PROCESS's CPU ms, not system-wide.
      // cpu = (processCpuMs consumed / wallClockMs elapsed) * 100
      // i.e. "what fraction of one CPU core did mpvRx use last second"
      val currentCpuMs  = runCatching { android.os.Process.getElapsedCpuTime() }.getOrDefault(lastCpuMs)
      val currentTimeMs = android.os.SystemClock.elapsedRealtime()
      val cpuDelta      = (currentCpuMs - lastCpuMs).coerceAtLeast(0L)
      val timeDelta     = (currentTimeMs - lastTimeMs).coerceAtLeast(1L)
      val cpu           = ((cpuDelta.toFloat() / timeDelta.toFloat()) * 100f).coerceIn(0f, 100f)

      // ── GPU pressure estimate (delta-based, per-second) ────────────────────
      // The old formula used CUMULATIVE drop+delay totals which drift to 100%
      // over a long session, and added a fixed FPS-proportional baseline that
      // made a 120fps video with ZERO drops show 70% GPU load — meaningless.
      //
      // New approach: measure how many frames were dropped/delayed THIS SECOND
      // relative to the expected frame rate.  0 drops → 0% pressure.  All
      // frames dropped → 100% pressure.  A small non-zero floor (5%) signals
      // that the GPU is actively rendering.
      val estFps         = runCatching { MPVLib.getPropertyDouble("estimated-vf-fps") ?: 0.0 }.getOrDefault(0.0).toFloat()
      val droppedDelta   = (dropped - lastDropped).coerceAtLeast(0)
      val delayedDelta   = (delayed - lastDelayed).coerceAtLeast(0)
      val framePressure  = if (estFps > 0f) {
        ((droppedDelta + delayedDelta).toFloat() / estFps).coerceIn(0f, 1f)
      } else 0f
      // 5% baseline shows the GPU is working; scales to 100% when all frames drop.
      val gpuEstimate    = (framePressure * 95f + if (estFps > 0f) 5f else 0f).coerceIn(0f, 100f)

      val netBps = readNetworkBytesPerSecondForOverlay()
      val netText =
        when {
          netBps >= 1024 * 1024 -> String.format("%.1f MB/s", netBps / (1024 * 1024))
          netBps >= 1024        -> String.format("%.0f KB/s", netBps / 1024)
          else                  -> "${netBps.toInt()} B/s"
        }
      val netMbps = ((netBps * 8.0) / (1024.0 * 1024.0)).toFloat().coerceAtLeast(0f)
      val battery = readBatterySnapshot(context)
      history.addLast(netMbps)
      if (history.size > 42) history.removeFirst()

      value = CustomStatsSnapshot(
        fileName          = fileName,
        renderContext     = renderContext,
        cache             = cache,
        fps               = fps,
        droppedFrames     = "$dropped (decoder)  $delayed (output)  +$droppedDelta/+$delayedDelta this sec",
        video             = videoCodec,
        audio             = audioCodec,
        cpuPercent        = cpu,
        gpuEstimatePercent= gpuEstimate,
        networkText       = netText,
        networkMbps       = netMbps,
        networkHistory    = history.toList(),
        batteryPercentText= battery.percentageText,
        batteryRateText   = battery.rateText,
      )

      // Advance delta baselines
      lastCpuMs   = currentCpuMs
      lastTimeMs  = currentTimeMs
      lastDropped = dropped
      lastDelayed = delayed

      // ── Pause-aware backoff ────────────────────────────────────────────────
      // When playback is paused most metrics are static (FPS=0, no new drops,
      // network idle for local files).  Polling every 2 s instead of 1 s halves
      // the wasted JNI overhead without affecting the UX noticeably.
      val isPaused = runCatching { MPVLib.getPropertyBoolean("pause") }.getOrDefault(false)
      delay(if (isPaused == true) 2000L else 1000L)
    }
  }

  Column(
    modifier =
      modifier
        .widthIn(max = 520.dp)
        .alpha(0.84f),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    val textStyle =
      MaterialTheme.typography.bodyMedium.copy(
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        shadow = Shadow(color = Color.Black, blurRadius = 4f),
      )
    Text("File: ${stats.fileName}", style = textStyle)
    Text("Context: ${stats.renderContext}", style = textStyle)
    Text("Total Cache: ${stats.cache}", style = textStyle)
    Text("Refresh Rate: ${stats.fps} Hz (estimated)", style = textStyle)
    Text("Dropped Frames: ${stats.droppedFrames}", style = textStyle)
    Text("Video: ${stats.video}", style = textStyle)
    Text("Audio: ${stats.audio}", style = textStyle)
    Text("Network Throughput: ${stats.networkText}", style = textStyle)
    Text("Download: ${String.format("%.1f", stats.networkMbps)} Mbps", style = textStyle)
    Text("Battery: ${stats.batteryPercentText}", style = textStyle)
    Text("Battery Rate: ${stats.batteryRateText}", style = textStyle)
    NetworkSparkline(
      points = stats.networkHistory,
      modifier =
        Modifier
          .fillMaxWidth()
          .height(60.dp)
          .padding(top = 2.dp, bottom = 4.dp),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text("Page 6 • Live Performance", style = textStyle.copy(fontWeight = FontWeight.Bold))
    LinearProgressIndicator(progress = { stats.cpuPercent / 100f }, modifier = Modifier.fillMaxWidth())
    // cpu is process-only (getElapsedCpuTime), not system-wide — label accordingly
    Text("App CPU (this process) ${stats.cpuPercent.toInt()}%", style = textStyle)
    LinearProgressIndicator(progress = { stats.gpuEstimatePercent / 100f }, modifier = Modifier.fillMaxWidth())
    // gpuEstimate is based on per-second frame-drop pressure, not a hardware GPU counter
    Text("Frame Pressure (drop-based est.) ${stats.gpuEstimatePercent.toInt()}%", style = textStyle)
  }
}

@Composable
private fun NetworkSparkline(
  points: List<Float>,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    if (points.size < 2) return@Canvas

    val maxY = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val stepX = size.width / (points.size - 1).coerceAtLeast(1)

    val linePath = Path()
    val fillPath = Path()

    points.forEachIndexed { index, value ->
      val x = index * stepX
      val normalized = (value / maxY).coerceIn(0f, 1f)
      val y = size.height - (normalized * size.height)
      if (index == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
    }

    fillPath.lineTo(size.width, size.height)
    fillPath.close()

    drawPath(
      path = fillPath,
      brush =
        Brush.verticalGradient(
          colors = listOf(Color(0x66FF9800), Color(0x12FF9800)),
        ),
    )
    drawPath(
      path = linePath,
      color = Color(0xFFFFB74D),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f),
    )
  }
}

private fun readNetworkBytesPerSecondForOverlay(): Double {
  val directBytesPerSecond = listOf("demuxer-cache-speed", "cache-speed", "demuxer-speed")
    .asSequence()
    .mapNotNull { name -> runCatching { MPVLib.getPropertyDouble(name) }.getOrNull() }
    .firstOrNull { it > 0.0 }

  if (directBytesPerSecond != null) return directBytesPerSecond

  val bitratesBitsPerSecond = listOf("packet-video-bitrate", "video-bitrate", "audio-bitrate")
    .asSequence()
    .mapNotNull { name -> runCatching { MPVLib.getPropertyDouble(name) }.getOrNull() }
    .filter { it > 0.0 }
    .sum()

  return if (bitratesBitsPerSecond > 0.0) bitratesBitsPerSecond / 8.0 else 0.0
}
