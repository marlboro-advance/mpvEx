package app.marlboroadvance.mpvex.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection

internal sealed interface AppIconSource

@Immutable
internal data class DrawableAppIconSource(
  @DrawableRes val resId: Int,
) : AppIconSource

@Immutable
internal data class VectorAppIconSource(
  val imageVector: ImageVector,
) : AppIconSource

@Immutable
class AppIcon private constructor(
  private val ltrSource: AppIconSource,
  private val rtlSource: AppIconSource? = null,
  val mirrorInRtl: Boolean = false,
) {
  constructor(
    @DrawableRes ltrResId: Int,
    @DrawableRes rtlResId: Int? = null,
    mirrorInRtl: Boolean = false,
  ) : this(
    ltrSource = DrawableAppIconSource(ltrResId),
    rtlSource = rtlResId?.let(::DrawableAppIconSource),
    mirrorInRtl = mirrorInRtl,
  )

  constructor(
    ltrImageVector: ImageVector,
    rtlImageVector: ImageVector? = null,
    mirrorInRtl: Boolean = false,
  ) : this(
    ltrSource = VectorAppIconSource(ltrImageVector),
    rtlSource = rtlImageVector?.let(::VectorAppIconSource),
    mirrorInRtl = mirrorInRtl,
  )

  internal fun resolve(isRtl: Boolean): AppIconSource = if (isRtl) rtlSource ?: ltrSource else ltrSource
  internal fun hasExplicitRtlSource(): Boolean = rtlSource != null
}

@Composable
fun Icon(
  imageVector: AppIcon,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
) {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val resolvedSource = imageVector.resolve(isRtl)
  val mirroredModifier =
    if (isRtl && !imageVector.hasExplicitRtlSource() && imageVector.mirrorInRtl) {
      modifier.scale(scaleX = -1f, scaleY = 1f)
    } else {
      modifier
    }

  when (resolvedSource) {
    is VectorAppIconSource -> {
      MaterialIcon(
        painter = rememberVectorPainter(image = resolvedSource.imageVector),
        contentDescription = contentDescription,
        modifier = mirroredModifier,
        tint = tint,
      )
    }

    is DrawableAppIconSource -> {
      MaterialIcon(
        painter = painterResource(resolvedSource.resId),
        contentDescription = contentDescription,
        modifier = mirroredModifier,
        tint = tint,
      )
    }
  }
}
