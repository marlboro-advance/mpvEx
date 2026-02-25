package app.marlboroadvance.mpvex.ui.components

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

// ─── MPV Conf Autocomplete ─────────────────────────────────────────────────────

/**
 * Provides auto-completion items for MPV configuration files (mpv.conf, input.conf).
 *
 * All major MPV options are included, grouped by category: general, video, audio,
 * subtitles, OSD, cache, HDR/tone-mapping, Vulkan, screenshots, window, demuxer.
 *
 * @see <a href="https://mpv.io/manual/master/">MPV Manual</a>
 */
object MpvConfCompletions {

    data class ConfOption(val key: String, val description: String, val defaultValue: String = "")

    // ── General ──
    private val generalOptions = listOf(
        ConfOption("profile", "Use a configuration profile", ""),
        ConfOption("profile-desc", "Description for a profile", ""),
        ConfOption("profile-cond", "Conditional profile activation (Lua expression)", ""),
        ConfOption("profile-restore", "Restore options on profile deactivation", "default"),
        ConfOption("keep-open", "Keep player open after playback ends", "no"),
        ConfOption("keep-open-pause", "Pause when keep-open is active", "yes"),
        ConfOption("save-position-on-quit", "Save playback position on quit", "no"),
        ConfOption("watch-later-options", "Options to save in watch-later data", ""),
        ConfOption("fullscreen", "Start in fullscreen mode", "no"),
        ConfOption("fs", "Start in fullscreen mode (alias)", "no"),
        ConfOption("loop-file", "Loop the current file", "no"),
        ConfOption("loop-playlist", "Loop the playlist", "no"),
        ConfOption("loop", "Loop playback", "no"),
        ConfOption("shuffle", "Shuffle playlist", "no"),
        ConfOption("idle", "Stay open when no file is playing", "no"),
        ConfOption("input-default-bindings", "Enable default key bindings", "yes"),
        ConfOption("input-vo-keyboard", "Enable keyboard input on video window", "yes"),
        ConfOption("log-file", "Path to log file", ""),
        ConfOption("msg-level", "Set message level for modules", ""),
        ConfOption("term-osd-bar", "Show OSD bar in terminal", "no"),
        ConfOption("priority", "Process priority (Windows)", "normal"),
        ConfOption("load-scripts", "Load scripts from scripts directory", "yes"),
        ConfOption("ytdl", "Use yt-dlp for URL resolution", "yes"),
        ConfOption("ytdl-format", "yt-dlp format selection", ""),
        ConfOption("ytdl-raw-options", "Additional yt-dlp options", ""),
        ConfOption("script-opts", "Script option key=value pairs", ""),
        ConfOption("reset-on-next-file", "Reset options on next file", ""),
    )

    // ── Video ──
    private val videoOptions = listOf(
        ConfOption("vo", "Video output driver", "gpu"),
        ConfOption("gpu-api", "GPU API backend", "auto"),
        ConfOption("gpu-context", "GPU context backend", "auto"),
        ConfOption("hwdec", "Hardware decoding mode", "no"),
        ConfOption("hwdec-codecs", "Codecs to use hardware decoding for", "h264,vc1,hevc,vp8,vp9,av1"),
        ConfOption("vf", "Video filter chain", ""),
        ConfOption("video-sync", "Video sync mode", "audio"),
        ConfOption("video-aspect-override", "Override video aspect ratio", ""),
        ConfOption("video-rotate", "Rotate video (degrees)", "0"),
        ConfOption("video-zoom", "Video zoom factor", "0"),
        ConfOption("video-pan-x", "Video pan X offset", "0"),
        ConfOption("video-pan-y", "Video pan Y offset", "0"),
        ConfOption("video-align-x", "Video alignment X", "0"),
        ConfOption("video-align-y", "Video alignment Y", "0"),
        ConfOption("video-unscaled", "Display video at original resolution", "no"),
        ConfOption("deinterlace", "Enable deinterlacing", "no"),
        ConfOption("interpolation", "Enable frame interpolation", "no"),
        ConfOption("tscale", "Temporal scaling filter", "oversample"),
        ConfOption("scale", "Upscaling filter", "lanczos"),
        ConfOption("dscale", "Downscaling filter", ""),
        ConfOption("cscale", "Chroma scaling filter", ""),
        ConfOption("scale-antiring", "Anti-ringing for upscaling", "0"),
        ConfOption("correct-downscaling", "Enable correct downscaling", "no"),
        ConfOption("sigmoid-upscaling", "Enable sigmoid upscaling", "no"),
        ConfOption("linear-downscaling", "Enable linear downscaling", "yes"),
        ConfOption("linear-upscaling", "Enable linear upscaling", "no"),
        ConfOption("dither-depth", "Dither depth", "auto"),
        ConfOption("vd-queue-enable", "Enable video decoder queue", "no"),
        ConfOption("vd-lavc-threads", "Video decoder threads", "0"),
    )

