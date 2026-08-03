// ============================================================================
//  reloj.h  -  Hora real con NTP y fallback monotónico
// ----------------------------------------------------------------------------
//  Sella cada lectura con su timestamp real. Con WiFi sincroniza por NTP y
//  persiste la última hora conocida en NVS; sin red, estima la hora con el
//  contador monotónico del ESP32 (para respetar el timestamp original de las
//  lecturas bufferizadas offline - HU-13 CA-03).
// ============================================================================
#ifndef RELOJ_H
#define RELOJ_H

#include <Arduino.h>

namespace reloj {

// Inicializa NTP (no bloquea) y recupera la última hora conocida de NVS.
void iniciar();

// Intenta sincronizar por NTP si hay WiFi. Llamar desde task_red.
void sincronizarSiHayRed();

// Epoch en segundos. Si nunca hubo NTP, estima con el monotónico desde el
// último instante conocido (mejor esfuerzo).
uint32_t ahoraEpoch();

// true si al menos una vez se logró sincronizar la hora real.
bool horaValida();

}  // namespace reloj

#endif  // RELOJ_H
