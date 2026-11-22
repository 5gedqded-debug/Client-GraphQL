package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.senkuro.unofficial.data.RichBlockKind
import me.senkuro.unofficial.data.RichMark
import me.senkuro.unofficial.data.RichNode
import me.senkuro.unofficial.data.richPlainText

/**
 * Содержимое комментария как на сайте: жирный/курсив/зачёркнутый/код, спойлеры (по нажатию), ссылки, цитаты, списки,
 * блоки кода, эмодзи в строке, стикеры, YouTube.
 */
@Composable
fun RichContent(
    nodes: List<RichNode>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 14.sp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RichBlocks(nodes, color, fontSize, bold = false)
    }
}

private sealed class RichGroup {
    class Inline(val nodes: List<RichNode>) : RichGroup()
    class Single(val node: RichNode) : RichGroup()
}

private fun groupNodes(nodes: List<RichNode>): List<RichGroup> {
    val out = ArrayList<RichGroup>()
    var buf = ArrayList<RichNode>()
    fun flush() {
        if (buf.isNotEmpty()) {
            out.add(RichGroup.Inline(buf))
            buf = ArrayList()
        }
    }
    for (n in nodes) {
        when (n) {
            is RichNode.Text, is RichNode.Emoji, is RichNode.Mention, is RichNode.HardBreak -> buf.add(n)
            else -> {
                flush()
                out.add(RichGroup.Single(n))
            }
        }
    }
    flush()
    return out
}

@Composable
private fun RichBlocks(nodes: List<RichNode>, color: Color, fontSize: TextUnit, bold: Boolean) {
    val groups = remember(nodes) { groupNodes(nodes) }
    groups.forEach { g ->
        when (g) {
            is RichGroup.Inline -> RichInline(g.nodes, color, fontSize, bold)
            is RichGroup.Single -> RichBlock(g.node, color, fontSize)
        }
    }
}

@Composable
private fun RichBlock(node: RichNode, color: Color, fontSize: TextUnit) {
    when (node) {
        is RichNode.Block -> when (node.kind) {
            RichBlockKind.HEADING -> RichBlocks(node.children, color, fontSize * (1.55f - 0.1f * (node.level - 1)), bold = true)
            RichBlockKind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RichBlocks(node.children, color.copy(alpha = 0.92f), fontSize, bold = false)
                }
            }
            RichBlockKind.BULLET -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                node.children.forEach { item ->
                    Row {
                        Text("•", color = color, fontSize = fontSize, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RichBlocks((item as? RichNode.Block)?.children ?: listOf(item), color, fontSize, bold = false)
                        }
                    }
                }
            }
            RichBlockKind.ORDERED -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                node.children.forEachIndexed { idx, item ->
                    Row {
                        Text("${node.start + idx}.", color = color, fontSize = fontSize, modifier = Modifier.padding(end = 6.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RichBlocks((item as? RichNode.Block)?.children ?: listOf(item), color, fontSize, bold = false)
                        }
                    }
                }
            }
            RichBlockKind.CODE -> Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(richPlainText(node.children).trimEnd(), color = color, fontFamily = FontFamily.Monospace, fontSize = fontSize * 0.92f)
            }
            RichBlockKind.PARAGRAPH, RichBlockKind.DIV, RichBlockKind.LIST_ITEM ->
                if (node.children.isEmpty()) Spacer(Modifier.height(fontSize.value.dp * 0.7f)) else RichBlocks(node.children, color, fontSize, bold = false)
        }
        is RichNode.Sticker -> AsyncImage(
            model = node.url,
            contentDescription = "Стикер",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.height(128.dp),
        )
        is RichNode.Youtube -> YoutubePreview(node)
        is RichNode.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
        else -> Unit
    }
}

private val YT_ID = Regex("""embed/([\w-]{11})""")

