package me.senkuro.unofficial.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.EmojiItem
import me.senkuro.unofficial.data.EmojiSetItem
import me.senkuro.unofficial.data.RichTokens
import me.senkuro.unofficial.data.StickerItem
import me.senkuro.unofficial.data.StickerSetItem

/**
 * Поле комментария как на сайте: форматирование (жирный, курсив, зачёркнутый, спойлер, код, цитата, список),
 * эмодзи из ваших наборов и стикеры. Текст хранится в простой разметке (`**жирный**`, `||спойлер||`, `> цитата`, `:эмодзи:`),
 * привычные BB-коды `[b]`, `[i]`, `[s]`, `[spoiler]`, `[code]`, `[quote]`, `[url=…]` тоже понимаются.
 *
 * [onSticker] — отправка стикера (как на сайте, отдельным комментарием); null — стикеры недоступны (например, при правке).
 */
@Composable
fun CommentComposer(
    input: String,
    onInput: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSticker: ((StickerItem) -> Unit)? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(input, TextRange(input.length))) }
    var picker by remember { mutableStateOf(false) }

    // Внешнее изменение текста (очистка после отправки, начало правки) переносим в поле.
    LaunchedEffect(input) {
        if (input != field.text) field = TextFieldValue(input, TextRange(input.length))
    }

    fun update(v: TextFieldValue) {
        field = v
        if (v.text != input) onInput(v.text)
    }

    fun wrap(left: String, right: String = left) {
        val t = field.text
        val a = minOf(field.selection.start, field.selection.end)
        val b = maxOf(field.selection.start, field.selection.end)
        val text = t.substring(0, a) + left + t.substring(a, b) + right + t.substring(b)
        val sel = if (a == b) TextRange(a + left.length) else TextRange(a + left.length, b + left.length)
        update(TextFieldValue(text, sel))
    }

    fun prefixLines(prefix: String) {
        val t = field.text
        val a = minOf(field.selection.start, field.selection.end)
        val b = maxOf(field.selection.start, field.selection.end)
        val lineStart = t.lastIndexOf('\n', a - 1) + 1
        val lines = t.substring(lineStart, b).split("\n")
        val text = t.substring(0, lineStart) + lines.joinToString("\n") { prefix + it } + t.substring(b)
        update(TextFieldValue(text, TextRange(b + prefix.length * lines.size)))
    }

    fun insert(s: String) {
        val t = field.text
        val a = minOf(field.selection.start, field.selection.end)
        val b = maxOf(field.selection.start, field.selection.end)
        update(TextFieldValue(t.substring(0, a) + s + t.substring(b), TextRange(a + s.length)))
    }

    Column(modifier) {
        OutlinedTextField(
            value = field,
            onValueChange = { update(it) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatButton("Ж", TextStyle(fontWeight = FontWeight.Bold)) { wrap("**") }
            FormatButton("К", TextStyle(fontStyle = FontStyle.Italic)) { wrap("*") }
            FormatButton("З", TextStyle(textDecoration = TextDecoration.LineThrough)) { wrap("~~") }
            FormatButton("Спойлер") { wrap("||") }
            FormatButton("Код") { wrap("`") }
            FormatButton("Цитата") { prefixLines("> ") }
            FormatButton("Список") { prefixLines("- ") }
            FormatButton("🙂") { picker = true }
        }
    }

    if (picker) {
        EmojiStickerSheet(
            onDismiss = { picker = false },
            onEmoji = { e -> insert(RichTokens.emoji(e.id, e.slug, e.url)) },
            onSticker = onSticker?.let { send -> { s: StickerItem -> picker = false; send(s) } },
        )
    }
}

@Composable
private fun FormatButton(text: String, style: TextStyle = TextStyle.Default, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = style, fontSize = 13.sp)
    }
}

/** Панель эмодзи и стикеров — наборы берутся из аккаунта (те же, что на сайте). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiStickerSheet(
    onDismiss: () -> Unit,
    onEmoji: (EmojiItem) -> Unit,
    onSticker: ((StickerItem) -> Unit)?,
) {
    var tab by remember { mutableStateOf(0) }
    var emoji by remember { mutableStateOf<List<EmojiSetItem>?>(null) }
    var stickers by remember { mutableStateOf<List<StickerSetItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(tab, reload) {
        error = null
        try {
            if (tab == 0) emoji = AppGraph.repository.emojiSets(force = reload > 0)
            else stickers = AppGraph.repository.stickerSets(force = reload > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 520.dp).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Эмодзи") })
                if (onSticker != null) FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Стикеры") })
            }
            Spacer(Modifier.size(8.dp))
            val err = error
            when {
                err != null -> Column {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = { reload++ }) { Text("Повторить") }
                }
                tab == 0 -> {
                    val sets = emoji
                    if (sets == null) Loading()
                    else if (sets.isEmpty()) Empty("Отсутствуют эмодзи", "Возможно, вам их ещё не продала какая-то лиса…\nОбычные эмодзи можно вводить с клавиатуры телефона.")
                    else LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxWidth()) {
                        sets.forEach { set ->
                            item(key = "h-${set.id}", span = { GridItemSpan(maxLineSpan) }) {
                                Text(set.title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                            }
                            items(set.emojis, key = { "e-${set.id}-${it.id}" }) { e ->
                                AsyncImage(
                                    e.url, e.slug, contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(44.dp).padding(3.dp).clickable { onEmoji(e) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    val sets = stickers
                    if (sets == null) Loading()
                    else if (sets.isEmpty()) Empty("Отсутствуют стикеры", "Возможно, вам их ещё не продала какая-то лиса…")
                    else LazyVerticalGrid(columns = GridCells.Adaptive(84.dp), modifier = Modifier.fillMaxWidth()) {
                        sets.forEach { set ->
                            item(key = "h-${set.id}", span = { GridItemSpan(maxLineSpan) }) {
                                Text(set.title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                            }
                            items(set.stickers, key = { "s-${set.id}-${it.id}" }) { st ->
                                AsyncImage(
                                    st.url, st.name, contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(84.dp).padding(4.dp).clickable { onSticker?.invoke(st) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(28.dp))
    }
}

@Composable
private fun Empty(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}
