## 1. Servicio de inferencia — parámetros y modelo

- [x] 1.1 `src/settings.py`: leer y validar `YOLO_CONF_GLOBAL`, `SOLAPAMIENTO_IOU` y `BANDAS_CONFIANZA_<CLASE>` (defaults de D4, `WARNING` por variable ausente, `sys.exit(1)` ante valores mal formados o `YOLO_CONF_GLOBAL` > algún umbral de validez)
- [x] 1.2 Resolver `MODEL_PATH` relativa contra la carpeta del servicio (padre de `src/`)
- [x] 1.3 `model.py`: `detect(image_path)` corre YOLO con `conf=YOLO_CONF_GLOBAL` y devuelve detecciones (`label` de la plataforma, `score`, `box` `xyxyn`); quitar la lógica de "caja de mayor score" y `_map_severidad`
- [x] 1.4 Al arrancar, exigir `len(MODEL_CLASSES) == len(model.names)` y loguear el mapeo índice → nombre del checkpoint → estado
- [x] 1.5 Actualizar `.env`, `.env.example` (`MODEL_PATH=modelo_final.pt`, variables nuevas con defaults provisorios y comentarios) y `src/.env` si corresponde

## 2. Servicio de inferencia — módulo de diagnóstico

- [x] 2.1 `src/diagnosis.py` (sin I/O ni `ultralytics`): tipos `Detection`, `ConfidenceBands`, `SeverityBands`, `Criteria`, `Diagnosis`
- [x] 2.2 Filtro por umbral de clase y nivel de confianza de una detección
- [x] 2.3 NMS calibrado entre clases (D3): orden `(nivel ↓, criticidad ↓ con Sano última, score ↓)`, IoU ≥ `SOLAPAMIENTO_IOU`
- [x] 2.4 Agregación por estado, severidad por cantidad, confianza por promedio
- [x] 2.5 Elección por relevancia con desempate por criticidad; Sano / No concluyente
- [x] 2.6 `requirements-dev.txt` con `pytest`; `tests/test_diagnosis.py` con el ejemplo del §4 del documento, empate de relevancia, empate de nivel en solapes, inválida que no elimina válida, Sano, No concluyente, bajo el mínimo
- [x] 2.7 `tests/test_settings.py`: defaults, variable ausente, bandas no crecientes, umbral global por encima de un umbral de clase

## 3. Servicio de inferencia — integración

- [x] 3.1 `api.py`: `fetch_criteria()` (`GET /api/configuracion/diagnostico`) y `registrar_diagnostico` con el payload nuevo (`sev`, `confidence`, `conf`, `detections`); quitar `obtener_configuracion`
- [x] 3.2 `main.py`: por ciclo con pendientes, leer el criterio (si falla, omitir el ciclo); validar que cada estado anómalo de `MODEL_CLASSES` tenga criterio; detectar → diagnosticar → registrar
- [x] 3.3 Probar a mano con `modelo_final.pt` sobre una captura real y revisar el log (cantidad por clase, diagnóstico elegido)

## 4. Backend — criterio de diagnóstico

- [x] 4.1 `DiagnosisCriteriaEntity` (tabla `diagnosis_criteria`) + `DiagnosisCriteriaRepository`
- [x] 4.2 `DiagnosisCriteriaService`: sembrado por defecto si la tabla está vacía, lectura, reemplazo validado (`400` con mensaje) y registro en el historial de la recalibración
- [x] 4.3 `DiagnosisCriteriaController`: `GET/PUT /api/configuracion/diagnostico`
- [x] 4.4 `MapaPermisos`: `GET` → `diagnosticos.ver`, `PUT` → `configuracion.editar` (antes de `/api/configuracion`); `MapaPermisosCoberturaTest` en verde
- [x] 4.5 Tests: sembrado, validaciones (bandas no crecientes, estado faltante, criticidad no permutación), `403` sin permiso, la cuenta Servicio lee

## 5. Backend — alta y consulta de diagnósticos

- [x] 5.1 `DiagnosticoEntity`: `conf` nullable, columnas `confidence`, `detections` (JSON texto), `detection_count`; `SectorEntity.diagnosisConfidence`
- [x] 5.2 `resources/migracion-diagnostico-detecciones.sql` (quita `NOT NULL` de `diagnostico.conf`) y su mención en el README del backend
- [x] 5.3 `DiagnosticoController.NuevoDiagnostico` con `confidence` y `detections`; `DiagnosticoService.alta` con las validaciones de coherencia estado/severidad/confianza/score y de detecciones (D7); actualiza `sector.diagnosis_*` incluida la confianza
- [x] 5.4 `concluyente` = estado ≠ No concluyente en `Diagnostico` y `DiagnosisCard` (D9); `esConcluyente` deja de usar el parámetro del motor
- [x] 5.5 DTOs: `Diagnosis`, `DiagnosisCard` (`confidence`, `detectionCount`), `Diagnostico` (`conf` nullable, `confidence`, `detectionCount`), `DiagnosticoPaso` (`confidence`); `NurseryService` y `PasadaRielService` los completan
- [x] 5.6 `GET /api/diagnosticos/{id}/detecciones` (`404` si no existe)
- [x] 5.7 Tests de `DiagnosticoService`/controller: cada fila de la tabla de D7, caja inválida, tope de 2000, sector actualizado, consulta de detecciones
- [x] 5.8 Verificar que `engine/**` no tiene cambios y que los tests del motor siguen en verde (`SupplyRule` con `conf` nulo → "sin dato")

