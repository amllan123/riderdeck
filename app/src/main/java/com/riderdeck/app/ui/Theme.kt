package com.riderdeck.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riderdeck.app.R
import kotlinx.coroutines.delay

/* ======================= type ======================= */

/** Body and numbers: a clean geometric sans, like CRED's. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    Font(R.font.manrope_extrabold, FontWeight.Black),
)

/** Display headings: a high-contrast serif, like CRED's Cirka. */
val Display = FontFamily(
    Font(R.font.playfair_bold, FontWeight.Bold),
    Font(R.font.playfair_black, FontWeight.Black),
)

private fun riderTypography(): Typography {
    val t = Typography()
    fun TextStyle.m() = copy(fontFamily = Manrope)
    return Typography(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m(),
    )
}

/* ======================= colour ======================= */

data class Pal(
    val bg: Color, val panel: Color, val panelHi: Color, val stroke: Color,
    val fg: Color, val muted: Color, val faint: Color, val glass: Color,
    val blue: Color, val cyan: Color, val ok: Color, val warn: Color, val bad: Color,
    val sheet: Color, val dark: Boolean,
    /** NeoPOP 3D sides of a slab: right face and bottom face. */
    val edgeR: Color, val edgeB: Color,
    /** The one loud accent. */
    val pop: Color, val onPop: Color,
)

val DarkPal = Pal(
    bg = Color(0xFF0B0B0C), panel = Color(0xFF161618), panelHi = Color(0xFF1E1E21), stroke = Color(0xFF2C2C30),
    fg = Color(0xFFF5F5F2), muted = Color(0xFF8E8E96), faint = Color(0xFF55555C), glass = Color(0x0FFFFFFF),
    blue = Color(0xFF4C8DF6), cyan = Color(0xFF5ED6F2), ok = Color(0xFF2BD67B), warn = Color(0xFFFFB020), bad = Color(0xFFFF4F4F),
    sheet = Color(0xFF131315), dark = true,
    edgeR = Color(0xFF242428), edgeB = Color(0xFF1C1C1F),
    pop = Color(0xFFD4FF4F), onPop = Color(0xFF0B0B0C),
)

val SunPal = Pal(
    bg = Color(0xFFE9E9E6), panel = Color(0xFFFFFFFF), panelHi = Color(0xFFF7F7F5), stroke = Color(0xFF0D0D0D),
    fg = Color(0xFF0D0D0D), muted = Color(0xFF55555C), faint = Color(0xFF9A9AA0), glass = Color(0x0D000000),
    blue = Color(0xFF1A62E8), cyan = Color(0xFF0A8FB8), ok = Color(0xFF0E9F55), warn = Color(0xFFB36B00), bad = Color(0xFFE02D2D),
    sheet = Color(0xFFFFFFFF), dark = false,
    edgeR = Color(0xFF7A7A7A), edgeB = Color(0xFF4A4A4A),
    pop = Color(0xFF5B2EFF), onPop = Color(0xFFFFFFFF),
)

val LocalPal = staticCompositionLocalOf { DarkPal }
val LocalGlove = staticCompositionLocalOf { 1f }

@Composable
fun RiderTheme(sun: Boolean, gloves: Boolean, content: @Composable () -> Unit) {
    val p = if (sun) SunPal else DarkPal
    val scheme = if (sun) {
        lightColorScheme(primary = p.pop, background = p.bg, surface = p.panel, onSurface = p.fg, onBackground = p.fg)
    } else {
        darkColorScheme(primary = p.pop, onPrimary = p.onPop, background = p.bg, surface = p.panel, onSurface = p.fg, onBackground = p.fg)
    }
    MaterialTheme(colorScheme = scheme, typography = remember { riderTypography() }) {
        CompositionLocalProvider(
            LocalPal provides p,
            LocalGlove provides (if (gloves) 1.22f else 1f),
            LocalContentColor provides p.fg,
        ) { content() }
    }
}

/* ======================= NeoPOP surfaces ======================= */