    // ── Audio ──
    private val audioOptions = listOf(
        ConfOption("ao", "Audio output driver", "auto"),
        ConfOption("audio-device", "Audio output device", "auto"),
        ConfOption("volume", "Startup volume", "100"),
        ConfOption("volume-max", "Maximum amplified volume", "130"),
        ConfOption("mute", "Mute audio on startup", "no"),
        ConfOption("audio-channels", "Audio channel layout", "auto-safe"),
        ConfOption("audio-normalize-downmix", "Normalize when downmixing", "no"),
        ConfOption("af", "Audio filter chain", ""),
        ConfOption("audio-spdif", "Passthrough codecs via S/PDIF", ""),
        ConfOption("audio-exclusive", "Exclusive audio output mode", "no"),
        ConfOption("audio-file-auto", "Auto-load external audio files", "no"),
        ConfOption("audio-pitch-correction", "Pitch correction on speed change", "yes"),
        ConfOption("gapless-audio", "Gapless audio playback", "weak"),
        ConfOption("audio-display", "Display cover art", "attachment"),
        ConfOption("ad-queue-enable", "Enable audio decoder queue", "no"),
        ConfOption("alang", "Preferred audio languages", ""),
    )

    // ── Subtitles ──
    private val subtitleOptions = listOf(
        ConfOption("sub-auto", "Auto-load subtitles", "exact"),
        ConfOption("sub-file-paths", "Subtitle file search paths", ""),
        ConfOption("sub-font", "Subtitle font name", ""),
        ConfOption("sub-font-size", "Subtitle font size", "55"),
        ConfOption("sub-color", "Subtitle font color", "#FFFFFFFF"),
        ConfOption("sub-border-color", "Subtitle border color", "#FF000000"),
        ConfOption("sub-border-size", "Subtitle border size", "3"),
        ConfOption("sub-shadow-color", "Subtitle shadow color", "#80000000"),
        ConfOption("sub-shadow-offset", "Subtitle shadow offset", "0"),
        ConfOption("sub-back-color", "Subtitle background color", ""),
        ConfOption("sub-bold", "Bold subtitles", "no"),
        ConfOption("sub-italic", "Italic subtitles", "no"),
        ConfOption("sub-blur", "Subtitle blur", "0"),
        ConfOption("sub-margin-x", "Subtitle horizontal margin", "25"),
        ConfOption("sub-margin-y", "Subtitle vertical margin", "22"),
        ConfOption("sub-pos", "Subtitle vertical position (%)", "100"),
        ConfOption("sub-spacing", "Subtitle letter spacing", "0"),
        ConfOption("sub-ass-override", "Override ASS subtitle styles", "yes"),
        ConfOption("sub-ass-force-margins", "Force subtitle margins", "no"),
        ConfOption("sub-ass-force-style", "Force ASS style overrides", ""),
        ConfOption("sub-fix-timing", "Fix subtitle timing", "yes"),
        ConfOption("sub-delay", "Subtitle delay (seconds)", "0"),
        ConfOption("sub-visibility", "Show subtitles", "yes"),
        ConfOption("secondary-sub-visibility", "Show secondary subtitles", "no"),
        ConfOption("slang", "Preferred subtitle languages", ""),
        ConfOption("sub-scale", "Subtitle scale factor", "1"),
        ConfOption("sub-ass-vsfilter-blur-compat", "VSFilter blur compatibility", "yes"),
        ConfOption("sub-ass-scale-with-window", "Scale ASS subs with window", "yes"),
        ConfOption("secondary-sid", "Secondary subtitle track ID", "no"),
        ConfOption("sub-forced-events-only", "Only show forced subtitle events", "no"),
    )

