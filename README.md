# mpvExtended


mpvExtended is a front-end for the versatile media player mpv, built on the libmpv library. It aims
to combine the powerful features of mpv with an easier-to-use interface and additional
functionality.

- **Simpler and Easier to Use UI**
- **Material3 Expressive Design**
- **Advanced Configuration and Scripting**
- **Enhanced Playback Features**
- **Picture-in-Picture (PiP)**
- **Background Playback**
- **Multi-Modal Controls**
- **High-Quality Rendering**
- **Network Streaming**
- **File Management**

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


---

## Acknowledgments
- [mpv-android](https://github.com/mpv-android) for the base mpv library to use for this project.
- [mpvKt](https://github.com/abdallahmehiz/mpvKt) for the modified version of mpv-android.
