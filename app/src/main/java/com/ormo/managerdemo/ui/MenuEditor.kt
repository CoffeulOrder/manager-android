package com.ormo.managerdemo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ormo.managerdemo.R
import com.ormo.managerdemo.domain.MenuCategory
import com.ormo.managerdemo.domain.MenuItem
import com.ormo.managerdemo.domain.MenuOption
import com.ormo.managerdemo.domain.OptionGroup
import com.ormo.managerdemo.domain.canAdd
import com.ormo.managerdemo.domain.canSave
import com.ormo.managerdemo.domain.formatWon
import com.ormo.managerdemo.domain.hasDuplicateName
import com.ormo.managerdemo.domain.isNameTaken
import com.ormo.managerdemo.ui.theme.OrmoColors
import com.ormo.managerdemo.ui.theme.OrmoType

private const val NAME_LIMIT = 20
private const val DESCRIPTION_LIMIT = 60
private const val PRICE_DIGITS = 6

private enum class PanelView { DETAIL, LINK, NEW_OPTION }

/** 저장하기 전의 입력값. [price]는 숫자만 담는다. */
private data class MenuDraft(
    val name: String,
    val price: String,
    val description: String,
    val category: MenuCategory,
    val optionGroups: List<String>,
    val visible: Boolean,
) {
    fun toItem() = MenuItem(
        name = name.trim(),
        price = price.toIntOrNull() ?: 0,
        category = category,
        description = description.trim(),
        optionGroups = optionGroups,
        hidden = !visible,
    )
}

/**
 * 메뉴 상세·수정과 새 메뉴(Figma 3.2.1). [original]이 null이면 새 메뉴다.
 * [저장하기]/[추가하기]를 눌러야 반영되고, [×]로 닫으면 버린다.
 */
