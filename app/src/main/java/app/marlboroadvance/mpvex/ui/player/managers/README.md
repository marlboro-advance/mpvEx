# Player Managers Architecture

## Overview

This package contains specialized manager classes that handle different aspects of the media player
functionality. The refactoring transformed a monolithic 1093-line `PlayerActivity` into a lean
327-line coordinator that delegates to focused, testable managers.

## Architecture Benefits

- **Separation of Concerns**: Each manager handles one specific responsibility
- **Testability**: Managers can be unit tested independently
- **Maintainability**: Changes to specific features are isolated to their respective managers
- **Reusability**: Managers can be potentially reused in other contexts
- **Reduced Complexity**: The main Activity is now a thin coordinator

## Manager Classes

### 1. PlayerConstants

**Lines**: 26  
**Purpose**: Centralized constants for timing, values, and configuration

Contains all magic numbers and constant values used throughout the player:

- Timing constants (delays, timeouts)
- Value constants (brightness, volume, position)
- Intent action strings

### 2. AudioFocusManager

**Lines**: 129  
**Purpose**: Manages audio focus and headphone disconnect events

**Responsibilities**:

- Request and abandon audio focus
- Handle audio focus change events
- Register/unregister noisy audio receiver (headphone disconnect)
- Provide audio manager utilities (get/set volume)

**Key Methods**:

- `requestAudioFocus()`: Request audio focus from the system
- `abandonAudioFocus()`: Release audio focus
- `registerNoisyReceiver()`: Listen for headphone disconnects
- `cleanup()`: Release all audio resources

### 3. MPVConfigurationManager

**Lines**: 192  
**Purpose**: Handles MPV library initialization, configuration, and asset management

**Responsibilities**:

- Initialize MPV library
- Copy MPV assets (scripts, config files, fonts)
- Setup MPV configuration
- Destroy MPV on cleanup

**Key Methods**:

- `initialize()`: Initialize MPV with file paths
- `setupAudio()`: Configure audio settings
- `destroy()`: Clean shutdown of MPV
- `copyMPVAssets()`: Copy all required assets

### 4. IntentHandler

**Lines**: 165  
**Purpose**: Parses and processes all intent data

**Responsibilities**:

- Extract playable URIs from various intent types
- Get file names from URIs
- Apply intent extras (position, subtitles, headers)
- Create result intents

**Key Methods**:

- `getPlayableUri()`: Extract playable URI from intent
- `getFileName()`: Get display name from intent
- `applyIntentExtras()`: Apply all intent extras to MPV
- `createResultIntent()`: Create result intent with playback state

### 5. SystemUIManager

**Lines**: 165  
**Purpose**: Manages system UI visibility and window properties

**Responsibilities**:

- Setup immersive mode
- Manage system bars visibility
- Handle display cutout configuration
- Manage screen brightness
- Control screen wake lock
- Handle PiP UI transitions

**Key Methods**:

- `setupSystemUI()`: Enter immersive playback mode
- `restoreSystemUI()`: Restore normal UI on exit
- `enterPipUIMode()`: Configure UI for PiP
- `exitPipUIMode()`: Restore UI from PiP
- `setBrightness()`: Set window brightness

### 6. PlaybackStateManager

**Lines**: 119  
**Purpose**: Manages playback state persistence and restoration

**Responsibilities**:

- Save playback state (position, speed, tracks, delays)
- Load and restore playback state
- Calculate save positions
- Apply default settings

**Key Methods**:

- `savePlaybackState()`: Save current state to database
- `loadPlaybackState()`: Load and apply saved state
- `applyPlaybackState()`: Apply state to MPV
- `applyDefaultSettings()`: Apply default preferences

### 7. OrientationManager

**Lines**: 42  
**Purpose**: Manages screen orientation

**Responsibilities**:

- Set screen orientation based on preferences
- Determine video-based orientation
- Handle orientation changes

**Key Methods**:

- `setOrientation()`: Set screen orientation from preferences
- `determineVideoOrientation()`: Calculate orientation from video aspect

### 8. KeyEventHandler

**Lines**: 124  
**Purpose**: Handles all keyboard and remote control events

**Responsibilities**:

- Process key down/up events
- Handle D-pad navigation
- Handle media keys
- Handle volume keys
- Delegate to MPV for unhandled keys

**Key Methods**:

