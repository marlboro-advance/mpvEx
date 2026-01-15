package app.marlboroadvance.mpvex.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfirmDialog(
  title: String,
  subtitle: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  customContent: (@Composable () -> Unit)? = null,
) {
  BasicAlertDialog(
    onCancel,
    modifier = modifier,
  ) {
    Surface(
      shape = MaterialTheme.shapes.extraLarge,
      color = AlertDialogDefaults.containerColor,
      tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
      Column(
        modifier = Modifier.padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Text(
          title,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = AlertDialogDefaults.titleContentColor,
        )
        Text(
          subtitle,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = AlertDialogDefaults.textContentColor,
        )
        if (customContent != null) {
          customContent()
        }
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onCancel,
            shape = MaterialTheme.shapes.extraLarge,
          ) {
            Text(
              stringResource(R.string.generic_cancel),
              fontWeight = FontWeight.Medium,
            )
          }
          TextButton(
            onConfirm,
            shape = MaterialTheme.shapes.extraLarge,
          ) {
            Text(
              stringResource(R.string.generic_confirm),
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}
