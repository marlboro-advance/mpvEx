package app.marlboroadvance.mpvex.ui.browser.selection

import androidx.compose.runtime.Stable

/**
 * State holder for item selection in browser screens
 */
@Stable
data class SelectionState<ID>(
  val selectedIds: Set<ID> = emptySet(),
) {
  /**
   * Whether selection mode is active
   */
  val isInSelectionMode: Boolean
    get() = selectedIds.isNotEmpty()

  /**
   * Number of selected items
   */
  val selectedCount: Int
    get() = selectedIds.size

  /**
   * Whether a single item is selected
   */
  val isSingleSelection: Boolean
    get() = selectedIds.size == 1

  /**
   * Check if an item is selected
   */
  fun isSelected(id: ID): Boolean = selectedIds.contains(id)

  /**
   * Toggle selection of an item
   */
  fun toggle(id: ID): SelectionState<ID> =
    copy(
      selectedIds =
        if (selectedIds.contains(id)) {
          selectedIds - id
        } else {
          selectedIds + id
        },
    )

  /**
   * Clear all selections
   */
  fun clear(): SelectionState<ID> = copy(selectedIds = emptySet())

  /**
   * Select all items
   */
  fun selectAll(ids: List<ID>): SelectionState<ID> = copy(selectedIds = ids.toSet())

  /**
   * Invert selection (select unselected, unselect selected)
   */
  fun invertSelection(ids: List<ID>): SelectionState<ID> {
    val allIds = ids.toSet()
    val invertedIds = allIds - selectedIds
    return copy(selectedIds = invertedIds)
  }

  /**
   * Get selected items from a list
   */
  fun <T> getSelected(
    items: List<T>,
    getId: (T) -> ID,
  ): List<T> = items.filter { selectedIds.contains(getId(it)) }
}
