## 1. El contrato, primero

- [x] 1.1 Crear `Desarrollo/contratos/camara/v1/` y escribir `openapi.yaml` con los siete
      endpoints del namespace `/api/camara/v1/**`: enrolar, token, config, heartbeat, stream de
      órdenes, subida de imagen y acuse de fallo
- [x] 1.2 Definir en el OpenAPI los esquemas de payload: orden, configuración de captura,
      metadata de imagen, acuse de fallo y heartbeat
- [x] 1.3 Definir el conjunto cerrado de motivos de fallo (incluyendo `COLA_LLENA`,
      `CAMARA_NO_LISTA`, `EXPORTACION_FALLIDA`) y los códigos de respuesta de cada endpoint
- [x] 1.4 Definir los eventos del stream (`orden`, `config`, `ping`) con su formato y la
      semántica de reconexión
- [x] 1.5 Escribir el documento de referencia del contrato: flujo de credenciales, máquina de
      estados de la orden, obligaciones exigibles a cualquier cliente conforme —redactadas sin
      nombrar tecnologías— y reglas de compatibilidad y versionado
- [x] 1.6 Documentar que el header `Authorization` es la vía canónica en todos los endpoints y
      que el token por query string se admite sólo en el stream, como alternativa para clientes
      que no pueden fijar headers en su canal de eventos

## 2. Cimientos del backend: persistencia y configuración

- [x] 2.1 Agregar al `pom.xml` la dependencia de JWT (`java-jwt` o `jjwt`)
- [x] 2.2 Crear `OrdenCapturaEntity` (id, sectorId, zonaId, posicionRiel, dispositivoId,
      estado, intentos, motivoFallo, creadaEn, entregadaEn, venceEn) con su enum de estados
      `PENDIENTE/ENTREGADA/RECIBIDA/FALLIDA/VENCIDA/ERROR` y su `JpaRepository`
- [x] 2.3 Crear `CapturaEntity` (id, ordenId, sectorId, zonaId, posicionRiel, dispositivoId,
      ancho, alto, bytes, sha256, capturadaEn, recibidaEn, rutaArchivo) y su repositorio
- [x] 2.4 Crear `DispositivoCamaraEntity` (id, nombre, plataforma, huella del refresh token,
      estado, ultimoHeartbeat, capturasOk, capturasError) y su repositorio
- [x] 2.5 Crear `DiagnosticoEntity` (id, sectorId, zonaId, **capturaId NOT NULL**, estado, conf,
      sev, creadoEn) y su repositorio — **sin columna de origen**
- [x] 2.6 Agregar a `application.properties` las claves nuevas: directorio de capturas, tamaño
      máximo de subida, plazo de vencimiento de orden, máximo de reintentos, intervalo del
      watchdog, vida de los tokens, secreto de firma, intervalo de heartbeat y umbrales de
      silencio, resolución máxima, calidad JPEG, warm-up y tope de cola de órdenes
- [x] 2.7 Crear `CapturaProperties` (`@ConfigurationProperties`) espejando `NurseryProperties`
- [x] 2.8 Documentar el DDL de las cuatro tablas nuevas en `resources/migracion-manual.sql`

## 3. Almacenamiento de imágenes

- [x] 3.1 Implementar `AlmacenamientoImagenService`: guardar el JPEG en
      `<dir>/AAAA/MM/DD/<capturaId>.jpg`, leerlo y borrarlo
- [x] 3.2 Validar en el arranque que el directorio raíz exista o pueda crearse y sea
      escribible; fallar temprano con mensaje explícito si no
- [x] 3.3 Calcular el SHA-256 de los bytes recibidos y compararlo contra el declarado por el
      cliente, rechazando con `422` cuando no coincida
- [x] 3.4 Ordenar la escritura: primero el archivo en disco, después la fila de metadata; si
      falla el archivo, no persistir la fila
- [x] 3.5 Tests: guardado, lectura, hash correcto, hash incorrecto y directorio no escribible

## 4. Autenticación de dispositivos de captura

- [x] 4.1 Implementar `TokenService`: emisión y verificación del JWT de acceso de vida corta,
      con `dispositivoId` como sujeto
- [x] 4.2 Implementar `DispositivoCamaraService`: generar código de vinculación de un solo uso
      y vida corta, consumirlo, enrolar el dispositivo y emitir su credencial de renovación
      (almacenada como huella, nunca en claro)
