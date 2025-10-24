package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mediaarea.mediainfo.lib.MediaInfo

object MediaInfoHelper {

  /**
   * Extract detailed media information from a video file
   */
  suspend fun getMediaInfo(context: Context, uri: Uri, fileName: String): Result<MediaInfoData> =
    withContext(Dispatchers.IO) {
      runCatching {
        val contentResolver = context.contentResolver
        val pfd = contentResolver.openFileDescriptor(uri, "r")
          ?: return@runCatching MediaInfoData.empty()

        val fd = pfd.detachFd()
        val mi = MediaInfo()

        try {
          mi.Open(fd, fileName)

          val generalInfo = extractGeneralInfo(mi)
          val videoStreams = extractVideoStreams(mi)
          val audioStreams = extractAudioStreams(mi)
          val textStreams = extractTextStreams(mi)

          MediaInfoData(
            general = generalInfo,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            textStreams = textStreams,
          )
        } finally {
          mi.Close()
          pfd.close()
        }
      }
    }

  private fun extractGeneralInfo(mi: MediaInfo): GeneralInfo {
    return GeneralInfo(
      format = mi.Get(MediaInfo.Stream.General, 0, "Format"),
      formatProfile = mi.Get(MediaInfo.Stream.General, 0, "Format_Profile"),
      codecId = mi.Get(MediaInfo.Stream.General, 0, "CodecID"),
      fileSize = mi.Get(MediaInfo.Stream.General, 0, "FileSize/String"),
      duration = mi.Get(MediaInfo.Stream.General, 0, "Duration/String"),
      overallBitRate = mi.Get(MediaInfo.Stream.General, 0, "OverallBitRate/String"),
      frameRate = mi.Get(MediaInfo.Stream.General, 0, "FrameRate"),
      encodedDate = mi.Get(MediaInfo.Stream.General, 0, "Encoded_Date"),
      writingApplication = mi.Get(MediaInfo.Stream.General, 0, "Writing_Application"),
    )
  }

  private fun extractVideoStreams(mi: MediaInfo): List<VideoStreamInfo> {
    val count = mi.Count_Get(MediaInfo.Stream.Video)
    return (0 until count).map { i ->
      VideoStreamInfo(
        streamIndex = i,
        format = mi.Get(MediaInfo.Stream.Video, i, "Format"),
        formatProfile = mi.Get(MediaInfo.Stream.Video, i, "Format_Profile"),
        codecId = mi.Get(MediaInfo.Stream.Video, i, "CodecID"),
        duration = mi.Get(MediaInfo.Stream.Video, i, "Duration/String"),
        bitRate = mi.Get(MediaInfo.Stream.Video, i, "BitRate/String"),
        width = mi.Get(MediaInfo.Stream.Video, i, "Width"),
        height = mi.Get(MediaInfo.Stream.Video, i, "Height"),
        displayAspectRatio = mi.Get(MediaInfo.Stream.Video, i, "DisplayAspectRatio/String"),
        frameRate = mi.Get(MediaInfo.Stream.Video, i, "FrameRate"),
        colorSpace = mi.Get(MediaInfo.Stream.Video, i, "ColorSpace"),
        chromaSubsampling = mi.Get(MediaInfo.Stream.Video, i, "ChromaSubsampling"),
        bitDepth = mi.Get(MediaInfo.Stream.Video, i, "BitDepth"),
        scanType = mi.Get(MediaInfo.Stream.Video, i, "ScanType"),
        encoderSettings = mi.Get(MediaInfo.Stream.Video, i, "Encoder_Settings"),
      )
    }
  }

  private fun extractAudioStreams(mi: MediaInfo): List<AudioStreamInfo> {
    val count = mi.Count_Get(MediaInfo.Stream.Audio)
    return (0 until count).map { i ->
      AudioStreamInfo(
        streamIndex = i,
        format = mi.Get(MediaInfo.Stream.Audio, i, "Format"),
        formatProfile = mi.Get(MediaInfo.Stream.Audio, i, "Format_Profile"),
        codecId = mi.Get(MediaInfo.Stream.Audio, i, "CodecID"),
        duration = mi.Get(MediaInfo.Stream.Audio, i, "Duration/String"),
        bitRate = mi.Get(MediaInfo.Stream.Audio, i, "BitRate/String"),
        channels = mi.Get(MediaInfo.Stream.Audio, i, "Channels"),
        channelLayout = mi.Get(MediaInfo.Stream.Audio, i, "ChannelLayout"),
        samplingRate = mi.Get(MediaInfo.Stream.Audio, i, "SamplingRate/String"),
        bitDepth = mi.Get(MediaInfo.Stream.Audio, i, "BitDepth"),
        language = mi.Get(MediaInfo.Stream.Audio, i, "Language/String"),
        title = mi.Get(MediaInfo.Stream.Audio, i, "Title"),
      )
    }
  }

  private fun extractTextStreams(mi: MediaInfo): List<TextStreamInfo> {
    val count = mi.Count_Get(MediaInfo.Stream.Text)
    return (0 until count).map { i ->
      TextStreamInfo(
        streamIndex = i,
        format = mi.Get(MediaInfo.Stream.Text, i, "Format"),
        codecId = mi.Get(MediaInfo.Stream.Text, i, "CodecID"),
        language = mi.Get(MediaInfo.Stream.Text, i, "Language/String"),
        title = mi.Get(MediaInfo.Stream.Text, i, "Title"),
      )
    }
  }
}

data class MediaInfoData(
  val general: GeneralInfo,
  val videoStreams: List<VideoStreamInfo>,
  val audioStreams: List<AudioStreamInfo>,
  val textStreams: List<TextStreamInfo>,
) {
  companion object {
    fun empty() = MediaInfoData(
      general = GeneralInfo(),
      videoStreams = emptyList(),
      audioStreams = emptyList(),
      textStreams = emptyList(),
    )
  }
}

data class GeneralInfo(
  val format: String = "",
  val formatProfile: String = "",
  val codecId: String = "",
  val fileSize: String = "",
  val duration: String = "",
  val overallBitRate: String = "",
  val frameRate: String = "",
  val encodedDate: String = "",
  val writingApplication: String = "",
)

data class VideoStreamInfo(
  val streamIndex: Int,
  val format: String = "",
  val formatProfile: String = "",
  val codecId: String = "",
  val duration: String = "",
  val bitRate: String = "",
  val width: String = "",
  val height: String = "",
  val displayAspectRatio: String = "",
  val frameRate: String = "",
  val colorSpace: String = "",
  val chromaSubsampling: String = "",
  val bitDepth: String = "",
  val scanType: String = "",
  val encoderSettings: String = "",
)

data class AudioStreamInfo(
  val streamIndex: Int,
  val format: String = "",
  val formatProfile: String = "",
  val codecId: String = "",
  val duration: String = "",
  val bitRate: String = "",
  val channels: String = "",
  val channelLayout: String = "",
  val samplingRate: String = "",
  val bitDepth: String = "",
  val language: String = "",
  val title: String = "",
)

data class TextStreamInfo(
  val streamIndex: Int,
  val format: String = "",
  val codecId: String = "",
  val language: String = "",
  val title: String = "",
)
