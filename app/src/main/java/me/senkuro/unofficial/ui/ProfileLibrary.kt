package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.BookmarkEntry
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.UserLibrary

/** Лента тайтлов одной категории: страницы подгружаются по курсору, при переключении категорий уже загруженное сохраняется. */
private class LibraryFeed(private val userId: String) {
    class Page {
        var items by mutableStateOf<List<BookmarkEntry>>(emptyList())
        var next: String? = null
        var loading by mutableStateOf(false)
        var loaded by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
    }

    private val pages = HashMap<String, Page>()

    /** null — все категории. */
    fun page(collectionId: String?): Page = pages.getOrPut(collectionId.orEmpty()) { Page() }

    suspend fun fetch(collectionId: String?) {
        val p = page(collectionId)
        if (p.loading || (p.loaded && p.next == null)) return
        p.loading = true
        p.error = null
        try {
            val r = AppGraph.repository.bookmarks(userId, collectionId, p.next)
            p.items = (p.items + r.items).distinctBy { it.manga.id }
            p.next = r.next
            p.loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            p.error = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
        } finally {
            p.loading = false
        }
    }
}

private fun count(n: Long): String = "%,d".format(java.util.Locale("ru"), n).replace(' ', ' ')

/**
 * Виджет «Библиотека манги» в профиле: переключатель категорий (коллекции закладок пользователя) и лента его тайтлов.
 * Нажатие на карточку открывает страницу тайтла.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileLibraryBlock(
    slug: String,
    userId: String,
    isOwn: Boolean,
    totalHint: Long,
    onManga: (MangaItem) -> Unit,
    onOpenAll: (() -> Unit)?,
) {
    var library by remember(slug) { mutableStateOf<UserLibrary?>(null) }
    var selected by remember(userId) { mutableStateOf<String?>(null) } // null — «Все»
    val feed = remember(userId) { LibraryFeed(userId) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(slug) { library = AppGraph.repository.userLibrary(slug, isOwn) }
    LaunchedEffect(userId, selected) { feed.fetch(selected) }

    val page = feed.page(selected)
    val total = library?.total?.takeIf { it > 0 } ?: totalHint

    Block("Библиотека манги", count = count(total), more = onOpenAll?.let { "Все ›" }, onMore = { onOpenAll?.invoke() }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "all") {
                    FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("Все") })
                }
                items(library?.collections.orEmpty(), key = { it.id }) { c ->
                    FilterChip(
                        selected = selected == c.id,
                        onClick = { selected = c.id },
                        label = { Text("${c.name} ${count(c.count)}") },
                    )
                }
            }
            val error = page.error
            when {
                page.items.isEmpty() && error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { feed.fetch(selected) } }) { Text("Повторить") }
                }
                page.items.isEmpty() && !page.loaded -> Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                page.items.isEmpty() -> Text("В этой категории пока пусто", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(page.items, key = { _, e -> e.manga.id }) { index, entry ->
                        if (index >= page.items.size - 3) LaunchedEffect(page.items.size, selected) { feed.fetch(selected) }
                        MangaCard(entry.manga, onClick = { onManga(entry.manga) }, modifier = Modifier.width(108.dp), compact = true)
                    }
                    if (page.loading) item(key = "loading") {
                        Box(Modifier.width(48.dp).height(150.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