- `onKeyDown()`: Handle key press events
- `onKeyUp()`: Handle key release events

### 9. MPVEventDispatcher

**Lines**: 135  
**Purpose**: Dispatches and handles MPV events and property changes

**Responsibilities**:

- Handle MPV property changes
- Dispatch MPV events
- Coordinate file loaded events
- Manage pause/play state
- Handle end-of-file events

**Key Methods**:

- `onObserverEvent()`: Handle various property changes
- `event()`: Handle MPV events by ID
- `handleFileLoaded()`: Apply settings when file loads
- `setFileName()` / `getFileName()`: Manage current file name

### 10. PipCoordinator

**Lines**: 125  
**Purpose**: Coordinates Picture-in-Picture mode

**Responsibilities**:

- Handle back press for PiP
- Manage PiP mode transitions
- Update PiP parameters
- Hide UI elements during PiP
- Handle configuration changes

**Key Methods**:

- `handleBackPress()`: Check if should enter PiP
- `onPictureInPictureModeChanged()`: Handle PiP state changes
- `updatePictureInPictureParams()`: Update PiP aspect ratio
- `enterPipModeHidingOverlay()`: Enter PiP with hidden UI

### 11. PlayerLifecycleManager

**Lines**: 122  
**Purpose**: Orchestrates all lifecycle events and coordinates managers

**Responsibilities**:

- Coordinate onStart, onStop, onPause, onDestroy
- Manage brightness restoration
- Coordinate playback state saving
- Handle cleanup on destroy

**Key Methods**:

- `onStart()`: Initialize on activity start
- `onStop()`: Cleanup on activity stop
- `onPause()`: Handle pause event
- `onDestroy()`: Full cleanup on destroy
- `onFinish()`: Handle activity finish

### 12. PlayerManagerFactory

**Lines**: 142  
**Purpose**: Factory for creating and providing all managers

**Responsibilities**:

- Lazy initialization of all managers
- Centralize dependency injection
- Manage manager creation order
- Provide single source of manager instances

**Key Properties**:

- All managers as lazy-initialized properties
- Handles complex dependency graphs between managers

## PlayerActivity (Refactored)

**Lines**: 327 (previously 1093)  
**Reduction**: 70% smaller

**Remaining Responsibilities**:

- Activity lifecycle callbacks
- View binding and UI setup
- Delegate to managers for all business logic
- MPV observer event routing (for backward compatibility)

## Data Flow

```
Intent → IntentHandler → MPVConfigurationManager → Player
                ↓
        MPVEventDispatcher ← PlayerObserver ← MPV Events
                ↓
         PlayerViewModel → UI Updates
                ↓
        User Interaction → KeyEventHandler
                ↓
         MPV Commands → MPVLib
```

## Lifecycle Flow

```
onCreate:
  - Setup managers via PlayerManagerFactory
  - Initialize MPV via MPVConfigurationManager
  - Setup audio focus via AudioFocusManager
  - Parse intent via IntentHandler
  - Start playback

onStart:
  - Setup system UI
  - Register receivers
  - Restore brightness

onResume:
  - Update volume

onPause:
  - Save playback state
  - Pause if not in PiP

onStop:
  - Save playback state
  - Unregister receivers

onDestroy:
  - Cleanup all managers
  - Destroy MPV
  - Abandon audio focus
```

## Testing Strategy

Each manager can be unit tested independently:

1. **Mock dependencies**: Each manager has clear constructor dependencies
2. **Test public methods**: Each manager has well-defined public API
3. **Verify interactions**: Test that managers call dependencies correctly
4. **Test edge cases**: Isolated managers make edge case testing easier

## Future Improvements

1. **Dependency Injection**: Consider using Koin modules for manager creation
2. **Use Cases**: Extract complex workflows into dedicated use case classes
3. **Interfaces**: Create interfaces for managers to enable easier mocking
4. **Event Bus**: Consider using event bus for manager communication
5. **State Machine**: Implement formal state machine for playback states

## Migration Notes

The refactored code maintains backward compatibility:

- All existing functionality preserved
- Public API of PlayerActivity unchanged
- No changes required to calling code
- Internal structure completely redesigned

## Performance Considerations

- Managers are created lazily (only when first accessed)
- No performance overhead from delegation
- Improved code organization may enable future optimizations
- Smaller classes are easier for compiler to optimize
