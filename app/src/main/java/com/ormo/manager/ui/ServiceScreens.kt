package com.ormo.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.formatClock
import com.ormo.manager.service.AppUpdate
import com.ormo.manager.service.Maintenance
import com.ormo.manager.service.ServiceStatus
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/** 서비스 점검(Figma 5.2.1). 상단 바 아래 전체를 대신한다. */
@Composable
fun MaintenanceScreen(maintenance: Maintenance, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundDefault),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .background(OrmoColors.FillDangerWeak, CircleShape),
        ) {
            Image(painterResource(R.drawable.ic_build), contentDescription = null)
        }
        Text(
            stringResource(R.string.maintenance_title),
            style = OrmoType.BodyLgSemibold,
            color = OrmoColors.TextStrong,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            stringResource(R.string.maintenance_body),
            style = OrmoType.BodyMdRegular,
            color = OrmoColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        maintenance.until?.let {
            Text(
                stringResource(R.string.maintenance_until, formatClock(it)),
                style = OrmoType.BodyMdMedium,
                color = OrmoColors.TextSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 로그인 만료(Figma 5.2.2). 닫을 수 없다. */
@Composable
fun SessionExpiredDialog(onLogin: () -> Unit) {
    AlertBox(
        title = stringResource(R.string.session_expired_title),
        body = stringResource(R.string.session_expired_body),
        onDismiss = {},
        dismissible = false,
    ) {
        ActionButton(
            text = stringResource(R.string.session_expired_login),
            tone = ButtonTone.BRAND,
            onClick = onLogin,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 업데이트 필요(Figma 5.2.3). 꼭 필요한 업데이트면 [나중에]가 없다. */
@Composable
fun UpdateDialog(update: AppUpdate, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertBox(
        title = stringResource(R.string.update_title),
        body = stringResource(R.string.update_body),
        onDismiss = { if (!update.required) onLater() },
        dismissible = !update.required,
    ) {
        if (!update.required) {
            ActionButton(
                text = stringResource(R.string.update_later),
                tone = ButtonTone.NEUTRAL_WEAK,
                onClick = onLater,
                modifier = Modifier.weight(1f),
            )
        }
        ActionButton(
            text = stringResource(R.string.update_now),
            tone = ButtonTone.BRAND,
            onClick = onUpdate,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 개발 빌드 전용: 서버가 있어야 생기는 화면을 골라서 띄운다. */
@Composable
fun TestStatusDialog(
    status: ServiceUiState,
    onSelect: ((ServiceStatus) -> ServiceStatus) -> Unit,
    salesFailing: Boolean,
    onToggleSalesFailure: () -> Unit,
    photoFailing: Boolean,
    onTogglePhotoFailure: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(width = 371.dp, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
            @Composable
            fun choice(textRes: Int, change: (ServiceStatus) -> ServiceStatus) {
                ActionButton(
                    text = stringResource(textRes),
                    tone = ButtonTone.NEUTRAL_WEAK,
                    onClick = {
                        onSelect(change)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (status.maintenance == null) {
                choice(R.string.test_maintenance_start) { it.copy(maintenance = Maintenance()) }
            } else {
                choice(R.string.test_maintenance_end) { it.copy(maintenance = null) }
            }
            choice(R.string.test_session_expired) { it.copy(sessionExpired = true) }
            choice(R.string.test_update_optional) { it.copy(update = AppUpdate(required = false)) }
            choice(R.string.test_update_required) { it.copy(update = AppUpdate(required = true)) }
            choice(R.string.test_update_clear) { it.copy(update = null) }
            ActionButton(
                text = stringResource(if (photoFailing) R.string.test_photo_recover else R.string.test_photo_fail),
                tone = ButtonTone.NEUTRAL_WEAK,
                onClick = {
                    onTogglePhotoFailure()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                text = stringResource(if (salesFailing) R.string.test_sales_recover else R.string.test_sales_fail),
                tone = ButtonTone.NEUTRAL_WEAK,
                onClick = {
                    onToggleSalesFailure()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