    // ── OSD ──
    private val osdOptions = listOf(
        ConfOption("osd-level", "OSD display level (0-3)", "1"),
        ConfOption("osd-font", "OSD font name", ""),
        ConfOption("osd-font-size", "OSD font size", "55"),
        ConfOption("osd-color", "OSD text color", "#FFFFFFFF"),
        ConfOption("osd-border-color", "OSD border color", "#FF000000"),
        ConfOption("osd-border-size", "OSD border width", "3"),
        ConfOption("osd-shadow-color", "OSD shadow color", ""),
        ConfOption("osd-shadow-offset", "OSD shadow offset", "0"),
        ConfOption("osd-back-color", "OSD background color", ""),
        ConfOption("osd-bold", "Bold OSD text", "yes"),
        ConfOption("osd-italic", "Italic OSD text", "no"),
        ConfOption("osd-bar", "Show OSD seek bar", "yes"),
        ConfOption("osd-duration", "OSD message duration (ms)", "1000"),
        ConfOption("osd-on-seek", "OSD display on seek", "bar"),
        ConfOption("osd-bar-align-y", "OSD bar vertical alignment", "0.5"),
        ConfOption("osd-bar-w", "OSD bar width (%)", "75"),
        ConfOption("osd-bar-h", "OSD bar height (%)", "3.125"),
        ConfOption("osd-border-style", "OSD border style", "background-box"),
        ConfOption("osd-margin-x", "OSD horizontal margin", "25"),
        ConfOption("osd-margin-y", "OSD vertical margin", "22"),
    )

    // ── Cache / Demuxer ──
    private val cacheOptions = listOf(
        ConfOption("cache", "Enable cache", "auto"),
        ConfOption("cache-secs", "Cache duration (seconds)", "10"),
        ConfOption("cache-on-disk", "Store cache on disk", "no"),
        ConfOption("cache-dir", "Cache directory path", ""),
        ConfOption("cache-pause", "Pause when cache is empty", "yes"),
        ConfOption("cache-pause-wait", "Wait time when cache is empty (s)", "1"),
        ConfOption("cache-pause-initial", "Pause initially to fill cache", "no"),
        ConfOption("demuxer-max-bytes", "Maximum demuxer cache bytes", "150MiB"),
        ConfOption("demuxer-max-back-bytes", "Maximum demuxer back-cache bytes", "50MiB"),
        ConfOption("demuxer-seekable-cache", "Enable seekable demuxer cache", "auto"),
        ConfOption("demuxer-readahead-secs", "Demuxer readahead duration (s)", "1"),
        ConfOption("demuxer-mkv-subtitle-preroll", "MKV subtitle preroll", "index"),
        ConfOption("demuxer-thread", "Enable threaded demuxing", "yes"),
    )

