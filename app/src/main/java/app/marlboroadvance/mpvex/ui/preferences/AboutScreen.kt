package app.marlboroadvance.mpvex.ui.preferences

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.ImageView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.crash.CrashActivity.Companion.collectDeviceInfo
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.serialization.Serializable

@Serializable
object AboutScreen : Screen {
  @Suppress("DEPRECATION")
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val clipboardManager = LocalClipboardManager.current
    val packageManager: PackageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName?.substringBefore('-') ?: packageInfo.versionName
    val buildType = BuildConfig.BUILD_TYPE
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.pref_about_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { paddingValues ->
      val cs = MaterialTheme.colorScheme
      val colorPrimary = cs.primaryContainer
      val colorTertiary = cs.tertiaryContainer
      val transition = rememberInfiniteTransition()
      val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 5000),
            repeatMode = RepeatMode.Reverse,
          ),
      )
      val cornerRadius = 28.dp

      Column(
        modifier =
          Modifier
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
      ) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(28.dp),
          color = Color.Transparent,
          shadowElevation = 0.dp,
          tonalElevation = 0.dp,
        ) {
          Box(
            modifier =
              Modifier
                .drawWithCache {
                  val cx = size.width - size.width * fraction
                  val cy = size.height * fraction

                  val gradient =
                    Brush.radialGradient(
                      colors = listOf(colorPrimary, colorTertiary),
                      center = Offset(cx, cy),
                      radius = 800f,
                    )

                  onDrawBehind {
                    drawRoundRect(
                      brush = gradient,
                      cornerRadius =
                        CornerRadius(
                          cornerRadius.toPx(),
                          cornerRadius.toPx(),
                        ),
                    )
                  }
                }
                .padding(20.dp),
          ) {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp)) {
                  AndroidView(
                    modifier = Modifier.matchParentSize(),
                    factory = { ctx ->
                      ImageView(ctx).apply {
                        setImageResource(R.mipmap.ic_launcher)
                      }
                    },
                  )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "mpvExtended",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer,
                  )
                  Spacer(Modifier.height(4.dp))
                  Text(
                    text = "v$versionName $buildType",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                  )
                }
              }

              Spacer(modifier = Modifier.height(20.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                val btnContainer = cs.primary
                val btnContent = cs.onPrimary
                Button(
                  onClick = { backstack.add(LibrariesScreen) },
                  modifier =
                    Modifier
                      .weight(1f)
                      .height(56.dp),
                  shape = RoundedCornerShape(16.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = btnContainer,
                      contentColor = btnContent,
                    ),
                ) {
                  Text(
                    text = stringResource(id = R.string.pref_about_oss_libraries),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                }

                Button(
                  onClick = {
                    context.startActivity(
                      Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.github_repo_url).toUri(),
                      ),
                    )
                  },
                  modifier =
                    Modifier
                      .weight(1f)
                      .height(56.dp),
                  shape = RoundedCornerShape(16.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = btnContainer,
                      contentColor = btnContent,
                    ),
                ) {
                  Text(
                    text = "GitHub",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }

              Spacer(modifier = Modifier.height(20.dp))

              Column(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      clipboardManager.setText(AnnotatedString(collectDeviceInfo()))
                    },
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(bottom = 8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Device Info",
                    modifier = Modifier.size(20.dp),
                    tint = cs.onPrimaryContainer,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Device Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer,
                  )
                }
                Text(
                  text = collectDeviceInfo(),
                  style = MaterialTheme.typography.bodySmall,
                  color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                )
              }
            }
          }
        }

        Spacer(Modifier.height(16.dp))

        // Donate Section
        Text(
          text = stringResource(id = R.string.pref_about_donate_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = cs.onSurface,
          modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        // Ko-fi
        Surface(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable {
                context.startActivity(
                  Intent(
                    Intent.ACTION_VIEW,
                    context.getString(R.string.pref_about_donate_kofi_url).toUri(),
                  ),
                )
              },
          shape = RoundedCornerShape(16.dp),
          color = cs.surfaceContainerHigh,
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.MonetizationOn,
              contentDescription = null,
              modifier = Modifier.size(24.dp),
              tint = cs.onSurface,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.pref_about_donate_kofi),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
              )
              Text(
                text = stringResource(id = R.string.pref_about_donate_kofi_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))

        // PayPal
        Surface(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable {
                context.startActivity(
                  Intent(
                    Intent.ACTION_VIEW,
                    context.getString(R.string.pref_about_donate_paypal_url).toUri(),
                  ),
                )
              },
          shape = RoundedCornerShape(16.dp),
          color = cs.surfaceContainerHigh,
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.AccountBalance,
              contentDescription = null,
              modifier = Modifier.size(24.dp),
              tint = cs.onSurface,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.pref_about_donate_paypal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
              )
              Text(
                text = stringResource(id = R.string.pref_about_donate_paypal_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))

        // UPI
        Surface(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable {
                clipboardManager.setText(
                  AnnotatedString(context.getString(R.string.pref_about_donate_upi_id)),
                )
              },
          shape = RoundedCornerShape(16.dp),
          color = cs.surfaceContainerHigh,
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.CurrencyRupee,
              contentDescription = null,
              modifier = Modifier.size(24.dp),
              tint = cs.onSurface,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.pref_about_donate_upi),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
              )
              Text(
                text = stringResource(id = R.string.pref_about_donate_upi_id),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

@Suppress("DEPRECATION")
@Serializable
object LibrariesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.pref_about_oss_libraries)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { paddingValues ->
      LibrariesContainer(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(paddingValues),
      )
    }
  }
}
