package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import java.util.UUID
import kotlin.math.roundToInt

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

@Serializable
object CustomButtonScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val preferences = koinInject<PlayerPreferences>()
        
        // State for the list of buttons
        var buttons by remember { mutableStateOf(emptyList<CustomButton>()) }
        
        // State for dialogs
        var showDialog by remember { mutableStateOf(false) }
        var buttonToEdit by remember { mutableStateOf<CustomButton?>(null) }
        
        // Load initial data
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
        
        // Save data helper
        fun saveButtons(newButtons: List<CustomButton>) {
            buttons = newButtons
            preferences.customButtons.set(Json.encodeToString(newButtons))
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
                        buttonToEdit = null // for adding new
                        showDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add New Button")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()) // Whole screen scrollable
            ) {
                val activeButtons = buttons.filter { it.isActive }
                val availableButtons = buttons.filter { !it.isActive }

                // 1. Live Preview Section (Purely Visual)
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LivePreviewBox(activeButtons = activeButtons)

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // 2. Active Buttons Implementation (Split Rows)
                
                // Left Buttons Row
                Text(
                    text = "Active - Left Buttons",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val leftButtons = activeButtons.filter { it.isLeft }
                    if (leftButtons.isEmpty()) {
                        Text("No left buttons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        leftButtons.forEach { button ->
                            CustomButtonChip(
                                button = button,
                                isActive = true,
                                onRemove = {
                                    saveButtons(buttons.map { if (it.id == button.id) it.copy(isActive = false) else it })
                                },
                                onClick = { /* Edit? Or just drag? */ }
                            )
                        }
                    }
                }

                // Right Buttons Row
                Text(
                    text = "Active - Right Buttons",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rightButtons = activeButtons.filter { !it.isLeft }
                    if (rightButtons.isEmpty()) {
                        Text("No right buttons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        rightButtons.forEach { button ->
                            CustomButtonChip(
                                button = button,
                                isActive = true,
                                onRemove = {
                                    saveButtons(buttons.map { if (it.id == button.id) it.copy(isActive = false) else it })
                                },
                                onClick = { }
                            )
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // 3. Available Buttons
                Text(
                    text = "Available Buttons",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (availableButtons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No available buttons. Create one!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    // Simple FlowRow implementation using explicit rows for now as FlowRow is experimental
                    // Or just a column of items as requested (but maybe grid is better)
                    // Let's stick to the previous list card style but make it compact
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableButtons.forEach { button ->
                            CustomButtonItem(
                                button = button,
                                isActive = false,
                                onEdit = {
                                    buttonToEdit = button
                                    showDialog = true
                                },
                                onDelete = {
                                    saveButtons(buttons.filter { it.id != button.id })
                                },
                                onToggleActive = {
                                    if (activeButtons.size < 6) {
                                        // When activating, ask for alignment or default to valid
                                        // For simplicity, default to Left or we could show a dialog
                                        // Let's default to button.isLeft (which is editable)
                                        val newButtons = buttons.map { 
                                            if (it.id == button.id) it.copy(isActive = true) else it 
                                        }
                                        saveButtons(newButtons)
                                    }
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
            }
        }

        if (showDialog) {
            CustomButtonDialog(
                button = buttonToEdit,
                onDismiss = { showDialog = false },
                onSave = { newButton ->
                    if (buttonToEdit == null) {
                        saveButtons(buttons + newButton)
                    } else {
                        saveButtons(buttons.map { if (it.id == newButton.id) newButton else it })
                    }
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun LivePreviewBox(activeButtons: List<CustomButton>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(16.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(16.dp)
        ) {
            val width = maxWidth
            
            // Dummy Controls
            Row(modifier = Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Audiotrack, null, tint = Color.White)
                Icon(Icons.Default.Subtitles, null, tint = Color.White)
                Icon(Icons.Default.Settings, null, tint = Color.White)
            }
            
            Row(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                 Icon(Icons.Default.Speed, null, tint = Color.White)
                 Icon(Icons.Default.AspectRatio, null, tint = Color.White)
                 Icon(Icons.Default.PictureInPicture, null, tint = Color.White)
            }

            Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 24.dp)) {
                 Icon(Icons.Default.Lock, null, tint = Color.White)
            }

            Row(modifier = Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                 Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                 Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), CircleShape).padding(8.dp))
                 Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).background(Color.Red, RoundedCornerShape(2.dp)))

            // Active Buttons Display
            // Left Zone
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 48.dp)
                    .width(width / 2 - 24.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeButtons.filter { it.isLeft }.forEach { button ->
                    VisualButton(button)
                }
            }

            // Right Zone
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 48.dp)
                    .width(width / 2 - 24.dp)
                    .horizontalScroll(rememberScrollState(), reverseScrolling = true),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeButtons.filter { !it.isLeft }.forEach { button ->
                    VisualButton(button)
                }
            }
        }
    }
}

@Composable
fun VisualButton(button: CustomButton) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Text(
            text = button.title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CustomButtonChip(
    button: CustomButton,
    isActive: Boolean,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                text = button.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp).padding(4.dp)
            ) {
                Icon(
                    Icons.Default.RemoveCircle,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = button.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                     IconButton(onClick = onToggleActive) {
                        Icon(
                            if (isActive) Icons.Default.RemoveCircle else Icons.Default.AddCircle,
                            contentDescription = if (isActive) "Deactivate" else "Activate",
                            tint = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var isLeft by remember { mutableStateOf(button?.isLeft ?: true) }
    
    val isEditing = button != null
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit button" else "Add button") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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
                    minLines = 3,
                    maxLines = 6
                )
                OutlinedTextField(
                    value = longPressContent,
                    onValueChange = { longPressContent = it },
                    label = { Text("Lua code (long press)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = onStartup,
                    onValueChange = { onStartup = it },
                    label = { Text("On startup") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Alignment:", style = MaterialTheme.typography.bodyMedium)
                    FilterChip(
                        selected = isLeft,
                        onClick = { isLeft = true },
                        label = { Text("Left") }
                    )
                    FilterChip(
                        selected = !isLeft,
                        onClick = { isLeft = false },
                        label = { Text("Right") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(
                            CustomButton(
                                id = button?.id ?: UUID.randomUUID().toString(),
                                title = title,
                                content = content,
                                longPressContent = longPressContent,
                                onStartup = onStartup,
                                isLeft = isLeft,
                                isActive = button?.isActive ?: false
                            )
                        )
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}