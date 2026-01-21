package app.marlboroadvance.mpvex.ui.browser.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.BuildConfig
import kotlinx.coroutines.launch

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserNavigationDrawer(
    drawerState: DrawerState,
    onHomeClick: () -> Unit,
    onRecentlyPlayedClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onNetworkStreamingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentRoute: String? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val onClickItem: (() -> Unit) -> Unit = { click ->
        scope.launch {
            drawerState.close()
            click()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "mpvEx",
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} By marlboro-advance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                NavItem(Icons.Filled.Home, "Home", currentRoute == "home") { onClickItem(onHomeClick) }
                NavItem(Icons.Filled.History, "Recently Played", currentRoute == "recently_played") { onClickItem(onRecentlyPlayedClick) }
                NavItem(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlists", currentRoute == "playlists") { onClickItem(onPlaylistsClick) }
                NavItem(Icons.Filled.Wifi, "Network Streaming", currentRoute == "network_streaming") { onClickItem(onNetworkStreamingClick) }
                NavItem(Icons.Filled.Settings, "Settings", currentRoute == "settings") { onClickItem(onSettingsClick) }
                Spacer(Modifier.height(12.dp))
            }
        },
        content = content
    )
}
