package com.ormo.manager.domain

/** 메뉴 카테고리. 선언 순서가 칩 표시 순서다. */
enum class MenuCategory { COFFEE, DESSERT, ADE, SMOOTHIE, DRINK }

/** 옵션 하나. 서버 id가 없어 이름이 곧 식별자라 그룹이 달라도 겹치지 않는다. */
data class MenuOption(
    val name: String,
    /** 추가 금액(원) */
    val extraPrice: Int,
    /** 모든 메뉴에서 품절 */
    val soldOut: Boolean = false,
    /** 전체 품절이 아닐 때, 이 옵션만 품절인 메뉴 이름 */
    val soldOutMenus: Set<String> = emptySet(),
)

data class OptionGroup(
    val name: String,
    val required: Boolean,
    val options: List<MenuOption>,
    /** 고를 수 있는 최대 개수. null이면 표시하지 않는다. */
    val maxCount: Int? = null,
)

data class MenuItem(
    /** 메뉴 이름. 서버 id가 생기기 전까지 이름이 곧 식별자다. */
    val name: String,
    /** 기본 가격(원). 옵션 추가금은 주문에만 반영된다. */
    val price: Int,
    val category: MenuCategory,
    val soldOut: Boolean = false,
    /** 고객 앱 메뉴에 보일 설명 */
    val description: String = "",
    /** 연결한 옵션 그룹 이름. 고객 앱 옵션 선택에 이 순서대로 보인다. */
    val optionGroups: List<String> = emptyList(),
    /** 고객 앱에서 숨긴 메뉴. 품절(오늘만)과 달리 오래 팔지 않을 때 쓴다. */
    val hidden: Boolean = false,
)

/** 이 옵션 그룹을 쓰는 메뉴 수 */
fun List<MenuItem>.countUsing(groupName: String): Int = count { groupName in it.optionGroups }

/** [original](고치는 중인 메뉴, 새 메뉴면 null)이 아닌 다른 메뉴가 같은 이름을 쓰는지 */
fun List<MenuItem>.isNameTaken(name: String, original: String?): Boolean =
    any { it.name == name && it.name != original }

/** 이름과 가격이 있고 이름이 겹치지 않아야 저장한다. */
fun List<MenuItem>.canSave(original: String?, item: MenuItem): Boolean =
    item.name.isNotBlank() && item.price > 0 && !isNameTaken(item.name, original)

/** 그룹 이름이나 항목 이름이 이미 있거나 새 그룹 안에서 겹치는지 */
fun List<OptionGroup>.hasDuplicateName(groupName: String, optionNames: List<String>): Boolean {
    val used = flatMap { group -> group.options.map { it.name } }.toSet()
    return any { it.name == groupName } ||
        optionNames.any { it in used } ||
        optionNames.size != optionNames.toSet().size
}

fun List<OptionGroup>.canAdd(group: OptionGroup): Boolean {
    val names = group.options.map { it.name }
    return group.name.isNotBlank() &&
        names.isNotEmpty() &&
        names.none { it.isBlank() } &&
        !hasDuplicateName(group.name, names)
}
