package app.marlboroadvance.mpvex.ui.preferences.folderpicker

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
object FolderPickerScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current

    val viewModel: FolderPickerViewModel = viewModel(
      factory = FolderPickerViewModel.factory(context.applicationContext as Application)
    )

    val currentPath by viewModel.currentPath.collectAsState()
    val directories by viewModel.directories.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    // Intercept back key: ascend directory hierarchy first; exit when at root
    BackHandler {
      val didAscend = viewModel.navigateUp()
      if (!didAscend) {
        backstack.removeLastOrNull()
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = stringResource(R.string.pref_folders_select_folders),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (selectedPaths.isNotEmpty()) {
                Text(
                  text = "${selectedPaths.size} selected",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          },
          navigationIcon = {
            IconButton(
              onClick = {
                val didAscend = viewModel.navigateUp()
                if (!didAscend) {
                  backstack.removeLastOrNull()
                }
              }
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.close),
              )
            }
          },
          actions = {
            IconButton(onClick = { showMenu = true }) {
              Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
              )
            }
            DropdownMenu(
              expanded = showMenu,
              onDismissRequest = { showMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.select_all)) },
                leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                onClick = {
                  viewModel.selectAllInCurrentDirectory()
                  showMenu = false
                },
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.deselect_all)) },
                leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
                onClick = {
                  viewModel.deselectAllInCurrentDirectory()
                  showMenu = false
                },
              )
              if (selectedPaths.isNotEmpty()) {
                DropdownMenuItem(
                  text = { Text("Clear all selections") },
                  leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
                  onClick = {
                    viewModel.clearAllSelections()
                    showMenu = false
                  },
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
        )
      },
      bottomBar = {
        // Persistent Bottom Action Button
        Surface(
          modifier = Modifier.fillMaxWidth(),
          tonalElevation = 3.dp,
          shadowElevation = 8.dp,
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 16.dp, vertical = 12.dp),
          ) {
            Button(
              onClick = {
                viewModel.commitSelectedToBlacklist { count ->
                  Toast.makeText(
                    context,
                    "$count folder(s) added to blacklist",
                    Toast.LENGTH_SHORT
                  ).show()
                  backstack.removeLastOrNull()
                }
              },
              enabled = selectedPaths.isNotEmpty() && !isSubmitting,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
              shape = MaterialTheme.shapes.medium,
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            ) {
              if (isSubmitting) {
                CircularProgressIndicator(
                  modifier = Modifier.size(24.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val label = if (selectedPaths.isEmpty()) {
                  "Add to Blacklist"
                } else {
                  "Add to Blacklist (${selectedPaths.size})"
                }
                Text(
                  text = label,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        // Breadcrumb Navigation Row
        BreadcrumbBar(
          breadcrumbs = breadcrumbs,
          onNavigate = { path -> viewModel.navigateTo(path) },
        )

        // Error Banner if accessible issue occurs
        if (errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        // Main Directory List
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        ) {
          when {
            isLoading -> {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            }
            directories.isEmpty() && errorMessage == null -> {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.padding(32.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.FolderOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                  )
                  Spacer(modifier = Modifier.height(16.dp))
                  Text(
                    text = stringResource(R.string.pref_folders_no_folders),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "No subdirectories in this location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                  )
                }
              }
            }
            else -> {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(directories, key = { it.path }) { item ->
                  val isSelected = selectedPaths.contains(item.path)

                  FolderRow(
                    item = item,
                    isSelected = isSelected,
                    onNavigate = { viewModel.navigateTo(item.path) },
                    onToggleSelect = { viewModel.toggleSelection(item.path) },
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun BreadcrumbBar(
    breadcrumbs: List<app.marlboroadvance.mpvex.domain.browser.PathComponent>,
    onNavigate: (String) -> Unit,
  ) {
    val scrollState = rememberScrollState()

    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(scrollState)
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        breadcrumbs.forEachIndexed { index, component ->
          val isLast = index == breadcrumbs.lastIndex

          AssistChip(
            onClick = { onNavigate(component.fullPath) },
            label = {
              Text(
                text = component.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
              )
            },
            colors = AssistChipDefaults.assistChipColors(
              containerColor = if (isLast) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surface
              },
              labelColor = if (isLast) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurface
              },
            ),
            border = null,
          )

          if (!isLast) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }
  }

  @Composable
  private fun FolderRow(
    item: DirectoryItem,
    isSelected: Boolean,
    onNavigate: () -> Unit,
    onToggleSelect: () -> Unit,
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onNavigate),
      colors = CardDefaults.cardColors(
        containerColor = if (isSelected) {
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else if (item.isAlreadyBlacklisted) {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        } else {
          MaterialTheme.colorScheme.surfaceVariant
        },
      ),
      shape = MaterialTheme.shapes.medium,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Independent Checkbox on the left
        Checkbox(
          checked = isSelected,
          onCheckedChange = { onToggleSelect() },
          modifier = Modifier.size(36.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Folder Icon
        Icon(
          imageVector = Icons.Default.Folder,
          contentDescription = null,
          tint = if (item.isAlreadyBlacklisted) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.size(32.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Folder Info (Title & Path)
        Column(
          modifier = Modifier.weight(1f),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = item.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (item.isAlreadyBlacklisted) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "• Blacklisted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          Text(
            text = item.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // Tap Arrow indicating navigability
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