private fun edgePaths(w: Float, h: Float, d: Float): Pair<Path, Path> {
    val right = Path().apply { moveTo(w - d, 0f); lineTo(w, d); lineTo(w, h); lineTo(w - d, h - d); close() }
    val bottom = Path().apply { moveTo(0f, h - d); lineTo(w - d, h - d); lineTo(w, h); lineTo(d, h); close() }
    return right to bottom
}

/** Dashboard slab: flat face with a hairline, sitting on hard 3D edges (CRED NeoPOP "elevated" card). */
@Composable
fun Modifier.panel(@Suppress("UNUSED_PARAMETER") radius: Dp = 0.dp, depth: Dp = 3.dp): Modifier {
    val p = LocalPal.current
    val d = with(LocalDensity.current) { depth.toPx() }
    return this
        .drawBehind {
            val (r, b) = edgePaths(size.width, size.height, d)
            drawPath(r, p.edgeR)
            drawPath(b, p.edgeB)
            drawRect(p.panel, size = size.copy(width = size.width - d, height = size.height - d))
            drawRect(
                Brush.verticalGradient(listOf(p.panelHi, p.panel), endY = size.height * 0.6f),
                size = size.copy(width = size.width - d, height = size.height - d),
            )
        }
        .padding(end = depth, bottom = depth)
        .border(1.dp, p.stroke)
        .clipToBounds()
}

enum class PopKind { Primary, Secondary, Danger, Accent, Plain }

data class PopColors(val face: Color, val content: Color, val edgeR: Color, val edgeB: Color, val border: Color?)

@Composable
fun popColors(kind: PopKind): PopColors {
    val p = LocalPal.current
    return when (kind) {
        PopKind.Primary -> if (p.dark) PopColors(Color(0xFFF5F5F2), Color(0xFF0B0B0C), Color(0xFF9C9C9C), Color(0xFF6E6E6E), null)
        else PopColors(Color(0xFF0D0D0D), Color.White, Color(0xFF5A5A5A), Color(0xFF333333), null)
        PopKind.Accent -> if (p.dark) PopColors(p.pop, p.onPop, Color(0xFF93B52A), Color(0xFF6E8A1D), null)
        else PopColors(p.pop, p.onPop, Color(0xFF3B1BB0), Color(0xFF281280), null)
        PopKind.Danger -> PopColors(Color(0xFFFF4F4F), Color.White, Color(0xFFB32E2E), Color(0xFF861F1F), null)
        PopKind.Secondary -> PopColors(p.panelHi, p.fg, p.edgeR, p.edgeB, p.stroke)
        PopKind.Plain -> PopColors(p.sheet, p.fg, p.edgeR, p.edgeB, p.stroke)
    }
}

/**
 * NeoPOP pressable: the face sits on two hard 3D edges and sinks into them when pressed,
 * with a spring and a haptic tick.
 */
@Composable
fun PopSurface(
    modifier: Modifier = Modifier,
    colors: PopColors,
    depth: Dp = 4.dp,
    shimmer: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val t by animateFloatAsState(
        if (pressed && onClick != null) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 1500f), label = "press",
    )
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed && onClick != null) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val dpx = with(LocalDensity.current) { depth.toPx() }
    Box(
        modifier
            .drawBehind {
                val (r, b) = edgePaths(size.width, size.height, dpx)
                drawPath(r, colors.edgeR)
                drawPath(b, colors.edgeB)
            }
            .then(
                if (onClick != null) Modifier.clickable(src, indication = null, enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(end = depth, bottom = depth),
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = dpx * t
                    translationY = dpx * t
                }
                .background(colors.face)
                .then(if (colors.border != null) Modifier.border(1.dp, colors.border) else Modifier)
                .then(if (shimmer) Modifier.shimmer(colors.face) else Modifier),
            contentAlignment = contentAlignment,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.content) { content() }
        }
    }
}

