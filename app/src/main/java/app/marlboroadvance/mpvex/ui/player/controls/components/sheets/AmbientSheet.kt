package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Vignette
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.presentation.components.SliderItem
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.theme.spacing

@Composable
fun AmbientSheet(
    viewModel: PlayerViewModel,
    onDismissRequest: () -> Unit
) {
    // ── Collect all state flows ──────────────────────────────────────────────
    val blurSamples      by viewModel.ambientBlurSamples.collectAsState()
    val maxRadius        by viewModel.ambientMaxRadius.collectAsState()
    val glowIntensity    by viewModel.ambientGlowIntensity.collectAsState()
    val satBoost         by viewModel.ambientSatBoost.collectAsState()
    val vignetteStrength by viewModel.ambientVignetteStrength.collectAsState()
    val warmth           by viewModel.ambientWarmth.collectAsState()
    val fadeCurve        by viewModel.ambientFadeCurve.collectAsState()
    val opacity          by viewModel.ambientOpacity.collectAsState()
    val bezelDepth       by viewModel.ambientBezelDepth.collectAsState()
    val ditherNoise      by viewModel.ambientDitherNoise.collectAsState()

    // Profile detection (all conditions must match for the button to highlight)
    val isFast = blurSamples == 16 && maxRadius == 0.17f &&
                 glowIntensity == 1.4f && satBoost == 1.2f &&
                 fadeCurve == 1.6f && opacity == 1.0f
    val isBalanced = blurSamples == 24 && maxRadius == 0.20f &&
                     glowIntensity == 1.45f && satBoost == 1.25f &&
                     fadeCurve == 1.7f && opacity == 1.0f
    val isHQ = blurSamples == 48 && maxRadius == 0.25f &&
               glowIntensity == 1.5f && satBoost == 1.3f &&
               fadeCurve == 1.8f && opacity == 1.0f

    val configuration = LocalConfiguration.current
    val customMaxHeight = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        (configuration.screenHeightDp * 0.5f).dp
    } else {
        null
    }

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        customMaxHeight = customMaxHeight
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {

            // ── Title ────────────────────────────────────────────────────────
            Text(
                text = "Ambience Mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )

            // ── Quality Presets ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { viewModel.applyAmbientProfileFast() },
                    modifier = Modifier.weight(1f),
                    colors = if (isFast) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) else ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text("Fast", fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = { viewModel.applyAmbientProfileBalanced() },
                    modifier = Modifier.weight(1f),
                    colors = if (isBalanced) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) else ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text("Balanced", fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = { viewModel.applyAmbientProfileHighQuality() },
                    modifier = Modifier.weight(1f),
                    colors = if (isHQ) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) else ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text("HQ", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            // ── Section: Glow ────────────────────────────────────────────────
            SectionLabel("Glow")

            SliderItem(
                label = "Blur Samples",
                valueText = "$blurSamples",
                value = blurSamples,
                onChange = { viewModel.updateAmbientParams(blurSamples = it) },
                min = 5,
                max = 64,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.BlurOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            SliderItem(
                label = "Spread",
                valueText = "%.2f".format(maxRadius),
                value = maxRadius,
                onChange = { viewModel.updateAmbientParams(maxRadius = it) },
                min = 0.05f,
                max = 0.80f,
                steps = 75,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Gradient,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            SliderItem(
                label = "Glow Intensity",
                valueText = "%.1f".format(glowIntensity),
                value = glowIntensity,
                onChange = { viewModel.updateAmbientParams(glowIntensity = it) },
                min = 0.5f,
                max = 3.0f,
                steps = 25,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Brightness6,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            SliderItem(
                label = "Fade Curve",
                valueText = "%.1f".format(fadeCurve),
                value = fadeCurve,
                onChange = { viewModel.updateAmbientParams(fadeCurve = it) },
                min = 0.5f,
                max = 3.0f,
                steps = 25,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            // ── Section: Color ───────────────────────────────────────────────
            SectionLabel("Color")

            SliderItem(
                label = "Saturation",
                valueText = "%.1f".format(satBoost),
                value = satBoost,
                onChange = { viewModel.updateAmbientParams(satBoost = it) },
                min = 0.0f,
                max = 3.0f,
                steps = 30,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            SliderItem(
                label = "Warmth",
                valueText = if (warmth == 0f) "0" else "%.2f".format(warmth),
                value = warmth,
                onChange = { viewModel.updateAmbientParams(warmth = it) },
                min = -1.0f,
                max = 1.0f,
                steps = 40,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Thermostat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            // ── Section: Compositing ─────────────────────────────────────────
            SectionLabel("Compositing")

            SliderItem(
                label = "Opacity",
                valueText = "%.2f".format(opacity),
                value = opacity,
                onChange = { viewModel.updateAmbientParams(opacity = it) },
                min = 0.0f,
                max = 1.0f,
                steps = 20,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Opacity,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            SliderItem(
                label = "Vignette",
                valueText = "%.1f".format(vignetteStrength),
                value = vignetteStrength,
                onChange = { viewModel.updateAmbientParams(vignetteStrength = it) },
                min = 0.0f,
                max = 1.0f,
                steps = 10,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Vignette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            // ── Section: Advanced ────────────────────────────────────────────
            SectionLabel("Advanced")

            SliderItem(
                label = "Bezel",
                valueText = "%.3f".format(bezelDepth),
                value = bezelDepth,
                onChange = { viewModel.updateAmbientParams(bezelDepth = it) },
                min = 0.0f,
                max = 0.1f,
                steps = 50,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.RoundedCorner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            SliderItem(
                label = "Dither",
                valueText = "%.3f".format(ditherNoise),
                value = ditherNoise,
                onChange = { viewModel.updateAmbientParams(ditherNoise = it) },
                min = 0.0f,
                max = 0.05f,
                steps = 50,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Grain,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── Helper: section label ────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = MaterialTheme.spacing.medium,
            vertical = 2.dp,
        ),
    )
}
