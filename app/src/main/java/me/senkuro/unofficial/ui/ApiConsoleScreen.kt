package me.senkuro.unofficial.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.SenkuroConfig
import org.json.JSONArray
import org.json.JSONObject

private const val SAMPLE_QUERY = "query fetchManga(\$slug: String!) {\n  manga(slug: \$slug) {\n    id\n    slug\n    isLicensed\n    viewerInBlockedCountry\n    chapters\n  }\n}"

/** Debug-консоль: отправка своего GraphQL-запроса на выбранный API и просмотр ответа. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConsoleScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(SAMPLE_QUERY) }
    var variables by rememberSaveable { mutableStateOf("{\n  \"slug\": \"\"\n}") }
    var operation by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf(SenkuroConfig.current.apiUrl) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var response by remember { mutableStateOf("") }

    fun pretty(text: String): String = try {
        when (text.trimStart().firstOrNull()) {
            '{' -> JSONObject(text).toString(2)
            '[' -> JSONArray(text).toString(2)
            else -> text
        }
    } catch (_: Exception) { text }

    fun clipboard() = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("←") }
                Text("Консоль API", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Text("API", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SenkuroConfig.DOMAINS.forEach { d ->
                    FilterChip(selected = url == d.apiUrl, onClick = { url = d.apiUrl }, label = { Text(d.apiHost, maxLines = 1) })
                }
            }
            OutlinedTextField(
                value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("URL API") },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            )
            OutlinedTextField(
                value = query, modifier = Modifier.fillMaxWidth(),
                onValueChange = { v ->
                    // вставили экспорт логгера прямо в поле запроса — разложим по полям
                    if (v.contains("### Query")) {
                        val p = parseCaptured(v)
                        query = p.query
                        p.variables?.let { variables = it }
                        p.operation?.let { operation = it }
                        p.url?.let { url = it }
                    } else query = v
                },
                label = { Text("Запрос (query / mutation)") }, minLines = 6, maxLines = 14,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            )
            OutlinedTextField(
                value = variables, onValueChange = { variables = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Переменные (JSON)") }, minLines = 3, maxLines = 8,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            )
            OutlinedTextField(
                value = operation, onValueChange = { operation = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("operationName (необязательно)") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Button(enabled = !sending && query.isNotBlank(), onClick = {
                    sending = true; status = null
                    scope.launch {
                        val r = AppGraph.rawApi.raw(url.trim(), operation, query, variables)
                        status = r.error?.let { "Ошибка: $it" } ?: "HTTP ${r.code} · ${r.millis} мс · ${r.body.length} симв."
                        response = pretty(r.body).take(200_000)
                        sending = false
                    }
                }) { Text(if (sending) "Отправка…" else "Отправить") }
                TextButton(onClick = {
                    val text = clipboard().primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    val p = parseCaptured(text)
                    query = p.query
                    p.variables?.let { variables = it }
                    p.operation?.let { operation = it }
                    p.url?.let { url = it }
                    status = "Вставлено из буфера: " + listOfNotNull(
                        p.operation, if (p.url != null) "URL" else null, if (p.variables != null) "переменные" else null,
                    ).joinToString(", ").ifEmpty { "запрос" }
                }) { Text("Вставить из логгера") }
                TextButton(onClick = { query = ""; variables = "{}"; response = ""; status = null }) { Text("Очистить") }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
            if (response.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { clipboard().setPrimaryClip(ClipData.newPlainText("response", response)) }) { Text("Копировать ответ") }
                    TextButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, response) }
                        ctx.startActivity(Intent.createChooser(i, "Ответ API"))
                    }) { Text("Поделиться") }
                }
                SelectionContainer {
                    Text(response, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}


private class Captured(val query: String, val variables: String?, val operation: String?, val url: String?)

/**
 * Разбирает вставленный текст целиком: экспорт логгера («## 1. fetchManga», «URL:», «### Query», «### Variables»),
 * JSON-тело запроса {"query","variables","operationName"} либо просто текст запроса.
 */
private fun parseCaptured(raw: String): Captured {
    val text = raw.trim()
    // 1) JSON-тело запроса
    if (text.startsWith("{")) {
        runCatching {
            val o = JSONObject(text)
            if (o.has("query")) {
                val v = o.opt("variables")
                return Captured(
                    o.getString("query"),
                    when (v) { is JSONObject -> v.toString(2); is String -> v; else -> null },
                    o.optString("operationName").takeIf { it.isNotBlank() && it != "null" },
                    null,
                )
            }
        }
    }
    // 2) экспорт логгера
    if (text.contains("### Query")) {
        val url = Regex("(?m)^URL:\\s*(\\S+)").find(text)?.groupValues?.get(1)
        val section = Regex("(?s)### Query\\s*\\n(.*?)(?=\\n### |\\z)").find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val vars = Regex("(?s)### Variables\\s*\\n(.*?)(?=\\n### |\\z)").find(text)?.groupValues?.get(1)?.trim()
            ?.let { v -> runCatching { if (v.startsWith("{")) JSONObject(v).toString(2) else v }.getOrDefault(v) }
        val op = Regex("(?m)^##\\s*\\d+\\.\\s*(\\w+)").find(text)?.groupValues?.get(1) ?: operationOf(section)
        return Captured(section, vars, op, url)
    }
    // 3) просто запрос
    return Captured(text, null, operationOf(text), null)
}

private fun operationOf(query: String): String? =
    Regex("(?:query|mutation|subscription)\\s+(\\w+)").find(query)?.groupValues?.get(1)
