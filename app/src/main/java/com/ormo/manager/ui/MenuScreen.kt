package com.ormo.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.MenuCategory
import com.ormo.manager.domain.MenuItem
import com.ormo.manager.domain.OptionGroup
import com.ormo.manager.domain.OptionSale
import com.ormo.manager.domain.namesUsing
import com.ormo.manager.domain.saleState
import com.ormo.manager.domain.formatWon
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

private enum class MenuSection { ITEMS, OPTIONS }

private enum class OptionFilter { ALL, ON_SALE, SOLD_OUT }

/** 오른쪽 패널에 연 것 */
private sealed interface Editor {
    /** [original]이 null이면 새 메뉴 */
    data class Menu(val original: String?) : Editor

    data object NewOption : Editor

    /** 옵션 상세. 품절만 바꾸므로 영업 중에도 비밀번호 없이 연다. */
    data class Option(val name: String) : Editor
}

/** 추가·수정·삭제를 열 수 있는 조건: 마감 + 2차 비밀번호 */
class MenuAccess(
    val canEdit: Boolean,
    val unlocked: Boolean,
    val onRequestUnlock: () -> Unit,
    val onLocked: () -> Unit,
)

class MenuActions(
    val onSoldOutChange: (String, Boolean) -> Unit,
    val onOptionSoldOutChange: (String, Boolean) -> Unit,
    val onOptionMenuSoldOutChange: (String, String, Boolean) -> Unit,
    val onSaveMenu: (String?, MenuItem) -> Boolean,
    val onDeleteMenu: (String) -> Unit,
    val onAddOptionGroup: (OptionGroup, Set<String>) -> Boolean,
    val onUploadPhoto: suspend (ByteArray) -> Result<String>,
    val onNoCamera: () -> Unit,
    /** 저장 버튼이 있는 편집 패널이 열리고 닫힐 때 */
    val onEditorOpenChange: (Boolean) -> Unit = {},
)

/**
 * 메뉴 관리(Figma 3.2). 품절 스위치는 영업 중에도 바로 저장하고 비밀번호를 묻지 않는다.
 * 추가·수정·삭제·숨김은 [MenuAccess]를 통과해야 열린다. 옵션 상세는 품절만 다뤄 바로 열린다. 메뉴 사진은 아직 없다.
 */
