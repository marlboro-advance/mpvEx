package app.marlboroadvance.mpvex.ui.player.managers

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.ui.player.MPVView
import app.marlboroadvance.mpvex.ui.player.PlayerObserver
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.TAG
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages MPV library initialization, configuration, and asset copying.
 */
class MPVConfigurationManager(
  private val context: Context,
  private val player: MPVView,
  private val playerObserver: PlayerObserver,
  private val audioPreferences: AudioPreferences,
  private val subtitlesPreferences: SubtitlesPreferences,
  private val advancedPreferences: AdvancedPreferences,
  private val fileManager: FileManager,
  private val scope: CoroutineScope,
) {

  /**
   * Copies all MPV assets without initializing the player.
   * This can be called early before ViewModel creation.
   */
  fun copyAssetsOnly() {
    copyMPVAssets()
  }

  /**
   * Initializes the player and adds observer.
   * Should be called after copyAssetsOnly() or as part of initialize().
   */
  fun initializePlayerOnly(filesDir: String, cacheDir: String) {
    player.initialize(filesDir, cacheDir)
    MPVLib.addObserver(playerObserver)
  }

  /**
   * Initializes MPV and sets up all configurations.
   */
  fun initialize(filesDir: String, cacheDir: String) {
    copyMPVAssets()
    player.initialize(filesDir, cacheDir)
    MPVLib.addObserver(playerObserver)
  }

  /**
   * Sets up audio configuration.
   */
  fun setupAudio() {
    audioPreferences.audioChannels.get().let {
      MPVLib.setPropertyString(it.property, it.value)
    }
  }

  /**
   * Destroys MPV and cleans up resources.
   * Should be called when the player is exiting.
   */
  fun destroy() {
    runCatching {
      // Pause playback first
      MPVLib.setPropertyString("pause", "yes")
      Thread.sleep(PlayerConstants.PAUSE_DELAY_MS)

      // Quit MPV properly
      MPVLib.command("quit")
      Thread.sleep(PlayerConstants.QUIT_DELAY_MS)
    }.onFailure { e ->
      Log.e(TAG, "Error quitting MPV", e)
    }

    // Remove observer
    runCatching {
      MPVLib.removeObserver(playerObserver)
      Thread.sleep(PlayerConstants.OBSERVER_REMOVAL_DELAY_MS)
    }.onFailure { e ->
      Log.e(TAG, "Error removing MPV observer", e)
    }

    // Destroy MPV
    runCatching {
      MPVLib.destroy()
    }.onFailure { e ->
      Log.e(TAG, "Error destroying MPV (may be expected)", e)
    }
  }

  private fun copyMPVAssets() {
    Utils.copyAssets(context)
    copyMPVScripts()
    copyMPVConfigFiles()
    scope.launch(Dispatchers.IO) {
      copyMPVFonts()
    }
  }

  private fun copyMPVConfigFiles() {
    val applicationPath = context.filesDir.path
    runCatching {
      val mpvConfUri = advancedPreferences.mpvConfStorageUri.get().toUri()
      val mpvConf = fileManager.fromUri(mpvConfUri)
        ?: error("User hasn't set any mpvConfig directory")

      if (!fileManager.exists(mpvConf)) {
        error("Couldn't access mpv configuration directory")
      }

      fileManager.copyDirectoryWithContent(
        mpvConf,
        fileManager.fromPath(applicationPath),
        true,
      )
    }.onFailure { e ->
      Log.e(TAG, "Couldn't copy mpv configuration files: ${e.message}")
      createDefaultConfigFiles(applicationPath)
    }
  }

  private fun createDefaultConfigFiles(applicationPath: String) {
    runCatching {
      File("$applicationPath/mpv.conf").apply {
        if (!exists()) createNewFile()
      }.writeText(advancedPreferences.mpvConf.get())

      File("$applicationPath/input.conf").apply {
        if (!exists()) createNewFile()
      }.writeText(advancedPreferences.inputConf.get())
    }.onFailure { e ->
      Log.e(TAG, "Error creating default config files", e)
    }
  }

  private fun copyMPVScripts() {
    runCatching {
      val mpvexLua = context.assets.open("mpvex.lua")
      val applicationPath = context.filesDir.path
      val scriptsDir = fileManager.createDir(
        fileManager.fromPath(applicationPath),
        "scripts",
      ) ?: error("Failed to create scripts directory")

      fileManager.deleteContent(scriptsDir)

      File("$scriptsDir/mpvex.lua").apply {
        if (!exists()) createNewFile()
      }.writeText(mpvexLua.bufferedReader().readText())
    }.onFailure { e ->
      Log.e(TAG, "Error copying MPV scripts", e)
    }
  }

  private fun copyMPVFonts() {
    runCatching {
      val cachePath = context.cacheDir.path
      val fontsFolderUri = subtitlesPreferences.fontsFolder.get().toUri()
      val fontsDir = fileManager.fromUri(fontsFolderUri)
        ?: error("User hasn't set any fonts directory")

      if (!fileManager.exists(fontsDir)) {
        error("Couldn't access fonts directory")
      }

      val destDir = ensureFontsDirectory(cachePath)
      copyDefaultSubfont(cachePath, destDir)

      fileManager.copyDirectoryWithContent(fontsDir, destDir, false)
    }.onFailure { e ->
      Log.e(TAG, "Couldn't copy fonts to application directory: ${e.message}")
    }
  }

  private fun ensureFontsDirectory(cachePath: String): com.github.k1rakishou.fsaf.file.AbstractFile {
    val destDir = fileManager.fromPath("$cachePath/fonts")
    if (!fileManager.exists(destDir)) {
      fileManager.createDir(fileManager.fromPath(cachePath), "fonts")
    }
    return destDir
  }

  private fun copyDefaultSubfont(
    cachePath: String,
    destDir: com.github.k1rakishou.fsaf.file.AbstractFile,
  ) {
    if (fileManager.findFile(destDir, "subfont.ttf") == null) {
      context.resources.assets.open("subfont.ttf")
        .use { input ->
          File("$cachePath/fonts/subfont.ttf")
            .outputStream()
            .use { output ->
              input.copyTo(output)
            }
        }
    }
  }
}
