package app.sfsakhawat999.mpvrex.presentation.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun ExposedDropDownMenu() {
  ExposedDropdownMenuBox(
    true,
    {},
  ) {
    DropdownMenu(true, {}) {
      DropdownMenuItem(
        { Text("") },
        {},
      )
    }
  }
}
