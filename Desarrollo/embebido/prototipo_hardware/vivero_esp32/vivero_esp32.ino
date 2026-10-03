// ============================================================
//  VIVERO ESP32 — Código base
//  Componentes:
//    - NEMA 17 + DRV8825 (riel cámara)
//    - JGA25-370 + L298N canal A (mediasombra)
//    - Bomba DC + L298N canal B (riego)
//    - 2x Endstop (mediasombra)
//    - 2x Endstop (riel NEMA 17: home y fin de carrera)
// ============================================================

// ── NEMA 17 / DRV8825 ────────────────────────────────────────
#define STEP_PIN     19
#define DIR_PIN      21
#define SLEEP_PIN  15
// Nivel de DIR_PIN que mueve el carro hacia HOME. Depende de cómo estén
// cableadas las bobinas al driver: si el motor va al revés, cambiar LOW↔HIGH.
#define DIR_HACIA_HOME HIGH
#define DIR_HACIA_FIN  (!DIR_HACIA_HOME)
// GPIO34 y GPIO35 son "solo entrada": no tienen pull-up/pull-down interno.
// Hace falta una resistencia pull-up externa (~10k a 3.3V) en cada pin,
// con el final de carrera entre el pin y GND.
#define ENDSTOP_RIEL_HOME 34   // final de carrera extremo home (retroceso)
#define ENDSTOP_RIEL_FIN  35   // final de carrera extremo opuesto (avance máximo)

// Tipo de contacto de los finales de carrera del riel:
// false = NO (normalmente abierto) → activado = LOW
// true  = NC (normalmente cerrado) → activado = HIGH
#define ENDSTOP_RIEL_NC true

#if ENDSTOP_RIEL_NC
  #define RIEL_ACTIVADO(pin) (digitalRead(pin) == HIGH)
#else
  #define RIEL_ACTIVADO(pin) (digitalRead(pin) == LOW)
#endif

#define POSICION_SECTOR_1 21000
#define POSICION_SECTOR_2 42000
// Máximo de pasos que puede dar el homing sin encontrar el endstop.
// Tiene que ser mayor que el largo total del riel en pasos (con margen).
#define MAX_PASOS_HOMING 80000

// ── L298N Canal A — Motor DC mediasombra ─────────────────────
#define MOTOR_IN1    22
#define MOTOR_IN2    23
#define MOTOR_ENA    25  // PWM velocidad

// ── L298N Canal B — Bomba ────────────────────────────────────
#define BOMBA_IN3    26
#define BOMBA_IN4    27
#define BOMBA_ENB    14  // PWM caudal

// ── Endstops mediasombra ─────────────────────────────────────
#define ENDSTOP_ENROLLADA    32
#define ENDSTOP_DESENROLLADA 33

// Tipo de contacto de los finales de carrera de la mediasombra (con INPUT_PULLUP,
// contacto entre el pin y GND):
// false = NO (normalmente abierto) → activado = LOW
// true  = NC (normalmente cerrado) → activado = HIGH
#define ENDSTOP_MEDIASOMBRA_NC true

#if ENDSTOP_MEDIASOMBRA_NC
  #define MEDIASOMBRA_ACTIVADO(pin) (digitalRead(pin) == HIGH)
#else
  #define MEDIASOMBRA_ACTIVADO(pin) (digitalRead(pin) == LOW)
#endif

// ── Configuración LEDC (PWM en ESP32) ────────────────────────
// Compatible con ESP32 Arduino 3.x (nueva API)
#define PWM_FREQ     1000
#define PWM_BITS     8    // resolución 8 bits → valores 0-255

// ── Variables NEMA 17 ─────────────────────────────────────────
long pasos_actuales = 0;  // posición actual en pasos desde home

// ── Velocidad NEMA (microsegundos entre pasos) ────────────────
// Más alto = más lento y más torque
#define VELOCIDAD_NEMA 500

