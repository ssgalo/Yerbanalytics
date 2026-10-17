#include "act_mediasombra.h"
#include "config.h"
#include "contrato.h"

namespace act_mediasombra {

static const int PWM_FREQ = 1000;
static const int PWM_BITS = 8;   // 0-255

// Movimiento en curso.
static bool     moviendo = false;
static bool     haciaEnrollada = false;
static uint32_t inicioMs = 0;
static char     commandId[40] = "";
static int      sectorComando = 1;

// Falla del final de carrera (S-05): cuál se esperaba y no se tocó. 'fallaInformada' evita repetir el error mientras el
// problema siga: se limpia cuando un movimiento termina bien.
static bool        fallaPendiente = false;
static bool        fallaInformada = false;
static const char* finFallido = VALOR_FIN_ABIERTA;

// Acks pendientes (el reemplazado y el que termina pueden salir en la misma vuelta).
static const int MAX_PENDIENTES = 2;
static Ack pendientes[MAX_PENDIENTES];
static int cantPendientes = 0;

// Final de carrera con INPUT_PULLUP: NC → activado = HIGH; NO → activado = LOW.
static bool finActivado(int pin) {
  return digitalRead(pin) == (FINES_CARRERA_NC ? HIGH : LOW);
}

static int pinFin(bool enrollada) {
  return enrollada ? PIN_MEDIASOMBRA_FIN_ENROLLADA : PIN_MEDIASOMBRA_FIN_DESENROLLADA;
}

static const char* nombre(bool enrollada) {
  return enrollada ? "enrollada" : "desenrollada";
}

static void detenerMotor() {
  digitalWrite(PIN_MEDIASOMBRA_IN1, LOW);
  digitalWrite(PIN_MEDIASOMBRA_IN2, LOW);
  ledcWrite(PIN_MEDIASOMBRA_ENA, 0);
}

// Enrollar = motor "atrás" (IN1 LOW, IN2 HIGH), como en vivero_esp32.
static void arrancarMotor(bool enrollar) {
  digitalWrite(PIN_MEDIASOMBRA_IN1, enrollar ? LOW : HIGH);
  digitalWrite(PIN_MEDIASOMBRA_IN2, enrollar ? HIGH : LOW);
  ledcWrite(PIN_MEDIASOMBRA_ENA, MEDIASOMBRA_VELOCIDAD_PWM);
}

static void ackBase(Ack& a, const char* id, int sector) {
  a = {};
  strlcpy(a.commandId, id, sizeof(a.commandId));
  a.sector = sector;
  a.targetPct = -1;
}

static void encolarAck(const Ack& a) {
  if (cantPendientes < MAX_PENDIENTES) {
    pendientes[cantPendientes++] = a;
  } else {
    Serial.println("[mediasombra] Demasiados acks pendientes; se pierde uno");
  }
}

// Para el motor y deja el ack del movimiento en curso.
static void terminar(EstadoAck status, const char* detalle) {
  if (!moviendo) {
    return;
  }
  detenerMotor();
  moviendo = false;

  if (status == EstadoAck::ERROR && strcmp(detalle, "falla_mecanica") == 0) {
    // Enrollar = abrir (deja pasar el sol): si no llegó, falló el final de "abierta"; si no desenrolló, el de "cerrada".
    if (!fallaInformada) {
      fallaPendiente = true;
      fallaInformada = true;
      finFallido = haciaEnrollada ? VALOR_FIN_ABIERTA : VALOR_FIN_CERRADA;
    }
  } else if (status == EstadoAck::SUCCESS && strcmp(detalle, "ok") == 0) {
    fallaInformada = false;   // se resolvió: la próxima falla es un evento nuevo
  }

  Ack a;
  ackBase(a, commandId, sectorComando);
  a.status = status;
  strlcpy(a.tipoDetalle, detalle, sizeof(a.tipoDetalle));
  if (status == EstadoAck::SUCCESS) {
    a.targetPct = haciaEnrollada ? 100 : 0;   // % de apertura alcanzado
  }
  encolarAck(a);
}

void iniciar() {
  pinMode(PIN_MEDIASOMBRA_IN1, OUTPUT);
  pinMode(PIN_MEDIASOMBRA_IN2, OUTPUT);
  pinMode(PIN_MEDIASOMBRA_FIN_ENROLLADA, INPUT_PULLUP);
  pinMode(PIN_MEDIASOMBRA_FIN_DESENROLLADA, INPUT_PULLUP);
  ledcAttach(PIN_MEDIASOMBRA_ENA, PWM_FREQ, PWM_BITS);
  detenerMotor();   // estado seguro
}

bool moviendoseAhora() {
  return moviendo;
}

bool yaEnPosicion(const Comando& cmd) {
  if (moviendo || cmd.accion != TipoAccion::POSICIONAR || cmd.targetPct < 0) {
    return false;
  }
  return finActivado(pinFin(cmd.targetPct >= MEDIASOMBRA_UMBRAL_APERTURA_PCT));
}

bool comenzar(const Comando& cmd, Ack& ack) {
  ackBase(ack, cmd.commandId, cmd.sector);
  ack.status = EstadoAck::ERROR;

  if (cmd.accion != TipoAccion::POSICIONAR || cmd.targetPct < 0) {
    strlcpy(ack.tipoDetalle, "parametros_invalidos", sizeof(ack.tipoDetalle));
    return true;
  }

  bool enrollar = cmd.targetPct >= MEDIASOMBRA_UMBRAL_APERTURA_PCT;
  Serial.printf("[mediasombra] Apertura %d%% → %s\n", cmd.targetPct, nombre(enrollar));

  // Un comando nuevo reemplaza al movimiento en curso (aunque vaya al mismo lado:
  // su ack es el que espera el backend ahora).
  terminar(EstadoAck::ERROR, "reemplazado");

  if (finActivado(pinFin(enrollar))) {
    ack.status = EstadoAck::SUCCESS;
    ack.targetPct = enrollar ? 100 : 0;
    strlcpy(ack.tipoDetalle, "sin_cambio", sizeof(ack.tipoDetalle));
    return true;
  }

  moviendo = true;
  haciaEnrollada = enrollar;
  inicioMs = millis();
  strlcpy(commandId, cmd.commandId, sizeof(commandId));
  sectorComando = cmd.sector;
  arrancarMotor(enrollar);
  return false;
}

bool atender(Ack& out) {
  if (moviendo) {
    if (finActivado(pinFin(haciaEnrollada))) {
      Serial.printf("[mediasombra] %s\n", nombre(haciaEnrollada));
      terminar(EstadoAck::SUCCESS, "ok");
    } else if (millis() - inicioMs >= (uint32_t) TIMEOUT_MEDIASOMBRA_MS) {
      Serial.printf("[mediasombra] TIMEOUT: no llegó al final de carrera de %s\n", nombre(haciaEnrollada));
      terminar(EstadoAck::ERROR, "falla_mecanica");
    }
  }

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

EstadoFinesDeCarrera estadoFines() {
  return { finActivado(pinFin(false)), finActivado(pinFin(true)) };
}

bool tomarFallaPendiente(char* valor, size_t n) {
  if (!fallaPendiente) {
    return false;
  }
  fallaPendiente = false;
  strlcpy(valor, finFallido, n);
  return true;
}

}  // namespace act_mediasombra
