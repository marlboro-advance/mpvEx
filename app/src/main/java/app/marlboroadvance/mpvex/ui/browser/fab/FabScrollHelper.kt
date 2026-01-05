package app.marlboroadvance.mpvex.ui.browser.fab

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

/**
 * Common helper functions for FAB visibility based on scroll state
 */
object FabScrollHelper {
    /**
     * Sets up scroll tracking for both list and grid views to control FAB visibility
     */
    @Composable
    fun trackScrollForFabVisibility(
        listState: LazyListState,
        gridState: LazyGridState?,
        isFabVisible: MutableState<Boolean>,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit
    ) {
        // Track scroll position to determine direction (for list state)
        val previousListIndex = remember { mutableIntStateOf(0) }
        val previousListScrollOffset = remember { mutableIntStateOf(0) }
        
        // Track scroll position to determine direction (for grid state)
        val previousGridIndex = remember { mutableIntStateOf(0) }
        val previousGridScrollOffset = remember { mutableIntStateOf(0) }
        
        // Remember if we've just seen a change in states/tabs
        val justChangedStates = remember { mutableIntStateOf(0) }
        
        // When the listState object reference changes (tab switch), ensure FAB is visible
        LaunchedEffect(listState) {
            // Make FAB visible when switching tabs
            isFabVisible.value = true
            
            // Mark that we just changed states
            justChangedStates.intValue++
        }
        
        if (gridState != null) {
            // When the gridState object reference changes (tab switch), ensure FAB is visible
            LaunchedEffect(gridState) {
                // Make FAB visible when switching tabs
                isFabVisible.value = true
                
                // Mark that we just changed states
                justChangedStates.intValue++
            }
        }
        
        // Update FAB visibility based on list scroll direction
        LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, justChangedStates.intValue) {
            // Only process scroll events (not state changes) after a brief delay
            // This prevents the FAB from hiding when switching tabs
            if (justChangedStates.intValue > 0) {
                // Update previous values without changing visibility
                previousListIndex.intValue = listState.firstVisibleItemIndex
                previousListScrollOffset.intValue = listState.firstVisibleItemScrollOffset
                return@LaunchedEffect
            }
            
            updateFabVisibility(
                isFabVisible, 
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                previousListIndex.intValue,
                previousListScrollOffset.intValue
            )
            
            previousListIndex.intValue = listState.firstVisibleItemIndex
            previousListScrollOffset.intValue = listState.firstVisibleItemScrollOffset
        }
        
        // Reset the state change counter after a short delay
        LaunchedEffect(justChangedStates.intValue) {
            if (justChangedStates.intValue > 0) {
                kotlinx.coroutines.delay(300) // Wait for tab switch animations
                justChangedStates.intValue = 0
            }
        }
        
        // Update FAB visibility based on grid scroll direction (if grid state is provided)
        if (gridState != null) {
            LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, justChangedStates.intValue) {
                // Skip processing during tab changes
                if (justChangedStates.intValue > 0) {
                    // Update previous values without changing visibility
                    previousGridIndex.intValue = gridState.firstVisibleItemIndex
                    previousGridScrollOffset.intValue = gridState.firstVisibleItemScrollOffset
                    return@LaunchedEffect
                }
                
                updateFabVisibility(
                    isFabVisible,
                    gridState.firstVisibleItemIndex,
                    gridState.firstVisibleItemScrollOffset,
                    previousGridIndex.intValue,
                    previousGridScrollOffset.intValue
                )
                
                previousGridIndex.intValue = gridState.firstVisibleItemIndex
                previousGridScrollOffset.intValue = gridState.firstVisibleItemScrollOffset
            }
        }
        
        // Auto-collapse menu when list scrolling
        LaunchedEffect(listState.isScrollInProgress) {
            if (expanded && listState.isScrollInProgress) {
                onExpandedChange(false)
            }
        }
        
        // Auto-collapse menu when grid scrolling
        gridState?.let { grid ->
            LaunchedEffect(grid.isScrollInProgress) {
                if (expanded && grid.isScrollInProgress) {
                    onExpandedChange(false)
                }
            }
        }
    }
    
    /**
     * Helper function to update FAB visibility based on scroll position
     */
    private fun updateFabVisibility(
        isFabVisible: MutableState<Boolean>,
        currentIndex: Int,
        currentScrollOffset: Int,
        previousIndex: Int,
        previousScrollOffset: Int
    ) {
        // Always show at top
        if (currentIndex == 0 && currentScrollOffset == 0) {
            isFabVisible.value = true
        } else {
            // Calculate if scrolling down or up
            val isScrollingDown = if (currentIndex != previousIndex) {
                currentIndex > previousIndex
            } else {
                currentScrollOffset > previousScrollOffset
            }
            
            // Hide when scrolling down, show when scrolling up
            isFabVisible.value = !isScrollingDown
        }
    }
}
