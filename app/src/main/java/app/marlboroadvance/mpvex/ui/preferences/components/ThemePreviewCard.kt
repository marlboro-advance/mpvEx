package app.marlboroadvance.mpvex.ui.preferences.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.ui.theme.AppTheme

/**
 * A theme preview card that displays a mini preview of the app UI with the theme's colors.
 * Inspired by Aniyomi's theme picker design.
 */
@Composable
fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = if (isDarkMode) theme.getDarkColorScheme() else theme.getLightColorScheme()
    
    // Use the current MaterialTheme primary for selection to ensure visibility
    val selectionColor = MaterialTheme.colorScheme.primary
    
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "border"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) selectionColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = tween(durationMillis = 200),
        label = "elevation"
    )
    
    Column(
        modifier = modifier
            .width(100.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Theme preview card
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 140.dp)
                .shadow(
                    elevation = elevation,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                    spotColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                )
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.surface)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
        ) {
            // Inner content
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(if (isSelected) 3.dp else 1.dp)
                    .clip(RoundedCornerShape(if (isSelected) 9.dp else 11.dp))
                    .background(colorScheme.background)
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top bar simulation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant)
                )
                
                // Middle section - card with toggle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Toggle representation
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorScheme.primary)
                        )
                        // Accent indicator
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colorScheme.tertiary)
                        )
                    }
                }
                
                // Bottom section - button simulation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant)
                )
                
                // Bottom accent dot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorScheme.secondary)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Theme name
        Text(
            text = stringResource(theme.titleRes),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
