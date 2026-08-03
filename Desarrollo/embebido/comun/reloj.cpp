#include "reloj.h"
#include "config.h"
#include "net_wifi.h"
#include <time.h>
#include <Preferences.h>

namespace reloj {

static Preferences prefs;
static const char* NVS_NAMESPACE = "reloj";
static const char* NVS_KEY_EPOCH = "ultimoEpoch";

static bool     sincronizado = false;
static uint32_t epochBase    = 0;   // epoch real en el instante millisBase
static uint32_t millisBase   = 0;   // millis() cuando se fijó epochBase

static const uint32_t EPOCH_MINIMO_VALIDO = 1700000000UL;  // ~2023, filtra hora sin setear

void iniciar() {
  configTime(NTP_GMT_OFFSET_SEG, NTP_DST_OFFSET_SEG, NTP_SERVIDOR);

  // Recupera la última hora conocida para el fallback offline.
  prefs.begin(NVS_NAMESPACE, false);
  uint32_t guardado = prefs.getUInt(NVS_KEY_EPOCH, 0);
  prefs.end();
  if (guardado >= EPOCH_MINIMO_VALIDO) {
    epochBase  = guardado;
    millisBase = millis();
  }
}

static void persistir(uint32_t epoch) {
  prefs.begin(NVS_NAMESPACE, false);
  prefs.putUInt(NVS_KEY_EPOCH, epoch);
  prefs.end();
}

void sincronizarSiHayRed() {
  if (!net_wifi::conectado()) {
    return;
  }
  time_t ahora = time(nullptr);
  if (ahora >= (time_t) EPOCH_MINIMO_VALIDO) {
    epochBase    = (uint32_t) ahora;
    millisBase   = millis();
    sincronizado = true;
    persistir(epochBase);
  }
}

uint32_t ahoraEpoch() {
  if (epochBase == 0) {
    // Nunca hubo hora real: devolvemos segundos desde el arranque (mejor esfuerzo).
    return millis() / 1000UL;
  }
  uint32_t transcurrido = (millis() - millisBase) / 1000UL;
  return epochBase + transcurrido;
}

bool horaValida() {
  return sincronizado || epochBase >= EPOCH_MINIMO_VALIDO;
}

}  // namespace reloj
