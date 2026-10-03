// ============================================================
//  TEST CAUDALÍMETRO — ESP32-S3 (YF-S401)
//
//  Conexión:
//    Rojo   (VCC)   → 5V
//    Negro  (GND)   → GND
//    Amarillo (SIG) → GPIO 41  (ver nota de voltaje abajo)
//
//  Nota de voltaje: la señal del sensor puede llegar a 5V.
//  Usá un divisor (1k + 2k, igual que el ECHO del HC-SR04)
//  o alimentalo a 5V y poné una pull-up de 10k a 3.3V en la señal
//  (los sensores Hall de este tipo suelen ser colector abierto;
//  si no estás seguro, usá el divisor).
//
//  Comandos por monitor serial (115200):
//    reset       → pone en cero el volumen acumulado
//    factor N    → cambia pulsos por litro (default 450)
//    estado      → muestra caudal, pulsos y volumen
// ============================================================

#define FLOW_PIN 27

// YF-S401: 1 L = 5880 pulsos (≈ 0,17 mL por pulso)
// Rango de caudal: 0,3 a 6 L/min → 29 a 588 pulsos/s
// Calibrá con un recipiente medido y ajustá con "factor N"
float pulsos_por_litro = 5880.0;

volatile unsigned long contador_pulsos = 0;
unsigned long pulsos_totales = 0;
float volumen_litros = 0;
unsigned long ultimo_calculo = 0;

void IRAM_ATTR contarPulso() {
  contador_pulsos++;
}

void setup() {
  Serial.begin(115200);
  pinMode(FLOW_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(FLOW_PIN), contarPulso, FALLING);

  Serial.println("=== TEST CAUDALÍMETRO ===");
  Serial.println("Comandos: reset | factor N | estado");
  Serial.println("Hacé circular agua y mirá los valores.");
  ultimo_calculo = millis();
}

void loop() {
  // Calcula cada 1 segundo
  if (millis() - ultimo_calculo >= 1000) {
    noInterrupts();
    unsigned long pulsos = contador_pulsos;
    contador_pulsos = 0;
    interrupts();

    unsigned long dt = millis() - ultimo_calculo;
    ultimo_calculo = millis();

    pulsos_totales += pulsos;
    float litros_intervalo = pulsos / pulsos_por_litro;
    volumen_litros += litros_intervalo;
    float caudal_lmin = litros_intervalo * 60000.0 / dt;

    Serial.print("Pulsos/s: ");
    Serial.print(pulsos);
    Serial.print("  |  Caudal: ");
    Serial.print(caudal_lmin, 2);
    Serial.print(" L/min  |  Volumen: ");
    Serial.print(volumen_litros, 3);
    Serial.println(" L");
  }

  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();

    if (cmd == "reset") {
      noInterrupts();
      contador_pulsos = 0;
      interrupts();
      pulsos_totales = 0;
      volumen_litros = 0;
      Serial.println("Volumen reseteado.");

    } else if (cmd.startsWith("factor ")) {
      float f = cmd.substring(7).toFloat();
      if (f > 0) {
        pulsos_por_litro = f;
        Serial.print("Pulsos por litro = ");
        Serial.println(pulsos_por_litro);
      }

    } else if (cmd == "estado") {
      Serial.print("Pulsos totales: ");
      Serial.println(pulsos_totales);
      Serial.print("Volumen: ");
      Serial.print(volumen_litros, 3);
      Serial.println(" L");
      Serial.print("Pulsos/litro: ");
      Serial.println(pulsos_por_litro);

    } else {
      Serial.println("Comandos: reset | factor N | estado");
    }
  }
}
