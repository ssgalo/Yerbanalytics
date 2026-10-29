// ============================================================================
//  net_wifi_usb.cpp  -  net_wifi.h sin Wi-Fi, para el transporte USB
// ----------------------------------------------------------------------------
//  Implementa la misma interfaz que comun/net_wifi.cpp (design D1 y D6 de
//  add-transporte-usb-nodo). No enciende el Wi-Fi: el nodo habla con la PC por
//  el cable. Lo usan el sketch (MAC de los errores), task_sensado (MAC y señal
//  de la telemetría) y reloj (sincronizar cuando hay "red").
// ============================================================================
#include "net_wifi.h"
#include "enlace_usb.h"
#include <esp_mac.h>
#include <esp_netif.h>

namespace net_wifi {

void iniciar() {
  // Sin Wi-Fi nadie levanta la pila TCP/IP, y reloj::iniciar() llama a configTime (SNTP), que la necesita: sin
  // esto, algunas versiones del core abortan al arrancar. Levantarla no enciende ninguna radio.
  esp_netif_init();
  Serial.println("[wifi] Transporte USB: el Wi-Fi queda apagado.");
}

// "Hay red" = el puente está vivo y conectado al broker. Con esto reloj toma la hora del latido.
bool conectado() {
  return enlace_usb::hayLatido();
}

void reconectarSiNecesario() {
  // Nada que reconectar: el enlace lo mantiene el puente (net_mqtt_usb.cpp).
}

// Sin radio no hay RSSI. 0 dBm el dashboard lo muestra como señal buena (el umbral de débil es -85).
int rssi() {
  return 0;
}

// La MAC de estación sin encender el Wi-Fi: la misma que se reporta por Wi-Fi, así el backend ve el mismo nodo.
void obtenerMac(char* buf, size_t n) {
  uint8_t mac[6] = {0};
  esp_read_mac(mac, ESP_MAC_WIFI_STA);
  snprintf(buf, n, "%02X:%02X:%02X:%02X:%02X:%02X",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
}

}  // namespace net_wifi
