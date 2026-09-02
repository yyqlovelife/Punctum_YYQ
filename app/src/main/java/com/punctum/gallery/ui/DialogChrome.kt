package com.punctum.gallery.ui

import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.DialogSurface

internal val PunctumDialogShape = RoundedCornerShape(28.dp)

internal fun Modifier.punctumDialogChrome(): Modifier =
    shadow(
        elevation = 24.dp,
        shape = PunctumDialogShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.60f),
        spotColor = Color.Black.copy(alpha = 0.60f),
    ).border(
        width = 1.dp,
        color = Bone.copy(alpha = 0.12f),
        shape = PunctumDialogShape,
    )

@Composable
internal fun PunctumOverlayDialog(
    title: String,
    onDismissRequest: () -> Unit,
    animateScrim: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.98f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "punctum-dialog-scale",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (!animateScrim || entered) 0.55f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "punctum-dialog-scrim",
    )
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val surfaceInteractionSource = remember { MutableInteractionSource() }

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .widthIn(min = 280.dp, max = 560.dp)
                .punctumDialogChrome()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
                .clickable(
                    interactionSource = surfaceInteractionSource,
                    indication = null,
                    onClick = {},
                ),
            shape = PunctumDialogShape,
            color = DialogSurface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Bone,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    content = content,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
internal fun PunctumDialogTitle(text: String) {
    PunctumDialogWindowDim()
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Bone,
    )
}

@Composable
private fun PunctumDialogWindowDim() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) {
            onDispose { }
        } else {
            val originalDimAmount = window.attributes.dimAmount
            val originallyDimmed =
                window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.55f)
            onDispose {
                window.setDimAmount(originalDimAmount)
                if (!originallyDimmed) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
            }
        }
    }
}