    // ── HDR / Tone Mapping ──
    private val hdrOptions = listOf(
        ConfOption("target-colorspace-hint", "Signal HDR to display", "no"),
        ConfOption("target-trc", "Target transfer characteristics", "auto"),
        ConfOption("target-prim", "Target color primaries", "auto"),
        ConfOption("target-peak", "Target peak brightness (nits)", "auto"),
        ConfOption("tone-mapping", "Tone mapping algorithm", "auto"),
        ConfOption("tone-mapping-mode", "Tone mapping mode", "auto"),
        ConfOption("inverse-tone-mapping", "Enable inverse tone mapping", "no"),
        ConfOption("hdr-compute-peak", "Compute HDR peak per-frame", "auto"),
        ConfOption("hdr-peak-percentile", "HDR peak percentile", "99.995"),
        ConfOption("hdr-peak-decay-rate", "HDR peak decay rate", "20"),
        ConfOption("hdr-scene-threshold-low", "HDR scene change threshold low", "1"),
        ConfOption("hdr-scene-threshold-high", "HDR scene change threshold high", "3"),
        ConfOption("gamut-mapping-mode", "Gamut mapping mode", "auto"),
    )

    // ── Vulkan ──
    private val vulkanOptions = listOf(
        ConfOption("vulkan-async-compute", "Enable async compute", "no"),
        ConfOption("vulkan-async-transfer", "Enable async transfer", "no"),
        ConfOption("vulkan-queue-count", "Number of Vulkan queues", "1"),
        ConfOption("vulkan-swap-mode", "Vulkan swap chain mode", "auto"),
        ConfOption("vulkan-device", "Vulkan device to use", ""),
    )

    // ── Screenshot ──
    private val screenshotOptions = listOf(
        ConfOption("screenshot-format", "Screenshot image format", "jpg"),
        ConfOption("screenshot-directory", "Screenshot save directory", ""),
        ConfOption("screenshot-template", "Screenshot filename template", "mpv-shot%n"),
        ConfOption("screenshot-tag-colorspace", "Tag screenshot colorspace", "no"),
        ConfOption("screenshot-jpeg-quality", "JPEG screenshot quality", "90"),
        ConfOption("screenshot-png-compression", "PNG screenshot compression", "7"),
        ConfOption("screenshot-webp-quality", "WebP screenshot quality", "75"),
        ConfOption("screenshot-webp-lossless", "Lossless WebP screenshots", "no"),
        ConfOption("screenshot-high-bit-depth", "High bit-depth screenshots", "yes"),
    )

    // ── Window / Display ──
    private val windowOptions = listOf(
        ConfOption("geometry", "Window geometry / position", ""),
        ConfOption("autofit", "Maximum window size", ""),
        ConfOption("autofit-larger", "Maximum window size (limit large)", ""),
        ConfOption("autofit-smaller", "Minimum window size (expand small)", ""),
        ConfOption("window-scale", "Window scale factor", "1"),
        ConfOption("window-minimized", "Start minimized", "no"),
        ConfOption("window-maximized", "Start maximized", "no"),
        ConfOption("force-window", "Create window even without video", "no"),
        ConfOption("ontop", "Set window always on top", "no"),
        ConfOption("border", "Show window border", "yes"),
        ConfOption("title", "Window title", "\${media-title}"),
        ConfOption("cursor-autohide", "Auto-hide cursor (ms)", "1000"),
        ConfOption("cursor-autohide-fs-only", "Auto-hide cursor in fullscreen only", "no"),
        ConfOption("snap-window", "Snap window to edges", "no"),
        ConfOption("hidpi-window-scale", "Scale window for HiDPI", "yes"),
        ConfOption("native-keyrepeat", "Use native key repeat", "no"),
    )

    // ── Input / Keybind ──
    private val inputOptions = listOf(
        ConfOption("input-conf", "Path to input.conf for key bindings", ""),
        ConfOption("no-input-default-bindings", "Disable default key bindings", ""),
        ConfOption("input-ar-delay", "Auto-repeat delay (ms)", "200"),
        ConfOption("input-ar-rate", "Auto-repeat rate (per second)", "40"),
        ConfOption("input-cursor", "Enable cursor input", "yes"),
        ConfOption("input-right-alt-gr", "Right Alt is AltGr", "no"),
    )

    val allOptions: List<ConfOption> by lazy {
        generalOptions + videoOptions + audioOptions + subtitleOptions +
        osdOptions + cacheOptions + hdrOptions + vulkanOptions +
        screenshotOptions + windowOptions + inputOptions
    }

