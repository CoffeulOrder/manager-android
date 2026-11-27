package com.ormo.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.StoreStatus
import com.ormo.manager.domain.formatClock
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/** 상단 탭 */
enum class ManagerTab(val titleRes: Int) {
    ORDERS(R.string.tab_orders),
    HISTORY(R.string.tab_history),
    MENU(R.string.tab_menu),
    /** 들어갈 때 2차 비밀번호를 묻는다. */
    SALES(R.string.tab_sales),
    SETTINGS(R.string.tab_settings),
}

/** Figma "Top Bar"(높이 64): 매장 이름 · 영업 상태 · 탭 5개(가운데) · 소리 · 시각 */
@Composable
fun TopBar(
    selectedTab: ManagerTab,
    onSelectTab: (ManagerTab) -> Unit,
    pendingCount: Int,
    now: Long,
    soundOff: Boolean,
    storeStatus: StoreStatus,
    onStatusClick: () -> Unit,
    /** 상태 메뉴를 버튼 바로 아래에 열 수 있도록 버튼의 왼쪽 위치(px)를 알려준다. */
    onStatusPositioned: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(OrmoColors.FillNeutral)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.store_name),
                style = OrmoType.BodyLgSemibold,
                color = OrmoColors.TextOnFill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StoreStatusButton(storeStatus, onStatusClick, onStatusPositioned)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            ManagerTab.entries.forEach { tab ->
                NavItem(
                    text = stringResource(tab.titleRes),
                    selected = selectedTab == tab,
                    badge = if (tab == ManagerTab.ORDERS) pendingCount else 0,
                    onClick = { onSelectTab(tab) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            modifier = Modifier.weight(1f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                Image(
                    painter = painterResource(if (soundOff) R.drawable.ic_sound_off else R.drawable.ic_sound),
                    contentDescription = stringResource(if (soundOff) R.string.sound_off else R.string.sound_on),
                )
            }
            Text(formatClock(now), style = OrmoType.BodyLgMedium, color = OrmoColors.White700)
        }
    }
}

/** 영업 상태 버튼. 점 색: 영업 중 초록 · 잠시 중지 주황 · 마감 회색 */
@Composable
private fun StoreStatusButton(status: StoreStatus, onClick: () -> Unit, onPositioned: (Float) -> Unit) {
    val (label, dot) = when (status) {
        StoreStatus.Open -> R.string.store_open to OrmoColors.FillSuccess
        is StoreStatus.Paused -> R.string.store_paused to OrmoColors.StorePausedDot
        StoreStatus.Closed -> R.string.store_closed to OrmoColors.StoreClosedDot
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .onGloballyPositioned { onPositioned(it.positionInRoot().x) }
            .clip(CircleShape)
            .background(OrmoColors.White100)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(dot, CircleShape),
        )
        Text(stringResource(label), style = OrmoType.BodySmSemibold, color = OrmoColors.TextOnFill)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
            Image(painter = painterResource(R.drawable.ic_chevron_down), contentDescription = null)
        }
    }
}

@Composable
private fun NavItem(text: String, selected: Boolean, onClick: () -> Unit, badge: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = OrmoType.BodyLgSemibold,
            color = if (selected) OrmoColors.TextOnFill else OrmoColors.White600,
        )
        // 접수 대기가 0이면 배지를 숨긴다.
        if (badge > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .background(OrmoColors.FillBrand, CircleShape)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    style = OrmoType.BodyXsBold,
                    color = OrmoColors.TextOnFill,
                )
            }
        }
    }
}
