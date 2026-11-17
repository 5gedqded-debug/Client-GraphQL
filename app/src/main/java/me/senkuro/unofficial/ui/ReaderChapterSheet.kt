package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.Chapter

/** «Список глав» прямо из читалки: поиск по номеру или названию, смена порядка, тап — открыть главу. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderChapterSheet(mangaId: String, current: Chapter, onOpen: (Chapter) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<Chapter>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    LaunchedEffect(mangaId) {
        try {
            val r = AppGraph.repository.chapters(mangaId)
            list = (r.branches.firstOrNull { it.id == current.branchId }?.chapters ?: r.chapters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Список глав", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Номер или название главы") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Новые ↓" else "Старые ↑") }
            }
            val items = list
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                items == null -> Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val q = query.trim().lowercase()
                    val shown = items
                        .let { if (newestFirst) it.asReversed() else it }
                        .filter { q.isEmpty() || it.number.lowercase().contains(q) || it.title.lowercase().contains(q) }
                    LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(shown, key = { it.id }) { c ->
                            val isCurrent = c.id == current.id
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpen(c) }.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    c.title.replace(". Глава", " Глава"),
                                    Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                )
                                c.date?.take(10)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}