// =============================================================
//  SETUP
// =============================================================
void setup() {

  // NEMA 17
  pinMode(STEP_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(ENDSTOP_RIEL_HOME, INPUT);
  pinMode(ENDSTOP_RIEL_FIN,  INPUT);
  pinMode(SLEEP_PIN, OUTPUT);
  digitalWrite(SLEEP_PIN, LOW);

  // Motor DC mediasombra
  pinMode(MOTOR_IN1, OUTPUT);
  pinMode(MOTOR_IN2, OUTPUT);

  // Bomba
  pinMode(BOMBA_IN3, OUTPUT);
  pinMode(BOMBA_IN4, OUTPUT);

  // Endstops mediasombra (INPUT_PULLUP; lógica según ENDSTOP_MEDIASOMBRA_NC)
  pinMode(ENDSTOP_ENROLLADA,    INPUT_PULLUP);
  pinMode(ENDSTOP_DESENROLLADA, INPUT_PULLUP);

  // PWM con LEDC del ESP32 — API nueva (Arduino 3.x)
  // ledcAttach(pin, frecuencia, bits_resolución)
  ledcAttach(MOTOR_ENA, PWM_FREQ, PWM_BITS);
  ledcAttach(BOMBA_ENB, PWM_FREQ, PWM_BITS);

  // Asegurarse de que todo empieza apagado
  motorDC_parar();
  bomba_apagar();

  Serial.begin(115200);
  Serial.println("Iniciando sistema vivero...");

  // Homing del NEMA 17 al arrancar
  Serial.println("Buscando posición home...");
  nema_homing();
  Serial.println("Sistema listo.");
}

// =============================================================
//  LOOP — Monitor serial para pruebas
// =============================================================
void loop() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();

    // ── NEMA 17 ──────────────────────────────────────────────
    if (cmd == "home") {
      nema_homing();

    } else if (cmd == "rutina") {
      nema_rutina_captura();

    } else if (cmd.startsWith("mover ")) {
      long pasos = cmd.substring(6).toInt();
      nema_mover(pasos);
      Serial.print("Posición actual: ");
      Serial.println(pasos_actuales);

    // ── Mediasombra ───────────────────────────────────────────
    } else if (cmd == "enrollar") {
      mediasombra_enrollar();

    } else if (cmd == "desenrollar") {
      mediasombra_desenrollar();

    } else if (cmd == "parar motor") {
      motorDC_parar();

    // ── Bomba ─────────────────────────────────────────────────
    } else if (cmd == "bomba on") {
      bomba_encender(200); // 200/255 = ~78% caudal

    } else if (cmd == "bomba off") {
      bomba_apagar();

    // ── Estado sensores ───────────────────────────────────────
    } else if (cmd == "estado") {
      Serial.print("Endstop enrollada:    ");
      Serial.println(MEDIASOMBRA_ACTIVADO(ENDSTOP_ENROLLADA) ? "ACTIVADO" : "libre");
      Serial.print("Endstop desenrollada: ");
      Serial.println(MEDIASOMBRA_ACTIVADO(ENDSTOP_DESENROLLADA) ? "ACTIVADO" : "libre");
      Serial.print("Endstop riel home:    ");
      Serial.println(RIEL_ACTIVADO(ENDSTOP_RIEL_HOME) ? "ACTIVADO" : "libre");
      Serial.print("Endstop riel fin:     ");
      Serial.println(RIEL_ACTIVADO(ENDSTOP_RIEL_FIN) ? "ACTIVADO" : "libre");
      Serial.print("Pasos actuales:       ");
      Serial.println(pasos_actuales);

    } else {
      Serial.println("Comandos disponibles:");
      Serial.println("  home          → busca posición home NEMA");
      Serial.println("  mover N       → mueve N pasos (negativo = reversa)");
      Serial.println("  rutina        → home → 7000 → espera 3s → 14000");
      Serial.println("  enrollar      → enrolla mediasombra");
      Serial.println("  desenrollar   → desenrolla mediasombra");
      Serial.println("  parar motor   → para motor DC");
      Serial.println("  bomba on      → enciende bomba");
      Serial.println("  bomba off     → apaga bomba");
      Serial.println("  estado        → lee todos los sensores");
    }
  }
}

