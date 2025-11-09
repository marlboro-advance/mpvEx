# mpvExtended


---

mpvExtended is a front-end for the versatile media player mpv, built on the libmpv library. It aims
to combine the powerful features of mpv with an easier-to-use interface and additional
functionality.

- **Simpler and Easier to Use UI**: Designed to make navigation and playback smoother, especially
  for those who want a straightforward media player experience.
- **Advanced Configuration and Scripting**: Offers the full capabilities of mpv's scripting and
  configuration for users who want to customize their playback.
- **Enhanced Playback Features**: Frame-by-frame navigation, sleep timer, speed presets, and better
  playback history implementation.
- **Picture-in-Picture (PiP)**: Continue watching videos while using other apps.
- **Multi-Modal Controls**: Includes customizable gestures for controlling volume, brightness, and
  playback, along with keyboard input support.
- **High-Quality Rendering**: Hardware and software video decoding with advanced rendering settings.
- **Network Streaming**: Play network streams with the "Open URL" function.
- **File Management**: Provides basic file operations like copy, move, rename, and delete.

mpvExtended aims to enhance the mpv experience by making it more accessible while retaining its
flexibility and powerful playback capabilities.

---

## Installation

### Stable Release
Download the latest stable version from the [GitHub releases page](https://github.com/marlboro-advance/mpvEx/releases).

[![Download Release](https://img.shields.io/badge/Download-Release-blue?style=for-the-badge)](https://github.com/marlboro-advance/mpvEx/releases)

### Preview Builds
Try the latest preview builds:

[![Download Preview Builds](https://img.shields.io/badge/Download-Preview%20Builds-green?style=for-the-badge)](https://marlboro-advance.github.io/mpvEx/)

---

## Showcase
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/folderscreen.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/videoscreen.png" width="24%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/about.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/pip.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/player.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/framenavigation.png" width="49%" />

---

## Building

### Prerequisites

- JDK 17
- Android SDK with build tools 34.0.0+
- Git (for version information in builds)

### Build Variants

- **debug**: Development build with debug signing
- **preview**: Release-optimized build with debug signing and preview suffix
- **release**: Production build (requires release signing configuration)

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Preview build
./gradlew assemblePreview

# Release build
./gradlew assembleRelease
```

### APK Variants

The app generates multiple APK variants for different CPU architectures:

- **universal**: Works on all devices (larger size)
- **arm64-v8a**: Modern 64-bit ARM devices (recommended for most users)
- **armeabi-v7a**: Older 32-bit ARM devices
- **x86**: Intel/AMD 32-bit devices
- **x86_64**: Intel/AMD 64-bit devices

---

## Releases

### Creating a Release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`
2. Commit the changes
3. Create and push a tag:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```
4. GitHub Actions will automatically build, sign, and create a draft release

### Creating a Preview Release

1. Create and push a preview tag:
   ```bash
   git tag -a v1.0.0-preview.1 -m "Preview release"
   git push origin v1.0.0-preview.1
   ```
2. GitHub Actions will create a pre-release automatically

---

## Acknowledgments
- [mpv-android](https://github.com/mpv-android) for the base mpv library to use for this project.
- [mpvKt](https://github.com/abdallahmehiz/mpvKt) for the modified version of mpv-android.
