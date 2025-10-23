package app.marlboroadvance.mpvex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import app.marlboroadvance.mpvex.R

val provider = GoogleFont.Provider(
  providerAuthority = "com.google.android.gms.fonts",
  providerPackage = "com.google.android.gms",
  certificates = R.array.com_google_android_gms_fonts_certs,
)

val robotoFontFamily = FontFamily(
  Font(
    googleFont = GoogleFont("Roboto"),
    fontProvider = provider,
  ),
)

// Default Material 3 typography values
val baseline = Typography()

val AppTypography = Typography(
  displayLarge = baseline.displayLarge.copy(fontFamily = robotoFontFamily),
  displayMedium = baseline.displayMedium.copy(fontFamily = robotoFontFamily),
  displaySmall = baseline.displaySmall.copy(fontFamily = robotoFontFamily),
  headlineLarge = baseline.headlineLarge.copy(fontFamily = robotoFontFamily),
  headlineMedium = baseline.headlineMedium.copy(fontFamily = robotoFontFamily),
  headlineSmall = baseline.headlineSmall.copy(fontFamily = robotoFontFamily),
  titleLarge = baseline.titleLarge.copy(fontFamily = robotoFontFamily),
  titleMedium = baseline.titleMedium.copy(fontFamily = robotoFontFamily),
  titleSmall = baseline.titleSmall.copy(fontFamily = robotoFontFamily),
  bodyLarge = baseline.bodyLarge.copy(fontFamily = robotoFontFamily),
  bodyMedium = baseline.bodyMedium.copy(fontFamily = robotoFontFamily),
  bodySmall = baseline.bodySmall.copy(fontFamily = robotoFontFamily),
  labelLarge = baseline.labelLarge.copy(fontFamily = robotoFontFamily),
  labelMedium = baseline.labelMedium.copy(fontFamily = robotoFontFamily),
  labelSmall = baseline.labelSmall.copy(fontFamily = robotoFontFamily),
)
