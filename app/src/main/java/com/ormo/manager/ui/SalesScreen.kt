package com.ormo.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.DaySales
import com.ormo.manager.domain.Order
import com.ormo.manager.domain.businessDateOf
import com.ormo.manager.domain.daysOf
import com.ormo.manager.domain.formatWon
import com.ormo.manager.domain.summary
import com.ormo.manager.domain.todaySales
import com.ormo.manager.domain.topMenus
import com.ormo.manager.domain.weekOf
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class SalesPeriod { DAY, WEEK, MONTH }

/** 월간 보기의 [그래프 | 달력]. 매출 탭을 나갔다 와도 그대로 둔다. */
enum class MonthView { GRAPH, CALENDAR }

/** 매출 조회(Figma 6.1): 일간 · 주간 · 월간(그래프/달력) */
@Composable
fun SalesScreen(
    orders: List<Order>,
    now: Long,
    monthView: MonthView,
    onMonthViewChange: (MonthView) -> Unit,
    load: SalesLoad,
    onLoad: (List<LocalDate>, LocalDate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** 왼쪽 아래에 계속 떠 있는 알림의 높이. 그래프·달력 카드가 그 위에서 끝난다. */
    bottomInset: Dp = 0.dp,
) {
    val zone = ZoneId.systemDefault()
    val today = businessDateOf(now, zone)
    var period by rememberSaveable { mutableStateOf(SalesPeriod.DAY) }
    var anchorDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorDay)

    val live = remember(orders, today) {
        todaySales(
            today = today,
            orders = orders,
            dateOf = { businessDateOf(it, zone) },
            hourOf = { Instant.ofEpochMilli(it).atZone(zone).hour },
        )
    }
    val dates = when (period) {
        SalesPeriod.DAY -> listOf(anchor)
        SalesPeriod.WEEK -> weekOf(anchor)
        SalesPeriod.MONTH -> daysOf(YearMonth.from(anchor))
    }
    // 매출 화면에 들어오거나 기간을 바꿀 때마다 불러온다.
    LaunchedEffect(period, anchorDay, today) { onLoad(dates, today) }
    // 다른 기간의 값은 보여주지 않는다. 오늘은 앱 안의 주문으로 계산한다.
    val days = (load as? SalesLoad.Loaded)
        ?.takeIf { it.dates == dates }
        ?.days
        ?.map { if (it.date == today) live else it }

    fun move(step: Long) {
        val moved = when (period) {
            SalesPeriod.DAY -> anchor.plusDays(step)
            SalesPeriod.WEEK -> anchor.plusWeeks(step)
            SalesPeriod.MONTH -> anchor.plusMonths(step)
        }
        anchorDay = minOf(moved, today).toEpochDay()
    }

    fun openDay(date: LocalDate) {
        if (date.isAfter(today)) return
        anchorDay = date.toEpochDay()
        period = SalesPeriod.DAY
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundLower)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(48.dp),
        ) {
            Segmented(
                options = listOf(
                    stringResource(R.string.sales_daily),
                    stringResource(R.string.sales_weekly),
                    stringResource(R.string.sales_monthly),
                ),
                selected = period.ordinal,
                onSelect = {
                    period = SalesPeriod.entries[it]
                    anchorDay = today.toEpochDay()
                },
                height = 48.dp,
                textStyle = OrmoType.BodyMdSemibold,
            )
            Spacer(Modifier.weight(1f))
            ArrowButton(next = false, enabled = true, label = stringResource(R.string.sales_previous)) { move(-1) }
            Text(periodTitle(period, anchor), style = OrmoType.BodyXlBold, color = OrmoColors.TextStrong)
            ArrowButton(
                next = true,
                enabled = dates.last().isBefore(today),
                label = stringResource(R.string.sales_next),
            ) { move(1) }
        }

        if (load == SalesLoad.Failed) {
            // 잘못된 0원 대신 실패를 알린다.
            LoadFailed(onRetry, Modifier.fillMaxSize())
            return@Column
        }
        if (days == null) return@Column
        val summary = days.summary()

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(stringResource(R.string.sales_total), formatWon(summary.total))
            StatCard(stringResource(R.string.sales_orders), stringResource(R.string.sales_count, summary.orderCount))
            StatCard(
                stringResource(R.string.sales_refunds),
                stringResource(R.string.sales_refund_value, summary.refundCount, formatWon(summary.refundAmount)),
            )
        }

        if (period == SalesPeriod.DAY && summary.total == 0) {
            // 주문이 없는 날은 그래프와 순위 대신 빈 상태를 보여준다.
            Card(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    EmptyResult(
                        icon = R.drawable.ic_note,
                        title = stringResource(R.string.sales_empty_title),
                        description = stringResource(R.string.sales_empty_body),
                    )
                }
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            // 알림은 왼쪽 카드만 가리므로 오른쪽 순위는 줄이지 않는다.
            Card(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = cardBottomInset(bottomInset)),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    ChartHeader(period, monthView, onMonthViewChange)
                    when (period) {
                        SalesPeriod.DAY -> HourlyChart(days.single())
                        SalesPeriod.WEEK -> BarChart(
                            values = days.map { it.total },
                            labels = stringArrayResource(R.array.weekdays_from_monday).toList(),
                            barWidth = 77.dp,
                            onBarClick = { openDay(dates[it]) },
                        )
                        SalesPeriod.MONTH -> if (monthView == MonthView.GRAPH) {
                            BarChart(
                                values = days.map { it.total },
                                // 1, 5, 10 … 과 마지막 날만 적는다.
                                labels = dates.map {
                                    val day = it.dayOfMonth
                                    val last = day == dates.size
                                    if (day == 1 || last || (day % 5 == 0 && day <= dates.size - 3)) "$day" else ""
                                },
                                barWidth = 12.dp,
                                onBarClick = { openDay(dates[it]) },
                            )
                        } else {
                            Calendar(days, onDayClick = ::openDay)
                        }
                    }
                }
            }
            Card(
                Modifier
                    .width(400.dp)
                    .fillMaxHeight(),
            ) {
                if (period == SalesPeriod.MONTH) BestDays(days, ::openDay) else TopMenus(days)
            }
        }
    }
}

