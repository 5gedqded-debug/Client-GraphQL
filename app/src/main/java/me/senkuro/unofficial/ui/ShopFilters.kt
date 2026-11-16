package me.senkuro.unofficial.ui

import me.senkuro.unofficial.data.ShopItem

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
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
) {
    val onlyLimitedServer get() = limited == Tri.INCLUDE
    val excludePurchasedServer get() = owned == Tri.EXCLUDE

    val activeCount get() = listOf(free, discount, limited, owned).count { it != Tri.OFF } +
        (if (minPrice != null || maxPrice != null) 1 else 0)

    fun matches(s: ShopItem): Boolean {
        fun ok(t: Tri, value: Boolean) = when (t) { Tri.OFF -> true; Tri.INCLUDE -> value; Tri.EXCLUDE -> !value }
        val price = s.price
        if (!ok(free, price == 0)) return false
        if (!ok(discount, price != null && s.basePrice != null && price < s.basePrice)) return false
        if (!ok(limited, s.quantity != null)) return false
        if (!ok(owned, s.owned)) return false
        if (minPrice != null && (price == null || price < minPrice)) return false
        if (maxPrice != null && (price == null || price > maxPrice)) return false
        return true
    }
}
