package com.punctum.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Muted

@Composable
internal fun EmptyScreen(onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("P U N C T U M", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(22.dp))
        Text("Punctum", style = MaterialTheme.typography.displayMedium, color = Bone)
        Spacer(Modifier.height(18.dp))
        Text(
            "为情绪而生的画廊。\n从系统 DCIM 与 Pictures 图集里，映射你的第一个展厅。",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(52.dp))
        FrameButton(text = "选 择 系 统 图 集", onClick = onPickFolder)
    }
}
