package com.ormo.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.StoreStatus
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/** Figma "Menu · 영업 상태"(240): 영업 중 · 잠시 중지 · 마감. 지금 상태에 체크가 붙는다. */
@Composable
fun StoreStatusMenu(
    status: StoreStatus,
    onSelectOpen: () -> Unit,
    onSelectPause: () -> Unit,
    onSelectClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .width(240.dp)
            .shadow(16.dp, shape)
            .background(OrmoColors.BackgroundDefault, shape)
            .padding(horizontal = 5.dp, vertical = 8.dp)
            .selectableGroup(),
    ) {
        MenuItem(stringResource(R.string.store_open), status == StoreStatus.Open, onSelectOpen)
        MenuItem(stringResource(R.string.store_paused), status is StoreStatus.Paused, onSelectPause)
        MenuItem(stringResource(R.string.store_closed), status == StoreStatus.Closed, onSelectClose)
    }
}

@Composable
private fun MenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = if (selected) OrmoType.BodyMdSemibold else OrmoType.BodyMdRegular,
            color = if (selected) OrmoColors.TextBrand else OrmoColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = OrmoColors.FillBrand,
                    modifier = Modifier.size(width = 13.dp, height = 10.dp),
                )
            }
        }
    }
}
