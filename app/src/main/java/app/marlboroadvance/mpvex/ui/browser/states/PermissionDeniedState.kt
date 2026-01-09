@file:Suppress("DEPRECATION")

package app.marlboroadvance.mpvex.ui.browser.states

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R

@SuppressLint("UseKtx")
@Composable
fun PermissionDeniedState(
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showExplanationDialog by remember { mutableStateOf(false) }

  // Animated scale for the icon
  val infiniteTransition = rememberInfiniteTransition(label = "permission_icon")
  val scale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.1f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(2000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "icon_scale",
  )

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Spacer(modifier = Modifier.weight(0.5f))

      // Animated Icon with Surface
      Surface(
        modifier =
          Modifier
            .size(120.dp)
            .scale(scale),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 3.dp,
      ) {
        Icon(
          imageVector = Icons.Outlined.Warning,
          contentDescription = null,
          modifier =
            Modifier
              .padding(28.dp)
              .fillMaxSize(),
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Title
      Text(
        text = stringResource(R.string.permission_storage_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Description Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
          ),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.permission_storage_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Allow Access Button
      FilledTonalButton(
        onClick = {
          // Open All Files Access settings for Android 11+
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
              val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
              intent.data = Uri.parse("package:${context.packageName}")
              context.startActivity(intent)
            } catch (_: Exception) {
              // Fallback to general All Files Access settings
              val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
              context.startActivity(intent)
            }
          } else {
            // For older Android versions, use the regular permission request
            onRequestPermission()
          }
        },
        modifier =
          Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
      ) {
        Text(
          text = stringResource(R.string.permission_allow_access),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Why do I see this? link
      TextButton(
        onClick = { showExplanationDialog = true },
      ) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.permission_why_do_i_see_this),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
      }

      Spacer(modifier = Modifier.weight(1f))
    }
  }

  // Explanation Dialog
  if (showExplanationDialog) {
    val uriHandler = LocalUriHandler.current
    val githubUrl = "https://github.com/marlboro-advance/mpvex"

    AlertDialog(
      onDismissRequest = { showExplanationDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      },
      title = {
        Text(
          text = stringResource(R.string.permission_explanation_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Column(
          modifier =
            Modifier
              .heightIn(max = 400.dp)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.permission_explanation_p1),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Text(
            text = stringResource(R.string.permission_explanation_p2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Text(
            text = stringResource(R.string.permission_explanation_p3),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Text(
            text = stringResource(R.string.permission_explanation_p4),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          // Clickable GitHub link
          val annotatedString =
            buildAnnotatedString {
              pushStringAnnotation(
                tag = "URL",
                annotation = githubUrl,
              )
              withStyle(
                style =
                  SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                  ),
              ) {
                append(githubUrl)
              }
              pop()
            }

          ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            onClick = { offset ->
              annotatedString
                .getStringAnnotations(
                  tag = "URL",
                  start = offset,
                  end = offset,
                ).firstOrNull()
                ?.let {
                  uriHandler.openUri(it.item)
                }
            },
          )

          Text(
            text = stringResource(R.string.permission_explanation_p5),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
          )
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = { showExplanationDialog = false },
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(stringResource(R.string.got_it))
        }
      },
      shape = RoundedCornerShape(24.dp),
    )
  }
}
