## 1. Backend (`Desarrollo/backend/`)
- [ ] 1.1 Implementar controlador HTTP para el nuevo endpoint `GET /api/capturas/pendientes-diagnostico`.
- [ ] 1.2 Añadir consulta en `CapturaRepository` (o `DiagnosticoRepository`) que filtre capturas cuya relación en `diagnostico` sea nula (`LEFT JOIN diagnostico d ON c.id = d.captura_id WHERE d.id IS NULL`).
- [ ] 1.3 Desarrollar test de integración para validar el nuevo endpoint y asegurar que excluya capturas ya analizadas.

## 2. Servicio de Inferencia (`Desarrollo/servicio-inferencia/`)
- [ ] 2.1 Configurar estructura base de proyecto Python (`requirements.txt`, linter).
- [ ] 2.2 Configurar inyección de variables de entorno (usando `python-dotenv` o similar) para parametrizar: URL del backend, directorio del volumen de imágenes, `MODEL_PATH` para la ruta del archivo `.pt`, y `POLLING_INTERVAL_SECONDS` (default: `14400`).
- [ ] 2.3 Escribir el módulo `model.py` encargado de inicializar `torch` y ejecutar la inferencia abstrayendo el tensor y la transformación de la imagen.
- [ ] 2.4 Escribir el módulo cliente `api.py` para la abstracción de las peticiones HTTP con la librería `requests` (Polling GET, Submit POST).
- [ ] 2.5 Programar el orquestador principal `main.py` con un bucle periódico iterativo.

## 3. Integración, Documentación y Despliegue
- [ ] 3.1 Agregar `README.md` exhaustivo en `Desarrollo/servicio-inferencia/` enfocado en los prerrequisitos de hardware para inferencia.
- [ ] 3.2 Modificar `docker-compose.yml` para desplegar el nuevo contenedor y montar el volumen de captura.
- [ ] 3.3 Actualizar `CLAUDE.md` §1 (Mapa del Repositorio) y la sección de backend para trazar la existencia y operación del servicio de inferencia.
