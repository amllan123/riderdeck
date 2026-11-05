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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
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

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    Font(R.font.manrope_extrabold, FontWeight.Black),
)

/** Kept for the odd editorial touch; the dashboard itself is all Manrope. */
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
    /** Kept for compatibility; modern surfaces don't draw hard edges. */
    val edgeR: Color, val edgeB: Color,
    /** Brand accent: a violet → cyan glow. */
    val pop: Color, val onPop: Color, val pop2: Color,
)

val DarkPal = Pal(
    bg = Color(0xFF06080C), panel = Color(0xFF0F1219), panelHi = Color(0xFF171B25), stroke = Color(0x14FFFFFF),
    fg = Color(0xFFF4F6FA), muted = Color(0xFF8A93A6), faint = Color(0xFF4A5264), glass = Color(0x0DFFFFFF),
    blue = Color(0xFF4C8DF6), cyan = Color(0xFF22D3EE), ok = Color(0xFF34D399), warn = Color(0xFFFBBF24), bad = Color(0xFFFF5A5F),
    sheet = Color(0xFF0E1118), dark = true,
    edgeR = Color.Transparent, edgeB = Color.Transparent,
    pop = Color(0xFF8B7BFF), onPop = Color.White, pop2 = Color(0xFF22D3EE),
)

/** Sunlight: built for glare — pure black on white, darker greys, deeper accents. */
val SunPal = Pal(
    bg = Color(0xFFE6E9EE), panel = Color(0xFFFFFFFF), panelHi = Color(0xFFFFFFFF), stroke = Color(0x22000000),
    fg = Color(0xFF000000), muted = Color(0xFF2F333B), faint = Color(0xFF5B616D), glass = Color(0x12000000),
    blue = Color(0xFF0B57D0), cyan = Color(0xFF006C8A), ok = Color(0xFF036B47), warn = Color(0xFF8A4B00), bad = Color(0xFFC81E1E),
    sheet = Color(0xFFFFFFFF), dark = false,
    edgeR = Color.Transparent, edgeB = Color.Transparent,
    pop = Color(0xFF4B2BE0), onPop = Color.White, pop2 = Color(0xFF006C8A),
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

/** Brand gradient (violet → cyan). */
fun Pal.brand(): Brush = Brush.linearGradient(listOf(pop, pop2))

/* ======================= surfaces ======================= */

/** Dashboard card: soft gradient fill, rounded, a faint top highlight in dark and a soft shadow in light. */
@Composable
fun Modifier.panel(radius: Dp = 26.dp, @Suppress("UNUSED_PARAMETER") depth: Dp = 0.dp): Modifier {
    val p = LocalPal.current
    val shape = RoundedCornerShape(radius)
    val base = if (p.dark) this else this.shadow(10.dp, shape, ambientColor = Color(0x220F172A), spotColor = Color(0x330F172A))
    return base
        .clip(shape)
        .background(Brush.verticalGradient(listOf(p.panelHi, p.panel)))
        .border(
            1.dp,
            if (p.dark) Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x05FFFFFF))) else Brush.verticalGradient(listOf(p.stroke, p.stroke)),
            shape,
        )
}

enum class PopKind { Primary, Secondary, Danger, Accent, Plain }

data class PopColors(
    val face: Color,
    val content: Color,
    val edgeR: Color = Color.Transparent,
    val edgeB: Color = Color.Transparent,
    val border: Color? = null,
    val gradient: List<Color>? = null,
    val glow: Color? = null,
)

@Composable
fun popColors(kind: PopKind): PopColors {
    val p = LocalPal.current
    return when (kind) {
        PopKind.Primary -> if (p.dark) PopColors(Color(0xFFF4F6FA), Color(0xFF06080C)) else PopColors(Color(0xFF0B1220), Color.White)
        PopKind.Accent -> PopColors(p.pop, Color.White, gradient = listOf(p.pop, p.pop2), glow = p.pop)
        PopKind.Danger -> PopColors(Color(0xFFFF5A5F), Color.White, gradient = listOf(Color(0xFFFF7A7F), Color(0xFFE5383B)), glow = Color(0xFFFF5A5F))
        PopKind.Secondary -> PopColors(p.glass, p.fg, border = p.stroke)
        PopKind.Plain -> PopColors(if (p.dark) Color(0xF0151923) else Color(0xF7FFFFFF), p.fg, border = p.stroke)
    }
}

/**
 * Pressable surface: rounded, gradient or solid, soft glow for brand/danger buttons,
 * springs down a little when pressed with a haptic tick.
 */