@Composable
private fun YoutubePreview(node: RichNode.Youtube) {
    val id = YT_ID.find(node.src)?.groupValues?.get(1) ?: return
    val uri = LocalUriHandler.current
    Box(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .clickable {
                val t = if (node.start > 0) "&t=${node.start}" else ""
                runCatching { uri.openUri("https://www.youtube.com/watch?v=$id$t") }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage("https://i.ytimg.com/vi/$id/0.jpg", "YouTube", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Text("▶", color = Color.White, fontSize = 20.sp)
        }
    }
}

private class BuiltRich(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

@Composable
private fun RichInline(nodes: List<RichNode>, color: Color, fontSize: TextUnit, bold: Boolean) {
    val uri = LocalUriHandler.current
    var revealed by remember(nodes) { mutableStateOf<Set<Int>>(emptySet()) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val hiddenColor = color.copy(alpha = 0.28f)
    val shownBg = color.copy(alpha = 0.10f)

    val built = remember(nodes, revealed, color, primary, codeBg) {
        val sb = AnnotatedString.Builder()
        val inline = HashMap<String, InlineTextContent>()
        var spoilerIdx = -1
        var prevSpoiler = false
        nodes.forEachIndexed { i, n ->
            when (n) {
                is RichNode.Text -> {
                    val isSpoiler = RichMark.SPOILER in n.marks
                    if (isSpoiler && !prevSpoiler) spoilerIdx++
                    prevSpoiler = isSpoiler
                    val hidden = isSpoiler && spoilerIdx !in revealed
                    val decorations = ArrayList<TextDecoration>()
                    if (RichMark.STRIKE in n.marks) decorations.add(TextDecoration.LineThrough)
                    if (n.href != null && !hidden) decorations.add(TextDecoration.Underline)
                    val style = SpanStyle(
                        color = when {
                            hidden -> hiddenColor
                            n.href != null -> primary
                            else -> Color.Unspecified
                        },
                        fontWeight = if (RichMark.BOLD in n.marks) FontWeight.Bold else null,
                        fontStyle = if (RichMark.ITALIC in n.marks) FontStyle.Italic else null,
                        fontFamily = if (RichMark.CODE in n.marks) FontFamily.Monospace else null,
                        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                        background = when {
                            hidden -> hiddenColor
                            isSpoiler -> shownBg
                            RichMark.CODE in n.marks -> codeBg
                            else -> Color.Unspecified
                        },
                    )
                    val start = sb.length
                    sb.withStyle(style) { append(n.text) }
                    val end = sb.length
                    if (isSpoiler) sb.addStringAnnotation("spoiler", spoilerIdx.toString(), start, end)
                    n.href?.let { sb.addStringAnnotation("url", it, start, end) }
                }
                is RichNode.Emoji -> {
                    prevSpoiler = false
                    if (n.url != null) {
                        val key = "emoji$i"
                        sb.appendInlineContent(key, n.unicode ?: "🙂")
                        inline[key] = InlineTextContent(Placeholder(1.5.em, 1.5.em, PlaceholderVerticalAlign.Center)) {
                            AsyncImage(n.url, n.unicode ?: "emoji", modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        sb.append(n.unicode.orEmpty())
                    }
                }
                is RichNode.Mention -> {
                    prevSpoiler = false
                    sb.withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) { append("@" + n.label) }
                }
                is RichNode.HardBreak -> {
                    prevSpoiler = false
                    sb.append("\n")
                }
                else -> Unit
            }
        }
        BuiltRich(sb.toAnnotatedString(), inline)
    }

    Text(
        text = built.text,
        inlineContent = built.inline,
        color = color,
        fontSize = fontSize,
        fontWeight = if (bold) FontWeight.Bold else null,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(built.text) {
            detectTapGestures { pos ->
                val l = layout ?: return@detectTapGestures
                val off = l.getOffsetForPosition(pos)
                val sp = built.text.getStringAnnotations("spoiler", off, off).firstOrNull()?.item?.toIntOrNull()
                if (sp != null && sp !in revealed) {
                    revealed = revealed + sp
                    return@detectTapGestures
                }
                built.text.getStringAnnotations("url", off, off).firstOrNull()?.let { runCatching { uri.openUri(it.item) } }
            }
        },
    )
}
