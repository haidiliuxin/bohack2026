package com.neurogarden.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object NeuroColors {
    val Background = Color(0xFFF7F9FC)
    val Card = Color(0xFFFFFFFF)
    val CardSoft = Color(0xFFF4F7FB)
    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF6B7280)
    val TextMuted = Color(0xFF9CA3AF)
    val Line = Color(0xFFE7ECF3)
    val Blue = Color(0xFF2F7CF6)
    val BlueSoft = Color(0xFFEAF2FF)
    val Mint = Color(0xFF75D7BB)
    val Coral = Color(0xFFFF7E76)
    val Lavender = Color(0xFFA99AF8)
    val Amber = Color(0xFFFFC45D)
    val Teal = Color(0xFF23C9D4)
    val Danger = Color(0xFFDB4F4A)
}

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun NeuroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = NeuroColors.Card,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(14.dp, CardShape, clip = false),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, NeuroColors.Line.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun NeuroGradientCard(
    modifier: Modifier = Modifier,
    brush: Brush,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun NeuroListRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {
        Text("›", color = NeuroColors.TextMuted, style = MaterialTheme.typography.headlineSmall)
    }
) {
    NeuroCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            trailing()
        }
    }
}

@Composable
fun NeuroPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeuroColors.Blue,
            contentColor = Color.White
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NeuroSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NeuroColors.Line)
    ) {
        Text(text, color = NeuroColors.Blue, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NeuroAlertDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dismissLabel = dismissText
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = NeuroColors.Card,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                title,
                color = NeuroColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeuroColors.Blue)
            ) {
                Text(confirmText, color = Color.White)
            }
        },
        dismissButton = if (dismissLabel != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel, color = NeuroColors.TextSecondary)
                }
            }
        } else {
            null
        }
    )
}
