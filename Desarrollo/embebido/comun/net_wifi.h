// ============================================================================
//  net_wifi.h  -  Conexión y reconexión WiFi
// ============================================================================
#ifndef NET_WIFI_H
#define NET_WIFI_H

#include <Arduino.h>

namespace net_wifi {

// Inicia la conexión (no bloquea indefinidamente; deja el reintento a la tarea de red).
void iniciar();

// Devuelve true si hay conexión WiFi activa.
bool conectado();

// Reintenta la conexión si se cayó. Llamar periódicamente desde task_red.
void reconectarSiNecesario();

// RSSI actual en dBm (0 si no hay conexión).
int rssi();

// MAC del nodo formateada "AA:BB:CC:DD:EE:FF" en el buffer provisto.
void obtenerMac(char* buf, size_t n);

}  // namespace net_wifi

#endif  // NET_WIFI_H