    /**
     * Adds matching MPV conf completion items to the given [publisher].
     */
    fun addCompletions(
        prefix: String,
        publisher: CompletionPublisher,
    ) {
        val lower = prefix.lowercase()
        for (opt in allOptions) {
            if (opt.key.lowercase().contains(lower) || opt.description.lowercase().contains(lower)) {
                val label = if (opt.defaultValue.isNotEmpty()) "${opt.key}=${opt.defaultValue}" else opt.key
                publisher.addItem(
                    SimpleCompletionItem(
                        opt.key, /* label */
                        opt.description, /* desc */
                        0, /* prefixLen – replaced by publisher prefix */
                        label, /* commit text */
                    ).kind(CompletionItemKind.Property)
                )
            }
        }
    }
}

// ─── MPV Lua API Autocomplete ──────────────────────────────────────────────────

/**
 * Provides auto-completion items for MPV Lua scripting API.
 *
 * Covers: mp.*, mp.msg.*, mp.utils.*, mp.options.*
 *
 * @see <a href="https://mpv.io/manual/master/#lua-scripting">MPV Lua Scripting</a>
 */
object MpvLuaCompletions {

    data class LuaApi(val name: String, val signature: String, val description: String)

    private val mpCoreFunctions = listOf(
        // ── Commands / Properties ──
        LuaApi("mp.command", "mp.command(string)", "Run an mpv command string"),
        LuaApi("mp.commandv", "mp.commandv(arg1, arg2, ...)", "Run an mpv command with variadic args"),
        LuaApi("mp.command_native", "mp.command_native(table)", "Run an mpv command via native table"),
        LuaApi("mp.command_native_async", "mp.command_native_async(table, cb)", "Async mpv native command"),
        LuaApi("mp.abort_async_command", "mp.abort_async_command(id)", "Abort an async command"),
        LuaApi("mp.get_property", "mp.get_property(name [, def])", "Get property as string"),
        LuaApi("mp.get_property_osd", "mp.get_property_osd(name [, def])", "Get property formatted for OSD"),
        LuaApi("mp.get_property_bool", "mp.get_property_bool(name [, def])", "Get property as boolean"),
        LuaApi("mp.get_property_number", "mp.get_property_number(name [, def])", "Get property as number"),
        LuaApi("mp.get_property_native", "mp.get_property_native(name [, def])", "Get property as native Lua type"),
        LuaApi("mp.set_property", "mp.set_property(name, value)", "Set property from string"),
        LuaApi("mp.set_property_bool", "mp.set_property_bool(name, value)", "Set property from boolean"),
        LuaApi("mp.set_property_number", "mp.set_property_number(name, value)", "Set property from number"),
        LuaApi("mp.set_property_native", "mp.set_property_native(name, value)", "Set property from native value"),
        // ── Observe / Events ──
        LuaApi("mp.observe_property", "mp.observe_property(name, type, fn)", "Observe a property for changes"),
        LuaApi("mp.unobserve_property", "mp.unobserve_property(fn)", "Stop observing a property"),
        LuaApi("mp.register_event", "mp.register_event(name, fn)", "Register an event handler"),
        LuaApi("mp.unregister_event", "mp.unregister_event(fn)", "Unregister an event handler"),
        LuaApi("mp.register_idle", "mp.register_idle(fn)", "Register an idle callback"),
        LuaApi("mp.unregister_idle", "mp.unregister_idle(fn)", "Unregister an idle callback"),
        // ── Key Bindings ──
        LuaApi("mp.add_key_binding", "mp.add_key_binding(key, name, fn [, flags])", "Add a key binding"),
        LuaApi("mp.add_forced_key_binding", "mp.add_forced_key_binding(key, name, fn [, flags])", "Add forced key binding (overrides user)"),
        LuaApi("mp.remove_key_binding", "mp.remove_key_binding(name)", "Remove a key binding"),
        // ── OSD / Timers ──
        LuaApi("mp.osd_message", "mp.osd_message(text [, duration])", "Show OSD message"),
        LuaApi("mp.add_timeout", "mp.add_timeout(seconds, fn)", "One-shot timer"),
        LuaApi("mp.add_periodic_timer", "mp.add_periodic_timer(seconds, fn)", "Repeating timer"),
        // ── Script lifecycle ──
        LuaApi("mp.get_script_name", "mp.get_script_name()", "Get name of running script"),
        LuaApi("mp.get_script_directory", "mp.get_script_directory()", "Get directory of running script"),
        LuaApi("mp.get_time", "mp.get_time()", "Get monotonic time in seconds"),
        LuaApi("mp.enable_messages", "mp.enable_messages(level)", "Enable log message events at level"),
        LuaApi("mp.register_script_message", "mp.register_script_message(name, fn)", "Register handler for script messages"),
        LuaApi("mp.unregister_script_message", "mp.unregister_script_message(name)", "Unregister script message handler"),
        // ── Input ──
        LuaApi("mp.input_enable_section", "mp.input_enable_section(name [, flags])", "Enable input section"),
        LuaApi("mp.input_disable_section", "mp.input_disable_section(name)", "Disable input section"),
        LuaApi("mp.input_define_section", "mp.input_define_section(name, contents [, flags])", "Define/update input section"),
        // ── Misc ──
        LuaApi("mp.create_osd_overlay", "mp.create_osd_overlay(format)", "Create ASS or text OSD overlay"),
        LuaApi("mp.get_osd_size", "mp.get_osd_size()", "Get OSD dimensions {w, h, aspect}"),
    )

