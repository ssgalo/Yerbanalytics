// ============================================================================
//  sensor_suelo.h  -  Sonda de suelo NPK-pH-EC por RS-485 / Modbus RTU
// ----------------------------------------------------------------------------
//  Lee 9 registros holding (humedad, temp, EC, pH, N, P, K, salinidad, TDS)
//  vía función Modbus 0x03. Portado de sensores_unificados.ino con validación
//  de trama y CRC, y resultado tipado.
// ============================================================================
#ifndef SENSOR_SUELO_H
#define SENSOR_SUELO_H

#include "tipos.h"

namespace sensor_suelo {

void iniciar();
LecturaSuelo leer();   // estado = ERROR si no responde o el CRC/trama es inválido

}  // namespace sensor_suelo

#endif  // SENSOR_SUELO_H
