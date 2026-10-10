# Prototipo de hardware (pruebas por monitor serie)

Sketches de Arduino IDE con los que se armó y probó el hardware del prototipo. Salvo
`vivero_esp32_red/`, **no tienen red**: no usan WiFi ni MQTT y no hablan con el backend. Se manejan con comandos por el monitor serie
(115200 baudios). El firmware que sí cumple el contrato MQTT es el modular de esta misma carpeta
(`nodo_sensor/`, `nodo_actuador/`, `nodo_combinado/`); ver `../README.md`.

Versión recibida el 03/10/2026.

| Sketch | Qué prueba |
|---|---|
| `vivero_esp32/` | Riel de la cámara (NEMA 17 + DRV8825, con homing y dos finales de carrera), mediasombra (motor DC por L298N canal A, con dos finales de carrera) y bomba de riego (L298N canal B, PWM) |
| `vivero_esp32_red/` | Basado en `vivero_esp32` (que no se toca), **para ESP32-S3 DevKitC-1 N16R8, con WiFi y MQTT para el riel**: recibe `IR_A`/`HOME` en `nursery/rail/command` y responde en `nursery/rail/event`. El riego pasa a un **módulo de 4 relés** (2 minibombas y 2 electroválvulas); riego y mediasombra son sólo por serie. Pines en su `config.h`. La versión para ESP32 clásico se probó con el riel real (03/10/2026); la del S3 compila pero falta probarla. Instalar, configurar, flashear y probar: [`vivero_esp32_red/README.md`](vivero_esp32_red/README.md) |
| `test_caudalimetro/` | Lectura del caudalímetro |
| `test_hcsr04/` | Sensor ultrasónico HC-SR04 |

## Pines de `vivero_esp32` (ESP32 clásico, DevKit v1)

Los de `vivero_esp32_red` (ESP32-S3) están en su README y en su `config.example.h`.

| Función | Pines |
|---|---|
| Riel: STEP / DIR / SLEEP | 19 / 21 / 15 |
| Riel: final de carrera home / fin | 34 / 35 (sólo entrada: pull-up externo) |
| Mediasombra: IN1 / IN2 / ENA (PWM) | 22 / 23 / 25 |
| Mediasombra: final de carrera enrollada / desenrollada | 32 / 33 |
| Bomba: IN3 / IN4 / ENB (PWM) | 26 / 27 / 14 |

Comandos por serie: `home`, `mover N`, `rutina`, `enrollar`, `desenrollar`, `parar motor`,
`bomba on`, `bomba off`, `estado`.

## Relación con el firmware modular

Desde el 10/10/2026 el firmware modular (`nodo_combinado`, `sensado/`, `actuacion/`) está adaptado a
este hardware: ESP32-S3, riego por relés, mediasombra de dos estados, riel por MQTT y los sensores
(LDR, DHT11, sonda RS-485, 2 HC-SR04). Los pines son los mismos que en `vivero_esp32_red`, y su
fuente de verdad es `comun/config.example.h`. Ver `../README.md`.

Lo que sigue aplicando a los sketches de esta carpeta:

- `vivero_esp32/`, `test_caudalimetro/` y `test_hcsr04/` son del ESP32 clásico y tienen sus propios
  pines. Con el S3 sirven como referencia, no para flashear tal cual.
- **Conflictos de pines entre sketches viejos:** `test_caudalimetro` usa el 27 (IN4 de la bomba de
  `vivero_esp32`) y `test_hcsr04` el 15 (SLEEP del riel). En el S3 el HC-SR04 va en TRIG 4 y ECHO 5/6.
