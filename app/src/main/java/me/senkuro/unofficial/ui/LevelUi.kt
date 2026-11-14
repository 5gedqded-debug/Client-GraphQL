package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Открыть страницу уровней; вызывается там, где показан уровень именно авторизованного пользователя. */
val LocalOpenLevels = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Ступень цвета как на сайте: округление вверх до кратного 5 (0 — серая, с 75 — последняя). */
fun levelTier(level: Int): Int = when {
    level <= 0 -> 0
    level >= 75 -> 75
    else -> (level + 4) / 5 * 5
}

private val TierGray = Color(0xFF6B7280)

/** Цвета ступени (1 или 2 цвета: градиент) из CSS сайта lvl__tag--N. */
fun levelColors(level: Int): List<Color> = when (levelTier(level)) {
    0 -> listOf(TierGray)
    5, 50 -> listOf(Color(0xFF30BA2F))
    10, 55 -> listOf(Color(0xFF5177FF))
    15, 60 -> listOf(Color(0xFFFFB300))
    20 -> listOf(Color(0xFFFF4848))
    25 -> listOf(Color(0xFFB751FF))
    30 -> listOf(Color(0xFFFF81E4))
    35 -> listOf(Color(0xFF33A3FF))
    40 -> listOf(Color(0xFF7751FF))
    45 -> listOf(Color(0xFFF69F42))
    65 -> listOf(Color(0xFFFF4848), Color(0xFF33A3FF))
    70 -> listOf(Color(0xFFB751FF), Color(0xFF7751FF))
    else -> listOf(Color(0xFFFF81E4), Color(0xFFF69F42))
}

fun levelColor(level: Int): Color = levelColors(level).first()

fun tierBrush(level: Int): Brush {
    val c = levelColors(level)
    return Brush.linearGradient(if (c.size == 1) listOf(c[0], c[0]) else c)
}

@Composable
fun LevelTag(level: Int, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.height(20.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(tierBrush(level)), contentAlignment = Alignment.Center) {
            Text(level.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text("Ур.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 6.dp))
    }
}
