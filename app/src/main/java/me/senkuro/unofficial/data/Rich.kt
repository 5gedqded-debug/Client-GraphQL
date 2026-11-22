package me.senkuro.unofficial.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Текст комментариев на сайте — Tiptap-документ: абзацы, цитаты, списки, код, метки (жирный, курсив, зачёркнутый,
 * спойлер, код, ссылка), а также узлы «эмодзи», «стикер», «упоминание», «YouTube». Здесь:
 *  - модель узлов и разбор ответа сайта (RichDto → RichNode);
 *  - перевод документа в простую разметку для правки (richToMarkup) и обратно в Tiptap JSON для отправки (RichMarkup).
 *
 * Разметка поля ввода (как у редактора сайта + привычные BB-коды):
 *   **жирный**  [b]..[/b]      *курсив*  [i]..[/i]      ~~зачёркнутый~~  [s]..[/s]
 *   ||спойлер||  [spoiler]..[/spoiler]      `код`  [code]..[/code]      [url=https://…]текст[/url] или просто ссылка
 *   > цитата  [quote]..[/quote]      - список      1. нумерованный      ```блок кода```
 *   :имя: — эмодзи из ваших наборов (вставляется из панели).
 */

enum class RichMark { BOLD, ITALIC, STRIKE, CODE, SPOILER }

enum class RichBlockKind { PARAGRAPH, QUOTE, BULLET, ORDERED, LIST_ITEM, CODE, HEADING, DIV }

sealed class RichNode {
    data class Text(val text: String, val marks: Set<RichMark> = emptySet(), val href: String? = null) : RichNode()
    data class Emoji(val id: String, val url: String?, val unicode: String?) : RichNode()
    data class Sticker(val id: String, val url: String?) : RichNode()
    data class Mention(val id: String, val label: String) : RichNode()
    data class Youtube(val src: String, val start: Int) : RichNode()
    data class Block(val kind: RichBlockKind, val children: List<RichNode>, val level: Int = 1, val start: Int = 1) : RichNode()
    object HardBreak : RichNode()
    object Rule : RichNode()
}

// ------------------------------------------------------------------------------------------ разбор ответа сайта

private fun norm(s: String?): String = s.orEmpty().lowercase().replace("_", "").replace("-", "")

fun List<RichDto>?.toRich(): List<RichNode> = this.orEmpty().mapNotNull { it.toRichNode() }

private fun RichDto.toRichNode(): RichNode? {
    val tn = norm(typename?.removePrefix("TiptapNode"))
    val ty = norm(type)
    val kind = when (tn) {
        "nestedblock", "onlyblock", "" -> ty
        else -> tn
    }
    val kids = content.toRich()
    return when (kind) {
        "text" -> {
            val set = HashSet<RichMark>()
            var href: String? = null
            for (m in marks.orEmpty()) {
                when (norm(m.type)) {
                    "bold" -> set.add(RichMark.BOLD)
                    "italic" -> set.add(RichMark.ITALIC)
                    "strike" -> set.add(RichMark.STRIKE)
                    "code" -> set.add(RichMark.CODE)
                    "spoiler" -> set.add(RichMark.SPOILER)
                    "link" -> href = m.attrs?.href
                }
                if (m.attrs?.href != null && norm(m.typename).endsWith("link")) href = m.attrs?.href
            }
            text?.let { RichNode.Text(it, set, href) }
        }
        "emoji" -> RichNode.Emoji(attrs?.id.orEmpty(), attrs?.url, attrs?.unicode)
        "sticker" -> RichNode.Sticker(attrs?.id.orEmpty(), attrs?.url)
        "mention" -> RichNode.Mention(attrs?.id.orEmpty(), attrs?.label.orEmpty())
        "youtube" -> RichNode.Youtube(attrs?.src.orEmpty(), attrs?.start?.toInt() ?: 0)
        "heading" -> RichNode.Block(RichBlockKind.HEADING, kids, level = (attrs?.level ?: 1).coerceIn(1, 6))
        "orderedlist" -> RichNode.Block(RichBlockKind.ORDERED, kids, start = attrs?.start?.toInt() ?: 1)
        "codeblock" -> RichNode.Block(RichBlockKind.CODE, kids)
        "paragraph" -> RichNode.Block(RichBlockKind.PARAGRAPH, kids)
        "blockquote" -> RichNode.Block(RichBlockKind.QUOTE, kids)
        "bulletlist" -> RichNode.Block(RichBlockKind.BULLET, kids)
        "listitem" -> RichNode.Block(RichBlockKind.LIST_ITEM, kids)
        "hardbreak" -> RichNode.HardBreak
        "horizontalrule" -> RichNode.Rule
        else -> if (content != null) RichNode.Block(RichBlockKind.DIV, kids) else null
    }
}

/** Простой текст комментария (для случаев, когда рисовать разметку нечем). */
fun richPlainText(nodes: List<RichNode>): String = buildString { appendPlain(nodes) }.trim()

private fun StringBuilder.appendPlain(nodes: List<RichNode>) {
    for (n in nodes) {
        when (n) {
            is RichNode.Text -> append(n.text)
            is RichNode.Emoji -> append(n.unicode.orEmpty())
            is RichNode.Sticker -> append("[стикер]")
            is RichNode.Mention -> append("@").append(n.label)
            is RichNode.Youtube -> append(n.src)
            is RichNode.HardBreak -> append('\n')
            is RichNode.Rule -> append("\n---\n")
            is RichNode.Block -> {
                appendPlain(n.children)
                append('\n')
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ токены вложенных узлов

/**
 * Узлы, которых нельзя набрать текстом (эмодзи, стикер, упоминание, YouTube, линия), в поле ввода выглядят как `:имя:`.
 * Здесь хранится соответствие «имя → узел Tiptap», чтобы при отправке вернуть настоящий узел.
 */
object RichTokens {
    private val nodes = ConcurrentHashMap<String, JsonObject>()

    fun node(name: String): JsonObject? = nodes[name]

    private fun clean(s: String): String = s.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun register(name: String, node: JsonObject): String {
        nodes[name] = node
        return ":$name:"
    }

    private fun uniqueName(base: String, id: String): String {
        val existing = nodes[base]
        if (existing == null || existing.idOf() == id) return base
        return base + "_" + clean(id).takeLast(4)
    }

    private fun JsonObject.idOf(): String =
        ((this["attrs"] as? JsonObject)?.get("id") as? JsonPrimitive)?.content.orEmpty()

    /** Эмодзи из панели: токен `:slug:`. */
    fun emoji(id: String, slug: String?, url: String?): String {
        val base = slug?.let(::clean)?.takeIf { it.length >= 2 } ?: ("e" + clean(id).takeLast(10))
        val name = uniqueName(base, id)
        return register(name, buildJsonObject {
            put("type", "emoji")
            put("attrs", buildJsonObject {
                put("id", id)
                put("url", url?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        })
    }

    fun tokenFor(n: RichNode): String = when (n) {
        is RichNode.Emoji -> emoji(n.id, null, n.url)
        is RichNode.Sticker -> register("s" + clean(n.id).takeLast(10), buildJsonObject {
            put("type", "sticker")
            put("attrs", buildJsonObject {
                put("id", n.id)
                put("url", n.url?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        })
        is RichNode.Mention -> register("m" + clean(n.id).takeLast(10), buildJsonObject {
            put("type", "mention")
            put("attrs", buildJsonObject {
                put("id", n.id)
                put("label", n.label)
            })
        })
        is RichNode.Youtube -> register("y" + clean(n.src).takeLast(11), buildJsonObject {
            put("type", "youtube")
            put("attrs", buildJsonObject {
                put("src", n.src)
                put("start", n.start)
            })
        })
        is RichNode.Rule -> register("hr", buildJsonObject { put("type", "horizontalRule") })
        else -> ""
    }

    fun stickerNode(id: String, url: String?): JsonObject = buildJsonObject {
        put("type", "sticker")
        put("attrs", buildJsonObject {
            put("id", id)
            put("url", url?.let { JsonPrimitive(it) } ?: JsonNull)
        })
    }
}

// ------------------------------------------------------------------------------------------ документ → разметка (правка)

fun richToMarkup(nodes: List<RichNode>): String {
    val lines = ArrayList<String>()
    blocksToLines(nodes, "", lines)
    return lines.joinToString("\n").trimEnd('\n')
}

private fun inlineMarkup(n: RichNode): String = when (n) {
    is RichNode.Text -> {
        var s = n.text
        if (RichMark.CODE in n.marks) {
            s = "`$s`"
        } else {
            if (RichMark.BOLD in n.marks) s = "**$s**"
            if (RichMark.ITALIC in n.marks) s = "*$s*"
            if (RichMark.STRIKE in n.marks) s = "~~$s~~"
            if (RichMark.SPOILER in n.marks) s = "||$s||"
        }
        val href = n.href
        if (href != null && href != n.text) "[url=$href]$s[/url]" else s
    }
    is RichNode.Emoji -> RichTokens.tokenFor(n)
    is RichNode.Mention -> RichTokens.tokenFor(n)
    else -> ""
}

private fun blocksToLines(nodes: List<RichNode>, prefix: String, out: MutableList<String>) {
    val inline = StringBuilder()
    fun flush() {
        if (inline.isNotEmpty()) {
            out.add(prefix + inline.toString())
            inline.setLength(0)
        }
    }
    for (n in nodes) {
        when (n) {
            is RichNode.Text, is RichNode.Emoji, is RichNode.Mention -> inline.append(inlineMarkup(n))
            is RichNode.HardBreak -> {
                out.add(prefix + inline.toString())
                inline.setLength(0)
            }
            is RichNode.Sticker, is RichNode.Youtube, is RichNode.Rule -> {
                flush()
                out.add(prefix + RichTokens.tokenFor(n))
            }
            is RichNode.Block -> {
                flush()
                blockLines(n, prefix, out)
            }
        }
    }
    flush()
}

private fun innerLines(children: List<RichNode>): List<String> {
    val tmp = ArrayList<String>()
    blocksToLines(children, "", tmp)
    return tmp
}

private fun blockLines(n: RichNode.Block, prefix: String, out: MutableList<String>) {
    when (n.kind) {
        RichBlockKind.QUOTE -> {
            val tmp = innerLines(n.children)
            if (tmp.isEmpty()) out.add("$prefix>") else tmp.forEach { out.add("$prefix> $it") }
        }
        RichBlockKind.BULLET -> n.children.forEach { item ->
            val tmp = innerLines((item as? RichNode.Block)?.children ?: listOf(item))
            if (tmp.isEmpty()) out.add("$prefix- ") else tmp.forEach { out.add("$prefix- $it") }
        }
        RichBlockKind.ORDERED -> {
            var num = n.start
            n.children.forEach { item ->
                val tmp = innerLines((item as? RichNode.Block)?.children ?: listOf(item))
                if (tmp.isEmpty()) out.add("$prefix$num. ") else tmp.forEach { out.add("$prefix$num. $it"); num++ }
                if (tmp.isEmpty()) num++
            }
        }
        RichBlockKind.CODE -> {
            out.add("$prefix```")
            richPlainText(n.children).split("\n").forEach { out.add(prefix + it) }
            out.add("$prefix```")
        }
        else -> {
            val tmp = innerLines(n.children)
            if (tmp.isEmpty()) out.add(prefix) else tmp.forEach { out.add(prefix + it) }
        }
    }
}

// ------------------------------------------------------------------------------------------ разметка → документ (отправка)

object RichMarkup {
    private val BULLET = Regex("""^\s*[-•]\s+(.*)$""")
    private val ORDERED = Regex("""^\s*(\d{1,3})[.)]\s+(.*)$""")
    private val QUOTE_INLINE = Regex("""^\[quote](.*)\[/quote]$""", RegexOption.IGNORE_CASE)
    private val TOKEN = Regex(""":([\p{L}\p{N}_\-]{1,48}):""")
    private val BB = Regex("""\[(/?)(b|i|s|spoiler|sp|url(?:=[^\]\s]+)?)]""", RegexOption.IGNORE_CASE)
    private val BLOCK_TOKENS = setOf("sticker", "youtube", "horizontalRule")
    private val INLINE_TOKENS = setOf("emoji", "mention")

    fun toContent(source: String): JsonArray {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val out = ArrayList<JsonElement>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") || trimmed.equals("[code]", true) -> {
                    val close = if (trimmed.startsWith("```")) "```" else "[/code]"
                    val body = ArrayList<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(close, true)) {
                        body.add(lines[i])
                        i++
                    }
                    i++
                    out.add(codeBlock(body.joinToString("\n")))
                }
                trimmed.equals("[quote]", true) -> {
                    val body = ArrayList<JsonElement>()
                    i++
                    while (i < lines.size && !lines[i].trim().equals("[/quote]", true)) {
                        body.add(paragraph(inline(lines[i])))
                        i++
                    }
                    i++
                    out.add(node("blockquote", body))
                }
                QUOTE_INLINE.matches(trimmed) -> {
                    val inner = QUOTE_INLINE.matchEntire(trimmed)!!.groupValues[1]
                    out.add(node("blockquote", listOf(paragraph(inline(inner)))))
                    i++
                }
                trimmed.startsWith(">") -> {
                    val body = ArrayList<JsonElement>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        body.add(paragraph(inline(lines[i].trim().removePrefix(">").removePrefix(" "))))
                        i++
                    }
                    out.add(node("blockquote", body))
                }
                BULLET.matches(line) -> {
                    val items = ArrayList<JsonElement>()
                    while (i < lines.size && BULLET.matches(lines[i])) {
                        items.add(node("listItem", listOf(paragraph(inline(BULLET.matchEntire(lines[i])!!.groupValues[1])))))
                        i++
                    }
                    out.add(node("bulletList", items))
                }
                ORDERED.matches(line) -> {
                    val items = ArrayList<JsonElement>()
                    val start = ORDERED.matchEntire(line)!!.groupValues[1].toInt()
                    while (i < lines.size && ORDERED.matches(lines[i])) {
                        items.add(node("listItem", listOf(paragraph(inline(ORDERED.matchEntire(lines[i])!!.groupValues[2])))))
                        i++
                    }
                    out.add(buildJsonObject {
                        put("type", "orderedList")
                        put("attrs", buildJsonObject { put("start", start) })
                        put("content", JsonArray(items))
                    })
                }
                else -> {
                    val whole = TOKEN.matchEntire(trimmed)?.groupValues?.get(1)?.let { RichTokens.node(it) }
                    val type = (whole?.get("type") as? JsonPrimitive)?.content
                    if (whole != null && type in BLOCK_TOKENS) out.add(whole) else out.add(paragraph(inline(line)))
                    i++
                }
            }
        }
        while (out.size > 1 && isEmptyParagraph(out[out.lastIndex])) out.removeAt(out.lastIndex)
        if (out.isEmpty()) out.add(paragraph(emptyList()))
        return JsonArray(out)
    }

    private fun isEmptyParagraph(e: JsonElement): Boolean =
        e is JsonObject && (e["type"] as? JsonPrimitive)?.content == "paragraph" && e["content"] == null

    private fun node(type: String, content: List<JsonElement>): JsonObject = buildJsonObject {
        put("type", type)
        put("content", JsonArray(content))
    }

    private fun paragraph(content: List<JsonElement>): JsonObject = buildJsonObject {
        put("type", "paragraph")
        if (content.isNotEmpty()) put("content", JsonArray(content))
    }

    private fun codeBlock(code: String): JsonObject = buildJsonObject {
        put("type", "codeBlock")
        put("attrs", buildJsonObject { put("language", JsonNull) })
        if (code.isNotEmpty()) put("content", JsonArray(listOf(buildJsonObject { put("type", "text"); put("text", code) })))
    }

    // ---- строчная разметка

    private sealed class Tok {
        class Txt(val s: String, val href: String? = null) : Tok()
        class Code(val s: String) : Tok()
        class Node(val json: JsonObject) : Tok()
        class Mk(
            val kind: String,
            val raw: String,
            val explicit: Int, // 0 — симметричный маркер (**), 1 — открывающий BB-тег, -1 — закрывающий
            var href: String?,
            val canOpen: Boolean,
            val canClose: Boolean,
        ) : Tok() {
            var role = 0 // 1 — открывает пару, -1 — закрывает, 0 — не парный (останется обычным текстом)
        }
    }

    private fun tokenize(s: String): List<Tok> {
        val toks = ArrayList<Tok>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                toks.add(Tok.Txt(buf.toString()))
                buf.setLength(0)
            }
        }
        fun symmetric(kind: String, raw: String, at: Int): Tok.Mk {
            val before = if (at > 0) s[at - 1] else ' '
            val after = if (at + raw.length < s.length) s[at + raw.length] else ' '
            return Tok.Mk(kind, raw, 0, null, canOpen = !after.isWhitespace(), canClose = !before.isWhitespace())
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '[') {
                if (s.startsWith("[code]", i, true)) {
                    val j = s.indexOf("[/code]", i + 6, true)
                    if (j >= 0) {
                        flush()
                        toks.add(Tok.Code(s.substring(i + 6, j)))
                        i = j + 7
                        continue
                    }
                }
                val m = BB.find(s, i)
                if (m != null && m.range.first == i) {
                    val closing = m.groupValues[1] == "/"
                    val name = m.groupValues[2].lowercase()
                    val kind = when {
                        name == "b" -> "bold"
                        name == "i" -> "italic"
                        name == "s" -> "strike"
                        name == "spoiler" || name == "sp" -> "spoiler"
                        else -> "link"
                    }
                    val href = if (kind == "link" && name.contains('=')) name.substringAfter('=') else null
                    // в href регистр важен — берём из исходной строки
                    val realHref = if (href != null) m.groupValues[2].substringAfter('=') else null
                    flush()
                    toks.add(Tok.Mk(kind, m.value, if (closing) -1 else 1, realHref, canOpen = !closing, canClose = closing))
                    i = m.range.last + 1
                    continue
                }
            }
            if (s.startsWith("**", i)) { flush(); toks.add(symmetric("bold", "**", i)); i += 2; continue }
            if (s.startsWith("~~", i)) { flush(); toks.add(symmetric("strike", "~~", i)); i += 2; continue }
            if (s.startsWith("||", i)) { flush(); toks.add(symmetric("spoiler", "||", i)); i += 2; continue }
            if (c == '*') { flush(); toks.add(symmetric("italic", "*", i)); i += 1; continue }
            if (c == '`') {
                val j = s.indexOf('`', i + 1)
                if (j > i + 1) {
                    flush()
                    toks.add(Tok.Code(s.substring(i + 1, j)))
                    i = j + 1
                    continue
                }
            }
            if (c == ':') {
                val m = TOKEN.find(s, i)
                if (m != null && m.range.first == i) {
                    val json = RichTokens.node(m.groupValues[1])
                    val type = (json?.get("type") as? JsonPrimitive)?.content
                    if (json != null && type in INLINE_TOKENS) {
                        flush()
                        toks.add(Tok.Node(json))
                        i = m.range.last + 1
                        continue
                    }
                }
            }
            if ((c == 'h' || c == 'H') && (s.startsWith("http://", i, true) || s.startsWith("https://", i, true))) {
                var end = i
                while (end < s.length && !s[end].isWhitespace() && s[end] !in "<>[]\"*|`") end++
                while (end > i && s[end - 1] in ".,;:!?)") end--
                if (end - i > 8) {
                    flush()
                    val url = s.substring(i, end)
                    toks.add(Tok.Txt(url, url))
                    i = end
                    continue
                }
            }
            buf.append(c)
            i++
        }
        flush()
        return toks
    }

    private fun pair(toks: List<Tok>) {
        val open = HashMap<String, Int>()
        val bb = HashMap<String, ArrayList<Int>>()
        for ((idx, t) in toks.withIndex()) {
            if (t !is Tok.Mk) continue
            when (t.explicit) {
                0 -> {
                    val o = open[t.kind]
                    if (t.canClose && o != null) {
                        (toks[o] as Tok.Mk).role = 1
                        t.role = -1
                        open.remove(t.kind)
                    } else if (t.canOpen) {
                        open[t.kind] = idx
                    }
                }
                1 -> bb.getOrPut(t.kind) { ArrayList() }.add(idx)
                else -> {
                    val stack = bb[t.kind]
                    if (stack != null && stack.isNotEmpty()) {
                        val o = stack.removeAt(stack.lastIndex)
                        (toks[o] as Tok.Mk).role = 1
                        t.role = -1
                        val opener = toks[o] as Tok.Mk
                        if (opener.kind == "link" && opener.href == null) {
                            opener.href = toks.subList(o + 1, idx).joinToString("") { (it as? Tok.Txt)?.s.orEmpty() }
                        }
                    }
                }
            }
        }
    }

    private fun inline(line: String): List<JsonElement> {
        if (line.isEmpty()) return emptyList()
        val toks = tokenize(line)
        pair(toks)
        val out = ArrayList<JsonElement>()
        val counts = HashMap<String, Int>()
        val hrefs = ArrayList<String?>()
        var pendingText = StringBuilder()
        var pendingKey = ""
        var pendingMarks: JsonArray? = null

        fun flush() {
            if (pendingText.isNotEmpty()) {
                val text = pendingText.toString()
                val marks = pendingMarks
                out.add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                    if (marks != null && marks.isNotEmpty()) put("marks", marks)
                })
                pendingText = StringBuilder()
            }
        }

        fun marksJson(kinds: List<String>, href: String?): JsonArray? {
            val list = ArrayList<JsonElement>()
            for (k in kinds) list.add(buildJsonObject { put("type", k) })
            if (href != null) list.add(buildJsonObject {
                put("type", "link")
                put("attrs", buildJsonObject { put("href", href) })
            })
            return if (list.isEmpty()) null else JsonArray(list)
        }

        fun add(text: String, kinds: List<String>, href: String?) {
            if (text.isEmpty()) return
            val key = kinds.joinToString(",") + "|" + href.orEmpty()
            if (key != pendingKey) {
                flush()
                pendingKey = key
                pendingMarks = marksJson(kinds, href)
            }
            pendingText.append(text)
        }

        fun activeKinds(): List<String> =
            listOf("bold", "italic", "strike", "spoiler").filter { (counts[it] ?: 0) > 0 }

        for (t in toks) {
            when (t) {
                is Tok.Txt -> add(t.s, activeKinds(), t.href ?: hrefs.lastOrNull())
                is Tok.Code -> add(t.s, listOf("code"), null)
                is Tok.Node -> {
                    flush()
                    pendingKey = ""
                    out.add(t.json)
                }
                is Tok.Mk -> when (t.role) {
                    1 -> if (t.kind == "link") hrefs.add(t.href) else counts[t.kind] = (counts[t.kind] ?: 0) + 1
                    -1 -> if (t.kind == "link") { if (hrefs.isNotEmpty()) hrefs.removeAt(hrefs.lastIndex) } else counts[t.kind] = (counts[t.kind] ?: 1) - 1
                    else -> add(t.raw, activeKinds(), hrefs.lastOrNull())
                }
            }
        }
        flush()
        return out
    }
}
