// ============================================================
//  TEST HC-SR04 — Sensor ultrasónico de distancia
//  
//  Conexión:
//    TRIG → GPIO 15
//    ECHO → GPIO 13 (con divisor de voltaje si es HC-SR04 5V)
//    VCC  → 5V
//    GND  → GND
//
//  Si es HC-SR04P (3.3V) → ECHO va directo sin divisor
// ============================================================

#define TRIG_PIN  15
#define ECHO_PIN  13

// Velocidad del sonido en cm/µs a 20°C
#define VELOCIDAD_SONIDO 0.0343

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);

  Serial.println("=== TEST HC-SR04 ===");
  Serial.println("Midiendo distancia cada 500ms...");
  Serial.println("Comandos:");
  Serial.println("  leer   → lectura única");
  Serial.println("  auto   → lecturas automáticas cada 500ms");
  Serial.println("  parar  → detiene lecturas automáticas");
  Serial.println();
}

bool modo_auto = true;
unsigned long ultima_lectura = 0;

void loop() {
  // Modo automático
  if (modo_auto && millis() - ultima_lectura >= 500) {
    float distancia = medir_distancia();
    imprimir_distancia(distancia);
    ultima_lectura = millis();
  }

  // Comandos por serial
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();

    if (cmd == "leer") {
      modo_auto = false;
      float distancia = medir_distancia();
      imprimir_distancia(distancia);

    } else if (cmd == "auto") {
      modo_auto = true;
      Serial.println("Modo automático activado.");

    } else if (cmd == "parar") {
      modo_auto = false;
      Serial.println("Lecturas detenidas.");

    } else {
      Serial.println("Comandos: leer | auto | parar");
    }
  }
}

// ── Función principal de medición ────────────────────────────

float medir_distancia() {
  // 1. Asegurarse de que TRIG está en LOW
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);

  // 2. Enviar pulso de 10µs en TRIG
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  // 3. Medir cuánto tiempo tarda en volver el eco
  // Timeout: 30.000µs = máximo ~5 metros
  long duracion = pulseIn(ECHO_PIN, HIGH, 30000);

  // 4. Si no hubo eco → objeto fuera de rango
  if (duracion == 0) {
    return -1;
  }

  // 5. Calcular distancia
  // Distancia = (tiempo × velocidad sonido) / 2
  // Dividimos por 2 porque el sonido va y vuelve
  float distancia = (duracion * VELOCIDAD_SONIDO) / 2.0;

  return distancia;
}

// ── Imprimir resultado ────────────────────────────────────────

void imprimir_distancia(float distancia) {
  if (distancia < 0) {
    Serial.println("Sin objeto detectado (fuera de rango)");
    return;
  }

  if (distancia < 2) {
    Serial.println("Objeto demasiado cerca (< 2cm)");
    return;
  }

  if (distancia > 400) {
    Serial.println("Objeto demasiado lejos (> 400cm)");
    return;
  }

  Serial.print("Distancia: ");
  Serial.print(distancia, 1);  // 1 decimal
  Serial.print(" cm  |  ");
  Serial.print(distancia / 100.0, 3);  // en metros
  Serial.println(" m");
}
