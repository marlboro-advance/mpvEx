package app.gyrolet.mpvrx.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.preferences.AiPreferences
import app.gyrolet.mpvrx.preferences.AiProvider
import app.gyrolet.mpvrx.repository.ai.AiService
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.compose.koinInject

private enum class RenamePhase { IDLE, GENERATING, PREVIEW }

/** State for one rename preview row */
private data class PreviewItem(
  val video: Video,
  val suggestedName: String,
  val extension: String?,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BulkAiRenameDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (Map<Video, String>) -> Unit,
  selectedVideos: List<Video>,
) {
  if (!isOpen) return

  val scope = rememberCoroutineScope()
  val aiService = koinInject<AiService>()
  val aiPreferences = koinInject<AiPreferences>()

  var phase by remember { mutableStateOf(RenamePhase.IDLE) }
  var errorMessage by remember { mutableStateOf("") }
  var previewItems by remember { mutableStateOf<List<PreviewItem>>(emptyList()) }

  // Per-row: checked state (deselect from rename) and edited name
  val checkedState = remember { mutableStateMapOf<String, Boolean>() }
  val editedNames = remember { mutableStateMapOf<String, String>() }

  val canUseAi = aiPreferences.enabled.get() && aiPreferences.renameWithAi.get()

  // --- Phase 1: ask AI for suggested names ---
  fun generatePreviews() {
    scope.launch {
      phase = RenamePhase.GENERATING
      errorMessage = ""
      previewItems = emptyList()
      checkedState.clear()
      editedNames.clear()

      val provider = aiPreferences.provider.get()
      // LOCAL model is single-threaded; cap concurrency to 1
      val semaphore = if (provider == AiProvider.LOCAL) Semaphore(1) else Semaphore(3)

      val results = mutableListOf<PreviewItem>()
      var failCount = 0

      val deferred = selectedVideos.map { video ->
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
          try {
            semaphore.withPermit {
              val base = video.displayName.substringBeforeLast('.')
              val ext = video.displayName.substringAfterLast('.', "").let { if (it.isNotEmpty()) ".$it" else null }
              aiService.renameWithAi(base, ext)
                .onSuccess { name ->
                  synchronized(results) {
                    results.add(PreviewItem(video, name.removeSuffix(ext ?: ""), ext))
                  }
                }
                .onFailure { synchronized(results) { failCount++ } }
            }
          } catch (e: Exception) {
            synchronized(results) { failCount++ }
          }
        }
      }
      deferred.forEach { it.join() }

      if (results.isEmpty()) {
        errorMessage = "AI rename failed for all ${failCount} items. Check your model/API key."
        phase = RenamePhase.IDLE
        return@launch
      }

      // Sort preview in same order as input
      val ordered = selectedVideos.mapNotNull { v -> results.find { it.video == v } }
      ordered.forEach { item ->
        checkedState[item.video.id.toString()] = true
        editedNames[item.video.id.toString()] = item.suggestedName
      }
      previewItems = ordered
      phase = RenamePhase.PREVIEW
    }
  }

  // --- Phase 2: apply confirmed renames ---
  fun applyRenames() {
    val updates = mutableMapOf<Video, String>()
    previewItems.forEach { item ->
      val key = item.video.id.toString()
      if (checkedState[key] == true) {
        val name = editedNames[key] ?: item.suggestedName
        val fullName = if (item.extension != null) "$name${item.extension}" else name
        updates[item.video] = fullName
      }
    }
    onConfirm(updates)
    onDismiss()
  }

  AlertDialog(
    onDismissRequest = { if (phase != RenamePhase.GENERATING) onDismiss() },
    title = {
      Text(
        text = when (phase) {
          RenamePhase.IDLE -> "Bulk AI Rename"
          RenamePhase.GENERATING -> "Generating Previews…"
          RenamePhase.PREVIEW -> "Review & Confirm"
        },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
          !canUseAi -> Text(
            "AI Rename is disabled. Enable it in Settings → AI Integration.",
            color = MaterialTheme.colorScheme.error,
          )

          phase == RenamePhase.IDLE -> Text(
            "AI will suggest new names for ${selectedVideos.size} selected file(s). " +
              "You can review and edit each name before confirming.",
            style = MaterialTheme.typography.bodyMedium,
          )

          phase == RenamePhase.GENERATING -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
          ) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
            Text(
              "Processing ${selectedVideos.size} file(s)…",
              style = MaterialTheme.typography.bodyMedium,
            )
          }

          phase == RenamePhase.PREVIEW -> {
            if (errorMessage.isNotBlank()) {
              Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }

            val checkedCount = checkedState.values.count { it }
            Text(
              "$checkedCount / ${previewItems.size} file(s) selected to rename",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(4.dp))

            LazyColumn(
              modifier = Modifier.height(320.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(previewItems) { item ->
                val key = item.video.id.toString()
                val checked = checkedState[key] ?: true
                val editedName = editedNames[key] ?: item.suggestedName

                Column {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Checkbox(
                      checked = checked,
                      onCheckedChange = { checkedState[key] = it },
                    )
                    Text(
                      text = item.video.displayName,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                      modifier = Modifier.weight(1f),
                    )
                  }

                  if (checked) {
                    OutlinedTextField(
                      value = editedName,
                      onValueChange = { editedNames[key] = it },
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                      label = { Text("New name") },
                      suffix = { item.extension?.let { Text(it, color = MaterialTheme.colorScheme.outline) } },
                      singleLine = true,
                      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                      shape = MaterialTheme.shapes.medium,
                    )
                  }

                  HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                  )
                }
              }
            }
          }
        }

        if (errorMessage.isNotBlank() && phase == RenamePhase.IDLE) {
          Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      when (phase) {
        RenamePhase.IDLE ->
          Button(
            onClick = { generatePreviews() },
            enabled = canUseAi && selectedVideos.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = MaterialTheme.shapes.extraLarge,
          ) {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Generate Previews", fontWeight = FontWeight.Bold)
          }

        RenamePhase.GENERATING -> {}

        RenamePhase.PREVIEW -> {
          val checkedCount = checkedState.values.count { it }
          Button(
            onClick = { applyRenames() },
            enabled = checkedCount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = MaterialTheme.shapes.extraLarge,
          ) {
            Icon(imageVector = app.gyrolet.mpvrx.ui.icons.Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Rename $checkedCount File(s)", fontWeight = FontWeight.Bold)
          }
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          if (phase == RenamePhase.PREVIEW) phase = RenamePhase.IDLE
          else onDismiss()
        },
        enabled = phase != RenamePhase.GENERATING,
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text(if (phase == RenamePhase.PREVIEW) "Back" else "Cancel", fontWeight = FontWeight.Medium)
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}
