package app.marlboroadvance.mpvex.presentation.preferences

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

@Composable
fun MultiChoiceSegmentedButton(
    choices: ImmutableList<String>,
    selectedIndices: ImmutableList<Int>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiChoiceSegmentedButtonRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
    ) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                checked = selectedIndices.contains(index),
                onCheckedChange = { onClick(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
            ) {
                Text(text = choice)
            }
        }
    }
}
