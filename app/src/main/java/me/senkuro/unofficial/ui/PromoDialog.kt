package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph

/** Ввод промокода (мутация redeemRedemption) и показ полученной награды. */
@Composable
fun PromoDialog(onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Промокод") },
        text = {
            Column {
                if (result != null) {
                    Text(result!!)
                } else {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it; error = null },
                        singleLine = true, enabled = !busy, label = { Text("Код") },
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        },
        confirmButton = {
            if (result != null) {
                TextButton(onClick = onDismiss) { Text("Готово") }
            } else {
                TextButton(enabled = code.isNotBlank() && !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val r = AppGraph.repository.redeemPromo(code.trim())
                            result = buildList {
                                if (r.points > 0) add("Коины: +${r.points}")
                                if (r.experience > 0) add("Опыт: +${r.experience}")
                                r.collectible?.let { add("Предмет: $it") }
                            }.ifEmpty { listOf("Промокод активирован") }.joinToString("\n")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val m = e.msg()
                            error = when {
                                m.contains("not found", true) -> "Промокод не найден"
                                m.contains("already", true) || m.contains("used", true) -> "Промокод уже использован"
                                m.contains("expire", true) -> "Срок действия промокода истёк"
                                else -> m
                            }
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "Проверка…" else "Активировать") }
            }
        },
        dismissButton = { if (result == null) TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}