    private val mpMsgFunctions = listOf(
        LuaApi("mp.msg.fatal", "mp.msg.fatal(...)", "Log fatal message"),
        LuaApi("mp.msg.error", "mp.msg.error(...)", "Log error message"),
        LuaApi("mp.msg.warn", "mp.msg.warn(...)", "Log warning message"),
        LuaApi("mp.msg.info", "mp.msg.info(...)", "Log info message"),
        LuaApi("mp.msg.verbose", "mp.msg.verbose(...)", "Log verbose message"),
        LuaApi("mp.msg.debug", "mp.msg.debug(...)", "Log debug message"),
        LuaApi("mp.msg.trace", "mp.msg.trace(...)", "Log trace message"),
    )

    private val mpUtilsFunctions = listOf(
        LuaApi("mp.utils.getcwd", "mp.utils.getcwd()", "Get current working directory"),
        LuaApi("mp.utils.readdir", "mp.utils.readdir(path [, filter])", "List directory contents"),
        LuaApi("mp.utils.file_info", "mp.utils.file_info(path)", "Get file info (size, type, dates)"),
        LuaApi("mp.utils.split_path", "mp.utils.split_path(path)", "Split into directory and filename"),
        LuaApi("mp.utils.join_path", "mp.utils.join_path(p1, p2)", "Join two path components"),
        LuaApi("mp.utils.subprocess", "mp.utils.subprocess(t)", "Run subprocess synchronously"),
        LuaApi("mp.utils.subprocess_detached", "mp.utils.subprocess_detached(t)", "Run subprocess detached"),
        LuaApi("mp.utils.getpid", "mp.utils.getpid()", "Get process ID"),
        LuaApi("mp.utils.parse_json", "mp.utils.parse_json(str)", "Parse JSON string to Lua table"),
        LuaApi("mp.utils.format_json", "mp.utils.format_json(v)", "Encode Lua value as JSON string"),
        LuaApi("mp.utils.to_string", "mp.utils.to_string(v)", "Convert value to readable string"),
        LuaApi("mp.utils.get_user_path", "mp.utils.get_user_path(path)", "Expand ~/ paths"),
    )

    private val mpOptionsFunctions = listOf(
        LuaApi("mp.options.read_options", "mp.options.read_options(table [, id [, on_update]])", "Read script options from config"),
    )

