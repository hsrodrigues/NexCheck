package com.nexcheck.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nexcheck.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

val Typography = Typography(
    displaySmall = style(32, 40, FontWeight.ExtraBold, -0.5),
    headlineLarge = style(28, 36, FontWeight.ExtraBold, -0.4),
    headlineMedium = style(24, 32, FontWeight.Bold, -0.3),
    headlineSmall = style(20, 28, FontWeight.Bold, -0.2),
    titleLarge = style(20, 28, FontWeight.Bold, -0.2),
    titleMedium = style(16, 24, FontWeight.SemiBold, -0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.2),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.4)
)
