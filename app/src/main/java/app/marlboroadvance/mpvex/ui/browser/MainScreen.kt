package app.marlboroadvance.mpvex.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.folderlist.FolderListScreen
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistScreen
import app.marlboroadvance.mpvex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import kotlinx.serialization.Serializable

@Serializable
object MainScreen : Screen {
  // Use a companion object to store state more persistently
  private var persistentSelectedTab: Int = 0

  @Composable
  override fun Content() {
    var selectedTab by androidx.compose.runtime.remember {
      androidx.compose.runtime.mutableIntStateOf(persistentSelectedTab)
    }

    // Update persistent state whenever tab changes
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
      android.util.Log.d("MainScreen", "selectedTab changed to: $selectedTab (was ${persistentSelectedTab})")
      persistentSelectedTab = selectedTab
    }

    val tabs = listOf(
      BottomNavItem(
        label = "Folders",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
      ),
      BottomNavItem(
        label = "Recent",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
      ),
      BottomNavItem(
        label = "Playlist",
        selectedIcon = Icons.Filled.PlaylistPlay,
        unselectedIcon = Icons.Outlined.PlaylistPlay,
      ),
      BottomNavItem(
        label = "Network",
        selectedIcon = Icons.Filled.Wifi,
        unselectedIcon = Icons.Outlined.Wifi,
      ),
    )

    // Use Scaffold only for bottom bar, let nested screens handle their own top bars and padding
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        NavigationBar {
          tabs.forEachIndexed { index, item ->
            NavigationBarItem(
              icon = {
                Icon(
                  imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                  contentDescription = item.label,
                )
              },
              label = { Text(item.label) },
              selected = selectedTab == index,
              onClick = {
                android.util.Log.d(
                  "MainScreen",
                  "onClick triggered for tab $index (${item.label}) from tab $selectedTab",
                )
                val stackTrace = Thread.currentThread().stackTrace
                android.util.Log.d("MainScreen", "Stack trace: ${stackTrace.take(10).joinToString("\n")}")
                selectedTab = index
              },
            )
          }
        }
      },
    ) { paddingValues ->
      // Each screen handles its own bottom padding for the navigation bar
      Box(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
          0 -> FolderListScreen.Content()
          1 -> RecentlyPlayedScreen.Content()
          2 -> PlaylistScreen.Content()
          3 -> NetworkStreamingScreen.Content()
        }
      }
    }
  }

  private data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
  )
}
