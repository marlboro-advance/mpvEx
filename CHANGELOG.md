# Changelog

These notes are written in plain English and focus on what changed for real use.

## 1.3.6

- **Six AI providers, one gorgeous settings page** — OpenAI, Anthropic, OpenRouter, and Together joined Groq and Gemini in a completely redesigned UI. Every provider gets its own API key, every single model is visible (free ones get a bold green badge), and the new searchable model picker sorts free models to the top. The offline model experience got a premium card-based overhaul too — tiers, speed/translation badges, device recommendations, DeepSeek-R1 support, reasoning toggles, and a benchmark button for downloaded models. One-tap download, delete, and switch between models without ever leaving the screen.

- **Subtitle translation** — SUPPORTS ASS Subs Translation tooooooooo..... , you can now configure your target languages once in settings. One language means one tap to translate. Two or more means a clean picker showing only the languages you chose. Translation progress appears right on the video screen (even with the sheet closed), partially translated subs survive restarts, and a red X lets you cancel mid-translation instantly. When using local models, the system automatically picks the best downloaded model for each language, keeps it warm between chunks, and never runs two local AI jobs at once.

- **Generate subtitles from video audio** — **_(EXPERIMENTAL)_** This is work in progress might not work Don't baby Cry that this shit aint working ,i ain't getting paid enough to implement this whole heartedly , so what it does is -> one tap generates subtitles using the audio you're already playing. Media3 extraction feeds Groq, Gemini, or offline Whisper, and the resulting SRT/VTT saves automatically.

- **Smarter AI across the board** — reasoning tags are automatically stripped from final results, token limits prevent stalls in heavy tasks, and every AI feature (rename, formatting, translation) comes with customizable prompts that fall back gracefully to built-in instructions.

## 1.3.5

- **Removed Play Store and F-Droid build variants** — streamlined to a single `standard` flavor with full update support and all features enabled.
- **Revamped README** — comprehensive feature documentation organized by category, UPI QR code and Buy Me a Coffee links in the Support section.
- **SMB Network Thumbnail Generation** — fixed thumbnail generation for SMB shares through Codex AI (Beta).
- **Bulk AI Rename** — rename multiple files at once using Gemini or Groq with concurrency limiting and edge case handling.
- **AI Subtitle Translation** — translate subtitles using AI providers with custom prompts, progress indication, and user preference management.
- **AI Subtitle Translation Enhancements** — in-house developed translation pipeline with fully customizable prompts and per-user preference overrides.

## 1.3.4

- Capped generated thumbnails to safer preview sizes so large videos do not waste memory while browsing.
- Improved MKV/WebM thumbnail handling, including embedded artwork and smarter fallback frames.
- Cleaned old thumbnail cache paths when clearing thumbnail cache.
- Fixed the About and crash info screen showing `UNKNOWN` in the bundled mpv version.
- Updated Gradle, Kotlin, Compose, Koin, Navigation 3, AndroidX, and related dependency versions through the version catalog.
- Added SUbHub MpvRx specific Subtitle Fetching nd Downloading featured developed by me
- Added Video COmpresser Overlay in Tree Mode also
- Cleaned up codebase and Improved Playback bottlenecks
- Added Window Offset to prevent Camera notch overlap issues


## 1.3.3

- Fixed Background Playback and Pip issues 
- Anime4K should feel much smoother now. The player now uses the clean six-preset Anime4K flow from the reference app and avoids piling old shader work on top of the new preset when you switch modes.
- Anime4K is still off by default, but when you turn it on the picker is simpler: Off, A, B, C, A+, B+, and C+.
- Moved the Fast / Balanced / High Anime4K choice into Decoder settings, with Balanced as the default.
- Removed frame interpolation because it added a lot of GPU load and did not add enough real value.
- Removed the old OneThird and Halfway thumbnail choices.
- Removed the unused old player screen path.
- Cleaned up the track sheets so audio, subtitle, chapter, decoder, and online subtitle lists no longer depend on the old generic sheet.
- Removed SubDL from subtitle search sources.
- Network streaming is now opt-in instead of being enabled on a fresh install.
- HDR and Ambient controls are no longer placed on the default player buttons, so heavy visual extras stay out of the way unless you add them yourself.
- Turning HDR on now starts with Linear HDR by default.
- The app now does less background media scanning and cache cleanup on startup, which should help large libraries open with less churn.
- Added new MpvLib File with Some Optimization and Removing Deprecated Andorid Versions
- Thumbnails are now Loaded Faster and more Precisly

