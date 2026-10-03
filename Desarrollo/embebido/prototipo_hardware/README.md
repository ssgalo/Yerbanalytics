# Prototipo de hardware (pruebas por monitor serie)

Sketches de Arduino IDE con los que se armó y probó el hardware del prototipo. Salvo
`vivero_esp32_red/`, **no tienen red**: no usan WiFi ni MQTT y no hablan con el backend. Se manejan con comandos por el monitor serie
(115200 baudios). El firmware que sí cumple el contrato MQTT es el modular de esta misma carpeta
(`nodo_sensor/`, `nodo_actuador/`, `nodo_combinado/`); ver `../README.md`.

Versión recibida el 03/10/2026.

| Sketch | Qué prueba |
|---|---|
| `vivero_esp32/` | Riel de la cámara (NEMA 17 + DRV8825, con homing y dos finales de carrera), mediasombra (motor DC por L298N canal A, con dos finales de carrera) y bomba de riego (L298N canal B, PWM) |
| `vivero_esp32_red/` | Lo mismo que `vivero_esp32` (que no se toca) **más WiFi y MQTT para el riel**: recibe `IR_A`/`HOME` en `nursery/rail/command` y responde en `nursery/rail/event`. Bomba y mediasombra siguen sólo por serie. Compilado, flasheado y probado con el riel real (03/10/2026). Instalar, configurar, flashear y probar: [`vivero_esp32_red/README.md`](vivero_esp32_red/README.md) |
| `test_caudalimetro/` | Lectura del caudalímetro |
| `test_hcsr04/` | Sensor ultrasónico HC-SR04 |

## Pines de `vivero_esp32`

| Función | Pines |
|---|---|
| Riel: STEP / DIR / SLEEP | 19 / 21 / 15 |
| Riel: final de carrera home / fin | 34 / 35 (sólo entrada: pull-up externo) |
| Mediasombra: IN1 / IN2 / ENA (PWM) | 22 / 23 / 25 |
| Mediasombra: final de carrera enrollada / desenrollada | 32 / 33 |
| Bomba: IN3 / IN4 / ENB (PWM) | 26 / 27 / 14 |

Comandos por serie: `home`, `mover N`, `rutina`, `enrollar`, `desenrollar`, `parar motor`,
`bomba on`, `bomba off`, `estado`.

## Diferencias con el firmware modular

El hardware real no coincide con lo que supone el firmware modular; hay que resolverlas para
integrar el prototipo con el backend:

- **Riego:** acá es una bomba DC por L298N con PWM, no una electroválvula.
- **Mediasombra:** dos estados con finales de carrera (enrollada / desenrollada), no un
  porcentaje de apertura.
- **Riel de la cámara:** el firmware modular no lo contempla. Su contrato MQTT (`nursery/rail/*`,
  sección "Riel" de `comun/contrato.h`) lo implementa sólo `vivero_esp32_red`.
- **Sensores del nodo testigo** (humedad, temperatura, luz, sonda de suelo): no están en este
  sketch.
- **Conflictos de pines entre sketches:** `test_caudalimetro` usa el 27 (IN4 de la bomba) y
  `test_hcsr04` el 15 (SLEEP del riel).
