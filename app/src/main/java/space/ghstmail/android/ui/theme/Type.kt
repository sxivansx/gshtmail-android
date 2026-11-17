package space.ghstmail.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import space.ghstmail.android.R

val LeniaSans = FontFamily(
    Font(R.font.lenia_sans_regular, FontWeight.Normal),
    Font(R.font.lenia_sans_medium, FontWeight.Medium),
    Font(R.font.lenia_sans_semibold, FontWeight.SemiBold),
    Font(R.font.lenia_sans_bold, FontWeight.Bold),
    Font(R.font.lenia_sans_extrabold, FontWeight.ExtraBold),
)

val MonoFamily: FontFamily = FontFamily.Monospace

private fun display(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = LeniaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = (-0.02).em,
)

private fun body(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = LeniaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

val GhstTypography = Typography(
    displaySmall = display(34, 36, FontWeight.ExtraBold),
    headlineLarge = display(30, 34, FontWeight.ExtraBold),
    headlineMedium = display(26, 30),
    headlineSmall = display(22, 26),
    titleLarge = display(19, 24),
    titleMedium = body(16, 22, FontWeight.Medium),
    titleSmall = body(14, 20, FontWeight.Medium),
    bodyLarge = body(16, 24),
    bodyMedium = body(15, 22),
    bodySmall = body(13, 18),
    labelLarge = body(15, 20, FontWeight.SemiBold),
    labelMedium = body(13, 18, FontWeight.Medium),
    labelSmall = body(12, 16, FontWeight.Medium),
)

val AddressStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.02).em,
)

val AddressSmallStyle = AddressStyle.copy(
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)
