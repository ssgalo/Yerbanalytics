# Prototipo de hardware (diagnósticos de banco)

Dos sketches de Arduino IDE para probar **un solo sensor** por monitor serie (115200 baudios). No tienen
red: no usan WiFi ni MQTT ni hablan con el backend. El firmware real es el modular de `../`
(`nodo_sensor/`, `nodo_actuador/`, `nodo_combinado/`); ver `../README.md`.

Los sketches de banco anteriores (`vivero_esp32`, `vivero_esp32_red` y `sensores_unificados`) se retiraron
el 23/10/2026 en favor del firmware modular; el historial de git los conserva.

| Sketch | Qué prueba |
|---|---|
| `test_caudalimetro/` | Lectura del caudalímetro (ESP32 clásico; usa el GPIO 27) |
| `test_hcsr04/` | Sensor ultrasónico HC-SR04 (ESP32 clásico; usa el GPIO 15) |

Son del ESP32 clásico y tienen sus propios pines: con el S3 sirven de referencia, no para flashear tal
cual. En el S3 el HC-SR04 va en TRIG 4 y ECHO 5/6 (fuente de verdad: `../comun/config.example.h`).