@Composable
fun MenuEditorPanel(
    original: MenuItem?,
    newCategory: MenuCategory,
    menus: List<MenuItem>,
    optionGroups: List<OptionGroup>,
    onSave: (MenuItem) -> Unit,
    onDelete: () -> Unit,
    onAddOptionGroup: (OptionGroup, Set<String>) -> Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember {
        mutableStateOf(
            MenuDraft(
                name = original?.name.orEmpty(),
                price = original?.price?.toString().orEmpty(),
                description = original?.description.orEmpty(),
                category = original?.category ?: newCategory,
                optionGroups = original?.optionGroups.orEmpty(),
                visible = original?.hidden != true,
            ),
        )
    }
    var view by remember { mutableStateOf(PanelView.DETAIL) }
    var linked by remember { mutableStateOf(emptyList<String>()) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val title = original?.name ?: stringResource(R.string.menu_new)
    val openLink = {
        linked = draft.optionGroups
        view = PanelView.LINK
    }

    when (view) {
        PanelView.DETAIL -> MenuDetail(
            title = title,
            original = original,
            draft = draft,
            onDraft = { draft = it },
            nameTaken = menus.isNameTaken(draft.name.trim(), original?.name),
            canSave = menus.canSave(original?.name, draft.toItem()),
            optionGroups = optionGroups,
            onLink = openLink,
            onSave = { onSave(draft.toItem()) },
            onDelete = { confirmingDelete = true },
            onClose = onClose,
            modifier = modifier,
        )
        PanelView.LINK -> {
            BackHandler { view = PanelView.DETAIL }
            OptionLink(
                menuName = draft.name.trim().ifEmpty { title },
                optionGroups = optionGroups,
                linked = linked,
                onToggle = { linked = if (it in linked) linked - it else linked + it },
                onNewOption = { view = PanelView.NEW_OPTION },
                onApply = {
                    draft = draft.copy(optionGroups = linked)
                    view = PanelView.DETAIL
                },
                onBack = { view = PanelView.DETAIL },
                modifier = modifier,
            )
        }
        PanelView.NEW_OPTION -> {
            BackHandler { view = PanelView.LINK }
            // 아직 저장하지 않은 이름이 아니라 목록에 있는 이름으로 보여준다.
            val current = original?.name ?: draft.name.trim()
            val listed = if (original == null && current.isNotEmpty() && menus.none { it.name == current }) {
                listOf(draft.toItem()) + menus
            } else {
                menus
            }
            NewOptionPanel(
                menus = listed,
                optionGroups = optionGroups,
                preselected = setOfNotNull(current.takeIf { it.isNotEmpty() }),
                onAdd = { group, chosen ->
                    // 지금 고치는 메뉴는 [저장하기]에서 덮어쓰이지 않도록 입력값에도 넣는다.
                    val others = if (original == null) chosen - current else chosen
                    if (onAddOptionGroup(group, others)) {
                        if (current in chosen) {
                            draft = draft.copy(optionGroups = draft.optionGroups + group.name)
                            linked = linked + group.name
                        }
                        view = PanelView.LINK
                    }
                },
                onClose = { view = PanelView.LINK },
                modifier = modifier,
            )
        }
    }

    if (confirmingDelete && original != null) {
        AlertBox(
            title = stringResource(R.string.dialog_delete_menu_title, original.name),
            body = stringResource(R.string.dialog_delete_menu_body),
            onDismiss = { confirmingDelete = false },
        ) {
            ActionButton(
                text = stringResource(R.string.dialog_close),
                tone = ButtonTone.NEUTRAL_WEAK,
                onClick = { confirmingDelete = false },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                text = stringResource(R.string.action_delete),
                tone = ButtonTone.DANGER,
                onClick = {
                    confirmingDelete = false
                    onDelete()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MenuDetail(
    title: String,
    original: MenuItem?,
    draft: MenuDraft,
    onDraft: (MenuDraft) -> Unit,
    nameTaken: Boolean,
    canSave: Boolean,
    optionGroups: List<OptionGroup>,
    onLink: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    // 숨긴 메뉴는 "고객에게 보이기"를 스크롤 없이 보이게 맨 위에 둔다.
    val visibilityOnTop = original?.hidden == true
    val visibility: @Composable () -> Unit = {
        SwitchRow(
            title = stringResource(R.string.menu_visible_title),
            body = stringResource(if (visibilityOnTop) R.string.menu_hidden_body else R.string.menu_visible_body),
            checked = draft.visible,
            onCheckedChange = { onDraft(draft.copy(visible = it)) },
        )
    }

    Panel(
        modifier = modifier,
        header = { PanelHeader(title, onClose = onClose) },
        footer = {
            if (original == null) {
                ActionButton(stringResource(R.string.dialog_close), ButtonTone.NEUTRAL_WEAK, onClose)
                ActionButton(
                    text = stringResource(R.string.action_add),
                    tone = ButtonTone.BRAND,
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                )
            } else {
                ActionButton(stringResource(R.string.menu_delete), ButtonTone.DANGER_WEAK, onDelete)
                ActionButton(
                    text = stringResource(R.string.action_save),
                    tone = ButtonTone.BRAND,
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        if (visibilityOnTop) visibility()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Labeled(stringResource(R.string.field_name), Modifier.weight(1f)) {
                FormField(
                    value = draft.name,
                    onValueChange = { onDraft(draft.copy(name = it.take(NAME_LIMIT))) },
                    hint = stringResource(R.string.menu_name_hint),
                    label = stringResource(R.string.field_name),
                    error = nameTaken,
                )
                if (nameTaken) ErrorText(stringResource(R.string.menu_name_taken))
            }
            Labeled(stringResource(R.string.field_price), Modifier.width(200.dp)) {
                PriceField(
                    digits = draft.price,
                    onDigitsChange = { onDraft(draft.copy(price = it)) },
                    label = stringResource(R.string.field_price),
                )
            }
        }
        Labeled(stringResource(R.string.field_description)) {
            FormField(
                value = draft.description,
                onValueChange = { onDraft(draft.copy(description = it.take(DESCRIPTION_LIMIT))) },
                hint = stringResource(R.string.menu_description_hint),
                label = stringResource(R.string.field_description),
            )
        }
        Labeled(stringResource(R.string.field_category)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                MenuCategory.entries.forEach {
                    SelectChip(stringResource(categoryRes(it)), draft.category == it) {
                        onDraft(draft.copy(category = it))
                    }
                }
            }
        }
        Column {
            SectionHeader(
                stringResource(R.string.field_options),
                action = stringResource(R.string.add_option),
                onAction = onLink,
            )
            val linked = draft.optionGroups.mapNotNull { name -> optionGroups.firstOrNull { it.name == name } }
            if (linked.isEmpty()) {
                NoOptions()
            } else {
                linked.forEach { group ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .clickable(role = Role.Button, onClick = onLink),
                    ) {
                        GroupSummary(group, Modifier.weight(1f), showPrices = true)
                        Chevron()
                    }
                }
            }
        }
        // 새 메뉴는 처음부터 고객에게 보인다.
        if (original != null && !visibilityOnTop) visibility()
    }
}

/** 메뉴에 연결할 옵션 고르기(Figma 3.2.1 "메뉴 · 옵션 연결"). 창 없이 패널 안에서 넘어간다. */
@Composable
private fun OptionLink(
    menuName: String,
    optionGroups: List<OptionGroup>,
    linked: List<String>,
    onToggle: (String) -> Unit,
    onNewOption: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Panel(
        modifier = modifier,
        header = { PanelHeader(stringResource(R.string.link_title, menuName), onBack = onBack) },
        footer = {
            ActionButton(
                text = stringResource(R.string.link_apply),
                tone = ButtonTone.BRAND,
                onClick = onApply,
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        Column {
            optionGroups.forEach { group ->
                CheckRow(checked = group.name in linked, onToggle = { onToggle(group.name) }, height = 78) {
                    GroupSummary(group, Modifier.weight(1f))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onNewOption)
                    .padding(horizontal = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = null,
                    tint = OrmoColors.TextPrimary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    stringResource(R.string.link_new_option),
                    style = OrmoType.BodyMdSemibold,
                    color = OrmoColors.TextPrimary,
                )
            }
        }
    }
}

private data class ItemDraft(val name: String = "", val price: String = "")

/** 새 옵션 그룹(Figma 3.2.2 "옵션 추가"). 이름이 빈 항목 줄은 버린다. */
@Composable
fun NewOptionPanel(
    menus: List<MenuItem>,
    optionGroups: List<OptionGroup>,
    preselected: Set<String>,
    onAdd: (OptionGroup, Set<String>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var required by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(listOf(ItemDraft(), ItemDraft())) }
    var chosen by remember { mutableStateOf(preselected) }

    val group = OptionGroup(
        name = name.trim(),
        required = required,
        options = items.filter { it.name.isNotBlank() }
            .map { MenuOption(it.name.trim(), it.price.toIntOrNull() ?: 0) },
    )
    val duplicated = optionGroups.hasDuplicateName(group.name, group.options.map { it.name })

    Panel(
        modifier = modifier,
        header = { PanelHeader(stringResource(R.string.option_new), onClose = onClose) },
        footer = {
            ActionButton(stringResource(R.string.dialog_close), ButtonTone.NEUTRAL_WEAK, onClose)
            ActionButton(
                text = stringResource(R.string.action_add),
                tone = ButtonTone.BRAND,
                onClick = { onAdd(group, chosen) },
                enabled = optionGroups.canAdd(group),
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        Labeled(stringResource(R.string.option_name)) {
            FormField(
                value = name,
                onValueChange = { name = it.take(NAME_LIMIT) },
                hint = stringResource(R.string.option_name_hint),
                label = stringResource(R.string.option_name),
                error = duplicated,
            )
            if (duplicated) ErrorText(stringResource(R.string.option_name_taken))
        }
        SwitchRow(
            title = stringResource(R.string.option_required_title),
            checked = required,
            onCheckedChange = { required = it },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                stringResource(R.string.option_items),
                action = stringResource(R.string.option_add_item),
                onAction = { items = items + ItemDraft() },
            )
            items.forEachIndexed { index, item ->
                fun update(new: ItemDraft) {
                    items = items.mapIndexed { i, old -> if (i == index) new else old }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = item.name,
                        onValueChange = { update(item.copy(name = it.take(NAME_LIMIT))) },
                        hint = stringResource(R.string.option_item_hint),
                        label = stringResource(R.string.option_item_hint),
                        modifier = Modifier.weight(1f),
                    )
                    PriceField(
                        digits = item.price,
                        onDigitsChange = { update(item.copy(price = it)) },
                        label = stringResource(R.string.field_price),
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
        }
        Column {
            SectionHeader(
                stringResource(R.string.option_linked_menus),
                action = stringResource(R.string.option_link_all),
                onAction = { chosen = menus.map { it.name }.toSet() },
            )
            menus.forEach { menu ->
                CheckRow(
                    checked = menu.name in chosen,
                    onToggle = { chosen = if (menu.name in chosen) chosen - menu.name else chosen + menu.name },
                    height = 48,
                ) {
                    Text(
                        menu.name,
                        style = OrmoType.BodyMdMedium,
                        color = OrmoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 오른쪽 패널: 머리 + 스크롤되는 내용 + 아래에 고정한 버튼 */
@Composable
private fun Panel(
    modifier: Modifier,
    header: @Composable () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(OrmoColors.BackgroundDefault),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 20.dp),
        ) {
            header()
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
                content = content,
            )
        }
        HorizontalDivider(color = OrmoColors.Border)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            content = footer,
        )
    }
}

@Composable
private fun PanelHeader(title: String, onClose: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(start = if (onBack == null) 24.dp else 16.dp, end = 16.dp),
    ) {
        if (onBack != null) {
            IconBox(stringResource(R.string.navigate_back), onBack) {
                // 아래 화살표를 눕혀 "<"로 쓴다.
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = OrmoColors.TextSecondary,
                    modifier = Modifier
                        .size(width = 16.dp, height = 10.dp)
                        .rotate(90f),
                )
            }
        }
        Text(
            title,
            style = OrmoType.BodyLgBold,
            color = OrmoColors.TextStrong,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            IconBox(stringResource(R.string.dialog_close), onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    tint = OrmoColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun IconBox(description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        icon()
    }
}

@Composable
private fun Labeled(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(label, style = OrmoType.BodyMdSemibold, color = OrmoColors.TextSecondary)
        content()
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = OrmoType.BodyMdSemibold, color = OrmoColors.TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            text = action,
            style = OrmoType.BodySmMedium,
            color = OrmoColors.TextTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onAction)
                .padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = OrmoType.BodySmRegular, color = OrmoColors.TextDanger)
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = OrmoType.BodyMdMedium, color = OrmoColors.TextPrimary)
            if (body != null) {
                Text(body, style = OrmoType.BodySmRegular, color = OrmoColors.TextTertiary)
            }
        }
        SaleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** "HOT / ICE" + "필수 · HOT, ICE". [showPrices]면 추가 금액도 적는다(상세 화면). */
@Composable
private fun GroupSummary(group: OptionGroup, modifier: Modifier, showPrices: Boolean = false) {
    val options = group.options.map {
        if (showPrices && it.extraPrice > 0) "${it.name} +${formatWon(it.extraPrice)}" else it.name
    }
    Column(modifier) {
        Text(group.name, style = OrmoType.BodyMdMedium, color = OrmoColors.TextPrimary)
        Text(
            text = stringResource(
                R.string.menu_summary_options,
                stringResource(if (group.required) R.string.group_required else R.string.group_optional),
                options.joinToString(", "),
            ),
            style = OrmoType.BodySmRegular,
            color = OrmoColors.TextTertiary,
            maxLines = 1,
        )
    }
}

/** 줄 전체를 누르는 원형 체크박스(Checkbox Circle) */
@Composable
private fun CheckRow(
    checked: Boolean,
    onToggle: () -> Unit,
    height: Int,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        content()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .then(
                    if (checked) {
                        Modifier.background(OrmoColors.FillBrand, CircleShape)
                    } else {
                        Modifier.border(1.5.dp, OrmoColors.FieldBorder, CircleShape)
                    },
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = if (checked) OrmoColors.TextOnFill else OrmoColors.SwitchOff,
            )
        }
    }
}

/** 연결한 옵션이 없을 때(DS Result). 아이콘(tune)은 선 세 줄로 직접 그린다. */
@Composable
private fun NoOptions() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
    ) {
        val color = OrmoColors.ChevronWeak
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .background(OrmoColors.FillNeutralWeak, CircleShape),
        ) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = 2.dp.toPx()
                listOf(0.2f to 0.65f, 0.5f to 0.3f, 0.8f to 0.55f).forEach { (y, knob) ->
                    val lineY = size.height * y
                    drawLine(color, Offset(0f, lineY), Offset(size.width, lineY), stroke, StrokeCap.Round)
                    val x = size.width * knob
                    val half = size.height * 0.13f
                    drawLine(color, Offset(x, lineY - half), Offset(x, lineY + half), stroke, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.options_empty_title), style = OrmoType.BodyMdSemibold, color = OrmoColors.TextStrong)
        Text(
            stringResource(R.string.options_empty_body),
            style = OrmoType.BodySmRegular,
            color = OrmoColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 높이 56 · radius 16 입력칸. 누르고 있는 칸은 파란 테두리. */
@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    label: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = OrmoType.BodyMdRegular.copy(color = OrmoColors.TextPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            FieldBox(focused, error) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(hint, style = OrmoType.BodyMdRegular, color = OrmoColors.Placeholder)
                    }
                    inner()
                }
            }
        },
    )
}

/** 금액 칸. 세 자리마다 쉼표를 보여주고 뒤에 "원"을 둔다. */
@Composable
private fun PriceField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val text = digits.toIntOrNull()?.let { "%,d".format(it) }.orEmpty()
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    // 쉼표를 넣어 글자가 바뀌면 커서를 끝에 둔다.
    val shown = if (value.text == text) value else TextFieldValue(text, TextRange(text.length))

    BasicTextField(
        value = shown,
        onValueChange = { new ->
            value = new
            val typed = new.text.filter { it.isDigit() }.take(PRICE_DIGITS)
            if (typed != digits) onDigitsChange(typed)
        },
        singleLine = true,
        textStyle = OrmoType.BodyMdRegular.copy(color = OrmoColors.TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            FieldBox(focused, error = false) {
                Box(Modifier.weight(1f)) {
                    if (digits.isEmpty()) {
                        Text("0", style = OrmoType.BodyMdRegular, color = OrmoColors.Placeholder)
                    }
                    inner()
                }
                Text(stringResource(R.string.unit_won), style = OrmoType.BodyMdRegular, color = OrmoColors.TextTertiary)
            }
        },
    )
}

@Composable
private fun FieldBox(focused: Boolean, error: Boolean, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    error -> OrmoColors.FillDanger
                    focused -> OrmoColors.FillBrand
                    else -> OrmoColors.FieldBorder
                },
                shape,
            )
            .padding(horizontal = 16.dp),
        content = content,
    )
}
