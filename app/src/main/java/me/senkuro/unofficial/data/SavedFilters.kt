package me.senkuro.unofficial.data

import org.json.JSONArray
import org.json.JSONObject

/** Именованный набор фильтров каталога, сохранённый локально. */
data class SavedFilter(val name: String, val filters: CatalogFilters)

object SavedFiltersCodec {
    private fun filtersToJson(f: CatalogFilters) = JSONObject().apply {
        put("orderField", f.orderField)
        put("ascending", f.ascending)
        put("states", JSONObject().also { o -> f.states.forEach { (k, v) -> o.put(k, v.name) } })
        f.chaptersMin?.let { put("chaptersMin", it) }
        f.chaptersMax?.let { put("chaptersMax", it) }
        f.yearMin?.let { put("yearMin", it) }
        f.yearMax?.let { put("yearMax", it) }
    }

    private fun filtersFromJson(o: JSONObject): CatalogFilters {
        val st = LinkedHashMap<String, Tri>()
        o.optJSONObject("states")?.let { s ->
            for (k in s.keys()) {
                val t = runCatching { Tri.valueOf(s.getString(k)) }.getOrNull() ?: continue
                if (t != Tri.NONE) st[k] = t
            }
        }
        fun int(key: String): Int? = if (o.has(key) && !o.isNull(key)) o.optInt(key) else null
        return RestrictedLabels.clean(CatalogFilters(
            orderField = o.optString("orderField", "POPULARITY_SCORE").ifBlank { "POPULARITY_SCORE" },
            ascending = o.optBoolean("ascending", false),
            states = st,
            chaptersMin = int("chaptersMin"), chaptersMax = int("chaptersMax"),
            yearMin = int("yearMin"), yearMax = int("yearMax"),
        ))
    }

    fun encode(list: List<SavedFilter>): String = JSONObject().apply {
        put("type", "senkuro-unofficial-filters")
        put("version", 1)
        put("items", JSONArray().also { a ->
            list.forEach { a.put(JSONObject().put("name", it.name).put("filters", filtersToJson(it.filters))) }
        })
    }.toString()

    /** Разбор сохранённого/импортированного JSON; некорректные элементы пропускаются, при мусоре — null. */
    fun decode(text: String): List<SavedFilter>? = try {
        val items = JSONObject(text.trim()).getJSONArray("items")
        (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            val f = o.optJSONObject("filters") ?: return@mapNotNull null
            if (name.isEmpty()) null else SavedFilter(name, filtersFromJson(f))
        }
    } catch (_: Exception) { null }
}
