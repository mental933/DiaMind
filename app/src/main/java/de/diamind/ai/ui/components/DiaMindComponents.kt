package de.diamind.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DiaMindRed = Color(0xFFE53935)
val DiaMindBackground = Color(0xFF070B16)
val DiaMindCard = Color(0xFF171B2D)
val DiaMindCardDark = Color(0xFF111525)
val DiaMindMuted = Color(0xFFAAB0C6)
val DiaMindWarning = Color(0xFFFFCC80)
val DiaMindCyan = Color(0xFF46DFFF)
val DiaMindPurple = Color(0xFF9B4DFF)
val DiaMindPink = Color(0xFFFF3DD1)

@Composable
fun CardBox(
    color: Color = DiaMindCard,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    brush: Brush = Brush.linearGradient(
        listOf(
            Color(0xFF171B34).copy(alpha = 0.98f),
            Color(0xFF10172B).copy(alpha = 0.98f)
        )
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
}
