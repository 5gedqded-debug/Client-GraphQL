package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.LevelRow
import me.senkuro.unofficial.data.LevelsInfo

private fun grouped(v: Long): String = "%,d".format(java.util.Locale.US, v).replace(',', ' ')

@Composable
fun LevelsScreen(onBack: () -> Unit) {
    var info by remember { mutableStateOf<LevelsInfo?>(null) }
    var myLevel by remember { mutableStateOf(0) }
    var myExp by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null; info = null
        try {
            AppGraph.repository.balance()?.let { myLevel = it.level ?: 0; myExp = it.experience }
            info = AppGraph.repository.levels()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top("Личный уровень", onBack)
        val data = info
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            data == null -> LoadingBox()
            else -> LevelsContent(data, myLevel, myExp)
        }
    }
}

@Composable
private fun LevelsContent(data: LevelsInfo, level: Int, exp: Long) {
    val rows = listOf(LevelRow(0, 0, 0)) + data.levels
    val cur = rows.getOrNull(level) ?: rows.last()
    val next = rows.getOrNull(level + 1)
    val progress = if (next != null && next.experience > 0) ((exp - cur.totalExperience).toFloat() / next.experience).coerceIn(0f, 1f) else 1f
    val maxLevel = rows.last().number
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Прокачивай уровень и получай награды", color = muted, fontSize = 13.sp)
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Полоса прогресса: от цвета текущей ступени к цвету следующей.
                val from = levelColor(level)
                val to = levelColor(minOf(maxLevel, level + 1))
                Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background)) {
                    Box(Modifier.fillMaxWidth(progress).height(24.dp).background(Brush.horizontalGradient(listOf(from.copy(alpha = 0.5f), to))))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LevelTag(level)
                    Box(Modifier.weight(1f).height(1.dp).background(muted.copy(alpha = 0.4f)))
                    LevelTag(minOf(maxLevel, level + 1))
                }
                if (next != null) {
                    Row {
                        Text(grouped(exp), fontWeight = FontWeight.Bold)
                        Text(" из ${grouped(next.totalExperience)} опыта", color = muted)
                    }
                    Text("Ещё ${grouped((next.totalExperience - exp).coerceAtLeast(0))} впереди", color = muted, fontSize = 13.sp)
                }
                Text("Сегодня набрано: ${grouped(data.todayExperience)}", color = muted, fontSize = 13.sp)
            }
            Row(Modifier.padding(top = 16.dp, bottom = 4.dp).fillMaxWidth()) {
                Text("Уровень", Modifier.weight(0.8f), color = muted, fontSize = 12.sp)
                Text("Опыт для повышения", Modifier.weight(1.4f), color = muted, fontSize = 12.sp)
                Text("Награда", Modifier.weight(0.8f), color = muted, fontSize = 12.sp)
            }
        }
        items(data.levels.filter { it.number in 1..100 }) { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(0.8f)) { LevelTag(r.number) }
                Text(grouped(r.totalExperience), Modifier.weight(1.4f), fontSize = 14.sp)
                Text("● ${r.number * 5}", Modifier.weight(0.8f), fontSize = 14.sp, color = Color(0xFFFFB300))
            }
            when (r.number) {
                5 -> Note("5 уровня", Color(0xFF30BA2F), "появляется возможность вступать в гильдии и создавать карты")
                15 -> Note("15 уровня", Color(0xFFFFB300), "появляется возможность создавать гильдию с премиумом")
                20 -> Note("20 уровня", Color(0xFFFF4848), "появляется возможность создавать гильдию без премиума")
            }
        }
    }
}

@Composable
private fun Note(level: String, color: Color, text: String) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append("После получения ")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.SemiBold)); append(level); pop()
            append(", $text")
        },
        Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
