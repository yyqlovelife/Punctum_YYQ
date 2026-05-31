package com.punctum.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Hairline
import com.punctum.gallery.ui.theme.Muted

@Composable
internal fun SectionLabel(text: String, color: Color = Muted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
internal fun FrameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, Gold.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Gold)
    }
}

@Composable
internal fun ExifField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.width(132.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = Bone,
            modifier = Modifier.weight(1f),
        )
    }
}
