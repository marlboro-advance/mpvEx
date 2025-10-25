# mpvEx

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="mpvEx Icon" width="150"/>
</p>

mpvEx is a powerful Android video player built on top of the renowned mpv-android library. It offers
a modern, intuitive interface with advanced features for managing and playing your video collection.

## Features

- **MPV-powered playback**: Leverages the robust mpv library for excellent format support and
  performance
- **Material 3 Design**: Modern, beautiful UI with dynamic theming
- **Advanced File Management**:
    - Enhanced copy/move operations using the FSAF library for better performance
    - Support for both regular storage and SAF (Storage Access Framework)
    - Real-time progress tracking during file operations
    - Batch file operations with detailed progress feedback
- **Video Library**: Organize videos by folders with thumbnail previews
- **Search Functionality**: Quickly find videos in your collection
- **Subtitle Support**: Easily manage and select subtitle tracks
- **Audio Track Selection**: Switch between multiple audio tracks
- **Playback Controls**: Speed adjustment, seeking, and more
- **Media Info**: View detailed information about your video files using MediaInfo integration

## Enhanced File Operations

This app uses
the [Fuck-Storage-Access-Framework (FSAF)](https://github.com/K1rakishou/Fuck-Storage-Access-Framework)
library for significantly improved file copy/move operations:

### Why FSAF?

The Android Storage Access Framework (SAF) can be extremely slow (20-30ms per operation) due to IPC
calls. FSAF provides:

- **Better Performance**: Optimized file operations that avoid SAF's performance pitfalls
- **Unified API**: Works with both regular File objects and SAF URIs seamlessly
- **Reliable Operations**: Proper handling of cross-volume moves and large directory structures
- **Progress Tracking**: Real-time updates during long-running operations

### Benefits

- Copy/move operations are significantly faster, especially with many files
- Better handling of nested directory structures
- Improved reliability for cross-volume operations
- Consistent behavior across different Android versions and devices

## Showcase
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/folderscreen.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/videoscreen.png" width="24%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/settings.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/pip.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/player.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/framenavigation.png" width="49%" />

## Installation
you can download the app from the [Github releases page](https://github.com/marlboro-advance/mpvEx/releases)

You can also access preview builds from [here](https://marlboro-advance.github.io/mpvEx/)

## Acknowledgments
- [mpv-android](https://github.com/mpv-android) for the base mpv library to use for this project.
- [mpvKt](https://github.com/abdallahmehiz/mpvKt) for the modified version of mpv-android.
