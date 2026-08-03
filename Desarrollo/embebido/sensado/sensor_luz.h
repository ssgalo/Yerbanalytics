// ============================================================================
//  sensor_luz.h  -  Driver abstraído del sensor de luz
// ----------------------------------------------------------------------------
//  El valor devuelto alimenta el campo "uv" del contrato.
//
//  TODO (hardware pendiente): hoy la fuente es un fotoresistor (LDR) que da un
//  porcentaje de luz (0-100%), NO radiación UV real. Cuando se confirme el
//  sensor definitivo (p.ej. GUVA-S12SD / ML8511 para índice UV real), basta con
//  reimplementar leer() y ajustar la calibración en config.h; el resto del
//  firmware no cambia.
// ============================================================================
#ifndef SENSOR_LUZ_H
#define SENSOR_LUZ_H

#include "tipos.h"

namespace sensor_luz {

void iniciar();
LecturaLuz leer();

}  // namespace sensor_luz

#endif  // SENSOR_LUZ_H
