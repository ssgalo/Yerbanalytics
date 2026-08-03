#include "net_wifi.h"
#include "config.h"
#include <WiFi.h>

namespace net_wifi {

static unsigned long ultimoIntento = 0;
static const unsigned long INTERVALO_REINTENTO_MS = 5000;

void iniciar() {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  ultimoIntento = millis();
}

bool conectado() {
  return WiFi.status() == WL_CONNECTED;
}

void reconectarSiNecesario() {
  if (conectado()) {
    return;
  }
  unsigned long ahora = millis();
  if (ahora - ultimoIntento >= INTERVALO_REINTENTO_MS) {
    ultimoIntento = ahora;
    Serial.println("[wifi] Reintentando conexión...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  }
}

int rssi() {
  return conectado() ? WiFi.RSSI() : 0;
}

void obtenerMac(char* buf, size_t n) {
  uint8_t mac[6];
  WiFi.macAddress(mac);
  snprintf(buf, n, "%02X:%02X:%02X:%02X:%02X:%02X",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
}

}  // namespace net_wifi
