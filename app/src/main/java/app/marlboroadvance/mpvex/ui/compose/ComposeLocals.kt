package app.marlboroadvance.mpvex.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A CompositionLocal for providing LazyListState to nested composables.
 * This allows passing the list state down the composition tree without
 * explicit parameter passing.
 */
val LocalLazyListState = staticCompositionLocalOf<LazyListState> {
    error("No LazyListState provided. Make sure to provide a LazyListState using CompositionLocalProvider.")
}

/**
 * A CompositionLocal for providing LazyGridState to nested composables.
 * This allows passing the grid state down the composition tree without
 * explicit parameter passing.
 */
val LocalLazyGridState = staticCompositionLocalOf<LazyGridState> {
    error("No LazyGridState provided. Make sure to provide a LazyGridState using CompositionLocalProvider.")
}
