package de.diamind.ai.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DiaMindRed = Color(0xFFE53935)
val DiaMindBackground = Color(0xFF101010)
val DiaMindCard = Color(0xFF1E1E1E)
val DiaMindMuted = Color(0xFFBDBDBD)
val DiaMindWarning = Color(0xFFFFCC80)

@Composable
fun CardBox(
    color: Color = DiaMindCard,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color, RoundedCornerShape(18.dp))
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
