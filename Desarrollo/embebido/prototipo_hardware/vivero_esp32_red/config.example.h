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

// ── Nodo del stand (add-secuencias-demo-expo) ─────────────────
// Zona y sector a los que responde este ESP32 por MQTT. Tienen que coincidir con la
// topología que ve el backend (el sector es el destino de las secuencias de riego y
// mediasombra). Si falta NODO_ZONA_ID o NODO_SECTOR_ID, el sketch no compila.
#define NODO_ZONA_ID    "MZ-1"       // zona del stand (topología 1x2)
#define NODO_SECTOR_ID  "MZ-1-001"   // sector cuyos actuadores son la bomba y la mediasombra

// Caudal de la bomba cuando la válvula está abierta (PWM 0-255).
#define BOMBA_CAUDAL_PWM  200

// "Leer ahora": 0 = no hay sensores conectados, el nodo sólo loguea y no publica.
// Poner 1 recién cuando se complete leer_sensores() en el sketch (ver el hueco marcado).
#define LECTURA_SENSORES_HABILITADA  0
