package com.yerbanalytics.camara.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Fondo = Color(0xFF0E1512)
val Superficie = Color(0xFF16201C)
val Verde = Color(0xFF4ADE80)
val Ambar = Color(0xFFFBBF24)
val Rojo = Color(0xFFF87171)
val Texto = Color(0xFFE7EDEA)
val TextoTenue = Color(0xFF8CA39A)

/** Oscuro y fijo: el teléfono vive encendido en un riel y no hay quién elija un tema. */
@Composable
fun TemaCamara(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Verde,
            onPrimary = Fondo,
            background = Fondo,
            onBackground = Texto,
            surface = Superficie,
            onSurface = Texto,
            error = Rojo,
        ),
        content = contenido,
    )
}
