## 1. Spike de calibración

- [ ] 1.1 Elegir ~10 capturas reales completas (1080×1440) con estados distintos y contar a mano las hojas de cada una en la región recortada
- [ ] 1.2 Script descartable (fuera del repo) que corra el pipeline con solapamientos de 60, 130 y 200 px y `TILES_FUSION_IOS` de 0,4, 0,5 y 0,6, y compare la cantidad de hojas y el diagnóstico contra el conteo manual
- [ ] 1.3 Medir el tamaño máximo de las hojas (percentil 99 de `max(ancho, alto)` de las cajas) y las hojas por tile
- [ ] 1.4 Fijar los valores de fábrica de D7 con el resultado y anotarlo en el design

## 2. Servicio de inferencia: configuración

- [x] 2.1 `settings.py`: leer y validar `RECORTE_*`, `TILES_FILAS`, `TILES_COLUMNAS`, `TILES_SOLAPAMIENTO_PX`, `TILES_TOLERANCIA_BORDE_PX` y `TILES_FUSION_IOS` (fábrica con `WARNING`, mal formado ⇒ `SettingsError`)
- [x] 2.2 `main.py`: dejar en el log los valores efectivos al arrancar
- [x] 2.3 `env.example`: agregar cada variable con su explicación breve (tabla de D7)
- [x] 2.4 Tests de `settings` para valores ausentes, negativos, no numéricos y fuera de rango

## 3. Servicio de inferencia: tiling y unión

- [x] 3.1 `src/tiling.py` (puro): grilla de tiles (tamaño y posiciones de D3), validación de avance > 0 y del caso de un solo tile
- [x] 3.2 `tiling.py`: traslado de cajas del tile a píxeles de la imagen recortada y normalización final a 4 decimales
- [x] 3.3 `tiling.py`: fusión entre tiles vecinos (borde compartido + IoS, unión con clase y score de la caja más grande, repetida hasta estabilizar)
- [x] 3.4 Normalización y recorte de la imagen (`exif_transpose`, `convert("RGB")`, recorte con error si no entra)
- [x] 3.5 `model.detect` recibe una imagen PIL; devuelve cajas en píxeles de esa imagen
- [x] 3.6 Tests de `tiling`: grilla de fábrica (6 tiles de 466×445), cortada + entera, dos pedazos, hojas distintas que se pisan, esquina de cuatro tiles, hoja entera repetida en la franja (llega a `diagnose()` y queda una)
- [x] 3.7 Tests de normalización: EXIF rotado 90°, escala de grises, RGBA, recorte que no entra

## 4. Servicio de inferencia: pipeline y alta

- [x] 4.1 `main.py`: normalizar → recortar → tiles → detectar → unir → `diagnose()`; un recorte o una grilla inválidos dejan la captura pendiente con el error en el log
- [x] 4.2 `api.py`: enviar `crop` normalizado sobre la imagen orientada
- [x] 4.3 Log por captura: cantidad de tiles, cajas antes y después de la unión, tiempo total
- [x] 4.4 README del servicio: sección de preprocesado y tiling (pipeline, regla de unión, cómo ajustar el recorte)

## 5. Backend

- [x] 5.1 `DiagnosticoEntity`: columna `crop` nullable (JSON)
- [x] 5.2 `POST /api/diagnosticos`: aceptar y validar `crop` opcional (`400` que nombra el campo)
- [x] 5.3 `GET /api/diagnosticos/{id}/detecciones`: devolver `crop` (`null` si no tiene)
- [x] 5.4 Tests: alta con recorte, sin recorte, recorte inválido y consulta de uno viejo
- [x] 5.5 README del backend: el campo `crop` en el alta y la consulta

## 6. Frontend

- [x] 6.1 `types/domain.ts` y repositorios http y mock: `crop` en la respuesta de detecciones
- [x] 6.2 `CapturaModal`: mostrar la región `crop` en las tres vistas con `DetectionOverlay` encima (D6); sin `crop`, imagen entera
- [x] 6.3 Tests de `CapturaModal`: con recorte, sin recorte y detecciones que fallan
- [x] 6.4 `npm run lint` y `npm run test` sin errores

## 7. Cierre

- [ ] 7.1 Probar de punta a punta con una captura real: el visor muestra la región sin el riel y las cajas caen sobre las hojas
- [x] 7.2 Actualizar CLAUDE.md §5 (pipeline del servicio: preprocesado, tiling y unión antes del diagnóstico)
