package app.marlboroadvance.mpvex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.marlboroadvance.mpvex.R

// Google Sans Flex font family (variable font supporting weights 100-900)
val GoogleSansFlex = FontFamily(
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Thin,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.ExtraLight,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Light,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Medium,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.SemiBold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Bold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.ExtraBold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Black,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.Thin,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.ExtraLight,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.Light,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.Normal,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.Medium,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.SemiBold,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.Bold,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.ExtraBold,
    style = FontStyle.Italic,
  ),
  Font(
    resId = R.font.google_sans_flex_italic,
    weight = FontWeight.Black,
    style = FontStyle.Italic,
  ),
)

// Use Google Sans Flex typography app-wide
val AppTypography = Typography().run {
  copy(
    displayLarge = displayLarge.copy(fontFamily = GoogleSansFlex),
    displayMedium = displayMedium.copy(fontFamily = GoogleSansFlex),
    displaySmall = displaySmall.copy(fontFamily = GoogleSansFlex),
    headlineLarge = headlineLarge.copy(fontFamily = GoogleSansFlex),
    headlineMedium = headlineMedium.copy(fontFamily = GoogleSansFlex),
    headlineSmall = headlineSmall.copy(fontFamily = GoogleSansFlex),
    titleLarge = titleLarge.copy(fontFamily = GoogleSansFlex),
    titleMedium = titleMedium.copy(fontFamily = GoogleSansFlex),
    titleSmall = titleSmall.copy(fontFamily = GoogleSansFlex),
    bodyLarge = bodyLarge.copy(fontFamily = GoogleSansFlex),
    bodyMedium = bodyMedium.copy(fontFamily = GoogleSansFlex),
    bodySmall = bodySmall.copy(fontFamily = GoogleSansFlex),
    labelLarge = labelLarge.copy(fontFamily = GoogleSansFlex),
    labelMedium = labelMedium.copy(fontFamily = GoogleSansFlex),
    labelSmall = labelSmall.copy(fontFamily = GoogleSansFlex),
  )
}
