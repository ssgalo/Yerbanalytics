// ============================================================================
//  net_mqtt.h  -  Wrapper delgado sobre PubSubClient
// ----------------------------------------------------------------------------
//  Centraliza connect/publish/subscribe/loop. El callback de mensajes lo
//  provee el llamador (el sketch principal), que tiene acceso a las colas.
// ============================================================================
#ifndef NET_MQTT_H
#define NET_MQTT_H

#include <Arduino.h>
#include <PubSubClient.h>

namespace net_mqtt {

// Configura servidor, tamaño de buffer y callback de recepción.
void iniciar(MQTT_CALLBACK_SIGNATURE);

// Reconecta al broker si hace falta. Devuelve true si quedó conectado.
// Al reconectar, re-suscribe a los topics registrados con suscribir().
bool reconectarSiNecesario();

bool conectado();

// Registra un topic al que suscribirse (se re-aplica en cada reconexión).
void suscribir(const char* topic, uint8_t qos);

// Publica un payload. Devuelve true si el broker lo aceptó.
bool publicar(const char* topic, const char* payload, uint8_t qos);

// Debe llamarse seguido para procesar tráfico entrante/saliente.
void loop();

}  // namespace net_mqtt

#endif  // NET_MQTT_H
