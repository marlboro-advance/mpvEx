package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.presentation.Screen
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class CustomButton(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val longPressContent: String = "",
    val onStartup: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class CustomButtonSlots(
    val slots: List<CustomButton?> = List(8) { null }
)

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
object CustomButtonScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val preferences = koinInject<PlayerPreferences>()

        // 8 slots — order = left 0-3, right 4-7
        val buttonSlots = remember { mutableStateListOf<CustomButton?>(*Array(8) { null }) }

        // Import dialog state
        var showImportDialog by remember { mutableStateOf(false) }
        var importedSlots by remember { mutableStateOf<List<CustomButton?>>(emptyList()) }
        var selectedImportSlots by remember { mutableStateOf<Set<Int>>(emptySet()) }

        // Export launcher
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/xml")
        ) { uri ->
            uri?.let {
                runCatching {
                    val xmlContent = buildXmlExport(buttonSlots.toList())
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(xmlContent.toByteArray())
                    }
                    Toast.makeText(context, "Buttons exported successfully", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Import launcher
        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                runCatching {
                    val xmlContent = context.contentResolver.openInputStream(it)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: ""
                    
                    val parsed = parseXmlImport(xmlContent)
                    importedSlots = parsed
                    
                    // Pre-select all non-null slots
                    selectedImportSlots = parsed.indices.filter { index -> parsed[index] != null }.toSet()
                    
                    showImportDialog = true
                }.onFailure { e ->
                    Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Load saved data
        LaunchedEffect(Unit) {
            val jsonString = preferences.customButtons.get()
            if (jsonString.isNotBlank()) {
                runCatching {
                    val loaded = Json.decodeFromString<CustomButtonSlots>(jsonString)
                    val slots = loaded.slots.take(8).toMutableList()
                    while (slots.size < 8) slots.add(null)
                    buttonSlots.clear()
                    buttonSlots.addAll(slots)
                }.onFailure {
                    runCatching {
                        val old: List<CustomButton> = Json.decodeFromString(jsonString)
                        val slots = MutableList<CustomButton?>(8) { null }
                        old.forEachIndexed { i, b -> if (i < 8) slots[i] = b }
                        buttonSlots.clear()
                        buttonSlots.addAll(slots)
                    }
                }
            }
        }

        // Persist on every change
        LaunchedEffect(buttonSlots.toList()) {
            preferences.customButtons.set(Json.encodeToString(CustomButtonSlots(buttonSlots.toList())))
        }

        // Reorderable list state 
        val lazyListState = rememberLazyListState()
        val NON_SLOT_ITEMS_BEFORE = 1  // the Spacer item()
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIdx = (from.index - NON_SLOT_ITEMS_BEFORE).coerceIn(0, buttonSlots.lastIndex)
            val toIdx   = (to.index   - NON_SLOT_ITEMS_BEFORE).coerceIn(0, buttonSlots.lastIndex)
            val moved = buttonSlots.removeAt(fromIdx)
            buttonSlots.add(toIdx.coerceIn(0, buttonSlots.size), moved)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Custom Buttons",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Drag to reorder • Tap any slot to expand & edit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = backstack::removeLastOrNull) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            LazyColumn(
                state = lazyListState,
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Section divider items
                item { Spacer(Modifier.height(8.dp)) }

                // itemsIndexed gives us the real slot position so empty-slot keys are unique
                itemsIndexed(
                    items = buttonSlots.toList(),
                    key = { index, item -> item?.id ?: "slot_$index" },
                ) { index, button ->
                    val side = if (index < 4) "L${index + 1}" else "R${index - 3}"

                    ReorderableItem(reorderState, key = button?.id ?: "slot_$index") { isDragging ->
                        val elevation by animateFloatAsState(
                            targetValue = if (isDragging) 8f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "elevation"
                        )

                        ButtonSlotCard(
                            slotLabel = side,
                            button = button,
                            isDragging = isDragging,
                            elevation = elevation,
                            dragHandle = { interceptModifier ->
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    modifier = interceptModifier
                                        .draggableHandle()
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            },
                            onSave = { updated ->
                                buttonSlots[index] = updated
                            },
                            onDelete = {
                                buttonSlots[index] = null
                            },
                        )
                    }
                }

                // Import/Export section
                item { 
                    Spacer(Modifier.height(8.dp))
                }
                
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Import / Export",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            
                            Text(
                                text = "Backup or share all your custom buttons with their Lua code as an XML file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { importLauncher.launch(arrayOf("text/xml", "application/xml")) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Import")
                                }
                                
                                Button(
                                    onClick = { exportLauncher.launch("custom_buttons_${System.currentTimeMillis()}.xml") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Export")
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        
        // Import selection dialog
        if (showImportDialog) {
            ImportSelectionScreen(
                importedSlots = importedSlots,
                currentSlots = buttonSlots.toList(),
                selectedSlots = selectedImportSlots,
                onSlotToggle = { slotIndex ->
                    selectedImportSlots = if (selectedImportSlots.contains(slotIndex)) {
                        selectedImportSlots - slotIndex
                    } else {
                        selectedImportSlots + slotIndex
                    }
                },
                onConfirm = {
                    selectedImportSlots.forEach { slotIndex ->
                        if (slotIndex in importedSlots.indices) {
                            buttonSlots[slotIndex] = importedSlots[slotIndex]
                        }
                    }
                    Toast.makeText(context, "Imported ${selectedImportSlots.size} button(s)", Toast.LENGTH_SHORT).show()
                    showImportDialog = false
                    selectedImportSlots = emptySet()
                },
                onDismiss = {
                    showImportDialog = false
                    selectedImportSlots = emptySet()
                }
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Slot card — always expandable; top bar is clean, all editing happens inline
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonSlotCard(
    slotLabel: String,
    button: CustomButton?,
    isDragging: Boolean,
    elevation: Float,
    dragHandle: @Composable (Modifier) -> Unit,
    onSave: (CustomButton) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val buttonId  = button?.id ?: remember { java.util.UUID.randomUUID().toString() }
    var draftTitle     by remember(button?.id) { mutableStateOf(button?.title ?: "") }
    var draftContent   by remember(button?.id) { mutableStateOf(button?.content ?: "") }
    var draftLongPress by remember(button?.id) { mutableStateOf(button?.longPressContent ?: "") }
    var draftStartup   by remember(button?.id) { mutableStateOf(button?.onStartup ?: "") }
    var draftEnabled   by remember(button?.id) { mutableStateOf(button?.enabled ?: true) }

    var activeLuaField by remember { mutableStateOf<String?>(null) }

    val isPopulated = button != null
    val sideColor   = if (slotLabel.startsWith("L")) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.tertiary

    val cardColor by animateColorAsState(
        targetValue = when {
            isDragging  -> MaterialTheme.colorScheme.primaryContainer
            expanded    -> MaterialTheme.colorScheme.surfaceContainerHigh
            isPopulated -> MaterialTheme.colorScheme.surfaceContainer
            else        -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        label = "cardColor",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = elevation.dp, shape = RoundedCornerShape(16.dp)),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.fillMaxWidth()) {

            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Slot badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(sideColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = slotLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = sideColor,
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title or empty hint
                Column(Modifier.weight(1f)) {
                    val titleAlpha = if (isPopulated && !draftEnabled) 0.4f else 1f
                    Text(
                        text = draftTitle.ifBlank { if (isPopulated) button!!.title else "Empty slot" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isPopulated) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isPopulated)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    // Code preview — first line of tap action
                    val preview = draftContent.ifBlank { button?.content ?: "" }
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview.lines().first().take(60),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                }

                // Delete (only if a saved button exists)
                if (isPopulated) {
                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }

                // Drag handle button - always show but disable when empty
                if (isPopulated) {
                    dragHandle(
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    expanded = false
                                }
                            }
                        }
                    )
                } else {
                    // Disabled drag handle for empty slots
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag disabled",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    )
                }
            }

            // ── Expandable body ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                ButtonExpandedContent(
                    slotLabel      = slotLabel,
                    buttonId       = buttonId,
                    isPopulated    = isPopulated,
                    draftTitle     = draftTitle,
                    draftContent   = draftContent,
                    draftLongPress = draftLongPress,
                    draftStartup   = draftStartup,
                    draftEnabled   = draftEnabled,
                    onTitleChange   = { draftTitle   = it },
                    onEnabledChange = { draftEnabled = it },
                    onSave = {
                        // Build and save the button
                        onSave(
                            CustomButton(
                                id               = buttonId,
                                title            = draftTitle,
                                content          = draftContent,
                                longPressContent = draftLongPress,
                                onStartup        = draftStartup,
                                enabled          = draftEnabled,
                            )
                        )
                        expanded = false
                    },
                    onCollapse      = { expanded = false },
                    onOpenLuaEditor = { field -> activeLuaField = field },
                )
            }
        }
    }
    
    if (activeLuaField != null) {
        val fieldKey = activeLuaField!!
        val fieldLabel = when (fieldKey) {
            "content"   -> "Tap action  ·  $slotLabel"
            "longPress" -> "Long press  ·  $slotLabel"
            "startup"   -> "On startup  ·  $slotLabel"
            else        -> fieldKey
        }
        val fieldValue = when (fieldKey) {
            "content"   -> draftContent
            "longPress" -> draftLongPress
            "startup"   -> draftStartup
            else        -> ""
        }

        fun dismissAndSave() {
            // Save all drafts (including the field that was just edited) back to preferences
            if (draftTitle.isNotBlank()) {
                onSave(
                    CustomButton(
                        id               = buttonId,
                        title            = draftTitle,
                        content          = draftContent,
                        longPressContent = draftLongPress,
                        onStartup        = draftStartup,
                        enabled          = draftEnabled,
                    )
                )
            }
            activeLuaField = null
        }

        Dialog(
            onDismissRequest = { dismissAndSave() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress      = true,
                dismissOnClickOutside   = false,
                decorFitsSystemWindows  = false,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                text       = fieldLabel,
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { dismissAndSave() }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { dismissAndSave() }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                    val dialogScrollState = rememberScrollState()
                    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .imePadding()
                    ) {
                        BasicTextField(
                            value         = fieldValue,
                            onValueChange = { newCode ->
                                when (fieldKey) {
                                    "content"   -> draftContent   = newCode
                                    "longPress" -> draftLongPress = newCode
                                    "startup"   -> draftStartup   = newCode
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(dialogScrollState)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Accordion body — works for empty AND populated slots
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ButtonExpandedContent(
    slotLabel: String,
    buttonId: String,
    isPopulated: Boolean,
    draftTitle: String,
    draftContent: String,
    draftLongPress: String,
    draftStartup: String,
    draftEnabled: Boolean,
    onTitleChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCollapse: () -> Unit,
    onOpenLuaEditor: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDividerWithLabel("Button")

        // Title
        OutlinedTextField(
            value         = draftTitle,
            onValueChange = onTitleChange,
            label         = { Text("Button title *") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp),
        )

        HorizontalDividerWithLabel("Lua scripts")

        // Tap action — required
        LuaEditorEntryCard(
            label      = "Tap action *",
            code       = draftContent,
            isRequired = true,
            onClick    = { onOpenLuaEditor("content") },
        )

        // Long press
        LuaEditorEntryCard(
            label   = "Long press action",
            code    = draftLongPress,
            onClick = { onOpenLuaEditor("longPress") },
        )

        // On startup
        LuaEditorEntryCard(
            label   = "On startup",
            code    = draftStartup,
            onClick = { onOpenLuaEditor("startup") },
        )

        HorizontalDividerWithLabel("Settings")

        // Enable / Disable row
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text  = "Button enabled",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = if (draftEnabled) "Button is active in the player" else "Button is saved but hidden",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked         = draftEnabled,
                onCheckedChange = onEnabledChange,
            )
        }

        // Action row
        Row(
            horizontalArrangement = Arrangement.End,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onCollapse) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onSave,
                enabled = draftTitle.isNotBlank(),
            ) {
                Text(if (isPopulated) "Save" else "Add button")
            }
        }
    }
}

@Composable
fun HorizontalDividerWithLabel(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = " $label ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lua code entry card — tappable, shows preview
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuaEditorEntryCard(
    label: String,
    code: String,
    isRequired: Boolean = false,
    onClick: () -> Unit,
) {
    val hasCode = code.isNotBlank()
    val borderColor = if (hasCode) 
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    else 
        MaterialTheme.colorScheme.outlineVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasCode)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Code icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (hasCode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasCode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                if (hasCode) {
                    Text(
                        text = code.lines().take(3).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                } else {
                    Text(
                        text = "Tap to write Lua code…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            // Arrow
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(-90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Import Selection Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSelectionScreen(
    importedSlots: List<CustomButton?>,
    currentSlots: List<CustomButton?>,
    selectedSlots: Set<Int>,
    onSlotToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Select Buttons to Import",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Choose which buttons to import",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onConfirm,
                        enabled = selectedSlots.isNotEmpty(),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Import",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(
                items = importedSlots.mapIndexedNotNull { index, button -> 
                    button?.let { index to it }
                },
                key = { (index, _) -> "import_button_$index" }
            ) { (index, button) ->
                val side = if (index < 4) "L${index + 1}" else "R${index - 3}"
                val isSelected = selectedSlots.contains(index)
                val willOverwrite = currentSlots.getOrNull(index) != null
                val sideColor = if (index < 4) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.tertiary

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = if (isSelected) 
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else null,
                    onClick = { onSlotToggle(index) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSlotToggle(index) }
                        )
                        
                        Spacer(Modifier.width(12.dp))
                        
                        // Slot badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(sideColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = side,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = sideColor,
                            )
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = button.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            if (button.content.isNotBlank()) {
                                Text(
                                    text = button.content.lines().first().take(50),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (willOverwrite) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚠",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Will overwrite existing button",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// XML Import/Export helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildXmlExport(slots: List<CustomButton?>): String {
    val sb = StringBuilder()
    sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    sb.appendLine("<customButtons>")
    
    slots.forEachIndexed { index, button ->
        if (button != null) {
            sb.appendLine("  <button slot=\"$index\">")
            sb.appendLine("    <id>${escapeXml(button.id)}</id>")
            sb.appendLine("    <title>${escapeXml(button.title)}</title>")
            sb.appendLine("    <enabled>${button.enabled}</enabled>")
            sb.appendLine("    <content><![CDATA[${button.content}]]></content>")
            sb.appendLine("    <longPressContent><![CDATA[${button.longPressContent}]]></longPressContent>")
            sb.appendLine("    <onStartup><![CDATA[${button.onStartup}]]></onStartup>")
            sb.appendLine("  </button>")
        }
    }
    
    sb.appendLine("</customButtons>")
    return sb.toString()
}

private fun parseXmlImport(xmlContent: String): List<CustomButton?> {
    val slots = MutableList<CustomButton?>(8) { null }
    
    // Simple XML parsing using regex (for basic structure)
    val buttonPattern = """<button\s+slot="(\d+)">(.*?)</button>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val idPattern = """<id>(.*?)</id>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val titlePattern = """<title>(.*?)</title>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val enabledPattern = """<enabled>(.*?)</enabled>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val contentPattern = """<content><!\[CDATA\[(.*?)\]\]></content>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val longPressPattern = """<longPressContent><!\[CDATA\[(.*?)\]\]></longPressContent>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val startupPattern = """<onStartup><!\[CDATA\[(.*?)\]\]></onStartup>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    
    buttonPattern.findAll(xmlContent).forEach { buttonMatch ->
        val slotIndex = buttonMatch.groupValues[1].toIntOrNull() ?: return@forEach
        if (slotIndex !in 0..7) return@forEach
        
        val buttonXml = buttonMatch.groupValues[2]
        
        val id = idPattern.find(buttonXml)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: UUID.randomUUID().toString()
        val title = titlePattern.find(buttonXml)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: ""
        val enabled = enabledPattern.find(buttonXml)?.groupValues?.get(1)?.toBoolean() ?: true
        val content = contentPattern.find(buttonXml)?.groupValues?.get(1) ?: ""
        val longPress = longPressPattern.find(buttonXml)?.groupValues?.get(1) ?: ""
        val startup = startupPattern.find(buttonXml)?.groupValues?.get(1) ?: ""
        
        slots[slotIndex] = CustomButton(
            id = id,
            title = title,
            content = content,
            longPressContent = longPress,
            onStartup = startup,
            enabled = enabled
        )
    }
    
    return slots
}

private fun escapeXml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun unescapeXml(text: String): String {
    return text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
