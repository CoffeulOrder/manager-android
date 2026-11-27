package com.ormo.managerdemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.ormo.managerdemo.R

/** Figma 디자인 토큰(Semantic/\*). 값은 Figma 변수와 같다. */
object OrmoColors {
    val FillBrand = Color(0xFF298DFF)
    val FillBrandWeak = Color(0x29298DFF)
    val FillNeutral = Color(0xFF4E5968)
    val FillNeutralWeak = Color(0x0D07194C)
    val FillDanger = Color(0xFFF04452)
    val FillDangerWeak = Color(0x29F04452)
    val FillWarningWeak = Color(0x29FFB331)
    val FillSuccess = Color(0xFF03B26C)
    val StorePausedDot = Color(0xFFFFC342)
    val StoreClosedDot = Color(0x78F2F2FF)
    val TabIndicator = Color(0xFF333D4B)
    val SwitchOff = Color(0xFFD1D6DB)
    val ChevronWeak = Color(0x4F001936)
    val SnackbarAction = Color(0x30DEDEFF)
    val FieldBorder = Color(0x2E001D3A)
    val Placeholder = Color(0x75031832)

    val TextStrong = Color(0xFF191F28)
    val TextPrimary = Color(0xCC000C1E)
    val TextSecondary = Color(0xB2031228)
    val TextTertiary = Color(0x9400132B)
    val TextBrand = Color(0xFF006BDF)
    val TextDanger = Color(0xFFE42939)
    val TextWarning = Color(0xFFDD7D02)
    val TextOnFill = Color.White

    val BackgroundDefault = Color.White
    val BackgroundLower = Color(0xFFF2F4F6)
    val BackgroundPreview = Color(0x05001733)
    val Border = Color(0x1A001B37)
    val Dim = 0.2f

    // 상단 바의 흰색 투명도 단계
    val White100 = Color(0x1CD9D9FF)
    val White600 = Color(0x99F8F8FF)
    val White700 = Color(0xBFFDFDFF)
}

/** Figma 글꼴. SIL OFL (licenses/Pretendard-OFL.txt) */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** Figma 타이포 스타일 */
object OrmoType {
    private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = Pretendard,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        // Figma처럼 줄 높이의 가운데에 글자를 둔다.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    val BodyXsBold = style(12, 18, FontWeight.Bold)
    val BodySmRegular = style(14, 20, FontWeight.Normal)
    val BodySmMedium = style(14, 20, FontWeight.Medium)
    val BodySmSemibold = style(14, 20, FontWeight.SemiBold)
    val BodySmBold = style(14, 20, FontWeight.Bold)
    val BodyMdRegular = style(16, 24, FontWeight.Normal)
    val BodyMdMedium = style(16, 24, FontWeight.Medium)
    val BodyMdSemibold = style(16, 24, FontWeight.SemiBold)
    val BodyLgRegular = style(18, 28, FontWeight.Normal)
    val BodyLgMedium = style(18, 28, FontWeight.Medium)
    val BodyLgSemibold = style(18, 28, FontWeight.SemiBold)
    val BodyLgBold = style(18, 28, FontWeight.Bold)
    val BodyXlBold = style(20, 30, FontWeight.Bold)
    val TitleBold = style(24, 32, FontWeight.Bold)
    val HeadlineBold = style(30, 38, FontWeight.Bold)
    val DisplayMdBold = style(36, 44, FontWeight.Bold)
}

private val OrmoColorScheme = lightColorScheme(
    primary = OrmoColors.FillBrand,
    onPrimary = OrmoColors.TextOnFill,
    background = OrmoColors.BackgroundDefault,
    onBackground = OrmoColors.TextPrimary,
    surface = OrmoColors.BackgroundDefault,
    onSurface = OrmoColors.TextPrimary,
    error = OrmoColors.FillDanger,
)

/** Figma 프레임 가로폭(1280x800). 화면이 이보다 작거나 크면 전체를 같은 비율로 확대/축소한다. */
private const val REFERENCE_WIDTH_DP = 1280f

/**
 * 화면 크기(밀도)가 제각각인 태블릿/에뮬레이터에서도 Figma와 같은 비율로 보이도록
 * Density를 가로폭에 맞춰 조정한다.
 */
@Composable
private fun ScaleToReferenceWidth(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val configuration = LocalConfiguration.current
    val longSideDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val scale = (longSideDp / REFERENCE_WIDTH_DP).coerceIn(0.55f, 1.3f)
    val scaled = remember(base, scale) { Density(base.density * scale, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

/** 라이트 테마만 지원한다. */
@Composable
fun OrmoTheme(content: @Composable () -> Unit) {
    ScaleToReferenceWidth {
        MaterialTheme(colorScheme = OrmoColorScheme, content = content)
    }
}