- [x] 4.3 Implementar el intercambio credencial de renovación → token de acceso, con rechazo
      `401` para credenciales revocadas, inexistentes o vencidas
- [x] 4.4 Implementar el filtro de autenticación acotado por path a `/api/camara/**` y
      `/api/capturas/**`, aceptando el header `Authorization` en todas las rutas y el query
      param **únicamente** en la del stream
- [x] 4.5 Desactivar el logueo de query strings para no filtrar el token del stream a los logs
- [x] 4.6 Implementar heartbeat y derivación del estado operativo por umbrales de silencio,
      espejando la lógica de watchdog de `HardwareService`
- [x] 4.7 Crear `DispositivoCamaraController` con las rutas versionadas del contrato (enrolar,
      token, config, heartbeat) y la ruta de plataforma de generación del código de vinculación
- [x] 4.8 Tests: código consumido, código vencido, credencial revocada, token expirado, header
      aceptado en el stream, ruta protegida sin token, y que `GET /api/nursery` siga
      respondiendo sin token

## 5. Ciclo de vida de la orden de captura

- [x] 5.1 Implementar `CapturaService.emitirOrden`: validar sector contra la topología vigente,
      crear la orden en `PENDIENTE` con su plazo, y empujarla al emisor SSE si hay uno abierto
- [x] 5.2 Implementar el registro de emisores SSE por dispositivo, con drenado de las órdenes
      `PENDIENTE` al abrirse el stream y limpieza del emisor al cerrarse o expirar
- [x] 5.3 Implementar el keep-alive periódico del stream y el evento de cambio de configuración
- [x] 5.4 Implementar `CapturaService.recibirImagen`: correlación estricta por `ordenId` con
      `404` / `409` (devolviendo el `capturaId` existente) / `403` / `422`, y transición a
      `RECIBIDA`
- [x] 5.5 Implementar el acuse de fallo con validación del motivo contra el conjunto cerrado del
      contrato, rechazando con `400` los motivos desconocidos
- [x] 5.6 Implementar el watchdog `@Scheduled` que vence las `ENTREGADA` fuera de plazo, las
      reencola mientras queden intentos y las manda a `ERROR` al agotarlos, ignorando los
      estados terminales
- [x] 5.7 Crear `CapturaController` separando las rutas del contrato (stream, subida, fallo) de
      las de plataforma (emitir orden, consultar orden, servir imagen con `Cache-Control:
      immutable` y `ETag` de sha256)
- [x] 5.8 Tests de la máquina de estados: entrega, recepción, reintento de subida sobre orden
      ya recibida, subida desde otro dispositivo, vencimiento, reencolado, agotamiento de
      intentos y drenado de pendientes al conectar

## 6. Suite de conformidad del contrato

- [x] 6.1 Escribir la suite que recorre el ciclo completo contra un backend levantado:
      vinculación, enrolamiento, token, apertura del canal, recepción de orden, subida de
      imagen, acuse de fallo y heartbeat
- [x] 6.2 Cubrir los casos de borde del contrato: orden inexistente, orden ya resuelta,
      dispositivo ajeno, hash inválido, motivo de fallo desconocido y token vencido
- [x] 6.3 Verificar que el header `Authorization` funciona en todas las rutas del contrato,
      incluida la del stream
- [x] 6.4 Documentar cómo correr la suite, para que sirva de criterio de aceptación de cualquier
      cliente futuro

## 7. Alta de diagnósticos por camino único

- [x] 7.1 Implementar `DiagnosticoService`: alta con validación de captura existente
      (obligatoria), sector de la topología vigente, estado dentro de la taxonomía y rango de
      confianza; marcado de concluyente contra el umbral configurado; listado descendente
- [x] 7.2 Crear `DiagnosticoController` con el endpoint público de alta y el de listado, sin
      compuerta por modo de operación y sin ninguna variante según el emisor
- [x] 7.3 Verificar que `SimulacionController` **no** se modifica en ningún punto de este cambio
- [x] 7.4 Agregar el campo de URL de imagen a los DTO `DiagnosisCard` y a su tipo en
      `domain.ts`, vacío para los diagnósticos derivados de sectores
- [x] 7.5 Modificar `NurseryService` para anteponer los diagnósticos persistidos a las cards
      derivadas, sin colisión de identificadores y contándolos en `stats.diagCount`
- [x] 7.6 Tests: alta válida, alta sin captura, captura inexistente, sector inexistente, estado
      fuera de taxonomía, confianza fuera de rango, alta en ambos modos de operación con el
      mismo resultado, y snapshot incluyendo el diagnóstico con su URL de imagen

