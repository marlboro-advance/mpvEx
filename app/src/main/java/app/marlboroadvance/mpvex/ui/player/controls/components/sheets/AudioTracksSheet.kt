package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AudioChannels
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.koinInject

import java.util.Locale

@Composable
fun AudioTracksSheet(
  tracks: ImmutableList<TrackNode>,
  onSelect: (TrackNode) -> Unit,
  onAddAudioTrack: () -> Unit,
  onOpenDelayPanel: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val audioPreferences = koinInject<AudioPreferences>()
  val audioChannels by audioPreferences.audioChannels.collectAsState()

  GenericTracksSheet(
    tracks,
    onDismissRequest = onDismissRequest,
    header = {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_audio),
        onAddAudioTrack,
        actions = {
          IconButton(onClick = onOpenDelayPanel) {
            Icon(Icons.Default.MoreTime, null)
          }
        },
      )
    },
    track = {
      AudioTrackRow(
        title = getTrackTitle(it),
        details = getAudioTrackDetails(it),
        isSelected = it.isSelected,
        onClick = { onSelect(it) },
      )
    },
    footer = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(MaterialTheme.spacing.medium)
      ) {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Text(
          text = stringResource(id = R.string.pref_audio_channels),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.smaller))
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
        ) {
          items(AudioChannels.entries) {
            FilterChip(
              selected = audioChannels == it,
              onClick = {
                audioPreferences.audioChannels.set(it)
                if (it == AudioChannels.ReverseStereo) {
                  MPVLib.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
                } else {
                  MPVLib.setPropertyString(AudioChannels.ReverseStereo.property, "")
                }
                MPVLib.setPropertyString(it.property, it.value)
              },
              label = { Text(text = stringResource(id = it.title)) },
              leadingIcon = null,
            )
          }
        }
      }
    },
    modifier = modifier,
  )
}

@Composable
fun AudioTrackRow(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  details: String? = null,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(
          horizontal = MaterialTheme.spacing.medium,
          vertical = if (details != null) MaterialTheme.spacing.smaller else MaterialTheme.spacing.extraSmall,
        ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    RadioButton(
      isSelected,
      onClick,
    )
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
        fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
      )
      if (!details.isNullOrBlank()) {
        Text(
          text = details,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * Build a concise summary of the audio track's codec, bitrate, channels, and sample rate.
 * Example: "AAC • 320 kbps • 5.1 ch • 48 kHz"
 */
fun getAudioTrackDetails(track: TrackNode): String? {
  val parts = listOfNotNull(
    formatAudioCodec(track),
    formatAudioBitrate(track),
    formatAudioChannels(track),
    formatAudioSampleRate(track),
  )
  return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
}

private fun formatAudioCodec(track: TrackNode): String? {
  val raw = track.codec?.lowercase(Locale.US)?.trim() ?: return null
  return when {
    raw.contains("aac") -> "AAC"
    raw.contains("opus") -> "Opus"
    raw.contains("eac3") || raw.contains("ec-3") -> "E-AC-3"
    raw.contains("ac3") -> "AC-3"
    raw.contains("dts-hd") -> "DTS-HD"
    raw.contains("dts") -> "DTS"
    raw.contains("truehd") -> "TrueHD"
    raw.contains("flac") -> "FLAC"
    raw.contains("vorbis") -> "Vorbis"
    raw.contains("mp3") -> "MP3"
    raw.contains("alac") -> "ALAC"
    raw.contains("pcm") -> "PCM"
    raw.contains("wma") -> "WMA"
    else -> raw.uppercase(Locale.US)
  }
}

private fun formatAudioBitrate(track: TrackNode): String? {
  // 1. Direct demux bitrate from mpv
  val bitrate = track.demuxBitrate?.takeIf { it > 0 }
    ?: track.hlsBitrate?.takeIf { it > 0 }
  if (bitrate != null) {
    return "${bitrate / 1000} kbps"
  }

  // 2. Check metadata tags commonly present in MKV / MP4
  val meta = track.metadata
  if (!meta.isNullOrEmpty()) {
    val bpsEntry = meta.entries.firstOrNull {
      it.key.startsWith("BPS", ignoreCase = true)
    }?.value?.toLongOrNull()
    if (bpsEntry != null && bpsEntry > 0) {
      return "${bpsEntry / 1000} kbps"
    }

    val bitrateEntry = meta.entries.firstOrNull {
      it.key.equals("bitrate", ignoreCase = true)
    }?.value
    if (!bitrateEntry.isNullOrBlank()) {
      val numeric = bitrateEntry.filter { it.isDigit() }.toLongOrNull()
      if (numeric != null && numeric > 0) {
        return if (numeric > 10_000) "${numeric / 1000} kbps" else "$numeric kbps"
      }
    }
  }

  return null
}

private fun formatAudioChannels(track: TrackNode): String? {
  val raw = track.demuxChannels?.lowercase(Locale.US)?.trim()
  if (!raw.isNullOrBlank()) {
    when {
      raw == "stereo" -> return "Stereo"
      raw == "mono" -> return "Mono"
      raw.startsWith("5.1") -> return "5.1 ch"
      raw.startsWith("7.1") -> return "7.1 ch"
      raw.startsWith("2.1") -> return "2.1 ch"
      raw.endsWith("ch") || raw.endsWith("channels") -> return raw
    }
  }

  val count = track.demuxChannelCount ?: track.audioChannels
  return when (count) {
    1L -> "Mono"
    2L -> "Stereo"
    6L -> "5.1 ch"
    8L -> "7.1 ch"
    null -> raw?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    else -> "$count ch"
  }
}

private fun formatAudioSampleRate(track: TrackNode): String? {
  val sr = track.demuxSampleRate?.takeIf { it > 0 } ?: return null
  return if (sr % 1000L == 0L) {
    "${sr / 1000} kHz"
  } else {
    String.format(Locale.US, "%.1f kHz", sr / 1000.0)
  }
}