@Composable
fun MenuScreen(
    menus: List<MenuItem>,
    optionGroups: List<OptionGroup>,
    access: MenuAccess,
    actions: MenuActions,
    modifier: Modifier = Modifier,
    /** 왼쪽 아래에 계속 떠 있는 알림의 높이. 목록이 그 위에서 끝나 마지막 줄이 가려지지 않게 한다. */
    bottomInset: Dp = 0.dp,
) {
    var section by rememberSaveable { mutableStateOf(MenuSection.ITEMS) }
    var category by rememberSaveable { mutableStateOf(MenuCategory.COFFEE) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var pending by remember { mutableStateOf<Editor?>(null) }

    fun open(target: Editor) {
        when {
            !access.canEdit -> access.onLocked()
            access.unlocked -> editor = target
            else -> {
                pending = target
                access.onRequestUnlock()
            }
        }
    }
    LaunchedEffect(access.unlocked, pending) {
        if (access.unlocked && pending != null) {
            editor = pending
            pending = null
        }
    }
    // 고치는 중에 영업을 시작하면 닫는다.
    LaunchedEffect(access.canEdit) {
        if (!access.canEdit) {
            if (editor !is Editor.Option) editor = null
            pending = null
        }
    }
    val isEditing = editor != null && editor !is Editor.Option
    DisposableEffect(isEditing) {
        actions.onEditorOpenChange(isEditing)
        onDispose { actions.onEditorOpenChange(false) }
    }
    val close = { editor = null }
    BackHandler(enabled = editor != null) { close() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundLower),
    ) {
        SubTabs(
            section,
            onSelect = {
                section = it
                close()
            },
        )
        val opened = editor
        if (opened == null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp + bottomInset),
            ) {
                when (section) {
                    MenuSection.ITEMS -> {
                        Toolbar(stringResource(R.string.add_menu), { open(Editor.Menu(null)) }) {
                            MenuCategory.entries.forEach {
                                SelectChip(
                                    text = stringResource(categoryRes(it)),
                                    selected = category == it,
                                    textStyle = OrmoType.BodyMdMedium,
                                ) { category = it }
                            }
                        }
                        MenuList(
                            menus = menus.filter { it.category == category },
                            selected = null,
                            onSoldOutChange = actions.onSoldOutChange,
                            onOpen = { open(Editor.Menu(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    MenuSection.OPTIONS -> OptionsPane(
                        menus = menus,
                        groups = optionGroups,
                        actions = actions,
                        onOpen = { editor = Editor.Option(it) },
                        onAdd = { open(Editor.NewOption) },
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            ) {
                // 알림은 왼쪽 목록만 가리므로 오른쪽 패널은 줄이지 않는다.
                val listModifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight()
                    .padding(bottom = bottomInset)
                val panelModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                when (opened) {
                    is Editor.Menu -> {
                        MenuList(
                            menus = menus.filter { it.category == category },
                            selected = opened.original,
                            onSoldOutChange = actions.onSoldOutChange,
                            onOpen = { open(Editor.Menu(it)) },
                            modifier = listModifier.background(
                                OrmoColors.BackgroundDefault,
                                RoundedCornerShape(16.dp),
                            ),
                        )
                        val original = menus.firstOrNull { it.name == opened.original }
                        key(opened) {
                            MenuEditorPanel(
                                original = original,
                                newCategory = category,
                                menus = menus,
                                optionGroups = optionGroups,
                                onSave = { item ->
                                    if (actions.onSaveMenu(original?.name, item)) {
                                        category = item.category
                                        close()
                                    }
                                },
                                onDelete = {
                                    original?.let { actions.onDeleteMenu(it.name) }
                                    close()
                                },
                                onAddOptionGroup = actions.onAddOptionGroup,
                                onClose = close,
                                onUploadPhoto = actions.onUploadPhoto,
                                onNoCamera = actions.onNoCamera,
                                modifier = panelModifier,
                            )
                        }
                    }
                    is Editor.Option -> {
                        OptionColumn(
                            groups = optionGroups,
                            menus = menus,
                            filter = OptionFilter.ALL,
                            actions = actions,
                            onOpen = { editor = Editor.Option(it) },
                            selected = opened.name,
                            modifier = listModifier,
                        )
                        val group = optionGroups.firstOrNull { g -> g.options.any { it.name == opened.name } }
                        val option = group?.options?.firstOrNull { it.name == opened.name }
                        if (group != null && option != null) {
                            key(opened) {
                                OptionDetailPanel(
                                    group = group,
                                    option = option,
                                    menus = menus,
                                    onAllSoldOutChange = { actions.onOptionSoldOutChange(option.name, it) },
                                    onMenuSoldOutChange = { menuName, soldOut ->
                                        actions.onOptionMenuSoldOutChange(option.name, menuName, soldOut)
                                    },
                                    onClose = close,
                                    modifier = panelModifier,
                                )
                            }
                        }
                    }
                    Editor.NewOption -> {
                        OptionColumn(
                            groups = optionGroups,
                            menus = menus,
                            filter = OptionFilter.ALL,
                            actions = actions,
                            onOpen = { editor = Editor.Option(it) },
                            modifier = listModifier,
                        )
                        NewOptionPanel(
                            menus = menus,
                            optionGroups = optionGroups,
                            preselected = emptySet(),
                            onAdd = { group, linked ->
                                if (actions.onAddOptionGroup(group, linked)) close()
                            },
                            onClose = close,
                            modifier = panelModifier,
                        )
                    }
                }
            }
        }
    }
}

/** 칩 줄 + 오른쪽 [＋ 추가] */
@Composable
private fun Toolbar(addText: String, onAdd: () -> Unit, chips: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            chips()
        }
        Spacer(Modifier.weight(1f))
        AddButton(addText, onAdd)
    }
}

/** 판매하는 메뉴 아래에 "숨긴 메뉴" 묶음을 둔다. */
@Composable
private fun MenuList(
    menus: List<MenuItem>,
    selected: String?,
    onSoldOutChange: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    val (hidden, shown) = menus.partition { it.hidden }
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(OrmoColors.BackgroundDefault),
    ) {
        items(shown, key = { it.name }) { menu ->
            SaleRow(
                title = menu.name,
                subtitle = formatWon(menu.price),
                soldOut = menu.soldOut,
                selected = menu.name == selected,
                onSoldOutChange = { onSoldOutChange(menu.name, it) },
                onClick = { onOpen(menu.name) },
            )
        }
        if (hidden.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.menu_hidden_header),
                    style = OrmoType.BodyMdSemibold,
                    color = OrmoColors.TextSecondary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            items(hidden, key = { it.name }) { menu ->
                SaleRow(
                    title = menu.name,
                    subtitle = formatWon(menu.price),
                    soldOut = false,
                    hidden = true,
                    selected = menu.name == selected,
                    onSoldOutChange = {},
                    onClick = { onOpen(menu.name) },
                )
            }
        }
    }
}

/** 두 칸: 왼쪽은 앞의 그룹들, 오른쪽은 마지막 그룹. 칸이 넘치면 칸 안에서 스크롤한다. */
@Composable
private fun OptionsPane(
    menus: List<MenuItem>,
    groups: List<OptionGroup>,
    actions: MenuActions,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(OptionFilter.ALL) }

    Toolbar(stringResource(R.string.add_option), onAdd) {
        OptionFilter.entries.forEach {
            SelectChip(
                text = stringResource(optionFilterRes(it)),
                selected = filter == it,
                textStyle = OrmoType.BodyMdMedium,
            ) { filter = it }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(groups.dropLast(1), groups.takeLast(1)).forEach { column ->
            Box(Modifier.weight(1f)) {
                OptionColumn(column, menus, filter, actions, onOpen, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OptionColumn(
    groups: List<OptionGroup>,
    menus: List<MenuItem>,
    filter: OptionFilter,
    actions: MenuActions,
    onOpen: (String) -> Unit,
    modifier: Modifier,
    selected: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(OrmoColors.BackgroundDefault)
            .verticalScroll(rememberScrollState()),
    ) {
        groups.forEach { group ->
            // 걸러도 그룹 머리는 남긴다.
            GroupHeader(group)
            val linked = menus.namesUsing(group.name)
            group.options
                .filter {
                    // 일부 메뉴만 품절인 옵션은 아직 파는 것으로 거른다.
                    filter == OptionFilter.ALL ||
                        (filter == OptionFilter.SOLD_OUT) == (it.saleState(linked) == OptionSale.SOLD_OUT)
                }
                .forEach { option ->
                    val sale = option.saleState(linked)
                    // 고른 줄이 목록 밖에 있으면 보이는 곳까지 스크롤한다.
                    val requester = remember { BringIntoViewRequester() }
                    if (option.name == selected) {
                        LaunchedEffect(Unit) { requester.bringIntoView() }
                    }
                    Box(Modifier.bringIntoViewRequester(requester)) {
                        SaleRow(
                            title = option.name,
                            subtitle = stringResource(
                                R.string.option_subtitle,
                                formatWon(option.extraPrice),
                                linked.size,
                            ),
                            soldOut = sale == OptionSale.SOLD_OUT,
                            partly = sale == OptionSale.PARTLY_SOLD_OUT,
                            selected = option.name == selected,
                            onSoldOutChange = { actions.onOptionSoldOutChange(option.name, it) },
                            onClick = { onOpen(option.name) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun GroupHeader(group: OptionGroup) {
    val max = group.maxCount
    val text = when {
        group.required -> stringResource(R.string.option_group_required, group.name)
        max != null -> stringResource(R.string.option_group_optional_max, group.name, max)
        else -> stringResource(R.string.option_group_optional, group.name)
    }
    Text(
        text = text,
        style = OrmoType.BodyLgSemibold,
        color = OrmoColors.TextSecondary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

private fun optionFilterRes(filter: OptionFilter): Int = when (filter) {
    OptionFilter.ALL -> R.string.option_filter_all
    OptionFilter.ON_SALE -> R.string.option_filter_on_sale
    OptionFilter.SOLD_OUT -> R.string.option_filter_sold_out
}

internal fun categoryRes(category: MenuCategory): Int = when (category) {
    MenuCategory.COFFEE -> R.string.category_coffee
    MenuCategory.DESSERT -> R.string.category_dessert
    MenuCategory.ADE -> R.string.category_ade
    MenuCategory.SMOOTHIE -> R.string.category_smoothie
    MenuCategory.DRINK -> R.string.category_drink
}

/** DS Tabs Fixed: 메뉴 | 옵션, 화면 폭 반반 */
@Composable
private fun SubTabs(selected: MenuSection, onSelect: (MenuSection) -> Unit) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 20.dp)
                .selectableGroup(),
        ) {
            SubTab(
                text = stringResource(R.string.menu_tab_items),
                selected = selected == MenuSection.ITEMS,
                onClick = { onSelect(MenuSection.ITEMS) },
                modifier = Modifier.weight(1f),
            )
            SubTab(
                text = stringResource(R.string.menu_tab_options),
                selected = selected == MenuSection.OPTIONS,
                onClick = { onSelect(MenuSection.OPTIONS) },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = OrmoColors.Border)
    }
}

@Composable
private fun SubTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
    ) {
        Text(
            text = text,
            style = if (selected) OrmoType.BodyLgBold else OrmoType.BodyLgSemibold,
            color = if (selected) OrmoColors.TextPrimary else OrmoColors.TextTertiary,
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(OrmoColors.TabIndicator, CircleShape),
            )
        }
    }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OrmoColors.FillNeutralWeak)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                tint = OrmoColors.TextTertiary,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(text, style = OrmoType.BodyMdSemibold, color = OrmoColors.TextPrimary)
    }
}

/** 이름 · 설명 ........ 판매 중/품절 · 스위치 · > (숨긴 메뉴는 스위치 없이 "숨김") */
@Composable
private fun SaleRow(
    title: String,
    subtitle: String,
    soldOut: Boolean,
    onSoldOutChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    selected: Boolean = false,
    hidden: Boolean = false,
    /** 일부 메뉴에서만 품절(옵션). 스위치는 켜져 있다. */
    partly: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(if (selected) OrmoColors.FillBrandWeak else OrmoColors.BackgroundDefault)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            // 품절·숨김이면 글자를 흐리게 한다.
            Text(
                title,
                style = OrmoType.BodyMdMedium,
                color = if (soldOut || hidden) OrmoColors.TextTertiary else OrmoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = OrmoType.BodySmRegular, color = OrmoColors.TextTertiary)
        }
        if (hidden) {
            Text(stringResource(R.string.menu_hidden), style = OrmoType.BodyMdRegular, color = OrmoColors.TextTertiary)
        } else {
            SaleStateText(
                when {
                    soldOut -> OptionSale.SOLD_OUT
                    partly -> OptionSale.PARTLY_SOLD_OUT
                    else -> OptionSale.ON_SALE
                },
            )
            SaleSwitch(
                checked = !soldOut,
                onCheckedChange = { onSale -> onSoldOutChange(!onSale) },
            )
        }
        Chevron()
    }
}

/** 아래 화살표 아이콘을 눕혀서 ">"로 쓴다. */
@Composable
internal fun Chevron() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = OrmoColors.ChevronWeak,
            modifier = Modifier
                .size(width = 11.dp, height = 7.dp)
                .rotate(-90f),
        )
    }
}

/** Figma Switch(56x32). 메뉴에서는 켜짐 = 판매 중 */
@Composable
internal fun SaleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 0.dp, label = "thumb")
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(CircleShape)
            .background(if (checked) OrmoColors.FillBrand else OrmoColors.SwitchOff)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .background(OrmoColors.BackgroundDefault, CircleShape),
        )
    }
}