## 6. Frontend — datos y criterio

- [x] 6.1 `types/domain.ts`: `ConfidenceLevel`, `confidence` y `detectionCount` en `Diagnosis`/`DiagnosisCard`/`DiagnosisDetail`/`DiagnosticoPaso`; `Detection`; `DiagnosisCriteria`
- [x] 6.2 `DataRepository`: `getDiagnosisCriteria`, `saveDiagnosisCriteria`, `getDiagnosisDetections`; implementaciones http y mock (criterio por defecto y detecciones determinísticas para las capturas de demo)
- [x] 6.3 Validación en cliente del criterio (mismas reglas que el backend) con tests
- [x] 6.4 Generalizar `ConfigDraft` a `ConfigDraft<T>` y `ConfigLayout`; `useDiagnosisCriteriaDraft`
- [x] 6.5 Vista `DiagnosisCriteriaPage` (`/criterio-diagnostico`): bandas de severidad, orden de criticidad con subir/bajar, aviso de que la confianza la calibra el equipo, sólo lectura sin `configuracion.editar`
- [x] 6.6 `vistas.ts` (`configuracion.ver` + `diagnosticos.ver`), `router.tsx` y el ítem "Criterio de diagnóstico" en el grupo Configuración del `Sidebar`; tests de vistas
- [x] 6.7 Test de la vista: editar banda, reordenar, valor inválido bloquea el guardado, sólo lectura

## 7. Frontend — tarjeta y cajas

- [x] 7.1 Tokens `--det-sano`, `--det-clorosis`, `--det-estres-solar`, `--det-plaga-foliar`, `--det-dano-fungico` en `tokens.css` y `theme.ts`, con su uso restringido documentado en el catálogo `/sistema-diseno`; `tokens.test.ts` actualizado
- [x] 7.2 `DiagnosisTile`: chip de nivel de confianza y score secundario; severidad sólo en anómalos; quitar la barra 0–100 % y el chip "Bajo el umbral"; "sin nivel" para diagnósticos viejos
- [x] 7.3 `DetectionOverlay`: SVG `viewBox="0 0 1 1"` sobre un contenedor del tamaño de la imagen; trazo continuo grueso para el estado diagnosticado, discontinuo fino para el resto; leyenda con cantidad por clase
- [x] 7.4 `CapturaModal`: `Segmented` *Original · Diagnóstico · Todas* (default *Original*) sólo si `detectionCount > 0`; carga diferida de las cajas; aviso si fallan
- [x] 7.5 Detalle de sector (`DiagnosisCard` de sector): nivel de confianza y el mismo modal con el id del diagnóstico `DX-` más reciente del sector
- [x] 7.6 Demo Expo: mostrar el nivel de confianza en el diagnóstico de cada paso
- [x] 7.7 Actualizar los tests existentes que dependían de `conf`/`concluyente` (`diagnosisQuery`, `generators`, `httpRepository`) y agregar tests del overlay y del toggle
- [x] 7.8 `npm run lint`, `npm run test` y `npm run build` en verde; revisar la vista en `npm run dev` y en `npm run dev:demo`
- [x] 7.9 Quitar `ConfidenceBar` (barra 0–100 % con el mínimo del motor), sus estilos y su entrada del catálogo `/sistema-diseno`

## 8. Simulador

- [x] 8.1 Formulario de carga manual (`CameraPanel`): estado, severidad sólo en anómalos, nivel de confianza y score salvo en No concluyente; `api.ts` manda el payload nuevo
- [ ] 8.2 Probar el alta manual contra el backend (anómalo, Sano y No concluyente)

## 9. Despliegue y documentación

- [x] 9.1 `docker-compose.yml`: montar `modelo_final.pt`, `MODEL_PATH=/modelo_final.pt`, variables nuevas con default por entorno; actualizar el comentario de clases
- [x] 9.2 `Desarrollo/servicio-inferencia/README.md`: pipeline nuevo, tabla de variables, de dónde sacar `modelo_final.pt`, tests
- [x] 9.3 README del backend: criterio de diagnóstico, contrato nuevo del alta, migración manual, aviso sobre la semántica de `conf` en `SupplyRule`
- [x] 9.4 `docs-motor-reglas-e-integracion/diferencias-motor-reglas-vs-reglas-v2.md`: nota de que `SupplyRule` compara el score promedio y queda por rediseñar
- [x] 9.5 `CLAUDE.md`: servicio de inferencia en el mapa, §5 (YOLOv8 de detección, no MobileNetV3), glosario (diagnóstico con niveles, criterio de diagnóstico), sexta migración manual, vista nueva en el grupo Configuración
- [x] 9.6 `start-all.ps1` / `start-all.bat`: levantar el servicio de inferencia con `--build` y omitirlo, con aviso, si falta `modelo_final.pt`
- [x] 7.10 Sacar del dashboard el score promedio y la cantidad de detecciones (tarjeta, detalle de sector, Demo Expo, leyenda de cajas); la columna "Score" pasa a filtrar y ordenar por nivel de confianza
- [x] 5.9 La URL de la imagen de una captura se versiona con su sha256 (`?v=`): se sirve inmutable y los ids de captura se repiten tras una purga
