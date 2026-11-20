package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem

/**
 * Подсказка в читалке: авторизованный пользователь дочитался до 4-й главы, а тайтла нет в закладках.
 * Закладки проверяются на сайте (fetchMangaViewerState); отказ запоминается для тайтла.
 */
@Composable
fun ReaderBookmarkPrompt(manga: MangaItem?, chapterNumber: Float) {
    val token by AppGraph.auth.token.collectAsState()
    val account by AppGraph.account.collectAsState()
    val scope = rememberCoroutineScope()
    var show by remember(manga?.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val eligible = manga != null && token != null && chapterNumber >= 4f && chapterNumber < 5f
    LaunchedEffect(manga?.id, eligible, token) {
        if (!eligible || manga == null || AppGraph.bookmarkPromptDeclined(manga.id)) return@LaunchedEffect
        try {
            if (AppGraph.repository.bookmarkCollectionId(manga.slug) == null) show = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
    }
    if (!show || manga == null) return
    fun decline() { AppGraph.declineBookmarkPrompt(manga.id); show = false }
    AlertDialog(
        onDismissRequest = { decline() },
        // закрывается и кнопкой «Не сейчас», и тапом в любое место вне окна, и системной «Назад»
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = manga.cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 110.dp, height = 156.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(manga.title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Не хотите добавить тайтл в закладки?", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val acc = account
                // «Читаю», если такая коллекция есть, иначе первая системная
                val col = acc?.collections?.firstOrNull { it.name.equals("Читаю", true) }
                    ?: acc?.collections?.firstOrNull { it.system } ?: acc?.collections?.firstOrNull()
                if (col == null) { error = "Закладки ещё загружаются"; return@Button }
                busy = true; error = null
                scope.launch {
                    try {
                        AppGraph.repository.setBookmark(manga.id, col.id)
                        AppGraph.setMark(manga.id, col.name)
                        show = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.msg()
                    }
                    busy = false
                }
            }) { Text("Добавить в закладки") }
        },
        dismissButton = { TextButton(onClick = { decline() }) { Text("Не сейчас") } },
    )
}
