package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem

/** Плашка со счётчиком комментариев текущей страницы (до 99+), как на сайте. */
@Composable
fun CommentCountChip(count: Int?) {
    if (count == null || count <= 0) return
    Box(
        Modifier.padding(start = 4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp)
    }
}

/** Закладка в шапке читалки: контур + «+» если тайтла нет в закладках, заливка без «+» если есть. */
@Composable
fun ReaderBookmarkButton(manga: MangaItem?, onLogin: () -> Unit = {}) {
    if (manga == null) return
    val marks by AppGraph.marks.collectAsState()
    val token by AppGraph.auth.token.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    // Сервер — источник истины: если закладка есть на сайте, а локально о ней не знаем
    LaunchedEffect(manga.id, token) {
        if (token == null || marks[manga.id] != null) return@LaunchedEffect
        try {
            val id = AppGraph.repository.bookmarkCollectionId(manga.slug)
            val col = AppGraph.account.value?.collections?.firstOrNull { it.id == id }
            if (id != null) AppGraph.setMark(manga.id, col?.name ?: "")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
    }
    val added = marks[manga.id] != null
    IconButton(onClick = { dialog = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (added) bookmarkIcon(true) else bookmarkIcon(false),
                contentDescription = if (added) "В закладках" else "Добавить в закладки",
                tint = Color.White,
            )
            if (!added) Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.padding(0.dp))
        }
    }
    if (dialog) BookmarkDialog(mangaId = manga.id, onLogin = onLogin, onDismiss = { dialog = false })
}

private val bookmarkFilled: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("BookmarkFilled", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathData {
                moveTo(17f, 3f); horizontalLineTo(7f)
                curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
                verticalLineTo(21f); lineTo(12f, 18f); lineTo(19f, 21f)
                verticalLineTo(5f)
                curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f); close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}

private val bookmarkOutline: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("BookmarkOutline", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathData {
                moveTo(17f, 3f); horizontalLineTo(7f)
                curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
                verticalLineTo(21f); lineTo(12f, 18f); lineTo(19f, 21f)
                verticalLineTo(5f)
                curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f); close()
            },
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2f,
        )
    }.build()
}

internal fun bookmarkIcon(filled: Boolean) = if (filled) bookmarkFilled else bookmarkOutline
