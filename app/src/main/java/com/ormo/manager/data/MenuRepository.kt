package com.ormo.manager.data

import com.ormo.manager.domain.MenuCategory.ADE
import com.ormo.manager.domain.MenuCategory.COFFEE
import com.ormo.manager.domain.MenuCategory.DESSERT
import com.ormo.manager.domain.MenuCategory.DRINK
import com.ormo.manager.domain.MenuCategory.SMOOTHIE
import com.ormo.manager.domain.MenuItem
import com.ormo.manager.domain.MenuOption
import com.ormo.manager.domain.OptionGroup
import com.ormo.manager.domain.canAdd
import com.ormo.manager.domain.canSave
import com.ormo.manager.domain.isSoldOutFor
import com.ormo.manager.domain.namesUsing
import com.ormo.manager.domain.withMenuSoldOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 삭제한 메뉴. [되돌리기]를 누르면 원래 자리([index])로 돌아온다. */
data class RemovedMenu(val item: MenuItem, val index: Int)

/** 앱 메모리에만 저장하는 메뉴·옵션 목록 */
class InMemoryMenuRepository(
    initial: List<MenuItem> = emptyList(),
    initialOptions: List<OptionGroup> = emptyList(),
) {

    private val state = MutableStateFlow(initial)
    val menus: StateFlow<List<MenuItem>> = state.asStateFlow()

    fun setSoldOut(name: String, soldOut: Boolean) {
        state.update { list -> list.map { if (it.name == name) it.copy(soldOut = soldOut) else it } }
    }

    fun soldOutNames(): Set<String> = state.value.filter { it.soldOut }.map { it.name }.toSet()

    /** 지웠거나 이름을 바꿔 더는 없는 메뉴 이름 */
    private val goneNames = mutableSetOf<String>()

    /** 고객이 주문할 수 없는 메뉴: 품절 · 숨김 · 삭제 · 필수 옵션이 모두 품절 */
    fun unavailableNames(): Set<String> =
        state.value.filter { it.soldOut || it.hidden || lacksRequiredOption(it) }.map { it.name }.toSet() + goneNames

    private fun lacksRequiredOption(menu: MenuItem): Boolean = optionState.value.any { group ->
        group.required && group.name in menu.optionGroups && group.options.all { it.isSoldOutFor(menu.name) }
    }

    /** [original]이 null이면 새 메뉴를 맨 뒤에 더한다. 품절 상태는 지금 값을 그대로 둔다. */
    fun saveMenu(original: String?, item: MenuItem): Boolean {
        val list = state.value
        if (!list.canSave(original, item)) return false
        if (original == null) {
            // 같은 이름이었던 지난 메뉴의 옵션 품절은 이어받지 않는다.
            renameInOptions(item.name, null)
            state.value = list + item
        } else {
            val current = list.firstOrNull { it.name == original } ?: return false
            state.value = list.map { if (it.name == original) item.copy(soldOut = current.soldOut) else it }
            if (original != item.name) {
                goneNames += original
                renameInOptions(original, item.name)
            }
        }
        goneNames -= item.name
        return true
    }

    /** 메뉴별 옵션 품절의 메뉴 이름을 [to]로 바꾼다. null이면 지운다. */
    private fun renameInOptions(from: String, to: String?) {
        optionState.update { groups ->
            groups.map { group ->
                group.copy(
                    options = group.options.map { option ->
                        if (from !in option.soldOutMenus) {
                            option
                        } else {
                            option.copy(soldOutMenus = option.soldOutMenus - from + listOfNotNull(to))
                        }
                    },
                )
            }
        }
    }

    fun removeMenu(name: String): RemovedMenu? {
        val index = state.value.indexOfFirst { it.name == name }
        if (index < 0) return null
        val removed = RemovedMenu(state.value[index], index)
        state.value = state.value.filterNot { it.name == name }
        goneNames += name
        return removed
    }

    /** 그 사이 같은 이름의 메뉴가 생겼으면 되돌리지 않는다. */
    fun restoreMenu(removed: RemovedMenu) {
        val list = state.value
        if (list.any { it.name == removed.item.name }) return
        state.value = list.toMutableList().apply { add(removed.index.coerceAtMost(list.size), removed.item) }
        goneNames -= removed.item.name
    }

    /** 새 옵션 그룹을 만들고 [menuNames]에 연결한다. */
    fun addOptionGroup(group: OptionGroup, menuNames: Set<String>): Boolean {
        if (!optionState.value.canAdd(group)) return false
        optionState.value = optionState.value + group
        state.update { list ->
            list.map { if (it.name in menuNames) it.copy(optionGroups = it.optionGroups + group.name) else it }
        }
        return true
    }

    private val optionState = MutableStateFlow(initialOptions)
    val optionGroups: StateFlow<List<OptionGroup>> = optionState.asStateFlow()

    /** 그 옵션을 쓰는 모든 메뉴에 함께 적용한다. 메뉴별 설정은 지운다. */
    fun setOptionSoldOut(name: String, soldOut: Boolean) {
        optionState.update { groups ->
            groups.map { group ->
                group.copy(
                    options = group.options.map {
                        if (it.name == name) it.copy(soldOut = soldOut, soldOutMenus = emptySet()) else it
                    },
                )
            }
        }
    }

    /** 한 메뉴에서만 품절을 바꾼다. */
    fun setOptionSoldOutForMenu(optionName: String, menuName: String, soldOut: Boolean) {
        optionState.update { groups ->
            groups.map { group ->
                val linked = state.value.namesUsing(group.name)
                group.copy(
                    options = group.options.map {
                        if (it.name == optionName) it.withMenuSoldOut(menuName, soldOut, linked) else it
                    },
                )
            }
        }
    }

    fun isOptionSoldOutFor(optionName: String, menuName: String): Boolean =
        optionState.value.any { group ->
            group.options.any { it.name == optionName && it.isSoldOutFor(menuName) }
        }

    fun soldOutOptionNames(): Set<String> =
        optionState.value.flatMap { it.options }.filter { it.soldOut }.map { it.name }.toSet()
}

