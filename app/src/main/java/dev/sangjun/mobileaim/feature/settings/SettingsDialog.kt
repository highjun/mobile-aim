package dev.sangjun.mobileaim.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    current: MarkerSettings,
    onSave: (boardSizeMm: Double, xOffsetMm: Double, yOffsetMm: Double, z0Meters: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var boardSizeText by remember(current) { mutableStateOf(current.boardSizeMm.toString()) }
    var xOffsetText by remember(current) { mutableStateOf(current.xOffsetMm.toString()) }
    var yOffsetText by remember(current) { mutableStateOf(current.yOffsetMm.toString()) }
    var z0Text by remember(current) { mutableStateOf(current.z0Meters.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("마커 설정") },
        text = {
            Column {
                OutlinedTextField(
                    value = boardSizeText,
                    onValueChange = { boardSizeText = it },
                    label = { Text("D · 보드 한 변 (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = xOffsetText,
                    onValueChange = { xOffsetText = it },
                    label = { Text("X · 수평 오프셋 (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = yOffsetText,
                    onValueChange = { yOffsetText = it },
                    label = { Text("Y · 수직 오프셋 (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = z0Text,
                    onValueChange = { z0Text = it },
                    label = { Text("Z0 · 기준 거리 (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val boardSizeMm = boardSizeText.toDoubleOrNull() ?: current.boardSizeMm
                val xOffsetMm = xOffsetText.toDoubleOrNull() ?: current.xOffsetMm
                val yOffsetMm = yOffsetText.toDoubleOrNull() ?: current.yOffsetMm
                val z0Meters = z0Text.toFloatOrNull() ?: current.z0Meters
                onSave(boardSizeMm, xOffsetMm, yOffsetMm, z0Meters)
            }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
