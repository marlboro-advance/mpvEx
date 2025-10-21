# Permission Management Utility

This utility provides a simple and consistent way to handle storage permissions across different
Android versions.

## Features

- **Automatic Version Handling**: Automatically selects the correct permission based on Android
  version
    - Android 13+ (API 33+): `READ_MEDIA_VIDEO`
    - Android 12 and below: `READ_EXTERNAL_STORAGE`
- **Composable Integration**: Easy-to-use Composable functions that integrate seamlessly with
  Jetpack Compose
- **Auto-refresh Callback**: Automatically executes callback when permission is granted

## Usage

### Basic Usage

Use `HandleStoragePermission` to automatically request and handle storage permission:

```kotlin
val permissionState = PermissionUtils.HandleStoragePermission(
  onPermissionGranted = { 
    // This callback is automatically called when permission is granted
    viewModel.refresh()
  }
)

// Use permissionState.status to check current permission status
when (permissionState.status) {
  PermissionStatus.Granted -> {
    // Show content
  }
  is PermissionStatus.Denied -> {
    // Show permission denied UI
    PermissionDeniedState(
      onRequestPermission = { permissionState.launchPermissionRequest() }
    )
  }
}
```

### Advanced Usage

If you need more control, use the individual functions:

```kotlin
// Get the appropriate permission string for the current Android version
val permission = PermissionUtils.getStoragePermission()

// Or use the composable to get permission state
val permissionState = PermissionUtils.rememberStoragePermissionState()
```
