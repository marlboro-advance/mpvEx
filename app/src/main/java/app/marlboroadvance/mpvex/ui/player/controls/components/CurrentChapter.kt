package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.theme.spacing
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils

@Composable
fun CurrentChapter(
  chapter: Segment,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  Surface(
    modifier =
      modifier
        .clip(RoundedCornerShape(50))
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 5.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
  ) {
    AnimatedContent(
      targetState = chapter,
      modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.small),
      transitionSpec = {
        if (targetState.start > initialState.start) {
          (slideInVertically { height -> height } + fadeIn())
            .togetherWith(slideOutVertically { height -> -height } + fadeOut())
        } else {
          (slideInVertically { height -> -height } + fadeIn())
            .togetherWith(slideOutVertically { height -> height } + fadeOut())
        }.using(
          SizeTransform(clip = false),
        )
      },
      label = "Chapter",
    ) { currentChapter ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
      ) {
        Icon(
          imageVector = Icons.Default.Bookmarks,
          contentDescription = null,
          modifier =
            Modifier
              .padding(end = MaterialTheme.spacing.extraSmall)
              .size(16.dp),
        )
        Text(
          text = Utils.prettyTime(currentChapter.start.toInt()),
          fontWeight = FontWeight.ExtraBold,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Clip,
          color = MaterialTheme.colorScheme.tertiary,
        )
        currentChapter.name.let {
          Text(
            text = Typography.bullet.toString(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Clip,
          )
          Text(
            text = it,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
