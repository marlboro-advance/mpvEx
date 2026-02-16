package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Serializable
data class CustomButton(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val longPressContent: String = "",
    val onStartup: String = "",
    val isLeft: Boolean = true,
    val isActive: Boolean = false
)

private const val HEADER_LEFT_KEY = "header_left"
private const val HEADER_RIGHT_KEY = "header_right"
private const val EMPTY_SLOT_LEFT_KEY = "empty_slot_left"
private const val EMPTY_SLOT_RIGHT_KEY = "empty_slot_right"
private const val APPEND_SLOT_LEFT_KEY = "append_slot_left"
private const val APPEND_SLOT_RIGHT_KEY = "append_slot_right"

@Serializable
object CustomButtonScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val preferences = koinInject<PlayerPreferences>()

        var buttons by remember { mutableStateOf(emptyList<CustomButton>()) }
        var showDialog by remember { mutableStateOf(false) }
        var buttonToEdit by remember { mutableStateOf<CustomButton?>(null) }

        // Ensure atomic updates
        val buttonsState = rememberUpdatedState(buttons)

        // Load initial
        LaunchedEffect(Unit) {
            val jsonString = preferences.customButtons.get()
            if (jsonString.isNotBlank()) {
                try {
                    buttons = Json.decodeFromString(jsonString)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Persistence Effect
        LaunchedEffect(buttons) {
            // always persist full list (even empty is fine)
            preferences.customButtons.set(Json.encodeToString(buttons))
        }

        fun updateButtons(newButtons: List<CustomButton>) {
            buttons = newButtons
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Edit custom buttons",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = backstack::removeLastOrNull) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        buttonToEdit = null
                        showDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add New Button")
                }
            }
        ) { padding ->
            val gridState = rememberLazyGridState()

            // Reordering Logic (robust version)
            val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
                val fromKey = from.key as? String ?: return@rememberReorderableLazyGridState
                val toKey = to.key as? String ?: return@rememberReorderableLazyGridState

                // Build explicit lists for easier logic
                val left = buttonsState.value.filter { it.isActive && it.isLeft }.toMutableList()
                val right = buttonsState.value.filter { it.isActive && !it.isLeft }.toMutableList()
                val inactive = buttonsState.value.filter { !it.isActive }.toMutableList()

                // find moving item from either side
                val moving = (left + right).find { it.id == fromKey } ?: return@rememberReorderableLazyGridState

                // remove from its source list
                if (moving.isLeft) left.removeAll { it.id == moving.id } else right.removeAll { it.id == moving.id }

                // helper to write back combined list
                fun commit(newLeft: List<CustomButton>, newRight: List<CustomButton>) {
                    // preserve inactive ordering
                    updateButtons(newLeft + newRight + inactive)
                }

                // Determine destination
                when (toKey) {
                    HEADER_LEFT_KEY, EMPTY_SLOT_LEFT_KEY -> {
                        if (left.size >= 4) return@rememberReorderableLazyGridState
                        val moved = moving.copy(isLeft = true)
                        left.add(0, moved)
                        commit(left, right)
                    }
                    HEADER_RIGHT_KEY, EMPTY_SLOT_RIGHT_KEY -> {
                        if (right.size >= 4) return@rememberReorderableLazyGridState
                        val moved = moving.copy(isLeft = false)
                        right.add(0, moved)
                        commit(left, right)
                    }
                    APPEND_SLOT_LEFT_KEY -> {
                         if (left.size >= 4) return@rememberReorderableLazyGridState
                         val moved = moving.copy(isLeft = true)
                         left.add(moved) // Append
                         commit(left, right)
                    }
                    APPEND_SLOT_RIGHT_KEY -> {
                         if (right.size >= 4) return@rememberReorderableLazyGridState
                         val moved = moving.copy(isLeft = false)
                         right.add(moved) // Append
                         commit(left, right)
                    }
                    else -> {
                        // dropped on another button
                        val target = (left + right).find { it.id == toKey }
                        if (target == null) return@rememberReorderableLazyGridState

                        val targetIsLeft = target.isLeft
                        val targetList = if (targetIsLeft) left else right
                        
                        // Check original relative positions to determine insertion side
                        val originalList = if (targetIsLeft) buttonsState.value.filter { it.isActive && it.isLeft } else buttonsState.value.filter { it.isActive && !it.isLeft }
                        val originalMovingIdx = originalList.indexOfFirst { it.id == moving.id }
                        val originalTargetIdx = originalList.indexOfFirst { it.id == target.id }

                        if (moving.isLeft != targetIsLeft) {
                             if (targetList.size >= 4) return@rememberReorderableLazyGridState
                             val moved = moving.copy(isLeft = targetIsLeft)
                             val idx = targetList.indexOfFirst { it.id == target.id }
                             if (idx != -1) targetList.add(idx, moved) else targetList.add(moved)
                             commit(left, right)
                        } else {
                            // Same list reordering
                            val moved = moving.copy(isLeft = targetIsLeft)
                            val currentTargetIdx = targetList.indexOfFirst { it.id == target.id }
                            
                            if (currentTargetIdx != -1) {
                                // If original moving < original target (Moving Right/Down), insert AFTER target
                                if (originalMovingIdx != -1 && originalMovingIdx < originalTargetIdx) {
                                     targetList.add(currentTargetIdx + 1, moved)
                                } else {
                                     // Moving Left/Up, insert BEFORE target
                                     targetList.add(currentTargetIdx, moved)
                                }
                                commit(left, right)
                            }
                        }
                    }
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 72.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val activeButtons = buttons.filter { it.isActive }
                val availableButtons = buttons.filter { !it.isActive }

                // 1. Preview
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LivePreviewBox(activeButtons = activeButtons)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    }
                }

                // 2. Left Buttons Header
                item(span = { GridItemSpan(maxLineSpan) }, key = HEADER_LEFT_KEY) {
                    ReorderableItem(reorderableState, key = HEADER_LEFT_KEY, enabled = true) { _ ->
                        val count = activeButtons.filter { it.isLeft }.size
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Active - Left Buttons", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("$count / 4", style = MaterialTheme.typography.labelSmall, color = if (count >= 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                // 3. Left Buttons
                val leftButtons = activeButtons.filter { it.isLeft }
                if (leftButtons.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = EMPTY_SLOT_LEFT_KEY) {
                        ReorderableItem(reorderableState, key = EMPTY_SLOT_LEFT_KEY, enabled = true) { _ ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(vertical = 2.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Drop Left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                } else {
                    items(leftButtons, key = { it.id }) { button ->
                        ReorderableItem(reorderableState, key = button.id) { isDragging ->
                            val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            Box(
                                modifier = Modifier
                                    .shadow(elevation.value)
                                    .draggableHandle()
                            ) {
                                CustomButtonChip(button = button, onRemove = { updateButtons(buttons.map { if (it.id == button.id) it.copy(isActive = false) else it }) })
                            }
                        }
                    }
                    // Append Slot (Visible if 1-3 items)
                    if (leftButtons.isNotEmpty() && leftButtons.size < 4) {
                         item(span = { GridItemSpan(1) }, key = APPEND_SLOT_LEFT_KEY) {
                            ReorderableItem(reorderableState, key = APPEND_SLOT_LEFT_KEY, enabled = true) { _ ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // No Icon, just invisible drop target
                                }
                            }
                         }
                    }
                }

                // 4. Right Buttons Header
                item(span = { GridItemSpan(maxLineSpan) }, key = HEADER_RIGHT_KEY) {
                    ReorderableItem(reorderableState, key = HEADER_RIGHT_KEY, enabled = true) { _ ->
                        val count = activeButtons.filter { !it.isLeft }.size
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Active - Right Buttons", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("$count / 4", style = MaterialTheme.typography.labelSmall, color = if (count >= 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // 5. Right Buttons
                val rightButtons = activeButtons.filter { !it.isLeft }
                if (rightButtons.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = EMPTY_SLOT_RIGHT_KEY) {
                        ReorderableItem(reorderableState, key = EMPTY_SLOT_RIGHT_KEY, enabled = true) { _ ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(vertical = 2.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Drop Right", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                } else {
                    items(rightButtons, key = { it.id }) { button ->
                        ReorderableItem(reorderableState, key = button.id) { isDragging ->
                            val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            Box(
                                modifier = Modifier
                                    .shadow(elevation.value)
                                    .draggableHandle()
                            ) {
                                CustomButtonChip(button = button, onRemove = { updateButtons(buttons.map { if (it.id == button.id) it.copy(isActive = false) else it }) })
                            }
                        }
                    }
                    
                    // Append Slot
                    if (rightButtons.isNotEmpty() && rightButtons.size < 4) {
                         item(span = { GridItemSpan(1) }, key = APPEND_SLOT_RIGHT_KEY) {
                            ReorderableItem(reorderableState, key = APPEND_SLOT_RIGHT_KEY, enabled = true) { _ ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // No Icon
                                }
                            }
                         }
                    }
                }

                // 6. Available Header
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            text = "Available Buttons",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // 7. Available Items
                if (availableButtons.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(text = "No available buttons. Create one!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    items(availableButtons, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { button ->
                        CustomButtonItem(
                            button = button,
                            isActive = false,
                            onEdit = { buttonToEdit = button; showDialog = true },
                            onDelete = { updateButtons(buttons.filter { it.id != button.id }) },
                            onToggleActive = {
                                // toggle into correct side respecting limits
                                val leftCount = buttons.count { it.isActive && it.isLeft }
                                val rightCount = buttons.count { it.isActive && !it.isLeft }
                                if (button.isLeft) {
                                    if (leftCount < 4) updateButtons(buttons.map { if (it.id == button.id) it.copy(isActive = true) else it })
                                } else {
                                    if (rightCount < 4) updateButtons(buttons.map { if (it.id == button.id) it.copy(isActive = true) else it })
                                }
                            }
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        if (showDialog) {
            CustomButtonDialog(
                button = buttonToEdit,
                onDismiss = { showDialog = false },
                onSave = { newButton ->
                    if (buttonToEdit == null) updateButtons(buttons + newButton)
                    else updateButtons(buttons.map { if (it.id == newButton.id) newButton else it })
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun LivePreviewBox(activeButtons: List<CustomButton>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(16.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(220.dp).padding(16.dp)) {
            val width = maxWidth
            
            Row(modifier = Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                 Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                 Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                 Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).background(Color.Red, RoundedCornerShape(2.dp)))

            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 48.dp).width(width / 2 - 24.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeButtons.filter { it.isLeft }.forEach { VisualButton(it) }
            }

            // Right Buttons - Aligned from End
            val rightButtons = activeButtons.filter { !it.isLeft }
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 48.dp).width(width / 2 - 24.dp).horizontalScroll(rememberScrollState(), reverseScrolling = true),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                rightButtons.forEach { VisualButton(it) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VisualButton(button: CustomButton) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Text(
            text = button.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).basicMarquee()
        )
    }
}

@Composable
fun CustomButtonChip(
    button: CustomButton,
    onRemove: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = button.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.basicMarquee()
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp).padding(2.dp)) {
                Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CustomButtonItem(
    button: CustomButton,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = button.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row {
                 IconButton(onClick = onToggleActive) {
                    Icon(if (isActive) Icons.Default.RemoveCircle else Icons.Default.AddCircle, null, tint = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
        }
    }
}

@Composable
fun CustomButtonDialog(
    button: CustomButton?,
    onDismiss: () -> Unit,
    onSave: (CustomButton) -> Unit
) {
    var title by remember { mutableStateOf(button?.title ?: "") }
    var content by remember { mutableStateOf(button?.content ?: "") }
    var longPressContent by remember { mutableStateOf(button?.longPressContent ?: "") }
    var onStartup by remember { mutableStateOf(button?.onStartup ?: "") }
    var isLeft by remember { mutableStateOf(button?.isLeft ?: true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (button != null) "Edit button" else "Add button") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Lua code *") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = longPressContent, onValueChange = { longPressContent = it }, label = { Text("Long press Lua") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = onStartup, onValueChange = { onStartup = it }, label = { Text("On startup") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alignment:")
                    FilterChip(selected = isLeft, onClick = { isLeft = true }, label = { Text("Left") })
                    FilterChip(selected = !isLeft, onClick = { isLeft = false }, label = { Text("Right") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(CustomButton(id = button?.id ?: UUID.randomUUID().toString(), title = title, content = content, longPressContent = longPressContent, onStartup = onStartup, isLeft = isLeft, isActive = button?.isActive ?: false)) }, enabled = title.isNotBlank() && content.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}