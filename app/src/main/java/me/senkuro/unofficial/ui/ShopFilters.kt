package me.senkuro.unofficial.ui

import me.senkuro.unofficial.data.ShopItem
import me.senkuro.unofficial.data.ShopServer

/** Три состояния условия фильтра: не учитывать / только такие (включить) / кроме таких (исключить). */
enum class Tri { OFF, INCLUDE, EXCLUDE }

/**
 * Фильтры Senkoshop по цене и наличию. «Ограниченное количество» и «Куплено → исключить» сервер умеет сам
 * (onlyLimited / excludePurchased), остальное проверяется на устройстве по уже загруженным товарам.
 */
data class ShopFilters(
    val free: Tri = Tri.OFF,
    val discount: Tri = Tri.OFF,
    val limited: Tri = Tri.OFF,
    val owned: Tri = Tri.OFF,
    val subscription: Tri = Tri.OFF,
    /** Возрастной рейтинг (GENERAL, SENSITIVE, QUESTIONABLE, EXPLICIT) -> состояние. */
    val ratings: Map<String, Tri> = emptyMap(),
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
) {
    val server get() = ShopServer(
        onlyLimited = limited == Tri.INCLUDE,
        onlyWithSubscription = subscription == Tri.INCLUDE,
        excludePurchased = owned == Tri.EXCLUDE,
        ratingInclude = ratings.filterValues { it == Tri.INCLUDE }.keys.toList(),
        ratingExclude = ratings.filterValues { it == Tri.EXCLUDE }.keys.toList(),
    )

    fun rating(key: String): Tri = ratings[key] ?: Tri.OFF
    fun withRating(key: String, t: Tri) = copy(ratings = if (t == Tri.OFF) ratings - key else ratings + (key to t))

    val activeCount get() = listOf(free, discount, limited, owned, subscription).count { it != Tri.OFF } +
        ratings.size + (if (minPrice != null || maxPrice != null) 1 else 0)

    fun matches(s: ShopItem): Boolean {
        fun ok(t: Tri, value: Boolean) = when (t) { Tri.OFF -> true; Tri.INCLUDE -> value; Tri.EXCLUDE -> !value }
        val price = s.price
        if (!ok(free, price == 0)) return false
        if (!ok(discount, price != null && s.basePrice != null && price < s.basePrice)) return false
        if (!ok(limited, s.quantity != null)) return false
        if (!ok(owned, s.owned)) return false
        if (!ok(subscription, s.subscriptionPrice != null)) return false
        val inc = ratings.filterValues { it == Tri.INCLUDE }.keys
        val exc = ratings.filterValues { it == Tri.EXCLUDE }.keys
        if (inc.isNotEmpty() && s.rating !in inc) return false
        if (s.rating in exc) return false
        if (minPrice != null && (price == null || price < minPrice)) return false
        if (maxPrice != null && (price == null || price > maxPrice)) return false
        return true
    }
}
