package app.marlboroadvance.mpvex.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a customizable button in the player controls.
 * Now includes an icon for the preference UI.
 */
enum class PlayerButton(
  val icon: ImageVector,
) {
  BACK_ARROW(Icons.AutoMirrored.Outlined.ArrowBack),
  VIDEO_TITLE(Icons.Outlined.Title),
  BOOKMARKS_CHAPTERS(Icons.Outlined.Bookmarks),
  PLAYBACK_SPEED(Icons.Outlined.Speed),
  DECODER(Icons.Outlined.Memory),
  SCREEN_ROTATION(Icons.Outlined.ScreenRotation),
  FRAME_NAVIGATION(Icons.Outlined.Camera),
  VIDEO_ZOOM(Icons.Outlined.ZoomIn),
  PICTURE_IN_PICTURE(Icons.Outlined.PictureInPictureAlt),
  ASPECT_RATIO(Icons.Outlined.AspectRatio),
  LOCK_CONTROLS(Icons.Outlined.LockOpen),
  AUDIO_TRACK(Icons.Outlined.Audiotrack),
  SUBTITLES(Icons.Outlined.Subtitles),
  MORE_OPTIONS(Icons.Outlined.MoreVert),
  CURRENT_CHAPTER(Icons.Outlined.Bookmarks), // <-- CHANGED ICON
  REPEAT_MODE(Icons.Outlined.Repeat),
  SHUFFLE(Icons.Outlined.Shuffle),
  MIRROR(Icons.Outlined.Flip),
  VERTICAL_FLIP(Icons.Outlined.Flip),
  AB_LOOP(Icons.Outlined.Repeat),
  CUSTOM_SKIP(Icons.Outlined.FastForward),
  BACKGROUND_PLAYBACK(Icons.Outlined.Headset),
  NONE(Icons.Outlined.Bookmarks),
}

/**
 * A list of all buttons that the user can choose from in the customization menu.
 * Excludes NONE (placeholder) and constant buttons (BACK_ARROW, VIDEO_TITLE).
 */
val allPlayerButtons =
  PlayerButton.values().filter {
    it != PlayerButton.NONE &&
      it != PlayerButton.BACK_ARROW &&
      it != PlayerButton.VIDEO_TITLE
  }

/**
 * Gets the human-readable label for a player button.
 * TODO: You must add these string resources to your `strings.xml` file.
 */
@Composable
fun getPlayerButtonLabel(button: PlayerButton): String =
  when (button) {
    PlayerButton.BACK_ARROW -> "Back Arrow" // stringResource(R.string.btn_label_back)
    PlayerButton.VIDEO_TITLE -> "Video Title" // stringResource(R.string.btn_label_title)
    PlayerButton.BOOKMARKS_CHAPTERS -> "Chapters / Bookmarks" // stringResource(R.string.btn_label_bookmarks)
    PlayerButton.PLAYBACK_SPEED -> "Playback Speed" // stringResource(R.string.btn_label_speed)
    PlayerButton.DECODER -> "Decoder" // stringResource(R.string.btn_label_decoder)
    PlayerButton.SCREEN_ROTATION -> "Screen Rotation" // stringResource(R.string.btn_label_rotation)
    PlayerButton.FRAME_NAVIGATION -> "Frame Navigation" // stringResource(R.string.btn_label_frame_nav)
    PlayerButton.VIDEO_ZOOM -> "Video Zoom" // stringResource(R.string.btn_label_zoom)
    PlayerButton.PICTURE_IN_PICTURE -> "Picture-in-Picture" // stringResource(R.string.btn_label_pip)
    PlayerButton.ASPECT_RATIO -> "Aspect Ratio" // stringResource(R.string.btn_label_aspect)
    PlayerButton.LOCK_CONTROLS -> "Lock Controls" // stringResource(R.string.btn_label_lock)
    PlayerButton.AUDIO_TRACK -> "Audio Track" // stringResource(R.string.btn_label_audio)
    PlayerButton.SUBTITLES -> "Subtitles" // stringResource(R.string.btn_label_subtitles)
    PlayerButton.MORE_OPTIONS -> "More Options" // stringResource(R.string.btn_label_more)
    PlayerButton.CURRENT_CHAPTER -> "Current Chapter" // stringResource(R.string.btn_label_chapter)
    PlayerButton.REPEAT_MODE -> "Repeat Mode" // stringResource(R.string.btn_label_repeat_mode)
    PlayerButton.SHUFFLE -> "Shuffle" // stringResource(R.string.btn_label_shuffle)
    PlayerButton.MIRROR -> "Horizontal Flip"
    PlayerButton.VERTICAL_FLIP -> "Vertical Flip"
    PlayerButton.AB_LOOP -> "A-B Loop"
    PlayerButton.CUSTOM_SKIP -> "Custom Skip"
    PlayerButton.BACKGROUND_PLAYBACK -> "Background Playback"
    PlayerButton.NONE -> "None"
  }
