package me.senkuro.unofficial.data

/**
 * Метки, связанные с темами, попадающими под ст. 6.21 / 6.21.2 КоАП РФ («пропаганда нетрадиционных сексуальных
 * отношений и смены пола»). Скрываются из фильтров каталога (список меток и поиск меток). Сопоставление по
 * названию, slug и алиасам метки, без учёта регистра и «ё». Список можно дополнять в [keywords].
 */
object RestrictedLabels {
    private val keywords = listOf(
        "лгбт", "lgbt", "lgbtq", "яой", "yaoi", "юри", "yuri", "бара", "сёдзё-ай", "сёнэн-ай", "сэйдзин-ай",
        "shoujo ai", "shounen ai", "shonen ai", "shojo ai", "bl", "gl", "boys love", "girls love",
        "гомосексуал", "гей", "лесби", "бисексуал", "квир", "queer", "трансгендер", "transgender", "транс",
        "смена пола", "смена гендера", "гендерная интрига", "гендербендер", "gender bender", "genderswap",
        "кроссдрессинг", "crossdressing", "нетрадиционн", "тоттемо", "тонбой",
    )

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').replace('й', 'и').trim()

    // короткие аббревиатуры сравниваются целиком, остальные — по вхождению
    private val exact = setOf("bl", "gl", "транс", "гей", "бара", "юри", "яой")
    private val normKeywords = keywords.map(::norm)

    fun isRestricted(vararg names: String): Boolean = names.any { n0 ->
        val n = norm(n0)
        n.isNotEmpty() && normKeywords.any { k -> if (k in exact.map(::norm)) n == k || n.split(' ', '-', '_').contains(k) else n.contains(k) }
    }

    fun filterGroups(groups: List<LabelGroup>): List<LabelGroup> = groups.map { g ->
        g.copy(labels = g.labels.filter { l -> !isRestricted(l.title, l.slug, *l.aliases.toTypedArray()) })
    }.filter { it.labels.isNotEmpty() }

    /** Убирает из набора фильтров состояния меток, которые скрыты (импорт/старые сохранённые наборы). */
    fun clean(f: CatalogFilters): CatalogFilters {
        val prefix = "${FilterDefs.GROUP_LABEL}:"
        return f.copy(states = f.states.filterKeys { !(it.startsWith(prefix) && isRestricted(it.removePrefix(prefix))) })
    }
}