@Composable
private fun periodTitle(period: SalesPeriod, anchor: LocalDate): String {
    val weekdays = stringArrayResource(R.array.weekdays_from_monday)
    return when (period) {
        SalesPeriod.DAY -> stringResource(
            R.string.sales_date_day,
            anchor.monthValue,
            anchor.dayOfMonth,
            weekdays[anchor.dayOfWeek.value - 1],
        )
        SalesPeriod.WEEK -> {
            val week = weekOf(anchor)
            val first = week.first()
            val last = week.last()
            if (first.month == last.month) {
                stringResource(R.string.sales_date_week, first.monthValue, first.dayOfMonth, last.dayOfMonth)
            } else {
                stringResource(
                    R.string.sales_date_week_across,
                    first.monthValue, first.dayOfMonth, last.monthValue, last.dayOfMonth,
                )
            }
        }
        SalesPeriod.MONTH -> stringResource(R.string.sales_date_month, anchor.year, anchor.monthValue)
    }
}

/** DS Segmented Control: 회색 바탕 위 흰 알약 */
@Composable
private fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    height: Dp,
    textStyle: TextStyle = OrmoType.BodySmSemibold,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(335.dp)
            .height(height)
            .background(OrmoColors.FillNeutralWeak, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, text ->
            val isSelected = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isSelected) Modifier.shadow(2.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) OrmoColors.BackgroundDefault else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = isSelected, onClick = { onSelect(index) }, role = Role.Tab),
            ) {
                Text(
                    text = text,
                    style = textStyle,
                    color = if (isSelected) OrmoColors.TextPrimary else OrmoColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun ArrowButton(next: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        // 아래 화살표 아이콘을 눕혀 쓴다.
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = if (enabled) OrmoColors.TextPrimary else OrmoColors.ChevronWeak,
            modifier = Modifier
                .size(width = 13.dp, height = 8.dp)
                .rotate(if (next) -90f else 90f),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCard(label: String, value: String) {
    Card(
        Modifier
            .weight(1f)
            .height(78.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Text(label, style = OrmoType.BodyLgSemibold, color = OrmoColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(value, style = OrmoType.BodyXlBold, color = OrmoColors.TextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(OrmoColors.BackgroundDefault, RoundedCornerShape(16.dp))) { content() }
}

@Composable
private fun ChartHeader(period: SalesPeriod, monthView: MonthView, onMonthViewChange: (MonthView) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                when (period) {
                    SalesPeriod.DAY -> R.string.sales_chart_hourly
                    SalesPeriod.WEEK -> R.string.sales_chart_weekly
                    SalesPeriod.MONTH -> R.string.sales_chart_monthly
                },
            ),
            style = OrmoType.BodyLgSemibold,
            color = OrmoColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (period == SalesPeriod.MONTH) {
            Segmented(
                options = listOf(stringResource(R.string.sales_graph), stringResource(R.string.sales_calendar)),
                selected = monthView.ordinal,
                onSelect = { onMonthViewChange(MonthView.entries[it]) },
                height = 40.dp,
            )
        }
    }
}

/** 영업 시간(8~18시)을 기본으로 하고, 그 밖의 시간에 주문이 있으면 범위를 넓힌다. */
@Composable
private fun HourlyChart(day: DaySales) {
    val first = minOf(8, day.hourly.keys.minOrNull() ?: 8)
    val last = maxOf(18, day.hourly.keys.maxOrNull() ?: 18)
    val hours = (first..last).toList()
    var selected by remember(day.date) { mutableStateOf<Int?>(null) }
    BarChart(
        values = hours.map { day.hourly[it] ?: 0 },
        labels = hours.map { stringResource(R.string.sales_hour, it) },
        // 막대가 늘어나면 폭을 줄여 겹치지 않게 한다.
        barWidth = if (hours.size <= 11) 50.dp else (550 / hours.size).dp,
        selected = selected,
        onBarClick = { selected = it },
    )
}

/**
 * 막대 그래프. 첫 막대는 왼쪽 끝, 마지막 막대는 오른쪽 끝에 맞춘다.
 * 칩은 고른 막대(없으면 가장 높은 막대) 위에 금액을 보여주고, 고른 막대가 있으면 나머지를 흐리게 한다.
 */
@Composable
private fun BarChart(
    values: List<Int>,
    labels: List<String>,
    barWidth: Dp,
    onBarClick: (Int) -> Unit,
    selected: Int? = null,
) {
    val max = values.maxOrNull() ?: 0
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val labelHeight = 32.dp
        val chipSpace = 36.dp
        val barsHeight = maxHeight - labelHeight
        // 가장 높은 막대는 그림 영역의 약 70%
        val tallest = (barsHeight - chipSpace) * 0.75f
        val step = if (values.size > 1) (maxWidth - barWidth) / (values.size - 1) else 0.dp

        values.forEachIndexed { index, value ->
            val height = if (max == 0) 0.dp else tallest * (value.toFloat() / max)
            val x = step * index
            val faded = selected != null && selected != index
            Box(
                Modifier
                    .offset(x = x, y = barsHeight - height)
                    .size(width = barWidth, height = height)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(if (faded) OrmoColors.FillBrandWeak else OrmoColors.FillBrand),
            )
            // 낮은 막대도 누를 수 있게 칸 전체를 누름 영역으로 둔다.
            Box(
                Modifier
                    .offset(x = x)
                    .size(width = barWidth, height = barsHeight)
                    .clickable(role = Role.Button) { onBarClick(index) }
                    .semantics { contentDescription = "${labels[index]} ${formatWon(value)}" },
            )
            // 글자는 막대보다 넓을 수 있어 가운데에 맞춰 넘치게 둔다.
            Text(
                text = labels[index],
                style = OrmoType.BodyMdRegular,
                color = OrmoColors.TextTertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .offset(x = x + barWidth / 2 - 24.dp, y = barsHeight + 8.dp)
                    .width(48.dp),
            )
        }

        val chipIndex = selected ?: if (max > 0) values.indexOf(max) else null
        if (chipIndex != null) {
            val value = values[chipIndex]
            val height = if (max == 0) 0.dp else tallest * (value.toFloat() / max)
            val center = step * chipIndex + barWidth / 2
            val chipTop = barsHeight - height - 36.dp
            val areaWidth = maxWidth
            Text(
                text = formatWon(value),
                style = OrmoType.BodySmSemibold,
                color = OrmoColors.TextOnFill,
                maxLines = 1,
                modifier = Modifier
                    // 칩은 막대 가운데에 맞추고, 그림 밖으로 나가면 안쪽 끝에 맞춘다.
                    .layout { measurable, constraints ->
                        val chip = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val left = (center.roundToPx() - chip.width / 2)
                            .coerceIn(0, areaWidth.roundToPx() - chip.width)
                        layout(chip.width, chip.height) { chip.place(left, chipTop.roundToPx()) }
                    }
                    .background(OrmoColors.FillNeutral, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private val CALENDAR_MIN_ROW_HEIGHT = 48.dp

/** 일요일부터 시작하는 달력. 가장 많이 판 날은 파랗게 표시한다. */
@Composable
private fun Calendar(days: List<DaySales>, onDayClick: (LocalDate) -> Unit) {
    val weekdays = stringArrayResource(R.array.weekdays_from_monday)
    val sundayFirst = listOf(weekdays[6]) + weekdays.take(6)
    val max = days.maxOf { it.total }
    // 1일 앞의 빈 칸 수 (일요일 = 0)
    val leading = days.first().date.dayOfWeek.value % 7
    val cells: List<DaySales?> = List(leading) { null } + days
    val weeks = cells.chunked(7)

    Column(Modifier.fillMaxSize()) {
        Row {
            sundayFirst.forEach {
                Text(
                    text = it,
                    style = OrmoType.BodySmRegular,
                    color = OrmoColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // 칸이 남는 높이를 똑같이 나눠 갖는다. 알림 때문에 낮아지면 금액이 잘리지 않는 높이까지만 줄이고 스크롤한다.
        BoxWithConstraints(Modifier.weight(1f)) {
            val rowHeight = maxOf(maxHeight / weeks.size, CALENDAR_MIN_ROW_HEIGHT)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                weeks.forEach { week ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .height(rowHeight)
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        for (column in 0 until 7) {
                            val day = week.getOrNull(column)
                            if (day == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val best = max > 0 && day.total == max
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (best) OrmoColors.FillBrandWeak else androidx.compose.ui.graphics.Color.Transparent,
                                        )
                                        .clickable(role = Role.Button) { onDayClick(day.date) },
                                ) {
                                    Text(
                                        "${day.date.dayOfMonth}",
                                        style = OrmoType.BodySmRegular,
                                        color = if (best) OrmoColors.TextBrand else OrmoColors.TextTertiary,
                                    )
                                    // 매출이 없는 날은 비워 둔다.
                                    if (day.total > 0) {
                                        Text(
                                            formatWon(day.total),
                                            style = OrmoType.BodySmSemibold,
                                            color = if (best) OrmoColors.TextBrand else OrmoColors.TextSecondary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMenus(days: List<DaySales>) {
    RankingList(stringResource(R.string.sales_top_menus)) {
        days.topMenus().forEach { menu ->
            RankingRow(menu.name, stringResource(R.string.sales_cups, menu.qty), formatWon(menu.amount))
        }
    }
}

@Composable
private fun BestDays(days: List<DaySales>, onDayClick: (LocalDate) -> Unit) {
    val weekdays = stringArrayResource(R.array.weekdays_from_monday)
    RankingList(stringResource(R.string.sales_best_days)) {
        days.filter { it.total > 0 }
            .sortedByDescending { it.total }
            .take(5)
            .forEach { day ->
                RankingRow(
                    title = stringResource(
                        R.string.sales_best_day,
                        day.date.monthValue,
                        day.date.dayOfMonth,
                        weekdays[day.date.dayOfWeek.value - 1],
                    ),
                    subtitle = stringResource(R.string.sales_count, day.orderCount),
                    amount = formatWon(day.total),
                    onClick = { onDayClick(day.date) },
                )
            }
    }
}

@Composable
private fun RankingList(title: String, content: @Composable () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = title,
            style = OrmoType.BodyLgSemibold,
            color = OrmoColors.TextPrimary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun RankingRow(title: String, subtitle: String, amount: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = OrmoType.BodyMdMedium, color = OrmoColors.TextPrimary)
            Text(subtitle, style = OrmoType.BodySmRegular, color = OrmoColors.TextTertiary)
        }
        Text(amount, style = OrmoType.BodyMdRegular, color = OrmoColors.TextSecondary)
    }
}

/** 불러오기 실패(Figma 6.1.4) */
@Composable
private fun LoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .background(OrmoColors.FillDangerWeak, CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_alert),
                contentDescription = null,
                tint = OrmoColors.TextDanger,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            stringResource(R.string.sales_failed_title),
            style = OrmoType.BodyLgSemibold,
            color = OrmoColors.TextStrong,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            stringResource(R.string.sales_failed_body),
            style = OrmoType.BodyMdRegular,
            color = OrmoColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        ActionButton(
            text = stringResource(R.string.snackbar_retry),
            tone = ButtonTone.BRAND,
            onClick = onRetry,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
