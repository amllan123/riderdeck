package com.riderdeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Pal(
    val bg: Color, val panel: Color, val panelHi: Color, val stroke: Color,
    val fg: Color, val muted: Color, val faint: Color, val glass: Color,
    val blue: Color, val cyan: Color, val ok: Color, val warn: Color, val bad: Color,
    val sheet: Color, val dark: Boolean,
)

val DarkPal = Pal(
    bg = Color(0xFF04060A), panel = Color(0xFF0E121A), panelHi = Color(0xFF18202D), stroke = Color(0x14FFFFFF),
    fg = Color(0xFFF3F6FB), muted = Color(0xFF94A0B6), faint = Color(0xFF5F6B82), glass = Color(0x10FFFFFF),
    blue = Color(0xFF3D8BFF), cyan = Color(0xFF4FD1FF), ok = Color(0xFF34D399), warn = Color(0xFFFBBF24), bad = Color(0xFFFF4D5A),
    sheet = Color(0xFF111723), dark = true,
)

val SunPal = Pal(
    bg = Color(0xFFE6EAF0), panel = Color(0xFFFFFFFF), panelHi = Color(0xFFF3F6FA), stroke = Color(0x170F172A),
    fg = Color(0xFF0B1220), muted = Color(0xFF566275), faint = Color(0xFF8A94A6), glass = Color(0x0F0F172A),
    blue = Color(0xFF1F6FFF), cyan = Color(0xFF0AA5D8), ok = Color(0xFF0F9F6E), warn = Color(0xFFB77900), bad = Color(0xFFE5383B),
    sheet = Color(0xFFFFFFFF), dark = false,
)

val LocalPal = staticCompositionLocalOf { DarkPal }
val LocalGlove = staticCompositionLocalOf { 1f }

@Composable
fun RiderTheme(sun: Boolean, gloves: Boolean, content: @Composable () -> Unit) {
    val p = if (sun) SunPal else DarkPal
    val scheme = if (sun) {
        lightColorScheme(primary = p.blue, background = p.bg, surface = p.panel, onSurface = p.fg, onBackground = p.fg)
    } else {
        darkColorScheme(primary = p.blue, background = p.bg, surface = p.panel, onSurface = p.fg, onBackground = p.fg)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPal provides p,
            LocalGlove provides (if (gloves) 1.22f else 1f),
            LocalContentColor provides p.fg,
        ) { content() }
    }
}

/** Rounded panel surface used by every dashboard card. */
@Composable
fun Modifier.panel(radius: Dp = 26.dp): Modifier {
    val p = LocalPal.current
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(p.panel)
        .background(Brush.radialGradient(listOf(p.panelHi, p.panel), center = Offset.Zero, radius = 900f))
        .border(1.dp, p.stroke, shape)
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted) {
    Text(text.uppercase(), modifier, color = color, fontSize = 10.5.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SectionTitle(text: String) {
    Label(text, Modifier.padding(top = 18.dp, bottom = 8.dp, start = 2.dp))
}

@Composable
fun SheetHeader(title: String, sub: String? = null) {
    val p = LocalPal.current
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.fg)
        if (sub != null) Text(sub, fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun Tile(icon: ImageVector, tint: Color, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.glass)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = (64 * g).dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 12.5.sp, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = p.faint)
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
            Text(title, fontSize = 15.sp, color = p.fg)
            if (sub != null) Text(sub, fontSize = 12.sp, color = p.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.ok, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun Group(content: @Composable () -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.glass)) { content() }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    val bg = if (primary) Brush.linearGradient(listOf(p.blue, p.cyan)) else Brush.linearGradient(listOf(p.glass, p.glass))
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = (52 * g).dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = if (primary) Color.White else p.fg, modifier = Modifier.size(20.dp))
        Text(text, color = if (primary) Color.White else p.fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}