## 8. Proyecto de la app de cámara

- [x] 8.1 Crear `Desarrollo/camara/` como proyecto Vite + React + TypeScript independiente, con
      su `package.json`, `tsconfig`, ESLint y Prettier propios
- [x] 8.2 Verificar que el proyecto instala, levanta y compila sin ejecutar nada del proyecto
      frontend
- [x] 8.3 Hacer que la config de Vite levante en HTTPS si encuentra `CAMARA_HTTPS_KEY`/
      `CAMARA_HTTPS_CERT`, y en HTTP si no
- [x] 8.4 Crear `manifest.json` con presentación autónoma, orientación fijada e íconos
- [x] 8.5 Crear el service worker: cache-first para el shell, exclusión explícita por path de
      `/api/**` y de las imágenes, y sin interceptar `POST`
- [x] 8.6 Crear su `env.example` con la URL base del backend
- [x] 8.7 Copiar los design tokens mínimos necesarios, sin importar nada del frontend

## 9. Cliente de cámara: cliente del contrato

- [x] 9.1 Implementar la capa de transporte contra el namespace del contrato, sin depender de
      ningún endpoint fuera de él
- [x] 9.2 Implementar el flujo de vinculación: pantalla de código, enrolamiento, guardado de la
      credencial y renovación automática del token antes de expirar
- [x] 9.3 Implementar la conexión `EventSource` al stream con reconexión automática y reapertura
      al renovar el token
- [x] 9.4 Implementar la cola FIFO de órdenes en memoria, con tope configurable y acuse de fallo
      `COLA_LLENA` al descartar
- [x] 9.5 Implementar el envío multipart de la imagen con su metadata y sha256, y el manejo del
      `409` de orden ya recibida como éxito
- [x] 9.6 Implementar los reintentos con espera exponencial y jitter
- [x] 9.7 Implementar el respaldo en IndexedDB de las imágenes que agotaron reintentos, con tope
      de cantidad y tamaño, descarte de las más antiguas y `navigator.storage.persist()`
- [x] 9.8 Implementar el drenado del respaldo ante el evento `online` y ante la reapertura del
      canal
- [x] 9.9 Implementar el heartbeat periódico y el acuse de fallo con motivos del conjunto cerrado
- [x] 9.10 Tests unitarios de la cola de órdenes, el backoff y la cola de IndexedDB

## 10. Cliente de cámara: captura y supervivencia en iOS

- [x] 10.1 Implementar el hook de cámara: `getUserMedia` con `facingMode: 'environment'` sólo
      tras el gesto del botón de inicio, stream sostenido, y `<video>` con `playsinline`,
      `muted` y `autoplay`
- [x] 10.2 Implementar el warm-up con el valor que baja la configuración remota
- [x] 10.3 Implementar la captura por `drawImage` + `toBlob('image/jpeg', calidad)` con la
      resolución y calidad del backend
- [x] 10.4 Implementar `applyConstraints` de exposición y balance de blancos en modo best
      effort, con `try/catch`, y loguear `getCapabilities()`/`getSettings()` reales
- [x] 10.5 Implementar el manejo de `visibilitychange`: verificar `readyState` de la pista y
      reinicializar stream y canal cuando no esté viva
- [x] 10.6 Implementar el wake lock con detección de soporte, re-solicitud al volver a visible y
      degradación silenciosa

## 11. Panel de estado de la app de cámara

- [x] 11.1 Maquetar el preview en vivo
- [x] 11.2 Implementar el indicador de conexión (conectado / reconectando / desconectado),
      legible a distancia
- [x] 11.3 Implementar la miniatura de la última captura con su timestamp e identificador
- [x] 11.4 Implementar los contadores de capturas exitosas y fallidas de la sesión
- [x] 11.5 Implementar el botón de captura manual de prueba
- [x] 11.6 Implementar el log de eventos desplazable, acotado en cantidad de entradas
- [x] 11.7 Implementar el estado inequívoco de "no operativo" cuando se pierde el canal o la
      cámara

## 12. Panel de cámara en el simulador

- [x] 12.1 Agregar los tipos de orden, captura, dispositivo y diagnóstico a `domain.ts` y los
      métodos correspondientes a `DataRepository`, `HttpRepository` y `MockRepository`
- [x] 12.2 Crear el componente de estado del dispositivo con generación del código de
      vinculación y el caso de "sin dispositivo enrolado"
