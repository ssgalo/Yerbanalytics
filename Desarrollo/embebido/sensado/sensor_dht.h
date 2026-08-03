// ============================================================================
//  sensor_dht.h  -  Driver del DHT11 (humedad y temperatura del aire)
// ============================================================================
#ifndef SENSOR_DHT_H
#define SENSOR_DHT_H

#include "tipos.h"

namespace sensor_dht {

void iniciar();
LecturaAmbiente leer();   // estado = ERROR si la lectura da NaN

}  // namespace sensor_dht

#endif  // SENSOR_DHT_H