@Composable
fun PopSurface(
    modifier: Modifier = Modifier,
    colors: PopColors,
    @Suppress("UNUSED_PARAMETER") depth: Dp = 0.dp,
    shimmer: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed && onClick != null) 0.95f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 900f), label = "press",
    )
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed && onClick != null) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val p = LocalPal.current
    val glow = colors.glow
    Box(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .then(
                when {
                    glow != null -> Modifier.shadow(14.dp, shape, ambientColor = glow, spotColor = glow)
                    !p.dark && colors.face.alpha > 0.5f -> Modifier.shadow(6.dp, shape, ambientColor = Color(0x220F172A), spotColor = Color(0x330F172A))
                    else -> Modifier
                }
            )
            .clip(shape)
            .then(if (colors.gradient != null) Modifier.background(Brush.linearGradient(colors.gradient)) else Modifier.background(colors.face))
            .then(if (colors.border != null) Modifier.border(1.dp, colors.border, shape) else Modifier)
            .then(if (shimmer) Modifier.shimmer(colors.face) else Modifier)
            .then(
                if (onClick != null) Modifier.clickable(src, indication = null, enabled = enabled, onClick = onClick)
                else Modifier
            ),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) { content() }
    }
}

/** A light sweep across a button every few seconds. */
@Composable
fun Modifier.shimmer(face: Color = Color.Black): Modifier {
    val tr = rememberInfiniteTransition(label = "shimmer")
    val x by tr.animateFloat(
        -0.6f, 1.6f,
        infiniteRepeatable(tween(1400, delayMillis = 2600, easing = LinearEasing)), label = "x",
    )
    val band = if (face.luminance() > 0.6f) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.28f)
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

/** Press-to-shrink for cards and icons, with haptic. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 900f), label = "scale")
    val view = LocalView.current
    LaunchedEffect(pressed) { if (pressed) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(src, indication = null, onClick = onClick)
}

/** Panels rise into place one after another. */
@Composable
fun Modifier.enter(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L + index * 70L)
        a.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 200f))
    }
    return graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 50f
        scaleX = 0.96f + 0.04f * v
        scaleY = 0.96f + 0.04f * v
    }
}

/** Odometer-style number: each digit rolls when it changes. */
@Composable
fun RollingNumber(
    value: Int,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    family: FontFamily = Manrope,
    brush: Brush? = null,
) {
    val text = value.toString()
    val base = TextStyle(
        fontFamily = family, fontWeight = fontWeight, fontSize = fontSize, lineHeight = fontSize,
        color = color, fontFeatureSettings = "tnum", letterSpacing = (-0.03f * fontSize.value).sp,
    )
    val style = if (brush != null) base.copy(brush = brush) else base
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
    Text(text.uppercase(), modifier, color = color, fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
fun SectionTitle(text: String) {
    Label(text, Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp))
}

@Composable
fun SheetHeader(title: String, sub: String? = null) {
    val p = LocalPal.current
    Column(Modifier.padding(bottom = 14.dp).enter(0)) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, color = p.fg, letterSpacing = (-0.5).sp)
        if (sub != null) Text(sub, fontSize = 13.5.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Tile(icon: ImageVector, tint: Color, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    PopSurface(modifier.fillMaxWidth(), colors = popColors(PopKind.Secondary), onClick = onClick, contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = (62 * g).dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
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
                checkedTrackColor = p.pop, checkedThumbColor = Color.White, checkedBorderColor = p.pop,
                uncheckedTrackColor = p.glass, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.stroke,
            ),
        )
    }
}

@Composable
fun Group(content: @Composable () -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.glass).border(1.dp, p.stroke, RoundedCornerShape(18.dp))) { content() }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    PopButton(text, modifier, if (primary) PopKind.Accent else PopKind.Secondary, icon, onClick = onClick)
}

/** Rounded pill button. Accent = brand gradient with glow; Primary = white pill; Secondary = glass. */
@Composable
fun PopButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: PopKind = PopKind.Primary,
    icon: ImageVector? = null,
    shimmer: Boolean = false,
    height: Dp = 50.dp,
    onClick: () -> Unit,
) {
    val g = LocalGlove.current
    PopSurface(modifier, colors = popColors(kind), shimmer = shimmer, onClick = onClick, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.defaultMinSize(minHeight = height * g).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(20.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        }
    }
}

/** Round icon button. */
@Composable
fun PopIcon(icon: ImageVector, desc: String, modifier: Modifier = Modifier, kind: PopKind = PopKind.Plain, size: Dp = 46.dp, tint: Color? = null, onClick: () -> Unit) {
    val g = LocalGlove.current
    PopSurface(modifier.size(size * g), colors = popColors(kind), onClick = onClick, shape = CircleShape) {
        Icon(icon, desc, tint = tint ?: LocalContentColor.current)
    }
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}
