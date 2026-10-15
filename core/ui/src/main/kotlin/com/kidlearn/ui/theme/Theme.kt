package com.kidlearn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF5B4CDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFFF8A3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0CC),
    tertiary = Color(0xFF1FA67A),
    tertiaryContainer = Color(0xFFCDF5E4),
    background = Color(0xFFFFFBF5),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFF1ECE4),
    error = Color(0xFFD93A3A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC6BFFF),
    secondary = Color(0xFFFFB68A),
    tertiary = Color(0xFF7DDBB3),
)

// Large, rounded type: easier for young readers.
private val KidTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
)

private val KidShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun KidLearnTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = KidTypography,
        shapes = KidShapes,
        content = content,
    )
}

/** Parses a subject's `#RRGGBB` colour, falling back to the theme colour. */
fun subjectColor(hex: String?, fallback: Color): Color = try {
    if (hex == null) fallback else Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    fallback
}
