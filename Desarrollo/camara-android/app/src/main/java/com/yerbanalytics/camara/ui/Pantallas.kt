package com.yerbanalytics.camara.ui

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yerbanalytics.camara.contrato.CanalOrdenes
import com.yerbanalytics.camara.registro.Entrada
import com.yerbanalytics.camara.registro.Nivel
import com.yerbanalytics.camara.servicio.EstadoDispositivo

/**
 * Se ve **una sola vez en la vida del dispositivo**. Pide lo único que no puede estar en el
 * código: dónde está el backend y el código de vinculación de un solo uso.
 */
@Composable
fun PantallaVinculacion(
    ocupado: Boolean,
    error: String?,
    onVincular: (baseUrl: String, codigo: String, nombre: String) -> Unit,
) {
    var url by remember { mutableStateOf("http://192.168.1.100:8000") }
    var codigo by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("Android riel") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Vincular dispositivo", color = Texto, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "El código sale del panel de simulación, en la sección Cámara del riel. Se tipea una " +
                "sola vez: después la credencial queda guardada en el teléfono.",
            color = TextoTenue,
            fontSize = 14.sp,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Backend") },
            supportingText = { Text("http://<ip-de-la-pc>:8000 · o https://<ip>:8443 si instalaste la CA") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it.uppercase() },
            label = { Text("Código de vinculación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre del dispositivo") },
            supportingText = { Text("Con esto se lo identifica en el panel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Text(error, color = Rojo, fontSize = 14.sp)
        }

        Button(
            onClick = { onVincular(url.trim(), codigo.trim(), nombre.trim()) },
            enabled = !ocupado && codigo.isNotBlank() && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ocupado) "Vinculando…" else "Vincular")
        }
    }
}

/**
 * Pantalla única de operación: estado arriba, visor **sólo mientras la cámara está trabajando**,
 * y el log abajo.
 *
 * El log en pantalla no es un lujo: depurar con las herramientas de desarrollo enchufadas a un
 * teléfono montado en un riel no es practicable.
 */
@Composable
fun PantallaOperacion(
    estado: EstadoDispositivo,
    entradas: List<Entrada>,
    resolucion: String,
    onVisor: (Preview.SurfaceProvider?) -> Unit,
    onPrueba: () -> Unit,
    onExportarLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TarjetaEstado(estado, resolucion)

        // El visor se monta siempre que la pantalla esté visible, incluso con la cámara en
        // reposo: la superficie tiene que estar ofrecida ANTES de la primera orden, o la sesión
        // se ata sin caso de uso de Preview y ya no hay forma de que aparezca imagen.
        Visor(estado.camaraAbierta, onVisor)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPrueba) { Text("Disparo de prueba") }
            TextButton(onClick = onExportarLog) { Text("Exportar log") }
        }

        LogEnPantalla(entradas, Modifier.weight(1f))
    }
}

@Composable
private fun TarjetaEstado(estado: EstadoDispositivo, resolucion: String) {
    val (color, etiqueta) = when {
        !estado.vinculado -> TextoTenue to "SIN VINCULAR"
        estado.degradado -> Ambar to "DEGRADADO"
        estado.canal == CanalOrdenes.Estado.CONECTADO -> Verde to "CONECTADO"
        estado.canal == CanalOrdenes.Estado.RECONECTANDO -> Ambar to "RECONECTANDO"
        else -> Rojo to "DESCONECTADO"
    }

    Card(colors = CardDefaults.cardColors(containerColor = Superficie)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.size(8.dp))
                // Legible a distancia: el teléfono está montado en un riel.
                Text(etiqueta, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (estado.camaraAbierta) "cámara abierta" else "cámara en reposo",
                    color = if (estado.camaraAbierta) Verde else TextoTenue,
                    fontSize = 12.sp,
                )
            }

            Text(
                "${estado.capturasOk} capturas · ${estado.capturasError} fallidas · " +
                    "${estado.pendientesEnvio} sin enviar · ${estado.ordenesEnCola} en cola",
                color = Texto,
                fontSize = 14.sp,
            )
            Text(
                if (estado.dispositivoId.isBlank()) resolucion
                else "${estado.dispositivoId} · $resolucion",
                color = TextoTenue,
                fontSize = 12.sp,
            )
            if (estado.degradado) {
                Text(
                    "No puede capturar: ${estado.detalle}. Dejá la app abierta un momento para " +
                        "recuperar el acceso a la cámara.",
                    color = Ambar,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Superficie de dibujo para la cámara. **La ofrece la pantalla, no la captura**: se entrega al
 * pasar a visible y se retira al dejar de serlo, con independencia de si la cámara está abierta.
 *
 * Atarla al estado de la cámara era circular —la cámara se abre al llegar la orden, la superficie
 * aparecía después, y la sesión ya estaba atada sin Preview—, y el síntoma era un visor en negro
 * permanente aunque la foto saliera bien.
 */
@Composable
private fun Visor(camaraAbierta: Boolean, onVisor: (Preview.SurfaceProvider?) -> Unit) {
    val contexto = LocalContext.current
    val duenio = LocalLifecycleOwner.current
    val vista = remember(contexto) {
        PreviewView(contexto).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    // Con la pantalla apagada no hay dónde dibujar: se retira la superficie y la captura sigue
    // igual, atada sólo a ImageCapture. Es el modo con el que opera montado en el riel.
    DisposableEffect(duenio, vista) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> onVisor(vista.surfaceProvider)
                Lifecycle.Event.ON_STOP -> onVisor(null)
                else -> Unit
            }
        }
        duenio.lifecycle.addObserver(observador)
        onDispose {
            duenio.lifecycle.removeObserver(observador)
            onVisor(null)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Superficie)
    ) {
        AndroidView(factory = { vista }, modifier = Modifier.fillMaxSize())
        if (!camaraAbierta) {
            // Un rectángulo negro sin explicación se lee como una falla. Con la cámara en reposo
            // el hardware está apagado a propósito, y eso hay que decirlo.
            Text(
                "Cámara en reposo · se enciende con la próxima orden",
                color = TextoTenue,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun LogEnPantalla(entradas: List<Entrada>, modifier: Modifier = Modifier) {
    val lista = rememberLazyListState()
    LaunchedEffect(entradas.size) {
        if (entradas.isNotEmpty()) lista.animateScrollToItem(entradas.lastIndex)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Superficie),
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            state = lista,
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entradas) { entrada ->
                Text(
                    entrada.formateada(),
                    color = when (entrada.nivel) {
                        Nivel.EXITO -> Verde
                        Nivel.AVISO -> Ambar
                        Nivel.ERROR -> Rojo
                        Nivel.INFO -> Texto
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