// =============================================================
//  FUNCIONES NEMA 17
// =============================================================

// Antes de mover → despertar
void nema_despertar() {
  digitalWrite(SLEEP_PIN, HIGH);
  delay(10);  // espera que estabilice
}

// Después de mover → dormir
void nema_dormir() {
  digitalWrite(SLEEP_PIN, LOW);
}

// Mueve N pasos. Positivo = adelante, negativo = atrás
void nema_mover(long pasos) {
  if (pasos == 0) return;

  nema_despertar();

  bool direccion = (pasos > 0); // true = avance (hacia FIN), false = retroceso (hacia HOME)
  digitalWrite(DIR_PIN, direccion ? DIR_HACIA_FIN : DIR_HACIA_HOME);
  delay(1); // pequeña pausa para que el driver procese la dirección

  long abs_pasos = abs(pasos);
  for (long i = 0; i < abs_pasos; i++) {
    // Verifica finales de carrera en cada paso (seguridad)
    if (!direccion && RIEL_ACTIVADO(ENDSTOP_RIEL_HOME)) {
      Serial.println("Final de carrera home alcanzado, deteniendo.");
      pasos_actuales = 0;
      nema_dormir();
      return;
    }
    if (direccion && RIEL_ACTIVADO(ENDSTOP_RIEL_FIN)) {
      pasos_actuales += i;  // solo los pasos que llegó a dar
      Serial.print("Final de carrera opuesto alcanzado, deteniendo tras ");
      Serial.print(i);
      Serial.print(" pasos. Posición: ");
      Serial.println(pasos_actuales);
      nema_dormir();
      return;
    }
    digitalWrite(STEP_PIN, HIGH);
    delayMicroseconds(VELOCIDAD_NEMA);
    digitalWrite(STEP_PIN, LOW);
    delayMicroseconds(VELOCIDAD_NEMA);
  }
  nema_dormir();  // ← apaga el driver al terminar
  pasos_actuales += pasos;
}

// Busca el final de carrera home y resetea el contador.
// Devuelve true si encontró home.
bool nema_homing() {
  // Si ya está en home, listo
  if (RIEL_ACTIVADO(ENDSTOP_RIEL_HOME)) {
    pasos_actuales = 0;
    Serial.println("Ya está en home.");
    return true;
  }

  // Mueve hacia atrás (retroceso) hasta encontrar el final de carrera home
  nema_despertar();
  digitalWrite(DIR_PIN, DIR_HACIA_HOME);
  delay(1);

  long timeout = MAX_PASOS_HOMING; // límite de seguridad antes de abortar
  long i = 0;
  // Si arranca apoyado en el endstop fin, es normal que siga activado
  // los primeros pasos mientras se aleja: solo cuenta si se libera y vuelve a activarse.
  bool fin_liberado = !RIEL_ACTIVADO(ENDSTOP_RIEL_FIN);
  while (!RIEL_ACTIVADO(ENDSTOP_RIEL_HOME) && i < timeout) {
    bool fin_activo = RIEL_ACTIVADO(ENDSTOP_RIEL_FIN);
    if (!fin_activo) {
      fin_liberado = true;
    } else if (fin_liberado) {
      // Llegó al extremo opuesto buscando home: dirección o cableado invertidos
      Serial.println("ERROR: Se activó el final de carrera opuesto durante el homing.");
      nema_dormir();
      return false;
    }
    digitalWrite(STEP_PIN, HIGH);
    delayMicroseconds(VELOCIDAD_NEMA);
    digitalWrite(STEP_PIN, LOW);
    delayMicroseconds(VELOCIDAD_NEMA);
    i++;
  }

  nema_dormir();

  if (i >= timeout) {
    Serial.println("ERROR: Home no encontrado. Revisá el final de carrera.");
    return false;
  }

  pasos_actuales = 0;
  Serial.println("Home encontrado. Posición reseteada a 0.");
  return true;
}

