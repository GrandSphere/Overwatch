package com.grandsphere.overwatch.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

@Composable
fun ColorPickerDialog(
    title: String,
    initialArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hsv = remember(initialArgb) {
        val out = FloatArray(3)
        AndroidColor.colorToHSV(initialArgb, out)
        out
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hexText by remember(initialArgb) {
        mutableStateOf(argbToHex(initialArgb))
    }
    var hexFromWheel by remember { mutableStateOf(true) }

    val currentArgb = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))).toArgb()

    LaunchedEffect(hue, sat, value) {
        if (hexFromWheel) {
            hexText = argbToHex(currentArgb)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(currentArgb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Spacer(Modifier.height(12.dp))
                SvSquare(
                    hue = hue,
                    saturation = sat,
                    value = value,
                    onChange = { s, v ->
                        hexFromWheel = true
                        sat = s
                        value = v
                    },
                )
                Spacer(Modifier.height(12.dp))
                HueBar(
                    hue = hue,
                    onHue = {
                        hexFromWheel = true
                        hue = it
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { raw ->
                        hexFromWheel = false
                        val trimmed = raw.trim()
                        hexText = trimmed
                        parseHexArgb(trimmed)?.let { argb ->
                            val out = FloatArray(3)
                            AndroidColor.colorToHSV(argb, out)
                            hue = out[0]
                            sat = out[1]
                            value = out[2]
                            hexFromWheel = true
                        }
                    },
                    label = { Text("Hex colour") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val argb = parseHexArgb(hexText) ?: currentArgb
                onConfirm(argb)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun argbToHex(argb: Int): String {
    val rgb = argb and 0xFFFFFF
    return "#%06X".format(rgb)
}

private fun parseHexArgb(raw: String): Int? {
    val s = raw.trim().removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    val value = s.toLongOrNull(16) ?: return null
    return if (s.length == 8) {
        value.toInt()
    } else {
        (0xFF000000 or value).toInt()
    }
}

@Composable
private fun SvSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(hue) {
                fun at(pos: Offset) {
                    val s = (pos.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                    onChange(s, v)
                }
                detectTapGestures { at(it) }
            }
            .pointerInput(hue) {
                fun at(pos: Offset) {
                    val s = (pos.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                    onChange(s, v)
                }
                detectDragGestures { change, _ -> at(change.position) }
            },
    ) {
        val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = saturation * size.width
        val cy = (1f - value) * size.height
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
        drawCircle(
            Color.Black,
            radius = 8.dp.toPx(),
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun HueBar(hue: Float, onHue: (Float) -> Unit) {
    val hues = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { onHue((it.x / size.width).coerceIn(0f, 1f) * 360f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onHue((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(hues))
        val x = (hue / 360f) * size.width
        drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, size.height / 2f))
        drawCircle(
            Color.Black,
            radius = 10.dp.toPx(),
            center = Offset(x, size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}