/** 서버 연동 전 예시 옵션 그룹(Figma 3.2.2와 같다). */
object MockOptions {
    val initial: List<OptionGroup> = listOf(
        OptionGroup(
            "HOT / ICE", required = true,
            options = listOf(MenuOption("HOT", 0), MenuOption("ICE", 0)),
        ),
        OptionGroup(
            "원두 선택", required = true,
            options = listOf(
                MenuOption("오리지널 원두", 0),
                MenuOption("블렌드 원두", 0, soldOut = true),
            ),
        ),
        OptionGroup(
            "사이즈", required = false,
            options = listOf(
                MenuOption("사이즈업", 900),
                MenuOption("두 배 사이즈업", 1_800, soldOut = true),
            ),
        ),
        OptionGroup(
            "샷 / 시럽 추가", required = false, maxCount = 2,
            options = listOf(
                MenuOption("연하게", 0),
                MenuOption("1샷 추가", 900),
                MenuOption("2샷 추가", 1_200),
                MenuOption("시럽 추가", 1_600),
            ),
        ),
    )
}

/** 서버 연동 전 예시 메뉴. 커피는 Figma 예시와 같고, 다른 카테고리와 옵션 연결은 임의의 예시다. */
object MockMenus {
    private val COFFEE_OPTIONS = listOf("HOT / ICE", "원두 선택", "사이즈", "샷 / 시럽 추가")
    private val SIZE_ONLY = listOf("사이즈")

    private fun coffee(name: String, price: Int, soldOut: Boolean = false, description: String = "") =
        MenuItem(name, price, COFFEE, soldOut, description, COFFEE_OPTIONS)

    val initial: List<MenuItem> = listOf(
        coffee("아메리카노", 4_000),
        coffee("카페라떼", 4_500, description = "부드러운 우유와 에스프레소"),
        coffee("바닐라라떼", 5_000, soldOut = true),
        coffee("돌체라떼", 5_000),
        coffee("콜드브루", 4_500),
        coffee("카푸치노", 4_500),
        MenuItem("크로플", 4_500, DESSERT),
        MenuItem("치즈케이크", 5_500, DESSERT),
        MenuItem("레몬에이드", 4_500, ADE, optionGroups = SIZE_ONLY),
        MenuItem("자몽에이드", 4_500, ADE, optionGroups = SIZE_ONLY),
        MenuItem("딸기 스무디", 5_500, SMOOTHIE),
        MenuItem("망고 스무디", 5_500, SMOOTHIE),
        MenuItem("아이스티", 3_000, DRINK, optionGroups = SIZE_ONLY),
        MenuItem("초코라떼", 4_500, DRINK, optionGroups = listOf("HOT / ICE", "사이즈")),
    )
}