## 1.3.2

### HDR — hdr-toys Pipeline

- Replaced the old 3-mode HDR system (Off / SDR with HDR / Normal HDR) with a proper shader-based pipeline powered by [hdr-toys](https://github.com/natural-harmonia-gropius/hdr-toys).
- Four HDR modes are now available: **BT.2100 PQ** (HDR10), **BT.2100 HLG**, **BT.2020**, and **Linear HDR** (mpv-native, no shaders).
- 77 GLSL shaders are bundled in the app and copied to the mpv config directory on first use — no manual setup required.
- The HDR panel no longer shows an "Off" option. Off is the default and is toggled by the HDR button; the panel only presents the four active modes.
- Selecting a mode while GPU Next + Vulkan is unavailable shows a clear error pill and falls back to Off safely.
- Added `boostSdrToHdr` preference (used by the Linear HDR path).
- `HdrToysManager` cleanly removes all hdr-toys shaders when switching to Off or when the pipeline is not ready, so no stale shaders leak between sessions.

### Thermal & Battery Improvements

- Added `ThermalMonitor` — samples `PowerManager.getThermalHeadroom()` (Android 11+) every 10 seconds during playback.
- Ambient shader sample budget is automatically capped based on thermal headroom: 8 samples (severe), 12 (moderate), 18 (mild), uncapped (cool).
- Anime4K is proactively downgraded to C/Fast when thermal headroom drops below 40%, before frame drops even start.
- Ambient shader recompilation is now skipped when all parameters are identical to the last compiled version — reduces unnecessary GPU stutter on orientation changes and no-op callbacks.
- Removed redundant dual position polling: the event-driven `time-pos` observer and the polling loop were both updating the same StateFlow, causing double seek-bar recompositions on every MPV event.
- Background playback position poll interval halved from 250 ms to 500 ms when controls are not visible, cutting idle JNI wake-ups by 50%.

### Stats Page 6 — Fixes

- **GPU estimate bar fixed**: was using cumulative drop + delay totals that drifted to 100% after long sessions and added a fixed FPS-proportional baseline (120fps with zero drops showed 70% GPU load). Now uses per-second delta counts relative to the current frame rate — 0 drops = 0%, all frames dropped = 100%.
- **CPU label corrected**: relabelled from "CPU Usage" to "App CPU (this process)" to accurately reflect that `getElapsedCpuTime()` measures only MpvRx's own process, not the whole device.
- **Frame drop text now shows per-second deltas** alongside the all-time totals, so you can tell current rendering pressure at a glance.
- **Pause-aware poll backoff**: the stats loop backs off from 1 s to 2 s intervals when playback is paused, cutting pointless JNI calls when metrics are static.

### Gesture & Action Overlay Toggles

- Added a new **"Gesture & Action Overlays"** section in Player Settings with seven independent on/off switches:
  - **Volume slider overlay** — vertical pill shown during volume swipe
  - **Brightness slider overlay** — vertical pill shown during brightness swipe
  - **Hold speed overlay** — speed badge and slider shown during long-press speed boost
  - **Aspect ratio feedback** — pill shown when cycling aspect ratio
  - **Zoom level feedback** — pill shown when pinching to zoom
  - **Repeat & shuffle feedback** — pill shown when toggling repeat or shuffle
  - **Action feedback pills** — brief text pills from custom buttons, ambient toggle, subtitle drag, and Lua/JS scripts
- All overlays default to **on**, so existing behaviour is unchanged until the user opts out.
- Disabling an overlay suppresses only the visual pill — the underlying gesture action (volume change, speed change, etc.) still happens normally.

## 1.3.1

- Update FFmpeg to n8.1 (latest stable)
- Update Android SDK to 36, build tools 36.0.0
- Update Kotlin to 2.1.21, Gradle to 8.11.1
- Update dependencies: unibreak 6.2, harfbuzz 11.5.0, fribidi 1.0.17, freetype 2.13.4, mbedtls 3.6.5
- Add mujs 1.3.5 support for JavaScript scripting inside mpv
- JavaScript (.js) scripts are now supported alongside Lua scripts, with "Scripts (Lua / JS)" kept to the main section titles.
- Script editor now uses the native Sora editor with TextMate syntax highlighting for Lua and JavaScript.
- Script editor includes a chip toggle to choose between `.lua` and `.js` file extensions when creating or editing scripts.
- Custom player buttons can now run either Lua or JavaScript, with language selection per button and import/export support.
- Long-pressing the HDR button now opens an HDR Output panel with Off, SDR with HDR, and Normal HDR modes.
- Media title resolution improved: MPV's resolved title is preferred for non-direct-media URLs and when the current filename looks like a generic route (e.g., `/watch`, `/stream`).
- Updated mpv library dependency from `mpv-android-lib-v0.0.1.aar` to `mpvlib.aar` and removed the old AAR.
- Added Multiple new provider to Wyzie subtitle sources.
- PiP and background playback now save the latest watched position instead of returning to the timestamp from before PiP started.
- Video lists refresh playback progress as soon as the saved position changes, so returning from the player shows the current progress.
- Folder thumbnails now begin rendering immediately when a folder opens, while still using cached thumbnail data first.

## 1.3.0

- The project now carries the `MpvRx` name across the app, docs, and release files.
- Tree View `NEW` labels now work properly and update as you watch.
- Single-child folders now flatten automatically so you reach files faster.
- Subtitle matching is smarter and better at finding subtitles that line up.
- Cached library data shows up first, then refreshes quietly in the background.
- Browser updates now react to changes instead of constantly polling.
- The player now remembers your chosen aspect ratio.
- Seeking feels steadier and cleanup after playback is smoother.
- Ambient mode and Lua scripting were reverted.
- The settings page was revamped.
- New tab and video animations were added.
- Icons were refreshed across the app.
- Network and playlist behavior was cleaned up.
- Folder pinning was added.
- A video size downgrade option was added in the video editing section.
- Page 6 was added to More Sheet for battery usage and extra system info.
- A new status icon row can show network speed, battery percentage, and time.

## 1.2.9

- Library scanning became faster and more dependable.
- Subtitle search got a noticeable improvement.
- Theme picking now jumps to the active theme more cleanly.
- Ambient mode got another round of polish and fixes.

## 1.2.8-hotfix

- A rough ambient mode change was rolled back to keep playback stable.
- The zoom sheet layout was cleaned up.
- Playback profiles became easier to manage.

## 1.2.8

- Background playback became more dependable.
- File rename and delete flows became safer and clearer.
- Custom buttons load more reliably.
- Play Store and F-Droid releases were cleaned up.
- The update and media tools were reorganized.

## 1.2.7

- The seekbar was cleaned up and accidental swipe behavior was reduced.
- F-Droid builds were added.
- Release packaging and signing became more reliable.

## 1.2.6

- Background playback and notifications became steadier.
- Filter presets and video quality controls were improved.
- External subtitle scaling and positioning were fixed.

## 1.2.5

- Video scaling and smooth motion options were added.
- Thumbnail generation became faster and more consistent.
- Browser spacing and player gestures were cleaned up.

## 1.2.4

- New videos now show a `NEW` label more reliably.
- Rotated videos and aspect handling were improved.
- Subtitle styling controls were expanded.
- Playlist order and storage permission handling were cleaned up.

## 1.2.3

- Network thumbnails became optional.
- Recently Played works better with network items.
- Thumbnail loading became faster.
- Browser navigation and floating actions became more consistent.

## 1.2.2

- Repeat and shuffle now stay the way you left them.
- Subtitle preferences now carry across playback more reliably.
- Hardware decoding falls back more safely on tricky devices.
- Player rotation and status bar behavior were improved.
- SMB playback became more dependable.

## 1.2.1

- Grid mode arrived for folders and videos.
- Scroll position is remembered when you come back.
- Thumbnail visibility can be toggled.
- A background playback edge case was fixed.

## 1.2.0

- The app got a major Material 3 refresh.
- Settings were reorganized into a cleaner card layout.
- Local M3U playlists were added.
- Recently Played got pull-to-refresh.
- Track and subtitle handling became smarter.

## 1.1.0

- Network browsing arrived for SMB, FTP, and WebDAV.
- File manager mode and breadcrumb navigation were added.
- Playlist mode became more useful.
- Recently Played learned how to handle playlists too.
- The project website and screenshots were refreshed.

## 1.0.0

- First public release.
- Media info viewing and sharing were added.
- F-Droid release work was prepared.
