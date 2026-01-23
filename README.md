![banner](fastlane/metadata/android/en-US/images/featureGraphic.png)

# mpvExtended
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/marlboro-advance/mpvex.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/marlboro-advance/mpvex/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/marlboro-advance/mpvex/total?logo=github&cacheSeconds=3600)](https://github.com/marlboro-advance/mpvex/releases/latest)


**mpvExtended is a fork of [mpv-android](https://github.com/mpv-android/mpv-android), built on the libmpv library. It aims
to combine the powerful features of mpv with an easy to use interface and additional
features.**

- Simpler and Easier to Use UI
- Material3 Expressive Design
- Advanced Configuration and Scripting
- Enhanced Playback Features
- Picture-in-Picture (PiP)
- Background Playback
- High-Quality Rendering
- Network Streaming
- File Management
- Completely free and open source and without any ads or excessive permissions
- Media picker with tree and folder view modes
- External Subtitle support
- Zoom gesture
- External Audio support
- Search Functionality
- SMB/FTP/WebDAV support
- Custom Playlist management support

**This project is still in development and is expected to have bugs. Please report any bugs you find in
the [Issues](https://github.com/marlboro-advance/mpvEx/issues) section.**

---

## Installation

### Stable Release
Download the latest stable version from the [GitHub releases page](https://github.com/marlboro-advance/mpvEx/releases).

[![Download Release](https://img.shields.io/badge/Download-Release-blue?style=for-the-badge)](https://github.com/marlboro-advance/mpvEx/releases)

Or you can get the stable releases here

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="50" alt="Get it at IzzyOnDroid">](https://apt.izzysoft.de/packages/app.marlboroadvance.mpvex)

### Preview Builds
For testing purposes only

[![Download Preview Builds](https://img.shields.io/badge/Download-Preview%20Builds-red?style=for-the-badge)](https://marlboro-advance.github.io/mpvEx/)

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

### APK Variants

The app generates multiple APK variants for different CPU architectures:

- **universal**: Works on all devices (larger size)
- **arm64-v8a**: Modern 64-bit ARM devices (recommended for most users)
- **armeabi-v7a**: Older 32-bit ARM devices
- **x86**: Intel/AMD 32-bit devices
- **x86_64**: Intel/AMD 64-bit devices

---

## Releases

### Setting Up Release Signing

To enable automatic signing for release builds in GitHub Actions, you need to configure the
following secrets in your GitHub repository:

1. Navigate to your repository on GitHub
2. Go to **Settings** → **Secrets and variables** → **Actions**
3. Add the following repository secrets:

| Secret Name              | Description                                          |
|--------------------------|------------------------------------------------------|
| `SIGNING_KEYSTORE`       | Base64-encoded keystore file (`.jks` or `.keystore`) |
| `SIGNING_KEY_ALIAS`      | The alias name used when creating the keystore       |
| `SIGNING_STORE_PASSWORD` | Password for the keystore file                       |
| `KEY_PASSWORD`           | Password for the key (can be same as store password) |

#### Encoding Your Keystore

To encode your keystore file to base64:

**Linux/macOS:**

```bash
base64 -i your-keystore.jks | tr -d '\n' > keystore.txt
```

**Windows (PowerShell):**

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("your-keystore.jks")) | Out-File -FilePath keystore.txt -NoNewline
```

Copy the contents of `keystore.txt` and paste it as the value for the `SIGNING_KEYSTORE` secret.

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

- [mpv-android](https://github.com/mpv-android)
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)
- [Next player](https://github.com/anilbeesetti/nextplayer)
- [Gramophone](https://github.com/FoedusProgramme/Gramophone)

---

## Support the Project <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Heart%20with%20Ribbon.png" alt="Heart with Ribbon" width="25" height="25" />

If you find mpvExtended useful, consider supporting the development:

[![UPI](https://img.shields.io/badge/UPI-aadiinarvekar@upi-blue?style=for-the-badge&logo=google-pay&logoColor=white)](upi://pay?pa=aadiinarvekar@upi)

---
## Star History <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Star.png" alt="Star" width="25" height="25" />

<a href="https://www.star-history.com/#marlboro-advance/mpvEx&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=marlboro-advance/mpvEx&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=marlboro-advance/mpvEx&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=marlboro-advance/mpvEx&type=date&legend=top-left" />
 </picture>
</a>
