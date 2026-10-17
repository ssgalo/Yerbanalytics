#include "act_riego.h"
#include "config.h"
#include "contrato.h"

// El límite local es la última barrera, pero no puede quedar por debajo de lo que el
// contrato permite pedir: recortaría los riegos largos en silencio.
static_assert(LIMITE_VALVULA_SEG_MAX >= CONTRATO_VALVULA_DURACION_MAX_SEG,
              "LIMITE_VALVULA_SEG_MAX (config.h) debe ser >= CONTRATO_VALVULA_DURACION_MAX_SEG (contrato.h)");

namespace act_riego {

// Tipo de tramo, que es también el índice de su bomba.
static const int RIEGO  = 0;
static const int INSUMO = 1;

// Un riego o un insumo en curso de un sector.
struct Tramo {
  bool     activo;
  char     commandId[40];
  uint32_t inicioMs;
  uint32_t duracionMs;
  long     durationSec;     // para el ack
  float    ml;              // para el ack (insumo pedido en ml)
  bool     limiteExcedido;  // se recortó al límite local
};

static Tramo    tramos[CANT_SECTORES + 1][2];          // [sector 1..2][RIEGO|INSUMO]; el 0 no se usa
static bool     valvulaAbierta[CANT_SECTORES + 1];
static uint32_t valvulaAbiertaDesdeMs[CANT_SECTORES + 1];
static bool     bombaPrendida[2];

// Acks de los tramos terminados, en orden, hasta que atender() los entrega.
static const int MAX_PENDIENTES = 6;
static Ack pendientes[MAX_PENDIENTES];
static int cantPendientes = 0;

// cerrado = true → el relé cierra su contacto NO (la carga recibe tensión).
static void releEscribir(int pin, bool cerrado) {
  digitalWrite(pin, (cerrado != (bool) RELE_ACTIVO_EN_LOW) ? HIGH : LOW);
}

static int pinValvula(int sector) {
  return (sector == 2) ? PIN_RELE_VALVULA_S2 : PIN_RELE_VALVULA_S1;
}

static int pinBomba(int bomba) {
  return (bomba == RIEGO) ? PIN_RELE_BOMBA_RIEGO : PIN_RELE_BOMBA_INSUMO;
}

static const char* nombre(int tipo) {
  return (tipo == RIEGO) ? "riego" : "insumo";
}

static void ackBase(Ack& a, const char* commandId, int sector) {
  a = {};
  strlcpy(a.commandId, commandId, sizeof(a.commandId));
  a.sector = sector;
  a.targetPct = -1;
}

static void encolarAck(const Ack& a) {
  if (cantPendientes < MAX_PENDIENTES) {
    pendientes[cantPendientes++] = a;
  } else {
    Serial.println("[riego] Demasiados acks pendientes; se pierde uno");
  }
}

// Cierra el tramo y deja su ack. Los relés los ajusta aplicarReles().
static void terminar(int sector, int tipo, EstadoAck status, const char* detalle) {
  Tramo& t = tramos[sector][tipo];
  if (!t.activo) {
    return;
  }
  t.activo = false;

  Ack a;
  ackBase(a, t.commandId, sector);
  a.status = status;
  strlcpy(a.tipoDetalle, detalle, sizeof(a.tipoDetalle));
  if (status == EstadoAck::SUCCESS) {
    a.durationSec = t.durationSec;
    a.ml = t.ml;
  }
  encolarAck(a);
  Serial.printf("[riego] Sector %d: %s terminado (%s)\n", sector, nombre(tipo), detalle);
}

// Lleva los relés al estado que piden los tramos activos.
static void aplicarReles() {
  uint32_t ahora = millis();

  bool valvulaQuiere[CANT_SECTORES + 1] = {false};
  bool bombaQuiere[2] = {false, false};
  for (int s = 1; s <= CANT_SECTORES; s++) {
    for (int tipo = RIEGO; tipo <= INSUMO; tipo++) {
      if (!tramos[s][tipo].activo) {
        continue;
      }
      valvulaQuiere[s] = true;
      if (valvulaAbierta[s] && ahora - valvulaAbiertaDesdeMs[s] >= RIEGO_VALVULA_ANTES_DE_BOMBA_MS) {
        bombaQuiere[tipo] = true;
      }
    }
  }

  // 1. Bombas que se apagan.
  for (int b = RIEGO; b <= INSUMO; b++) {
    if (bombaPrendida[b] && !bombaQuiere[b]) {
      releEscribir(pinBomba(b), false);
      bombaPrendida[b] = false;
      Serial.printf("[riego] Bomba de %s apagada\n", nombre(b));
    }
  }
  // 2. Válvulas.
  for (int s = 1; s <= CANT_SECTORES; s++) {
    if (valvulaQuiere[s] != valvulaAbierta[s]) {
      releEscribir(pinValvula(s), valvulaQuiere[s]);
      valvulaAbierta[s] = valvulaQuiere[s];
      if (valvulaQuiere[s]) {
        valvulaAbiertaDesdeMs[s] = ahora;
      }
      Serial.printf("[riego] Válvula sector %d %s\n", s, valvulaQuiere[s] ? "abierta" : "cerrada");
    }
  }
  // 3. Bombas que se prenden (su válvula ya estaba abierta el tiempo de espera).
  for (int b = RIEGO; b <= INSUMO; b++) {
    if (!bombaPrendida[b] && bombaQuiere[b]) {
      releEscribir(pinBomba(b), true);
      bombaPrendida[b] = true;
      Serial.printf("[riego] Bomba de %s prendida\n", nombre(b));
    }
  }
}

void iniciar() {
  for (int s = 1; s <= CANT_SECTORES; s++) {
    pinMode(pinValvula(s), OUTPUT);
    releEscribir(pinValvula(s), false);   // cerrada (estado seguro)
  }
  for (int b = RIEGO; b <= INSUMO; b++) {
    pinMode(pinBomba(b), OUTPUT);
    releEscribir(pinBomba(b), false);     // apagada (estado seguro)
  }
}

// Valida un ON y calcula su duración con los límites locales. Devuelve null si es válido; si no, el detalle.tipo del error.
static const char* evaluarEncendido(const Comando& cmd, int tipo, uint32_t& duracionMs, float& ml, bool& limite) {
  ml = 0;
  limite = false;
  if (tipo == RIEGO) {
    long seg = cmd.durationSec;
    if (seg <= 0) {
      return "duracion_invalida";
    }
    if (seg > LIMITE_VALVULA_SEG_MAX) {
      Serial.printf("[riego] Duración %lds excede el límite; se recorta a %ds\n", seg, LIMITE_VALVULA_SEG_MAX);
      seg = LIMITE_VALVULA_SEG_MAX;
      limite = true;
    }
    duracionMs = (uint32_t) seg * 1000UL;
  } else {
    if (cmd.ml > 0) {
      ml = cmd.ml;
      if (ml > LIMITE_BOMBA_ML_MAX) {
        Serial.printf("[riego] Dosis %.1fml excede el límite; se recorta a %dml\n", ml, LIMITE_BOMBA_ML_MAX);
        ml = LIMITE_BOMBA_ML_MAX;
        limite = true;
      }
      duracionMs = (uint32_t) (ml * BOMBA_INSUMO_MS_POR_ML);
    } else if (cmd.durationSec > 0) {
      duracionMs = (uint32_t) cmd.durationSec * 1000UL;
    } else {
      duracionMs = (uint32_t) INSUMO_SEG_POR_DEFECTO * 1000UL;   // "pump ON" sin parámetros
    }
    if (duracionMs > (uint32_t) LIMITE_INSUMO_SEG_MAX * 1000UL) {
      duracionMs = (uint32_t) LIMITE_INSUMO_SEG_MAX * 1000UL;
      limite = true;
    }
  }
  return nullptr;
}

const char* evaluar(const Comando& cmd, uint32_t& duracionMs, float& ml) {
  int s = cmd.sector;
  if (s < 1 || s > CANT_SECTORES) {
    return "sector_invalido";
  }
  if (cmd.accion != TipoAccion::ENCENDER) {
    return "accion_invalida";
  }
  bool limite;
  return evaluarEncendido(cmd, (cmd.actuador == TipoActuador::VALVULA) ? RIEGO : INSUMO, duracionMs, ml, limite);
}

bool activo(int sector, int tipo) {
  if (sector < 1 || sector > CANT_SECTORES || tipo < RIEGO || tipo > INSUMO) {
    return false;
  }
  return tramos[sector][tipo].activo;
}

bool comenzar(const Comando& cmd, Ack& ack) {
  ackBase(ack, cmd.commandId, cmd.sector);
  ack.status = EstadoAck::ERROR;

  int s = cmd.sector;
  int tipo = (cmd.actuador == TipoActuador::VALVULA) ? RIEGO : INSUMO;
  if (s < 1 || s > CANT_SECTORES) {
    strlcpy(ack.tipoDetalle, "sector_invalido", sizeof(ack.tipoDetalle));
    return true;
  }

  if (cmd.accion == TipoAccion::APAGAR) {
    terminar(s, tipo, EstadoAck::ERROR, "cancelado");
    aplicarReles();
    ack.status = EstadoAck::SUCCESS;
    strlcpy(ack.tipoDetalle, "ok", sizeof(ack.tipoDetalle));
    return true;
  }
  if (cmd.accion != TipoAccion::ENCENDER) {
    strlcpy(ack.tipoDetalle, "accion_invalida", sizeof(ack.tipoDetalle));
    return true;
  }

  // Duración del tramo, con los límites locales.
  uint32_t duracionMs;
  float ml;
  bool limite;
  const char* error = evaluarEncendido(cmd, tipo, duracionMs, ml, limite);
  if (error != nullptr) {
    strlcpy(ack.tipoDetalle, error, sizeof(ack.tipoDetalle));
    return true;
  }

  // Un comando nuevo para lo mismo reemplaza al que estaba en curso.
  terminar(s, tipo, EstadoAck::ERROR, "reemplazado");

  Tramo& t = tramos[s][tipo];
  t.activo = true;
  strlcpy(t.commandId, cmd.commandId, sizeof(t.commandId));
  t.inicioMs = millis();
  t.duracionMs = duracionMs;
  t.durationSec = (long) ((duracionMs + 999UL) / 1000UL);
  t.ml = ml;
  t.limiteExcedido = limite;
  Serial.printf("[riego] Sector %d: %s por %lu ms\n", s, nombre(tipo), (unsigned long) duracionMs);

  aplicarReles();
  return false;
}

bool atender(Ack& out) {
  uint32_t ahora = millis();
  for (int s = 1; s <= CANT_SECTORES; s++) {
    for (int tipo = RIEGO; tipo <= INSUMO; tipo++) {
      Tramo& t = tramos[s][tipo];
      if (t.activo && ahora - t.inicioMs >= t.duracionMs) {
        terminar(s, tipo, EstadoAck::SUCCESS, t.limiteExcedido ? "limite_excedido" : "ok");
      }
    }
  }
  aplicarReles();

  if (cantPendientes == 0) {
    return false;
  }
  out = pendientes[0];
  for (int i = 1; i < cantPendientes; i++) {
    pendientes[i - 1] = pendientes[i];
  }
  cantPendientes--;
  return true;
}

}  // namespace act_riego
