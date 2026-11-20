package me.senkuro.unofficial.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.CatalogFilters
import me.senkuro.unofficial.data.SavedFilter
import me.senkuro.unofficial.data.SavedFiltersCodec

private fun sameFilters(a: CatalogFilters, b: CatalogFilters) = a == b

/** Строчная карусель сохранённых наборов под поиском. В конце — «Сбросить» (другой стиль) и «⋯» управление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFiltersRow(current: CatalogFilters, onApply: (CatalogFilters) -> Unit, onManage: () -> Unit) {
    val saved by AppGraph.savedFilters.collectAsState()
    if (saved.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (s in saved) {
            val selected = sameFilters(current, s.filters)
            FilterChip(
                selected = selected,
                onClick = { onApply(if (selected) CatalogFilters() else s.filters) },
                label = { Text(s.name, maxLines = 1) },
            )
        }
        // Контурный «Сбросить» — отличается от залитых чипов наборов
        AssistChip(onClick = { onApply(CatalogFilters()) }, label = { Text("✕ Сбросить", maxLines = 1) })
        AssistChip(onClick = onManage, label = { Text("Наборы", maxLines = 1) })
    }
}

/** Ввод названия и сохранение текущих фильтров как набора (одноимённый набор перезаписывается). */
@Composable
fun SaveFilterNameDialog(filters: CatalogFilters, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Сохранить набор") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("Название") })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val n = name.trim()
                AppGraph.setSavedFilters(AppGraph.savedFilters.value.filter { !it.name.equals(n, true) } + SavedFilter(n, filters))
                onDone()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Отмена") } },
    )
}

/** Управление наборами: применить, переименовать, перезаписать, удалить, порядок, импорт и экспорт. */
@Composable
fun SavedFiltersDialog(current: CatalogFilters, onApply: (CatalogFilters) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val saved by AppGraph.savedFilters.collectAsState()
    var rename by remember { mutableStateOf<SavedFilter?>(null) }
    var renameText by remember { mutableStateOf("") }
    var importOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun replace(old: SavedFilter, new: SavedFilter?) {
        AppGraph.setSavedFilters(saved.flatMap { if (it === old) listOfNotNull(new) else listOf(it) })
    }
    fun move(i: Int, d: Int) {
        val j = i + d
        if (j !in saved.indices) return
        val l = saved.toMutableList(); val t = l[i]; l[i] = l[j]; l[j] = t
        AppGraph.setSavedFilters(l)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Наборы фильтров") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (saved.isEmpty()) Text("Пока пусто. Выберите фильтры и нажмите «Сохранить» в окне фильтров.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                saved.forEachIndexed { i, s ->
                    Column(Modifier.fillMaxWidth()) {
                        Text("${s.name} · ${s.filters.activeCount} фильтр.", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { onApply(s.filters); onDismiss() }) { Text("Применить") }
                            TextButton(onClick = { rename = s; renameText = s.name }) { Text("Имя") }
                            TextButton(enabled = current.activeCount > 0, onClick = { replace(s, s.copy(filters = current)) }) { Text("Перезаписать") }
                            TextButton(onClick = { move(i, -1) }, enabled = i > 0) { Text("←") }
                            TextButton(onClick = { move(i, 1) }, enabled = i < saved.lastIndex) { Text("→") }
                            TextButton(onClick = { replace(s, null) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(enabled = saved.isNotEmpty(), onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("filters", SavedFiltersCodec.encode(saved)))
                        message = "Скопировано в буфер обмена"
                    }) { Text("Копировать") }
                    TextButton(enabled = saved.isNotEmpty(), onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, SavedFiltersCodec.encode(saved)) }
                        ctx.startActivity(Intent.createChooser(i, "Экспорт наборов"))
                    }) { Text("Поделиться") }
                    TextButton(onClick = { importOpen = true; importText = "" }) { Text("Импорт") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )

    rename?.let { target ->
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Название набора") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it.take(30) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = { replace(target, target.copy(name = renameText.trim())); rename = null }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Отмена") } },
        )
    }

    if (importOpen) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text("Импорт наборов") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = importText, onValueChange = { importText = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8, label = { Text("Вставьте JSON") })
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        importText = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    }) { Text("Вставить из буфера") }
                }
            },
            confirmButton = {
                TextButton(enabled = importText.isNotBlank(), onClick = {
                    val list = SavedFiltersCodec.decode(importText)
                    if (list == null) message = "Не удалось прочитать: неверный формат"
                    else {
                        // одноимённые наборы заменяются, остальные добавляются
                        val names = list.map { it.name.lowercase() }.toSet()
                        AppGraph.setSavedFilters(saved.filter { it.name.lowercase() !in names } + list)
                        message = "Импортировано: ${list.size}"
                    }
                    importOpen = false
                }) { Text("Импортировать") }
            },
            dismissButton = { TextButton(onClick = { importOpen = false }) { Text("Отмена") } },
        )
    }
}
