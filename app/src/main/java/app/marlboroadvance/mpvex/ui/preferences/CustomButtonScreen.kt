package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import java.util.UUID

@Serializable
data class CustomButton(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val longPressContent: String = "",
    val onStartup: String = ""
)

@Serializable
data class CustomButtonSlots(
    val slots: List<CustomButton?> = List(8) { null }
)

@Serializable
object CustomButtonScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val preferences = koinInject<PlayerPreferences>()

        // Fixed 8 slots: 0-3 left, 4-7 right
        var buttonSlots by remember { mutableStateOf(List<CustomButton?>(8) { null }) }
        var showDialog by remember { mutableStateOf(false) }
        var editingSlot by remember { mutableStateOf(-1) }

        // Load initial
        LaunchedEffect(Unit) {
            val jsonString = preferences.customButtons.get()
            if (jsonString.isNotBlank()) {
                try {
                    val loaded = Json.decodeFromString<CustomButtonSlots>(jsonString)
                    // Ensure we always have exactly 8 slots
                    val slots = loaded.slots.take(8).toMutableList()
                    while (slots.size < 8) {
                        slots.add(null)
                    }
                    buttonSlots = slots
                } catch (e: Exception) {
                    // Try old format for backward compatibility
                    try {
                        val oldButtons: List<CustomButton> = Json.decodeFromString(jsonString)
                        val slots = MutableList<CustomButton?>(8) { null }
                        oldButtons.forEachIndexed { index, button ->
                            if (index < 8) slots[index] = button
                        }
                        buttonSlots = slots
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }
        }

        // Persistence Effect - Save with slot positions preserved
        LaunchedEffect(buttonSlots) {
            val slotsData = CustomButtonSlots(buttonSlots)
            preferences.customButtons.set(Json.encodeToString(slotsData))
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
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Buttons Section (Slots 0-3)
                ButtonSlotSection(
                    title = "Left Buttons",
                    slots = buttonSlots.subList(0, 4),
                    startIndex = 0,
                    onEdit = { slot ->
                        editingSlot = slot
                        showDialog = true
                    },
                    onDelete = { slot ->
                        buttonSlots = buttonSlots.toMutableList().apply {
                            this[slot] = null
                        }
                    },
                    onMoveUp = { slot ->
                        if (slot > 0) {
                            buttonSlots = buttonSlots.toMutableList().apply {
                                val temp = this[slot]
                                this[slot] = this[slot - 1]
                                this[slot - 1] = temp
                            }
                        }
                    },
                    onMoveDown = { slot ->
                        if (slot < 3) {
                            buttonSlots = buttonSlots.toMutableList().apply {
                                val temp = this[slot]
                                this[slot] = this[slot + 1]
                                this[slot + 1] = temp
                            }
                        }
                    }
                )

                HorizontalDivider()

                // Right Buttons Section (Slots 4-7)
                ButtonSlotSection(
                    title = "Right Buttons",
                    slots = buttonSlots.subList(4, 8),
                    startIndex = 4,
                    onEdit = { slot ->
                        editingSlot = slot
                        showDialog = true
                    },
                    onDelete = { slot ->
                        buttonSlots = buttonSlots.toMutableList().apply {
                            this[slot] = null
                        }
                    },
                    onMoveUp = { slot ->
                        if (slot > 4) {
                            buttonSlots = buttonSlots.toMutableList().apply {
                                val temp = this[slot]
                                this[slot] = this[slot - 1]
                                this[slot - 1] = temp
                            }
                        }
                    },
                    onMoveDown = { slot ->
                        if (slot < 7) {
                            buttonSlots = buttonSlots.toMutableList().apply {
                                val temp = this[slot]
                                this[slot] = this[slot + 1]
                                this[slot + 1] = temp
                            }
                        }
                    }
                )
            }
        }

        if (showDialog && editingSlot >= 0) {
            CustomButtonDialog(
                button = buttonSlots[editingSlot],
                onDismiss = { 
                    showDialog = false
                    editingSlot = -1
                },
                onSave = { newButton ->
                    buttonSlots = buttonSlots.toMutableList().apply {
                        this[editingSlot] = newButton
                    }
                    showDialog = false
                    editingSlot = -1
                }
            )
        }
    }
}

@Composable
fun ButtonSlotSection(
    title: String,
    slots: List<CustomButton?>,
    startIndex: Int,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        slots.forEachIndexed { index, button ->
            val slotNumber = startIndex + index
            ButtonSlotItem(
                slotNumber = slotNumber,
                button = button,
                onEdit = { onEdit(slotNumber) },
                onDelete = { onDelete(slotNumber) },
                canMoveUp = index > 0,
                canMoveDown = index < slots.size - 1,
                onMoveUp = { onMoveUp(slotNumber) },
                onMoveDown = { onMoveDown(slotNumber) }
            )
        }
    }
}

@Composable
fun ButtonSlotItem(
    slotNumber: Int,
    button: CustomButton?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (button != null) 
                MaterialTheme.colorScheme.surfaceContainer 
            else 
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (button != null) "#$slotNumber ${button.title}" else "#$slotNumber Empty Button",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (button != null) FontWeight.Bold else FontWeight.Normal,
                color = if (button != null) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (button != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Move Up",
                            modifier = Modifier.rotate(90f),
                            tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Move Down",
                            modifier = Modifier.rotate(90f),
                            tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }
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
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (button != null) "Edit button" else "Add button") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Lua code *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedTextField(
                    value = longPressContent,
                    onValueChange = { longPressContent = it },
                    label = { Text("Long press Lua") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = onStartup,
                    onValueChange = { onStartup = it },
                    label = { Text("On startup") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CustomButton(
                            id = button?.id ?: UUID.randomUUID().toString(),
                            title = title,
                            content = content,
                            longPressContent = longPressContent,
                            onStartup = onStartup
                        )
                    )
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
