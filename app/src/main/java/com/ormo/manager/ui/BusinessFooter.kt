package com.ormo.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ormo.manager.R
import com.ormo.manager.domain.BusinessInfo
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType
import com.ormo.manager.ui.theme.Pretendard

/**
 * 모든 화면 맨 아래에 고정되는 사업자 정보(통신판매중개자 고지).
 * Scaffold의 bottomBar로 쓰므로 목록을 스크롤해도 사라지지 않는다.
 * (Figma에는 없지만 유지한다.)
 */
@Composable
fun BusinessFooter(
    info: BusinessInfo,
    modifier: Modifier = Modifier,
    /** 개발 빌드 전용 버튼. null이면(로그인 전, 배포 빌드) 보이지 않는다. */
    onAddTestOrder: (() -> Unit)? = null,
) {
    val items = listOf(
        R.string.biz_company_name to info.companyName,
        R.string.biz_representative to info.representative,
        R.string.biz_registration_number to info.businessRegistrationNumber,
        R.string.biz_mail_order_number to info.mailOrderSalesNumber,
        R.string.biz_address to info.address,
        R.string.biz_customer_center to info.customerCenterPhone,
        R.string.biz_email to info.email,
    ).map { (labelRes, value) -> stringResource(R.string.biz_item_format, stringResource(labelRes), value) }

    Column(
        modifier
            .fillMaxWidth()
            .background(OrmoColors.BackgroundDefault),
    ) {
        HorizontalDivider(color = OrmoColors.Border)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Text(
                text = items.joinToString(" | "),
                // 잘리지 않도록 maxLines를 두지 않는다. 가로 태블릿에서는 보통 1~2줄이다.
                fontFamily = Pretendard,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = OrmoColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            // 개발 빌드 전용: Figma에 없는 버튼. 서버 연동 시 viewModel.addTestOrder()와 함께 제거한다.
            if (onAddTestOrder != null) {
                Text(
                    text = stringResource(R.string.add_test_order),
                    style = OrmoType.BodySmSemibold,
                    color = OrmoColors.TextPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OrmoColors.FillNeutralWeak)
                        .clickable(role = Role.Button, onClick = onAddTestOrder)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