    // ── Common Lua patterns for MPV scripting ──
    private val luaSnippets = listOf(
        LuaApi("require 'mp'", "require 'mp'", "Import MPV core module"),
        LuaApi("require 'mp.msg'", "require 'mp.msg'", "Import MPV logging module"),
        LuaApi("require 'mp.utils'", "require 'mp.utils'", "Import MPV utilities module"),
        LuaApi("require 'mp.options'", "require 'mp.options'", "Import MPV options module"),
        LuaApi("require 'mp.assdraw'", "require 'mp.assdraw'", "Import ASS drawing helpers"),
    )

    // ── Observable properties ──
    private val observableProperties = listOf(
        "path", "filename", "file-size", "stream-open-filename",
        "media-title", "duration", "time-pos", "time-remaining",
        "percent-pos", "playback-time", "chapter", "chapter-list",
        "playlist", "playlist-pos", "playlist-count",
        "pause", "idle-active", "core-idle", "seeking",
        "speed", "volume", "mute", "audio-delay",
        "sub-delay", "sub-visibility", "secondary-sub-visibility",
        "fullscreen", "window-minimized", "window-maximized",
        "ontop", "video-params", "video-out-params",
        "width", "height", "dwidth", "dheight",
        "osd-width", "osd-height", "track-list",
        "current-tracks", "hwdec-current", "estimated-vf-fps",
        "display-fps", "vsync-jitter", "video-bitrate",
        "audio-bitrate", "cache-speed", "demuxer-cache-duration",
        "demuxer-cache-state", "eof-reached",
    )

    val allApis: List<LuaApi> by lazy {
        mpCoreFunctions + mpMsgFunctions + mpUtilsFunctions + mpOptionsFunctions + luaSnippets
    }

    /**
     * Adds matching MPV Lua API completion items to the given [publisher].
     */
    fun addCompletions(
        prefix: String,
        publisher: CompletionPublisher,
    ) {
        val lower = prefix.lowercase()

        // MPV Lua API functions
        for (api in allApis) {
            if (api.name.lowercase().contains(lower) || api.signature.lowercase().contains(lower)) {
                val item = SimpleCompletionItem(api.name, api.description, prefix.length, api.name)
                item.kind(CompletionItemKind.Function)
                publisher.addItem(item)
            }
        }

        // Observable property names (when typing inside quotes after observe_property, etc.)
        if (lower.length >= 2) {
            for (prop in observableProperties) {
                if (prop.lowercase().contains(lower)) {
                    val item = SimpleCompletionItem(prop, "MPV observable property", prefix.length, prop)
                    item.kind(CompletionItemKind.Value)
                    publisher.addItem(item)
                }
            }
        }
    }
}

// ─── Language Wrapper ──────────────────────────────────────────────────────────

/**
 * Wraps [TextMateLanguage] via Kotlin interface-delegation and overlays
 * MPV-specific autocompletion on top of the default TextMate completions.
 *
 * All [Language] methods delegate to the underlying [base] language.
 * Only [requireAutoComplete] is overridden to inject MPV option / Lua API
 * suggestions alongside the identifier-based completions.
 */
class MpvLanguageWrapper(
    private val base: TextMateLanguage,
    private val fileExtension: String,
) : Language by base {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        // Let TextMate provide its default identifier completions first
        base.requireAutoComplete(content, position, publisher, extraArguments)

        // Extract the current word prefix from the line
        val line = content.getLine(position.line)
        val col = position.column
        val sb = StringBuilder()
        var i = col - 1
        while (i >= 0) {
            val ch = line[i]
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.') {
                sb.insert(0, ch)
                i--
            } else break
        }
        val prefix = sb.toString()

        if (prefix.isNotEmpty()) {
            if (fileExtension == "conf") {
                MpvConfCompletions.addCompletions(prefix, publisher)
            } else {
                MpvLuaCompletions.addCompletions(prefix, publisher)
            }
        }
    }
}
