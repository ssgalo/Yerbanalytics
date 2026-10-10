// ============================================================================
//  sensor_tanques.h  -  Nivel de los tanques de agua e insumo (2x HC-SR04)
// ----------------------------------------------------------------------------
//  Cada HC-SR04 mira hacia abajo desde la tapa de su tanque y mide la distancia
//  hasta el líquido. Comparten el pin TRIG (están en tanques distintos, sus ecos
//  no se cruzan) y se disparan de a uno, leyendo el ECHO del que corresponde.
//  La distancia se convierte a % con la calibración lleno/vacío de config.h.
// ============================================================================
#ifndef SENSOR_TANQUES_H
#define SENSOR_TANQUES_H

#include "tipos.h"

namespace sensor_tanques {

void iniciar();
LecturaTanques leer();   // cada tanque con su estado: ERROR si no hubo eco

}  // namespace sensor_tanques

#endif  // SENSOR_TANQUES_H
