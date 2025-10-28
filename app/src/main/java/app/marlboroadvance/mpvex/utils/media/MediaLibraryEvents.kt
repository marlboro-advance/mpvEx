package app.marlboroadvance.mpvex.utils.media

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Simple app-wide event bus to notify screens/viewmodels when the media library changes
 * (copy/move/delete/rename, private storage move/restore, rescans, etc.).
 */
object MediaLibraryEvents {
  private val _changes =
    MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val changes: SharedFlow<Unit> = _changes

  fun notifyChanged() {
    _changes.tryEmit(Unit)
  }
}