- [x] 12.3 Crear el formulario de solicitud de captura (sector y macro-zona de la topología
      vigente, posición de riel) contra el endpoint de emisión de órdenes
- [x] 12.4 Implementar el seguimiento de la orden por polling hasta su estado final, mostrando
      la imagen al recibirse y el motivo cuando no se cumple
- [x] 12.5 Crear el formulario de diagnóstico manual sobre la captura recibida (estado,
      severidad, confianza; sector y macro-zona precargados y editables) que llama al endpoint
      público de alta de diagnósticos
- [x] 12.6 Montar la sección de cámara en `SimulacionPage` sin tocar el dashboard
- [x] 12.7 Auditar que el panel no consume ningún endpoint exclusivo del simulador
- [x] 12.8 Tests del repositorio mock de captura y de la validación del formulario

## 13. Imagen real en la vista de Diagnósticos de IA

- [x] 13.1 Modificar `DiagCard` para mostrar la miniatura de la captura cuando exista, con
      carga diferida, y el gradiente actual como respaldo
- [x] 13.2 Modificar `PhotoModal` para mostrar la fotografía real cuando exista, con respaldo
      al gradiente ante ausencia de imagen o error de carga
- [x] 13.3 Verificar que la grilla y sus filtros sigan funcionando con las cards persistidas
      mezcladas

## 14. Integración, documentación y cierre

- [x] 14.1 Escribir el README de `Desarrollo/camara/`: cómo levantarla, cómo enrolarla y las dos
      recetas de HTTPS (certificado local con `mkcert` para la LAN; túnel para demos)
- [x] 14.2 Documentar en el README del backend los endpoints nuevos, distinguiendo la superficie
      del contrato de la API de plataforma, y la configuración agregada
- [x] 14.3 Actualizar `CLAUDE.md`: `Desarrollo/camara/` y `Desarrollo/contratos/` en el mapa del
      repositorio, el contrato de cámara como fuente de verdad junto a `contrato.h`, y el
      glosario (orden de captura, captura, dispositivo de captura)
- [x] 14.4 Correr la suite de conformidad contra el backend y dejarla en verde
- [ ] 14.5 **BLOQUEADA — requiere el iPhone físico.** Prueba de punta a punta con el iPhone real: vincular, iniciar cámara, pedir captura
      desde el simulador, recibir la imagen, cargar el diagnóstico y verificar la card en
      Diagnósticos de IA
- [ ] 14.6 **BLOQUEADA — requiere el iPhone físico.** Prueba de resiliencia en el iPhone: salir y volver al primer plano, cortar y
      restablecer la red, y verificar que las órdenes pendientes se drenan y las imágenes
      respaldadas se envían
- [ ] 14.7 **BLOQUEADA — depende de 14.5.** Registrar qué constraints de exposición y balance de blancos se aplicaron realmente y
      dejar la conclusión escrita, para decidir con datos si la consistencia entre imágenes
      alcanza
- [x] 14.8 Correr lint y tests del proyecto de cámara, del frontend y del backend en verde

---

## Estado de cierre

**86/94 tareas completas.** Las 3 pendientes (14.5, 14.6, 14.7) requieren el **iPhone físico
montado en el riel** y no pueden ejecutarse desde acá.

Lo que sí quedó verificado sin el teléfono:

- Suite de conformidad del contrato: **22/22** contra el backend real.
- Backend: **48 tests** en verde (23 del contrato de cámara, 11 de diagnósticos).
- Frontend: **59 tests**, lint y ambos builds en verde.
- App de cámara: **11 tests**, lint y build en verde.
- Recorrido completo por HTTP (orden → canal SSE → imagen → diagnóstico → tarjeta con foto
  en `/api/nursery`), simulando exactamente lo que hará el dispositivo.

Lo que **sólo** se puede comprobar con el teléfono:

| Tarea | Qué falta comprobar |
|---|---|
| 14.5 | Que `getUserMedia` funcione con el certificado elegido; que el preview no se abra a pantalla completa; el recorrido real con una foto de verdad |
| 14.6 | Que la cámara se reinicialice al volver del segundo plano; que el wake lock aguante; que las órdenes pendientes se drenen tras un corte de red real |
| 14.7 | **Qué constraints de exposición y balance de blancos acepta Safari realmente.** Es la evidencia para decidir si la consistencia entre imágenes alcanza para entrenar el modelo. El log en pantalla ya los registra; falta leerlos y anotar la conclusión |
