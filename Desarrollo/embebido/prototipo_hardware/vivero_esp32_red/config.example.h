// ============================================================
//  config.example.h — plantilla de config.h para vivero_esp32_red
//  Copiar como config.h (en esta misma carpeta) y completar.
//  config.h NO se versiona (está en Desarrollo/embebido/.gitignore).
// ============================================================
#pragma once

// Red WiFi de 2,4 GHz (el ESP32 no ve redes de 5 GHz)
#define WIFI_SSID      "mi-red"
#define WIFI_PASSWORD  "mi-clave"

// Broker MQTT: IP LAN de la PC donde corre Mosquitto (docker compose).
// No poner "localhost": para el ESP32, localhost es él mismo.
// En Linux la IP sale de `ip a` (ej. 192.168.0.100).
#define MQTT_HOST      "192.168.1.64"
#define MQTT_PORT      1883

// Prefijo del clientId MQTT; el sketch le agrega la MAC de la placa.
#define MQTT_CLIENT_ID_BASE "riel-esp32"
