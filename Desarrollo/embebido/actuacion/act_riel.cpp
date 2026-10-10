#include "act_riel.h"
#include "config.h"
#include "contrato.h"

namespace act_riel {

static const long RIEL_PASOS_ENTRE_CHEQUEOS = 200;   // ~200 ms entre chequeos de la cola
static const int  CANT_RECIENTES = 4;

// Resultado de un movimiento.
enum class Mov { OK, FIN_HOME, FIN_OPUESTO, INTERRUMPIDO, SIN_HOME };

static SistemaColas* sys = nullptr;

static long pasosActuales = 0;      // posición en pasos desde home
static bool referenciado = false;   // true si el último homing salió bien

static char actualId[40] = "";      // comando en ejecución ("" si está quieto)
static char ultimoId[40] = "";      // último comando terminado
static EventoRiel ultimoEvento;     // y su evento final (para republicarlo)
static char recientes[CANT_RECIENTES][40];
static int  recientesProximo = 0;

// Final de carrera con INPUT_PULLUP: NC → activado = HIGH; NO → activado = LOW.
static bool finActivado(int pin) {
  return digitalRead(pin) == (FINES_CARRERA_NC ? HIGH : LOW);
}

static void despertar() {
  digitalWrite(PIN_RIEL_SLEEP, HIGH);
  vTaskDelay(pdMS_TO_TICKS(10));   // espera que el driver estabilice
}

static void dormir() {
  digitalWrite(PIN_RIEL_SLEEP, LOW);   // sin ruido ni consumo
}

static void paso() {
  digitalWrite(PIN_RIEL_STEP, HIGH);
  delayMicroseconds(RIEL_MEDIO_PERIODO_US);
  digitalWrite(PIN_RIEL_STEP, LOW);
  delayMicroseconds(RIEL_MEDIO_PERIODO_US);
}

static bool esReciente(const char* id) {
  if (id[0] == '\0') return false;
  for (int k = 0; k < CANT_RECIENTES; k++) {
    if (strcmp(recientes[k], id) == 0) return true;
  }
  return false;
}

static void recordar(const char* id) {
  if (id[0] == '\0') return;
  strlcpy(recientes[recientesProximo], id, sizeof(recientes[0]));
  recientesProximo = (recientesProximo + 1) % CANT_RECIENTES;
}

// ¿Hay que abortar el movimiento en curso? Sólo un comando VÁLIDO con un
// commandId nuevo interrumpe (el último gana). No lo consume: lo ejecuta la
// tarea al terminar el movimiento. El repetido del que está en curso se descarta.
static bool hayReemplazo() {
  ComandoRiel c;
  if (xQueuePeek(sys->comandoRiel, &c, 0) != pdTRUE) {
    return false;
  }
  if (c.commandId[0] != '\0' && strcmp(c.commandId, actualId) == 0) {
    xQueueReceive(sys->comandoRiel, &c, 0);
    Serial.printf("[riel] %s repetido (en ejecución): lo ignoro\n", c.commandId);
    return false;
  }
  if (!c.valido) return false;   // inválido: no detiene el riel, se responde al terminar
  if (strcmp(c.commandId, ultimoId) == 0 || esReciente(c.commandId)) return false;

  Serial.printf("[riel] Llegó %s durante el movimiento: aborto el actual (el último gana)\n", c.commandId);
  return true;
}

// Cada RIEL_PASOS_ENTRE_CHEQUEOS pasos cede el núcleo un tick y mira la cola.
static bool chequeo(long i) {
  if (i > 0 && (i % RIEL_PASOS_ENTRE_CHEQUEOS) == 0) {
    vTaskDelay(1);
    return hayReemplazo();
  }
  return false;
}

// Mueve N pasos. Positivo = hacia el final opuesto, negativo = hacia home.
static Mov mover(long pasos) {
  if (pasos == 0) return Mov::OK;

  despertar();
  bool avance = (pasos > 0);
  digitalWrite(PIN_RIEL_DIR, avance ? !RIEL_DIR_HACIA_HOME : RIEL_DIR_HACIA_HOME);
  delay(1);   // el driver procesa la dirección

  long total = labs(pasos);
  for (long i = 0; i < total; i++) {
    if (!avance && finActivado(PIN_RIEL_FIN_HOME)) {
      Serial.println("[riel] Final de carrera home alcanzado, deteniendo");
      pasosActuales = 0;
      dormir();
      return Mov::FIN_HOME;
    }
    if (avance && finActivado(PIN_RIEL_FIN_OPUESTO)) {
      pasosActuales += i;   // sólo los pasos que llegó a dar
      Serial.printf("[riel] Final de carrera opuesto alcanzado tras %ld pasos. Posición: %ld\n", i, pasosActuales);
      dormir();
      return Mov::FIN_OPUESTO;
    }
    if (chequeo(i)) {
      pasosActuales += avance ? i : -i;
      Serial.printf("[riel] Movimiento interrumpido. Posición: %ld\n", pasosActuales);
      dormir();
      return Mov::INTERRUMPIDO;
    }
    paso();
  }
  dormir();
  pasosActuales += pasos;
  return Mov::OK;
}

// Busca el final de carrera home y resetea el contador. Actualiza "referenciado".
static Mov homing() {
  if (finActivado(PIN_RIEL_FIN_HOME)) {
    pasosActuales = 0;
    referenciado = true;
    Serial.println("[riel] Ya está en home");
    return Mov::OK;
  }

  referenciado = false;   // mientras busca home la posición no es confiable
  despertar();
  digitalWrite(PIN_RIEL_DIR, RIEL_DIR_HACIA_HOME);
  delay(1);

  long i = 0;
  // Si arranca apoyado en el final opuesto, es normal que siga activado los primeros
  // pasos mientras se aleja: sólo cuenta si se libera y vuelve a activarse.
  bool finLiberado = !finActivado(PIN_RIEL_FIN_OPUESTO);
  while (!finActivado(PIN_RIEL_FIN_HOME) && i < RIEL_MAX_PASOS_HOMING) {
    bool finActivo = finActivado(PIN_RIEL_FIN_OPUESTO);
    if (!finActivo) {
      finLiberado = true;
    } else if (finLiberado) {
      Serial.println("[riel] ERROR: se activó el final de carrera opuesto durante el homing");
      dormir();
      return Mov::SIN_HOME;
    }
    if (chequeo(i)) {
      Serial.println("[riel] Homing interrumpido por otro comando");
      dormir();
      return Mov::INTERRUMPIDO;
    }
    paso();
    i++;
  }
  dormir();

  if (i >= RIEL_MAX_PASOS_HOMING) {
    Serial.println("[riel] ERROR: home no encontrado. Revisá el final de carrera");
    return Mov::SIN_HOME;
  }
  pasosActuales = 0;
  referenciado = true;
  Serial.println("[riel] Home encontrado. Posición reseteada a 0");
  return Mov::OK;
}

// Posición lógica para el evento: 0 home, 1 y 2 los sectores, -1 = null.
static int posicionLogica() {
  if (!referenciado) return -1;
  if (pasosActuales == 0) return 0;
  if (pasosActuales == RIEL_POSICION_SECTOR_1) return 1;
  if (pasosActuales == RIEL_POSICION_SECTOR_2) return 2;
  return -1;
}

static void armarEvento(EventoRiel& e, const char* id, const char* status, int posicion,
                        const char* codigo, const char* detalle) {
  e = {};
  strlcpy(e.commandId, id, sizeof(e.commandId));
  strlcpy(e.status, status, sizeof(e.status));
  e.posicion = posicion;
  e.pasos = pasosActuales;
  if (codigo != nullptr) strlcpy(e.codigo, codigo, sizeof(e.codigo));
  if (detalle != nullptr) strlcpy(e.detalle, detalle, sizeof(e.detalle));
}

static void publicar(const EventoRiel& e) {
  if (xQueueSend(sys->eventoRiel, &e, 0) != pdTRUE) {
    Serial.println("[riel] Cola de eventos llena; se pierde el evento");
  }
}

// Ejecuta un comando válido (bloqueante cooperativo).
static Mov ejecutar(const ComandoRiel& c) {
  if (c.accion == AccionRiel::HOME) {
    Serial.println("[riel] HOME: buscando home...");
    return homing();
  }

  if (!referenciado) {
    Serial.println("[riel] Sin referencia: hago homing antes de ir a la posición");
    Mov rh = homing();
    if (rh != Mov::OK) return rh;
  }

  long objetivo = (c.posicion == 1) ? RIEL_POSICION_SECTOR_1 : RIEL_POSICION_SECTOR_2;
  Serial.printf("[riel] IR_A %d: de %ld a %ld pasos\n", c.posicion, pasosActuales, objetivo);
  Mov r = mover(objetivo - pasosActuales);
  if (r == Mov::FIN_OPUESTO) {
    referenciado = false;   // tocó el final antes de lo esperado: la cuenta no es confiable
  }
  if (r == Mov::OK) {
    Serial.printf("[riel] Llegó a la posición %d\n", c.posicion);
  }
  return r;
}

// Idempotencia (contrato.h):
//   1. mismo commandId que el en ejecución → se ignora (lo resuelve hayReemplazo)
//   2. mismo commandId que el último terminado → se republica su evento final
//   3. commandId entre los últimos 4 → se ignora
//   4. otro commandId durante un movimiento → el viejo termina en ERROR REEMPLAZADO
static void procesar(const ComandoRiel& c) {
  EventoRiel e;

  if (c.commandId[0] != '\0' && strcmp(c.commandId, ultimoId) == 0) {
    Serial.printf("[riel] %s repetido (último terminado): republico su evento final\n", c.commandId);
    publicar(ultimoEvento);
    return;
  }
  if (esReciente(c.commandId)) {
    Serial.printf("[riel] %s ya procesado antes: lo ignoro\n", c.commandId);
    return;
  }
  if (!c.valido) {
    Serial.printf("[riel] Comando inválido: %s\n", c.detalle);
    armarEvento(e, c.commandId, STATUS_RIEL_ERROR, posicionLogica(), CODIGO_RIEL_COMANDO_INVALIDO, c.detalle);
    publicar(e);
    recordar(c.commandId);
    return;
  }

  recordar(c.commandId);
  strlcpy(actualId, c.commandId, sizeof(actualId));
  armarEvento(e, c.commandId, STATUS_RIEL_ACEPTADO, -1, nullptr, nullptr);
  publicar(e);

  Mov r = ejecutar(c);

  const char* status = STATUS_RIEL_ERROR;
  const char* codigo = nullptr;
  const char* detalle = nullptr;
  switch (r) {
    case Mov::OK:
      status = STATUS_RIEL_LLEGO;
      break;
    case Mov::SIN_HOME:
      codigo = CODIGO_RIEL_HOME_NO_ENCONTRADO;
      detalle = "No se encontro el final de carrera de home (timeout o final opuesto)";
      break;
    case Mov::FIN_OPUESTO:
      codigo = CODIGO_RIEL_FIN_DE_CARRERA;
      detalle = "Se activo el final de carrera opuesto antes de llegar";
      break;
    case Mov::FIN_HOME:
      codigo = CODIGO_RIEL_FIN_DE_CARRERA;
      detalle = "Se activo el final de carrera de home antes de llegar";
      break;
    case Mov::INTERRUMPIDO:
      codigo = CODIGO_RIEL_REEMPLAZADO;
      detalle = "Llego otro comando durante el movimiento";
      break;
  }

  armarEvento(ultimoEvento, c.commandId, status, posicionLogica(), codigo, detalle);
  strlcpy(ultimoId, c.commandId, sizeof(ultimoId));
  actualId[0] = '\0';
  publicar(ultimoEvento);
}

void iniciar() {
  pinMode(PIN_RIEL_STEP, OUTPUT);
  pinMode(PIN_RIEL_DIR, OUTPUT);
  pinMode(PIN_RIEL_SLEEP, OUTPUT);
  dormir();   // driver dormido hasta el primer movimiento
  pinMode(PIN_RIEL_FIN_HOME, INPUT_PULLUP);
  pinMode(PIN_RIEL_FIN_OPUESTO, INPUT_PULLUP);
}

void tarea(void* pvParameters) {
  sys = (SistemaColas*) pvParameters;

  Serial.println("[riel] Buscando home...");
  homing();

  ComandoRiel c;
  for (;;) {
    if (xQueueReceive(sys->comandoRiel, &c, portMAX_DELAY) == pdTRUE) {
      procesar(c);
    }
  }
}

}  // namespace act_riel
