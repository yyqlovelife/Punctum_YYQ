package com.punctum.gallery.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Static, glyph-confined recessed walls. Text layout and accessibility remain native Compose Text. */
@Composable
internal fun PaperDebossText(
    text: AnnotatedString,
    color: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = LocalTextStyle.current,
    depth: Dp = .46.dp,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, color = color, fontFamily = fontFamily, fontSize = fontSize, fontWeight = fontWeight,
        lineHeight = lineHeight, letterSpacing = letterSpacing, textAlign = textAlign,
        maxLines = maxLines, overflow = overflow, style = style,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithCache {
            val measured = layout
            if (measured == null || size.width < 1 || size.height < 1) {
                onDrawWithContent { drawContent() }
            } else {
                val width = ceil(size.width).toInt().coerceAtLeast(1)
                val height = ceil(size.height).toInt().coerceAtLeast(1)
                val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
                CanvasDrawScope().draw(this, layoutDirection, ComposeCanvas(mask.asImageBitmap()),
                    Size(width.toFloat(), height.toFloat())) { drawText(measured, color = Color.White) }
                val amount = depth.toPx()
                // Original glyph minus shifted glyph: both walls stay inside the letter, so
                // this cannot cast a floating drop shadow onto the paper surface.
                val darkWall = recessedWall(mask, amount, amount)
                val lightWall = recessedWall(mask, -amount, -amount)
                val floorPaint = tintPaint(color)
                val shadePaint = tintPaint(Color(0xFF514A40).copy(alpha = .66f))
                val lightPaint = tintPaint(Color.White.copy(alpha = .96f))
                onDrawWithContent {
                    drawIntoCanvas { c ->
                        c.nativeCanvas.drawBitmap(mask, 0f, 0f, floorPaint)
                        c.nativeCanvas.drawBitmap(darkWall, 0f, 0f, shadePaint)
                        c.nativeCanvas.drawBitmap(lightWall, 0f, 0f, lightPaint)
                    }
                }
            }
        })
}

private fun tintPaint(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
    colorFilter = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_IN)
}

private fun recessedWall(mask: Bitmap, dx: Float, dy: Float): Bitmap {
    val result = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
    val canvas = android.graphics.Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(mask, 0f, 0f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    canvas.drawBitmap(mask, dx, dy, paint)
    return result
}
