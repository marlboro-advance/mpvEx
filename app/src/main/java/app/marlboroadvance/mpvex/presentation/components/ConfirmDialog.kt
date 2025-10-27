package app.marlboroadvance.mpvex.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
  title: String,
  subtitle: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BasicAlertDialog(
    onCancel,
    modifier = modifier,
  ) {
    Surface(
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor,
      tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
      Column(
        modifier = Modifier.padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Text(
          title,
          style = MaterialTheme.typography.headlineMedium,
          color = AlertDialogDefaults.titleContentColor,
        )
        Text(
          subtitle,
          color = AlertDialogDefaults.textContentColor,
        )
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onCancel) {
            Text(stringResource(R.string.generic_cancel))
          }
          TextButton(onConfirm) {
            Text(stringResource(R.string.generic_confirm))
          }
        }
      }
    }
  }
}
