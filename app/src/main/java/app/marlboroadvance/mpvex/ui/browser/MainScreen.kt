package app.marlboroadvance.mpvex.ui.browser

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    var selectedTab by remember {
      mutableIntStateOf(persistentSelectedTab)
    }

    // Update persistent state whenever tab changes
    LaunchedEffect(selectedTab) {
      android.util.Log.d("MainScreen", "selectedTab changed to: $selectedTab (was ${persistentSelectedTab})")
      persistentSelectedTab = selectedTab
    }

    // Define items for the navigation bar
    val items = listOf("Folders", "Recent", "Playlist", "Network")
    val selectedIcons = listOf(
        Icons.Filled.Folder, 
        Icons.Filled.History,
        Icons.AutoMirrored.Filled.PlaylistPlay,
        Icons.Filled.Wifi
    )
    val unselectedIcons = listOf(
        Icons.Outlined.Folder, 
        Icons.Outlined.History,
        Icons.AutoMirrored.Outlined.PlaylistPlay,
        Icons.Outlined.Wifi
    )

    // Use Scaffold only for bottom bar, let nested screens handle their own top bars and padding
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (selectedTab == index) selectedIcons[index] else unselectedIcons[index],
                            contentDescription = item,
                        )
                    },
                    label = { Text(item) },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                )
            }
        }
      },
    ) { _ ->
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

}
