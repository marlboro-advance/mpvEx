package app.marlboroadvance.mpvex.ui.player

import dev.vivvvek.seeker.Segment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterNode(
  val time: Float,
  val title: String,
) {
  fun toSegment(): Segment = Segment(title, time)
}

@Serializable
data class TrackNode(
  val id: Int,
  val type: String,
  @SerialName("src-id") val srcId: Long? = null,
  val title: String? = null,
  val lang: String? = null,
  val image: Boolean? = null,
  @SerialName("albumArt") val albumArt: Boolean? = null,
  val default: Boolean? = null,
  val forced: Boolean? = null,
  val dependent: Boolean? = null,
  @SerialName("visual-impaired") val visualImpaired: Boolean? = null,
  @SerialName("hearing-impaired") val hearingImpaired: Boolean? = null,
  @SerialName("hls-bitrate") val hlsBitrate: Long? = null,
  @SerialName("program-id") val programId: Long? = null,
  val selected: Boolean? = null,
  @SerialName("main-selection") val mainSelection: Long? = null,
  val external: Boolean? = null,
  @SerialName("external-filename") val externalFilename: String? = null,
  val codec: String? = null,
  @SerialName("codec-desc") val codecDesc: String? = null,
  @SerialName("codec-profile") val codecProfile: String? = null,
  @SerialName("ff-index") val ffIndex: Long? = null,
  val decoder: String? = null,
  @SerialName("decoder-desc") val decoderDesc: String? = null,
  @SerialName("demux-w") val demuxW: Long? = null,
  @SerialName("demux-h") val demuxH: Long? = null,
  @SerialName("demux-crop-x") val demuxCropX: Long? = null,
  @SerialName("demux-crop-y") val demuxCropY: Long? = null,
  @SerialName("demux-crop-w") val demuxCropW: Long? = null,
  @SerialName("demux-crop-h") val demuxCropH: Long? = null,
  @SerialName("demux-channel-count") val demuxChannelCount: Long? = null,
  @SerialName("demux-channels") val demuxChannels: String? = null,
  @SerialName("demux-samplerate") val demuxSampleRate: Long? = null,
  @SerialName("demux-fps") val demuxFps: Double? = null,
  @SerialName("demux-bitrate") val demuxBitrate: Long? = null,
  @SerialName("demux-rotation") val demuxRotation: Long? = null,
  @SerialName("demux-par") val demuxPar: Double? = null,
  @SerialName("format-name") val formatName: String? = null,
  @SerialName("audio-channels") val audioChannels: Long? = null,
  @SerialName("replaygain-track-peak") val replayGainTrackPeak: Double? = null,
  @SerialName("replaygain-track-gain") val replayGainTrackGain: Double? = null,
  @SerialName("replaygain-album-peak") val replayGainAlbumPeak: Double? = null,
  @SerialName("replaygain-album-gain") val replayGainAlbumGain: Double? = null,
  @SerialName("dolby-vision-profile") val dolbyVisionProfile: Long? = null,
  @SerialName("dolby-vision-level") val dolbyVisionLevel: Long? = null,
  val metadata: Map<String, String?>? = null,
) {
  val isAudio = type == "audio"
  val isSubtitle = type == "sub"
  val isSelected = selected == true
}
