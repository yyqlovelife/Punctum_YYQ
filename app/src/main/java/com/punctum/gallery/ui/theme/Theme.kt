package com.punctum.gallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.punctum.gallery.R

val Ink = Color(0xFF0A0A0A)
val Surface1 = Color(0xFF141414)
val DialogSurface = Color(0xFF181715)
val Bone = Color(0xFFEDE8DD)
val Muted = Color(0xFF8A8170)
val Gold = Color(0xFFC8A24B)
val Hairline = Color(0xFF2A2A28)

private val PunctumColors = darkColorScheme(
    background = Ink,
    surface = Surface1,
    onBackground = Bone,
    onSurface = Bone,
    primary = Gold,
    onPrimary = Ink,
    secondary = Muted,
    outline = Hairline,
)

/** 英文 UI 与大图页：Newsreader，默认 lining figures，接近 LFI 的 Chronicle Text G2。 */
val DetailSerif = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal),
    Font(R.font.newsreader_semibold, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_semibold, FontWeight.Bold),
)

val NotoSerifSc = FontFamily(
    Font(R.font.noto_serif_sc_regular, FontWeight.Normal),
    Font(R.font.noto_serif_sc_medium, FontWeight.Medium),
    Font(R.font.noto_serif_sc_semibold, FontWeight.SemiBold),
)

fun galleryTitleFont(text: String): FontFamily =
    if (text.any { it.code in 0x4E00..0x9FFF }) NotoSerifSc else DetailSerif

private val PunctumType = Typography(
    displayMedium = TextStyle(
        fontFamily = DetailSerif, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DetailSerif, fontWeight = FontWeight.Medium, fontSize = 29.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NotoSerifSc, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NotoSerifSc, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DetailSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp
    ),
)

@Composable
fun PunctumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PunctumColors,
        typography = PunctumType,
        content = content,
    )
}