/** CRED-style light sweep across a button every few seconds. */
@Composable
fun Modifier.shimmer(face: Color = Color.Black): Modifier {
    val tr = rememberInfiniteTransition(label = "shimmer")
    val x by tr.animateFloat(
        -0.6f, 1.6f,
        infiniteRepeatable(tween(1500, delayMillis = 2200, easing = LinearEasing)), label = "x",
    )
    val band = if (face.luminance() > 0.6f) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.30f)
    return drawWithContent {
        drawContent()
        val w = size.width
        val c = x * w
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, band, Color.Transparent),
                start = Offset(c - w * 0.25f, 0f), end = Offset(c + w * 0.25f, size.height),
            ),
        )
    }
}

/** Press-to-shrink for whole cards and panels, with haptic. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.975f else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "scale")
    val view = LocalView.current
    LaunchedEffect(pressed) { if (pressed) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(src, indication = null, onClick = onClick)
}

/** Panels rise into place one after another when the dashboard appears. */
@Composable
fun Modifier.enter(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120L + index * 80L)
        a.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 180f))
    }
    return graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 70f
        scaleX = 0.94f + 0.06f * v
        scaleY = 0.94f + 0.06f * v
    }
}

/** Odometer-style number: each digit rolls up or down when it changes. */
@Composable
fun RollingNumber(
    value: Int,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    family: FontFamily = Manrope,
) {
    val text = value.toString()
    val style = TextStyle(
        fontFamily = family, fontWeight = fontWeight, fontSize = fontSize, lineHeight = fontSize,
        color = color, fontFeatureSettings = "tnum", letterSpacing = (-0.02f * fontSize.value).sp,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { h -> if (up) h else -h } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { h -> if (up) -h else h } + fadeOut(tween(120))) using
                            SizeTransform(clip = true)
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style) }
            }
        }
    }
}

/* ======================= building blocks ======================= */

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted) {
    Text(text.uppercase(), modifier, color = color, fontSize = 10.5.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
fun SectionTitle(text: String) {
    Label(text, Modifier.padding(top = 20.dp, bottom = 10.dp, start = 2.dp))
}

@Composable
fun SheetHeader(title: String, sub: String? = null) {
    val p = LocalPal.current
    Column(Modifier.padding(bottom = 14.dp).enter(0)) {
        Text(title, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp, color = p.fg)
        if (sub != null) Text(sub, fontSize = 13.5.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Tile(icon: ImageVector, tint: Color, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    PopSurface(modifier.fillMaxWidth(), colors = popColors(PopKind.Secondary), depth = 3.dp, onClick = onClick, contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = (62 * g).dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.16f)).border(1.dp, tint.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, fontSize = 12.5.sp, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = p.faint)
        }
    }
}

@Composable
fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = p.fg, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 12.sp, color = p.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.pop, checkedThumbColor = p.onPop, checkedBorderColor = p.pop,
                uncheckedTrackColor = p.glass, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.stroke,
            ),
        )
    }
}

@Composable
fun Group(content: @Composable () -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().background(p.glass).border(1.dp, p.stroke)) { content() }
}

/** NeoPOP button. Primary = white slab with black text (dark) and a shimmer; secondary = outlined slab. */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    PopButton(text, modifier, if (primary) PopKind.Primary else PopKind.Secondary, icon, shimmer = primary, onClick = onClick)
}

@Composable
fun PopButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: PopKind = PopKind.Primary,
    icon: ImageVector? = null,
    shimmer: Boolean = false,
    height: Dp = 52.dp,
    onClick: () -> Unit,
) {
    val g = LocalGlove.current
    PopSurface(modifier, colors = popColors(kind), depth = 4.dp, shimmer = shimmer, onClick = onClick) {
        Row(
            Modifier.defaultMinSize(minHeight = height * g).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(20.dp))
            Text(text.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, letterSpacing = 1.2.sp, maxLines = 1)
        }
    }
}

/** Square NeoPOP icon button. */
@Composable
fun PopIcon(icon: ImageVector, desc: String, modifier: Modifier = Modifier, kind: PopKind = PopKind.Plain, size: Dp = 46.dp, tint: Color? = null, onClick: () -> Unit) {
    val g = LocalGlove.current
    PopSurface(modifier.size(size * g + 3.dp), colors = popColors(kind), depth = 3.dp, onClick = onClick) {
        Icon(icon, desc, tint = tint ?: LocalContentColor.current)
    }
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}
