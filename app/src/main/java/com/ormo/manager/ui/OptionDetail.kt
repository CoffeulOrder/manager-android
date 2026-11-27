package com.ormo.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.MenuItem
import com.ormo.manager.domain.MenuOption
import com.ormo.manager.domain.OptionGroup
import com.ormo.manager.domain.OptionSale
import com.ormo.manager.domain.OptionWarning
import com.ormo.manager.domain.formatWon
import com.ormo.manager.domain.isSoldOutFor
import com.ormo.manager.domain.namesUsing
import com.ormo.manager.domain.optionWarning
import com.ormo.manager.domain.saleState
import com.ormo.manager.domain.topicParticle
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/**
 * 옵션 상세(Figma 3.2.2). 품절 스위치는 영업 중에도 바로 저장하고 비밀번호를 묻지 않는다.
 * "모든 메뉴" 스위치는 메뉴별 스위치를 한꺼번에 바꾼다.
 */
@Composable
fun OptionDetailPanel(
    group: OptionGroup,
    option: MenuOption,
    menus: List<MenuItem>,
    onAllSoldOutChange: (Boolean) -> Unit,
    onMenuSoldOutChange: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linked = menus.namesUsing(group.name)
    val sale = option.saleState(linked)
    val scroll = rememberScrollState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(OrmoColors.BackgroundDefault),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = 20.dp, bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(start = 24.dp, end = 16.dp),
            ) {
                Text(option.name, style = OrmoType.BodyXlBold, color = OrmoColors.TextStrong, maxLines = 1)
                when (sale) {
                    OptionSale.SOLD_OUT -> StatusBadge(
                        stringResource(R.string.menu_sold_out),
                        OrmoColors.FillDangerWeak,
                        OrmoColors.TextDanger,
                    )
                    OptionSale.PARTLY_SOLD_OUT -> StatusBadge(
                        stringResource(R.string.option_partly_sold_out),
                        OrmoColors.FillWarningWeak,
                        OrmoColors.TextWarning,
                    )
                    OptionSale.ON_SALE -> Unit
                }
                Spacer(Modifier.weight(1f))
                IconBox(stringResource(R.string.dialog_close), onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        tint = OrmoColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                Text(
                    text = stringResource(
                        R.string.option_detail_summary,
                        group.name,
                        stringResource(if (group.required) R.string.group_required else R.string.group_optional),
                        formatWon(option.extraPrice),
                        linked.size,
                    ),
                    style = OrmoType.BodyMdRegular,
                    color = OrmoColors.TextSecondary,
                )
                optionWarning(group, option, menus)?.let { WarningCallout(group.name, it) }
                Spacer(Modifier.height(24.dp))
                SaleLine(
                    title = stringResource(R.string.option_all_menus),
                    sale = sale,
                    // 켜짐 = 모든 메뉴에서 판매 중
                    checked = sale == OptionSale.ON_SALE,
                    onSoldOutChange = onAllSoldOutChange,
                )
                HorizontalDivider(color = OrmoColors.Border, modifier = Modifier.padding(vertical = 4.dp))
                if (linked.isEmpty()) {
                    Text(
                        stringResource(R.string.option_no_menus),
                        style = OrmoType.BodyMdRegular,
                        color = OrmoColors.TextTertiary,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                linked.forEach { menu ->
                    val soldOut = option.isSoldOutFor(menu)
                    SaleLine(
                        title = menu,
                        sale = if (soldOut) OptionSale.SOLD_OUT else OptionSale.ON_SALE,
                        checked = !soldOut,
                        onSoldOutChange = { onMenuSoldOutChange(menu, it) },
                    )
                }
            }
        }
        // 아래에 더 있으면 흐려지게 한다(DS Scroll Fog).
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, OrmoColors.BackgroundDefault))),
            )
        }
    }
}

@Composable
private fun WarningCallout(groupName: String, warning: OptionWarning) {
    val message = when (warning) {
        is OptionWarning.AllGone ->
            stringResource(R.string.option_warning_all_gone, groupName, warning.menuCount)
        is OptionWarning.LastChoices -> stringResource(
            R.string.option_warning_last_choices,
            groupName,
            topicParticle(groupName),
            warning.others.joinToString(", "),
            warning.menuCount,
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .background(OrmoColors.FillWarningWeak, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Image(painterResource(R.drawable.ic_alert), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(message, style = OrmoType.BodySmRegular, color = OrmoColors.TextSecondary)
    }
}

/** 이름 ........ 판매 중/일부 품절/품절 · 스위치 */
@Composable
private fun SaleLine(
    title: String,
    sale: OptionSale,
    checked: Boolean,
    onSoldOutChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            title,
            style = OrmoType.BodyMdMedium,
            color = if (sale == OptionSale.SOLD_OUT) OrmoColors.TextTertiary else OrmoColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        SaleStateText(sale)
        SaleSwitch(checked = checked, onCheckedChange = { onSale -> onSoldOutChange(!onSale) })
    }
}

@Composable
internal fun SaleStateText(sale: OptionSale) {
    val (text, color) = when (sale) {
        OptionSale.ON_SALE -> R.string.menu_on_sale to OrmoColors.TextSecondary
        OptionSale.PARTLY_SOLD_OUT -> R.string.option_partly_sold_out to OrmoColors.TextWarning
        OptionSale.SOLD_OUT -> R.string.menu_sold_out to OrmoColors.TextDanger
    }
    Text(stringResource(text), style = OrmoType.BodyMdRegular, color = color)
}
