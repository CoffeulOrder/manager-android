package com.ormo.manager.domain

/** 옵션을 파는 상태. [PARTLY_SOLD_OUT]은 일부 메뉴에서만 품절이다. */
enum class OptionSale { ON_SALE, PARTLY_SOLD_OUT, SOLD_OUT }

/** 이 옵션 그룹을 쓰는 메뉴 이름 */
fun List<MenuItem>.namesUsing(groupName: String): List<String> =
    filter { groupName in it.optionGroups }.map { it.name }

fun MenuOption.isSoldOutFor(menuName: String): Boolean = soldOut || menuName in soldOutMenus

/** [linked]는 이 옵션을 쓰는 메뉴 이름이다. */
fun MenuOption.saleState(linked: List<String>): OptionSale {
    if (soldOut) return OptionSale.SOLD_OUT
    val count = linked.count { it in soldOutMenus }
    return when {
        count == 0 -> OptionSale.ON_SALE
        count == linked.size -> OptionSale.SOLD_OUT
        else -> OptionSale.PARTLY_SOLD_OUT
    }
}

/** 한 메뉴의 스위치를 바꾼다. 쓰는 메뉴가 모두 품절이 되면 옵션 전체 품절로 합친다. */
fun MenuOption.withMenuSoldOut(menuName: String, soldOut: Boolean, linked: List<String>): MenuOption {
    val current = if (this.soldOut) linked.toSet() else soldOutMenus.intersect(linked.toSet())
    val next = if (soldOut) current + menuName else current - menuName
    return if (linked.isNotEmpty() && next.containsAll(linked)) {
        copy(soldOut = true, soldOutMenus = emptySet())
    } else {
        copy(soldOut = false, soldOutMenus = next)
    }
}

/** 필수 옵션 때문에 메뉴를 팔 수 없게 되는 상황 */
sealed interface OptionWarning {
    /** [others]까지 품절되면 메뉴 [menuCount]개를 팔 수 없다. */
    data class LastChoices(val others: List<String>, val menuCount: Int) : OptionWarning

    /** 이미 모든 항목이 품절이라 메뉴 [menuCount]개를 팔 수 없다. */
    data class AllGone(val menuCount: Int) : OptionWarning
}

/** 필수 그룹의 옵션이 어딘가에서 품절일 때만 알려준다. */
fun optionWarning(group: OptionGroup, option: MenuOption, menus: List<MenuItem>): OptionWarning? {
    if (!group.required) return null
    val linked = menus.namesUsing(group.name)
    val soldHere = linked.filter { option.isSoldOutFor(it) }
    if (soldHere.isEmpty()) return null
    val blocked = linked.count { menu -> group.options.all { it.isSoldOutFor(menu) } }
    if (blocked > 0) return OptionWarning.AllGone(blocked)
    val others = group.options
        .filter { it.name != option.name && it.saleState(linked) != OptionSale.SOLD_OUT }
        .map { it.name }
    return OptionWarning.LastChoices(others, soldHere.size)
}

/** 주제 조사: 받침이 있으면 "은", 없거나 한글이 아니면 "는" */
fun topicParticle(word: String): String {
    val last = word.lastOrNull() ?: return "는"
    return if (last in '가'..'힣' && (last - '가') % 28 != 0) "은" else "는"
}