// Mueve a una posición absoluta (en pasos desde home)
void nema_ir_a(long posicion_objetivo) {
  long pasos = posicion_objetivo - pasos_actuales;
  nema_mover(pasos);
}

// Rutina: home → posición 7000 → espera 3s → posición 14000
void nema_rutina_captura() {
  Serial.println("Rutina: buscando home...");
  if (!nema_homing()) {
    Serial.println("Rutina abortada: no se pudo hacer home.");
    return;
  }

  Serial.println("Rutina: moviendo a sector 1...");
  nema_ir_a(POSICION_SECTOR_1);
  Serial.print("Posición actual: ");
  Serial.println(pasos_actuales);

  Serial.println("Rutina: esperando 3s...");
  delay(3000);

  Serial.println("Rutina: moviendo a sector 2...");
  nema_ir_a(POSICION_SECTOR_2);
  Serial.print("Posición actual: ");
  Serial.println(pasos_actuales);
  delay(3000);

  Serial.println("Rutina finalizada.");
}

// =============================================================
//  FUNCIONES MOTOR DC (mediasombra)
// =============================================================

void motorDC_parar() {
  digitalWrite(MOTOR_IN1, LOW);
  digitalWrite(MOTOR_IN2, LOW);
  ledcWrite(MOTOR_ENA, 0);
}

void motorDC_adelante(int velocidad) {
  // velocidad: 0-255
  digitalWrite(MOTOR_IN1, HIGH);
  digitalWrite(MOTOR_IN2, LOW);
  ledcWrite(MOTOR_ENA, velocidad);
}

void motorDC_atras(int velocidad) {
  digitalWrite(MOTOR_IN1, LOW);
  digitalWrite(MOTOR_IN2, HIGH);
  ledcWrite(MOTOR_ENA, velocidad);
}

void mediasombra_enrollar() {
  if (MEDIASOMBRA_ACTIVADO(ENDSTOP_ENROLLADA)) {
    Serial.println("Mediasombra ya enrollada.");
    return;
  }
  Serial.println("Enrollando mediasombra...");
  motorDC_atras(200); // 78% velocidad

  // Espera hasta que el endstop se active o timeout
  unsigned long inicio = millis();
  while (!MEDIASOMBRA_ACTIVADO(ENDSTOP_ENROLLADA)) {
    if (millis() - inicio > 30000) { // 30 segundos máximo
      Serial.println("TIMEOUT: Revisá el endstop de enrollado.");
      motorDC_parar();
      return;
    }
    delay(10);
  }
  motorDC_parar();
  Serial.println("Mediasombra enrollada.");
}

void mediasombra_desenrollar() {
  if (MEDIASOMBRA_ACTIVADO(ENDSTOP_DESENROLLADA)) {
    Serial.println("Mediasombra ya desenrollada.");
    return;
  }
  Serial.println("Desenrollando mediasombra...");
  motorDC_adelante(200); // 78% velocidad

  unsigned long inicio = millis();
  while (!MEDIASOMBRA_ACTIVADO(ENDSTOP_DESENROLLADA)) {
    if (millis() - inicio > 30000) {
      Serial.println("TIMEOUT: Revisá el endstop de desenrollado.");
      motorDC_parar();
      return;
    }
    delay(10);
  }
  motorDC_parar();
  Serial.println("Mediasombra desenrollada.");
}

// =============================================================
//  FUNCIONES BOMBA
// =============================================================

void bomba_encender(int caudal) {
  // caudal: 0-255
  digitalWrite(BOMBA_IN3, HIGH);
  digitalWrite(BOMBA_IN4, LOW);
  ledcWrite(BOMBA_ENB, caudal);
  Serial.print("Bomba encendida al ");
  Serial.print(map(caudal, 0, 255, 0, 100));
  Serial.println("%");
}

void bomba_apagar() {
  digitalWrite(BOMBA_IN3, LOW);
  digitalWrite(BOMBA_IN4, LOW);
  ledcWrite(BOMBA_ENB, 0);
  Serial.println("Bomba apagada.");
}
