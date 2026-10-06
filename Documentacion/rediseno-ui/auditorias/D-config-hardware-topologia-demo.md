# Auditoría UI/UX — Configuración · Hardware · Topología · Demo Expo

**Alcance:** `/configuracion`, `/hardware`, `/topologia`, `/demo-expo` del dashboard (`Desarrollo/frontend/src`).
**Método:** lectura de código (features, hooks, mock, tipos, validación), specs OpenSpec, cruces con el backend cuando un dato de UI dependía de él, y recorridos con Playwright en modo demo (`mock`) a 1440×900 y 390 px.
**Fecha:** 06/10/2026. **No se modificó ningún archivo del repo.**

Capturas de esta auditoría (scratchpad, `shotsD/`):

| Pantalla | Archivos |
|---|---|
| Configuración | `cfg-viewport-top.png`, `cfg-umbral-error.png`, `cfg-errors-mid.png`, `cfg-errors-bottom.png`, `cfg-saved.png`, `mob-noshell-cfg.png` |
| Hardware | `hw-alta-vacio.png`, `hw-alta-error.png`, `hw-alta-actuador.png`, `hw-alta-ok.png`, `hw-recambio.png`, `hw-recambio-ok.png`, `hw-filtro-fds.png`, `hw-filtro-vacio.png`, `hw-tras-regenerar.png`, `mob-noshell-hw.png`, `mob-hw-viewport.png` |
| Topología | `topo-disposicion-dirty.png`, `topo-cantidad-dirty.png`, `topo-confirmar.png`, `topo-500.png`, `topo-invalid.png`, `topo-empty.png`, `topo-regenerada.png`, `dash-tras-regenerar.png`, `mob-noshell-topo-savebar.png` |
| Demo Expo | `demo-desk-0-vacio.png` … `demo-desk-9-cancelada.png`, `demo-mob-*.png`, `mob-noshell-demo-curso.png`, `mob-noshell-demo-final.png` |

> `mob-noshell-*` son capturas a 390 px **con el sidebar oculto por CSS inyectado**. Sirven para separar lo que rompe el shell (sidebar fijo de 256 px, sin colapso) de lo que rompe cada página por sí sola.

---

## 0. Hallazgos transversales (afectan a las cuatro pantallas)

1. **No hay layout responsive en el shell.** `Sidebar.module.css:2` fija `width: 256px` y ni `AppLayout.module.css` ni `Sidebar.module.css` tienen una sola `@media`. A 390 px a `<main>` le quedan **134 px** de ancho (medido): cualquier página se vuelve una columna de 2–3 palabras por renglón (`shots/mob-*.png`, `shotsD/mob-hw-viewport.png`). Con `isMobile` el viewport se ensancha a 658–704 px y aparece scroll horizontal en el documento. Ninguna página de este alcance sirve en un celular si antes no se arregla el shell.
2. **Las vistas importan módulos del mock aunque la app corra en `http`.** Eso contradice el contrato de `CLAUDE.md` §4 (la UI no sabe qué hay detrás):
   - `ConfiguracionPage.tsx:8,85` — "Restablecer valores de fábrica" usa `buildConfig()` del mock, con `updatedBy: 'Mock System'` (`data/mock/config.ts:40`). En producción, restablecer deja la línea "Última edición: Mock System" y marca el borrador como modificado.
   - `lib/configValidation.ts:8,31-37` — el "rango fisiológico" sale de `data/mock/specs.ts`.
   - `HardwareFilters.tsx:2` y `AltaHardwareForm.tsx:4,21,121` — la lista de macro-zonas (`ZONA_IDS`) es la del mock. **En `http`, si la topología del backend tiene 8 zonas, el filtro y el alta siguen ofreciendo MZ-1…MZ-6.** Sólo el mock la actualiza (`setZonasDisponibles`).
   - `TopologiaPage.tsx:13` — los topes (50 / 500) y los defaults de disposición también salen del mock.
   - `selectors.ts:27` → `data/mock/sectorDetail.ts:84` — "Latencia configurada: 2 h" en el detalle de sector está escrito a mano, en los dos modos (ver §1.3).
3. **Los umbrales viven en dos lugares y nadie los cruza.** Configuración edita las bandas de estado (`umbral_metrica`). El Motor de reglas edita los umbrales que deciden (`riego.umbral-humedad` 45 %, `riego.umbral-critico` 35 %, `riego.saturacion-bloqueo` 75 %, `riego.saturacion-alerta` 80 %…). Para la humedad de sustrato, con los valores de fábrica:
   - 43 % se pinta **Saludable** (ideal 42–68) y **dispara riego** (< 45 %).
   - 33 % se pinta **En observación** (32–42) y el motor ya lo trata como **déficit crítico** (< 35 %).
   - 76 % se pinta En observación y el motor **bloquea el riego por saturación** (≥ 75 %).

   Ninguna de las dos pantallas muestra la otra mitad. Para peor, la regla "Seguimiento post-acción" del Motor dice **"Sin parámetros configurables"**, pero su latencia y su delta se editan en Configuración. Y hay dos "dosis máximas en 24 h": "1 dosis" en el Motor y "15 ml/24 h" en Configuración, con unidades distintas.
4. **Cada pantalla guarda de una forma distinta.**
   - Configuración: barra sticky con "Restablecer valores de fábrica" + "Guardar cambios", sin "Descartar" ni contador de cambios.
   - Topología: popup flotante fijo con "Descartar" + "Guardar cambios", que aparece sólo si hay cambios.
   - Motor de reglas: barra sticky con "Sin cambios pendientes." + "Descartar" + "Guardar cambios".
   - Hardware: formulario inline con "Cancelar" + "Registrar".

   Ninguna avisa al salir con cambios sin guardar. Verificado: se edita un umbral, se navega a Hardware y se vuelve, y el cambio se pierde sin aviso.
5. **Contraste.** Con los tokens de `styles/tokens.css`:
   - `--faint` (#97A299) sobre blanco da **2,64:1** y sobre `--bg` **2,35:1**. Ese token se usa en hints, unidades, IDs `DEV-…`, "—", la línea de auditoría y la duración de los pasos.
   - `--muted` sobre `--bg` da 4,18:1 (el texto intro de 13 px no llega a AA).
   - `--ok` sobre blanco da 3,26:1 ("Configuración guardada correctamente.", "Llegó", "Foto recibida").
   - `--info` sobre blanco da 3,68:1 ("El celular está sacando la foto").
   - `--crit` sobre blanco da 3,93:1 (todos los errores de 11–13 px).
   - El badge "Batería Baja" (#A66A12 sobre #FBF0DC) da 3,96:1.
   - Las celdas del preview de topología (`--off` sobre `--bg`) dan 1,94:1.
6. **Etiquetas sin asociar.** `NumberField.tsx:22`, `IntervalosForm.tsx:47`, `HardwareFilters.tsx:28,44,59`, `AltaHardwareForm.tsx:83,103,115,130` y `TopologiaPage.tsx:247` usan `<label>` sin `htmlFor`. Algunos inputs compensan con `aria-label`, pero los `<select>` de Hardware y el selector de unidad de Intervalos **no tienen nombre accesible**. Los hints no están enlazados con `aria-describedby` y los errores no tienen `role="alert"` ni `aria-invalid` (salvo los de Demo Expo).
7. **Mayúsculas y voz inconsistentes:**
   - "Frecuencia Operativa", "Intervalo de Evaluación (IA/Watchdog)" e "Intervalo de Sensado (IoT)" están en Title Case.
   - "Batería Baja" (`HardwareTable.tsx:77`) convive en la misma fila con el KPI "Batería baja".
   - "Falla Hidráulica" viene así del seed, en `data.sql:711` y `hardware.ts:102`.
   - "Último update" mezcla idiomas.
   - "Watchdog", "warn", "crit" e "IoT" son jerga.
   - La spec `gestion-hardware` misma escribe "Batería Baja" y "Fuera de Servicio".

---

## 1. Configuración agronómica (`/configuracion`)

### 1.1 Propósito y tareas
Quien la usa es el Ingeniero Agrónomo. La vista sirve para calibrar las decisiones automáticas al vivero (HU-15). Las tareas son:
- ajustar cuándo una métrica se considera ideal, en observación o crítica;
- fijar topes informativos de insumo;
- programar el plan de rustificación (apertura de la mediasombra por días);
- definir cuándo se evalúa si una acción fue efectiva;
- fijar los intervalos del ciclo de evaluación y del ciclo de lectura/riego;
- (colado) mostrar u ocultar la pestaña Demo Expo.

### 1.2 Inventario completo

**Encabezado (Topbar):** título "Configuración agronómica"; subtítulo "Umbrales, límites operativos y plan de rustificación" (`ConfiguracionPage.tsx:49`).
**Intro:** "Calibrá las decisiones automáticas del sistema a la realidad del vivero. El sistema arranca con valores de fábrica seguros para la yerba mate; los cambios se validan contra el rango fisiológico antes de aplicarse."

**Estados de carga:**
- "Cargando configuración…"
- Error: "No se pudo cargar la configuración: {mensaje}" en rojo, sin botón de reintento.

**A. Umbrales de métricas.** Hint: "Bandas ideal · advertencia · crítico por variable". Tabla de 10 filas × 6 inputs numéricos. Paso 0,1 si la métrica tiene decimales y 1 si no. Cada input tiene `aria-label` "{Métrica} · {columna}". Columnas: Ideal mín, Ideal máx, Advert. mín, Advert. máx, Crít. mín, Crít. máx.

| Métrica (unidad) | Ideal | Advert. | Crít. (= envolvente permitida) | ¿Afecta estado? |
|---|---|---|---|---|
| Humedad de sustrato (%) | 42–68 | 32–80 | 22–90 | Sí |
| Humedad ambiental (%) | 62–84 | 52–91 | 42–96 | Sí |
| Temperatura del aire (°C) | 18–27 | 15–31 | 11–35 | Sí |
| Luminosidad (%) — LDR, clave `uv` | 35–70 | 20–85 | 10–95 | Sí |
| Temperatura del sustrato (°C) | 16–24 | 13–28 | 10–32 | **No (provisional)** |
| Nutrientes (CE) (dS/m) | 1,0–1,9 | 0,8–2,5 | 0,5–3,1 | Sí |
| pH del sustrato (pH) | 5,0–6,0 | 4,5–6,5 | 4,0–7,0 | **No (provisional)** |
| Nitrógeno (mg/kg) | 100–200 | 70–260 | 40–320 | **No (provisional)** |
| Fósforo (mg/kg) | 30–60 | 20–80 | 10–100 | **No (provisional)** |
| Potasio (mg/kg) | 120–240 | 90–300 | 60–380 | **No (provisional)** |

Reglas de validación (`configValidation.ts:21-39`):
- **coherencia:** crit mín ≤ warn mín ≤ ideal mín < ideal máx ≤ warn máx ≤ crit máx;
- **envolvente:** crit mín ≥ crit de fábrica y crit máx ≤ crit de fábrica, o sea que el crit **sólo puede achicarse**.

Mensajes (fila roja debajo de la métrica):
- "Bandas incoherentes en «{Métrica}»: crit mín ≤ warn mín ≤ ideal mín < ideal máx ≤ warn máx ≤ crit máx."
- "«{Métrica}» fuera del rango fisiológico permitido ({min} a {max} {unidad})."
- A nivel global, no visible: "Se esperan los umbrales de las 10 métricas."

Semántica real, que la UI no explica: si el valor está fuera de la banda de advertencia es **Crítico**; si está fuera del ideal pero dentro de advertencia, **En observación** (`generators.ts:47-51`). "Crít. mín/máx" no es "donde empieza lo crítico": es el borde del rango físico/permitido.

**B. Límites de riego e insumos.** Hint: "Topes informativos de volumen y dosis".
- Aviso: "Los umbrales que usan las reglas del motor —volumen y caudal de riego, apertura máx. de mediasombra y el resto— se editan en **Motor de reglas**", con link a `/reglas`.
- Campo único "Dosis máx. de insumo": **15** ml/24 h, paso 0,5, mín. 0, debe ser > 0. Hint "No intervienen en decisiones del motor: sólo se informan en los textos." Error global: "El valor de la dosis máxima de insumo por 24 h debe ser mayor a 0."

**C. Plan de rustificación.** Hint: "Cronograma de exposición gradual de la mediasombra". Filas "Etapa n" con Día desde, Día hasta, Apertura % y el botón × (`aria-label` "Eliminar etapa n").

Valores de fábrica:

| Etapa | Días | Apertura |
|---|---|---|
| 1 | 1–7 | 20 % |
| 2 | 8–14 | 40 % |
| 3 | 15–21 | 70 % |
| 4 | 22–30 | 100 % |

- "+ Agregar etapa" crea una etapa que arranca el día siguiente a la última, dura 7 días y abre min(100, apertura máx.).
- La apertura máx. viene del catálogo del motor (`mediasombra.apertura-maxima`: 100 % de fábrica, rango 10–100).
- Mensajes (uno solo a la vez, debajo de la lista):
  - "Etapa de rustificación inválida: «día desde» debe ser ≥ 1 y ≤ «día hasta»."
  - "Las etapas de rustificación no pueden solaparse en el cronograma."
  - "La apertura de cada etapa debe estar entre 0 % y la apertura máxima ({n} %)."
- Estados del catálogo: "Cargando la apertura máxima del motor…" y, si falla: "No se pudo obtener la apertura máxima de la mediasombra ({error}). Sin ese tope no se puede validar el plan de rustificación ni guardar la configuración." + [Reintentar]. En ese caso **se bloquea el guardado de toda la página**.

**D. Seguimiento post-acción.** Hint: "Latencia y mejora mínima para evaluar efectividad".
- "Latencia de seguimiento": **2** min, > 0.
- "Delta mín. de recuperación": **5** puntos, paso 0,5, > 0.

**E. Frecuencia Operativa.** Hint: "Intervalos de sensado (IoT) y ejecución de inferencia (IA)". Cada campo tiene un número (`min=1`) + select Minutos/Horas + ícono ⓘ con `title`.
- "Intervalo de Evaluación (IA/Watchdog)": **5 Minutos** (5 min). Tooltip: "Cada cuánto tiempo el backend analiza todos los sectores y se procesan las fotos".
- "Intervalo de Sensado (IoT)": **4 Horas** (240 min). Tooltip: "Cada cuánto tiempo los sensores de los sectores reportan sus lecturas".
- Lo que hace en realidad: el backend lo usa como **ciclo de lectura del riego** (un riego por ciclo, anclado a las 02:00) y **lo acota en silencio a 60–360 min** (`backend/.../CicloLectura.java:22-25,34-43`). No configura cada cuánto reportan los nodos.

**F. Demo Expo.** Hint: "Preferencia de visualización · se guarda al instante". Switch "Mostrar Demo Expo" / "Muestra la pestaña Demo Expo en el menú". De fábrica está apagado. Se deshabilita mientras guarda. Error: "No se pudo guardar: {error}".

**Barra inferior sticky:**
- "Última edición: Mock System": en demo no hay fecha; después de guardar dice "Ingeniero Agrónomo · 06/10/2026 23:13".
- Feedback: "Configuración guardada correctamente." o el mensaje de error del backend (sólo el primero).
- [Restablecer valores de fábrica] y [Guardar cambios], que pasa a "Guardando…". Guardar se habilita sólo si hay cambios, no hay errores, no está guardando y se conoce la apertura máx.

### 1.3 Bugs e inconsistencias

| # | Hallazgo | Evidencia |
|---|---|---|
| C1 | **Al cambiar la unidad del intervalo, el valor queda ilegible.** Con 5 min y "Horas", el input muestra **0.08333333333333333**. Además, el estado local de la unidad no se reinicia con "Restablecer". | `IntervalosForm.tsx:28-29,38-43`; `shotsD/cfg-errors-bottom.png` |
| C2 | **Los intervalos no se pueden borrar para reescribirlos.** El handler ignora `""`, `0` y negativos, y el input controlado vuelve al valor anterior. Por eso `invalid` (`<= 0`) nunca llega a verse. Typear 1,5 min redondea a 2 sin avisar. | `IntervalosForm.tsx:31-36,83,90` |
| C3 | **"Intervalo de Sensado (IoT)" no es lo que dice.** El backend no se lo manda a los nodos: lo usa para el ciclo de riego y lo acota a 60–360 min. La UI acepta 5 min o 48 h sin advertir que se va a ignorar. El tooltip habla de "sensores de los sectores", cuando el sensado es por macro-zona (`CLAUDE.md` §2). Y choca con "Antigüedad máxima de la lectura: 90 s" del Motor. | `IntervalosForm.tsx:87-88`; `CicloLectura.java:22-43` |
| C4 | **Latencia de seguimiento: "2 min" acá y "Latencia configurada: 2 h" en el sector.** El detalle de sector lo tiene escrito a mano y no lee la configuración, en ninguno de los dos modos. El historial dice "2 min" (`history.ts:240`; el backend formatea `"{n} min"`, `ConfiguracionService.java:313`). | `data/mock/sectorDetail.ts:84`, `selectors.ts:27`, `PostActionCard.tsx:19` |
| C5 | **"Restablecer valores de fábrica" no confirma**, usa el mock en producción (ver §0.2) y la línea de auditoría lee el **borrador**: después de restablecer en `http` diría "Última edición: Mock System" aunque nadie guardó. | `ConfiguracionPage.tsx:84-87,161-166` |
| C6 | **"Última edición: Mock System"** sin fecha, en la demo. Además el usuario de la Topbar es "Mariano Duarte · Productor Viverista" y lo guardado dice "Ingeniero Agrónomo": la auditoría muestra un rol y no una persona. | `data/mock/config.ts:40`; `mockRepository.ts:95-118` |
| C7 | **El flag `provisional` se ignora.** Cinco métricas (sustrato, pH, N, P, K) no cambian el estado (`afectaEstado: false`), pero la tabla las edita igual que las demás. En el mapa sí se marcan como "rango provisional" (`SensadoCard.tsx:99-107`). | `UmbralesForm.tsx` (no lee `provisional`); `config.ts:32` |
| C8 | **Los encabezados de banda engañan.** "Advert. mín 32" no es donde empieza la advertencia: es donde empieza lo **crítico**. "Crít. mín 22" es el borde del rango permitido y además **sólo se puede achicar** (validación de envolvente). Nada de esto se ve. | `UmbralesForm.tsx:17-24`; `configValidation.ts:31-37`; `generators.ts:47-51` |
| C9 | **Hay errores sin campo marcado.** En umbrales y rustificación ningún input se pone rojo, sólo aparece un renglón. La spec pide "marca el campo como inválido". El mensaje de solape no dice qué etapa choca. | `UmbralesForm.tsx:50-60`; `RustificacionPlanForm.tsx:43-50,62-66` |
| C10 | **El plan de rustificación acepta huecos** (1–7 y 10–14 pasa). Según la spec `rustificacion-mediasombra`, un día fuera del plan es NOOP, así que la mediasombra queda sin plan esos días y nadie lo advierte. Tampoco se ve la **fecha de siembra** (propiedad del backend `yerbanalytics.nursery.fecha-siembra-iso`) ni "hoy es el día X". | `configValidation.ts:56-73` |
| C11 | **El feed de actividad del mock contradice el plan.** Dice "Plan de rustificación día 12 · apertura gradual 35% → 45%", cuando el día 12 corresponde a 40 %. Además "apertura" y "cobertura" se usan como si fueran lo mismo ("Cobertura llevada a 70%"). | `data/mock/specs.ts:260,281` |
| C12 | **Una tarjeta entera para un campo que no hace nada.** "Límites de riego e insumos" sólo tiene un campo, de insumo, que "no interviene en decisiones del motor". El título promete límites de riego que ya no están. El hint tiene un error gramatical ("No **intervienen**" con sujeto singular). | `LimitesActuadoresForm.tsx:28-36` |
| C13 | **"Seguimiento post-acción" aparece en las dos pantallas y se contradice.** En el Motor de reglas dice "Sin parámetros configurables"; acá tiene dos parámetros. No hay link de vuelta desde el Motor. | `features/reglas/components/ReglaCard.tsx:87` |
| C14 | **"Frecuencia Operativa"** en Title Case, y lo mismo las etiquetas de intervalos. | `ConfiguracionPage.tsx:146`; `IntervalosForm.tsx:80,87` |
| C15 | **Al limpiar un umbral o un NumberField queda 0** (`Number("") = 0`), no vacío: aparece de inmediato el error de coherencia, antes de que el usuario termine de tipear. | `UmbralesForm.tsx:57`; `NumberField.tsx:33` |
| C16 | **Un switch de presentación (Demo Expo) en "Configuración agronómica"**, al lado de umbrales fisiológicos, con un modelo de guardado opuesto (instantáneo vs. borrador). | `ConfiguracionPage.tsx:152-158` |
| C17 | **Si falla el catálogo del motor, no se puede guardar nada**, ni siquiera un umbral de temperatura que no tiene relación con la mediasombra. | `ConfiguracionPage.tsx:89` |
| C18 | **En demo, guardar umbrales no cambia nada visible:** el mapa sigue clasificando con `specs` estáticos. | `generators.ts:47-51` |
| C19 | **La spec quedó desactualizada:** dice "5 métricas" y "tiempo máximo de apertura y volumen diario de riego". El comentario de `UmbralesForm.tsx:1` también dice "5 métricas". | `openspec/specs/configuracion-agronomica/spec.md` |

### 1.4 UX
- **Jerarquía plana.** Seis tarjetas iguales con el mismo peso, en ~1.900 px de scroll y sin índice ni anclas. Lo que más impacta (umbrales y plan) se ve igual que un campo informativo.
- **Agrupación por implementación, no por tarea.** Lo que el agrónomo piensa como "riego" está repartido en tres lugares: umbral de estado (acá), umbral de decisión (Motor) y ciclo de lectura (acá, mal rotulado).
- **Validación.**
  - Los errores llegan como renglones sueltos.
  - "Guardar cambios" queda gris sin explicar por qué (no hay "3 errores · ir al primero").
  - Los errores de una métrica no señalan cuál de los 6 inputs está mal.
- **Guardado.**
  - La barra sticky no dice cuántos cambios hay ni ofrece "Descartar": la única salida es "Restablecer de fábrica", que es otra cosa y no pide confirmación.
  - No hay aviso al navegar con cambios sin guardar.
  - El mensaje "Configuración guardada correctamente." queda fijo hasta el próximo guardado.
- **Unidades y ayuda.**
  - "puntos" no dice de qué métrica: es la humedad de sustrato.
  - La latencia de 2 min es agronómicamente sospechosa y no tiene rango sugerido.
  - Los ⓘ funcionan sólo con hover.
- **Estados.** El error de carga no tiene reintento. Al cargar no hay skeleton.

### 1.5 Visual / UI
- La tabla de umbrales tiene **60 inputs idénticos** sin ninguna visualización de bandas. Cuesta leer "dónde está el ideal" en comparación con los colores del dashboard (verde, ámbar, rojo).
- La unidad va en gris claro pegada al nombre ("%", "°C"), con 2,6:1 de contraste.
- Rustificación: inputs de ~180 px para números de 1–3 dígitos, con las etiquetas "Día desde / Día hasta / Apertura" repetidas en cada fila. El "%" queda flotando entre el input y la ×. "Etapa n" en gris, sin peso.
- La barra sticky usa un gradiente transparente: el contenido se ve por detrás, desprolijo (`cfg-umbral-error.png`).
- Los intervalos tienen anchos fijos inline (`maxWidth: 90px/110px`) y el input recorta "0.08333".

### 1.6 Accesibilidad
- Tabla sin `<caption>` y sin `scope` en los `th`. El `aria-label` de cada celda no incluye la unidad.
- Errores sin `role="alert"` ni `aria-live` y sin `aria-invalid` ni `aria-describedby`.
- `NumberField`: `<label>` sin `htmlFor`; el hint no está enlazado.
- El select de unidad de los intervalos no tiene nombre accesible.
- El tooltip ⓘ es un `title` en un `div`: no se alcanza con teclado ni en pantallas táctiles.
- Contraste insuficiente de hints, unidades, auditoría y feedback verde (§0.5).
- `DemoExpoSwitch` está bien resuelto: `role="switch"`, label asociado, foco visible y error con `role="alert"`.

### 1.7 Responsive (390 px)
- Con el shell actual es inutilizable: la columna de contenido mide 134 px (`shots/mob-configuracion.png`).
- Sin sidebar (`mob-noshell-cfg.png`):
  - la **tabla de umbrales mide 776 px** y hace scroll horizontal en todo `<main>`;
  - en las filas de rustificación, "Etapa n" y "Día desde" quedan en un renglón y el resto se apila desordenado;
  - la barra inferior parte "Restablecer valores de fábrica" en 3 líneas y "Última edición: Mock System" en 4.

### 1.8 Propuestas (Configuración)

**Alta**
1. **Umbrales como "regla visual" por métrica.** Una barra horizontal por métrica con la envolvente (crit) como dominio y las tres zonas pintadas con los colores de estado: rojo fuera de advertencia, ámbar entre advertencia e ideal, verde en el ideal. Cuatro handles arrastrables (adv. mín, ideal mín, ideal máx, adv. máx) con valor visible y edición numérica al hacer foco. Encima se marcan **los umbrales del Motor que miran esa métrica** (para humedad de sustrato: 35, 45, 65, 75 y 80) como marcas de solo lectura con link "editar en Motor". Además:
   - agrupar en Ambiente / Nutrición;
   - mostrar las provisionales colapsadas, con badge "Provisional · no cambia el estado".

   ```
   Humedad de sustrato (%)                      22 ─────────────────────────────── 90
   [ rojo |  ámbar  |██████ verde ██████|  ámbar  | rojo ]
          32        42                  68        80
                      ▲35 crit.riego ▲45 riego  ▲65 obj.    ▲75 bloqueo ▲80 alerta   (Motor, sólo lectura)
   ```
2. **Corregir los intervalos (C1–C3):**
   - mostrar siempre un valor entero con su unidad;
   - convertir sin decimales infinitos y aceptar vacío mientras se tipea;
   - rotular el de sensado como **"Ciclo de lectura y riego"**, con rango 1–6 h validado en el cliente y la explicación "un riego por ciclo, anclado a las 02:00";
   - mover el de evaluación a "Motor".
3. **Una sola fuente para la latencia (C4):** que el detalle del sector la lea de `ConfigOperativa`, o mejor, que venga en `evo` del backend. Revisar si "2 min" es el valor agronómico que se quiere.
4. **Guardado unificado en las tres pantallas de gestión:**
   - barra sticky opaca: "N cambios sin guardar · M errores [Ir al primero] · [Descartar] [Guardar]";
   - "Restablecer de fábrica" pasa a un menú ⋯ por sección, con confirmación modal y diff;
   - `useBlocker` al navegar con cambios.
5. **Sacar el mock de las vistas (§0.2):** el backend expone los valores de fábrica (`GET /configuracion/fabrica`) y el envolvente; el front deja de importar `data/mock/*`.

**Media**

6. **Plan de rustificación como línea de tiempo.** Eje de días (1…N) con bloques por etapa cuya altura es la apertura (una escalera). Se puede arrastrar el borde de un bloque para mover el día de corte y la altura para la apertura. Debe mostrar:
   - un marcador **"Hoy: día 12 · 40 %"** a partir de la fecha de siembra (que hay que exponer: es un dato del backend);
   - los huecos marcados en rojo con "sin plan: la mediasombra no se mueve";
   - la línea de apertura máx. del Motor.

   La tabla queda como vista alternativa compacta, de 3 columnas, sin etiquetas repetidas.
7. **Mover "Dosis máx. de insumo" (C12)** junto a la regla de insumo del Motor, como dato informativo, o rotularla "Tope informativo (no lo usa el motor)" en una sección "Textos y reportes". Eliminar la tarjeta "Límites de riego e insumos".
8. **Sacar el switch Demo Expo** a "Preferencias de la app" o a un menú del usuario (C16).
9. **Errores por campo** (borde rojo + `aria-invalid` + mensaje enlazado), indicando la etapa ("Etapa 2 se solapa con la 1: días 5–7").

**Baja**

10. Índice lateral de secciones con anclas, en desktop.
11. Mostrar "Última edición" por sección, con persona y fecha relativa.
12. Revisar el vocabulario apertura/cobertura en todo el producto.

---

## 2. Hardware (`/hardware`)

### 2.1 Propósito y tareas
Quien la usa es el Administrador o el técnico (HU-18/HU-21). Las tareas son:
- saber qué equipo está caído, con batería baja o averiado;
- planificar el recambio;
- dar de alta equipos nuevos y asociarlos a un sector o macro-zona;
- detectar sectores sin todos sus actuadores, que quedan sin actuación autónoma.

### 2.2 Inventario completo

**Encabezado:** "Estado del hardware" con el subtítulo "{n} dispositivos registrados" (18).
**Intro:** "Monitoreá la flota de hardware del vivero: nodos sensores testigo y actuadores por sector. Detectá equipos con batería baja, señal intermitente, caídos o averiados para ejecutar el recambio preventivo, y registrá el equipamiento nuevo asociándolo a su posición."
**Estados:** "Cargando hardware…" y "No se pudo cargar el hardware: {msg}". No hay reintento.

**KPIs (5 tarjetas, no clickeables):**

| KPI | Valor | Color |
|---|---|---|
| Dispositivos | 18 | negro |
| Operativos | 15 | verde |
| Batería baja | 1 | ámbar |
| Fuera de servicio | 2 | rojo |
| Averiados | 1 | rojo |

No hay KPI de "Señal intermitente" (1).

**Toolbar:**
- Filtros:
  - **Tipo:** Todos / Nodo sensor testigo / Electroválvula / Bomba peristáltica / Mediasombra.
  - **Estado:** Todos / Operativo / Señal intermitente / Fuera de servicio.
  - **Macro-zona:** Todas / MZ-1…MZ-6.
- Contador "**18** dispositivos".
- Feedback verde ("Dispositivo «{serial}» registrado.", "{Tipo} recambiado en {ubicación}.").
- [Registrar dispositivo].

**Formulario de alta (inline, debajo de la toolbar):**
- Título "Registrar dispositivo"; hint "Asociá el equipo a su sector o macro-zona por serial/MAC".
- **Serial / MAC:** texto, placeholder "ej. A4:CF:12:9A:00:07", obligatorio.
- **Tipo:** select, por defecto "Nodo sensor testigo".
- Si es nodo, **Macro-zona:** select con MZ-1…MZ-6, por defecto **MZ-1**. Si es actuador, **Sector:** texto libre con placeholder "ej. MZ-1-003".
- [Cancelar] [Registrar], que pasa a "Guardando…".
- Errores:
  - en cliente: "El serial/MAC es obligatorio.", "Elegí la macro-zona del nodo testigo.", "Ingresá el sector del actuador (ej. MZ-1-003).";
  - del backend o mock: "Ya existe un dispositivo con el serial/MAC «…».", "Tipo de dispositivo inválido.", "El nodo testigo debe asociarse a una macro-zona.", "La macro-zona «…» no existe.", "La macro-zona «MZ-1» ya tiene un nodo testigo.", "El actuador debe asociarse a un sector.", "El sector «…» ya tiene asignada una {Tipo}.", y en backend "El sector «…» no existe.".

**Formulario de recambio:**
- Título "Recambiar {Tipo} · {ubicación}"; hint "La pieza nueva reutiliza el registro y limpia la avería".
- Serial / MAC de la pieza nueva y "Tipo · ubicación" como valor fijo.
- [Cancelar] [Confirmar recambio].
- Errores: "Ingresá el serial/MAC de la pieza nueva.", "El serial/MAC de la pieza nueva es obligatorio.", duplicado, "El dispositivo «…» no existe.".
- Efecto: se limpia la falla y se ponen en null la batería, la señal y el último update.

**Sectores con mapeo incompleto:** hint "La actuación autónoma permanece deshabilitada hasta completar el equipamiento". Filas:
- "MZ-1-003 — Falta: Bomba peristáltica"
- "MZ-3-010 — Falta: Bomba peristáltica · Mediasombra"

Si no hay ninguno, la tarjeta no se muestra.

**Tabla** (columnas DISPOSITIVO · SERIAL / MAC · UBICACIÓN · BATERÍA · SEÑAL · ÚLTIMO UPDATE · ESTADO · acciones):

| ID | Tipo | Serial | Ubicación | Bat. | Señal | Último update | Estado |
|---|---|---|---|---|---|---|---|
| DEV-001 | Nodo sensor testigo | A4:CF:12:9A:00:01 | MZ-1 | 88 % | −62 dBm | hace 20 s | Operativo |
| DEV-002 | Nodo | …:02 | MZ-2 | **16 %** (barra roja) | −78 dBm | hace 30 s | Operativo + **Batería Baja** |
| DEV-003 | Nodo | …:03 | MZ-3 | 75 % | −55 dBm | hace 3 h | **Señal intermitente** |
| DEV-004 | Nodo | …:04 | MZ-4 | 90 % | −60 dBm | hace 1 d | **Fuera de servicio** · [Recambiar] |
| DEV-005 / 006 | Nodo | …:05 / :06 | MZ-5 / MZ-6 | 64 / 82 % | −70 / −66 | hace 45 / 50 s | Operativo |
| DEV-101…109 | EV/BP/MS | EV-1-001… | MZ-1-001…MZ-3-010 | — | — | — | Operativo |
| DEV-110 | Electroválvula | EV-5-042 | MZ-5-042 | — | — | — | **Fuera de servicio** + "Falla Hidráulica" · [Recambiar] |
| DEV-111 / 112 | BP / MS | BP-5-042 / MS-5-042 | MZ-5-042 | — | — | — | Operativo |

Reglas de presentación:
- La barra de batería es roja por debajo de 20, ámbar por debajo de 45 y verde desde 45. El badge aparece por debajo de 20 (`hardware.ts:48`).
- Estado del nodo: intermitente si no reporta hace más de 2 h; fuera de servicio si pasaron más de 24 h, si nunca reportó o si tiene falla.
- **Los actuadores siempre figuran "Operativo"** salvo que tengan falla (`hardware.ts:116-125`).
- "Último update": "hace N s/min/h/d", "Sin reportes" (nodo sin dato) o "—" (actuador).
- [Recambiar] aparece sólo si el equipo está fuera de servicio o tiene falla.
- Vacío: "No hay dispositivos para los criterios seleccionados."

### 2.3 Bugs e inconsistencias

| # | Hallazgo | Evidencia |
|---|---|---|
| H1 | **El KPI "Hardware fuera de servicio" del Panel general no cuenta hardware: cuenta sectores.** Usa `stats.offline`, que es la cantidad de sectores en estado offline, con el subtítulo "Nodos testigo sin reportar". Con la flota de fábrica: Panel **10** vs. Hardware **2**. Después de regenerar la topología (4 × 50): Panel **200** con 0 dispositivos registrados. | `KpiRow.tsx:67-77`; `generators.ts:381`; `dash-tras-regenerar.png`, `hw-tras-regenerar.png` |
| H2 | **Los KPIs no suman.** Operativos (15) + Fuera de servicio (2) = 17 de 18: falta "Señal intermitente" (1), uno de los tres estados. "Batería baja" y "Averiados" son marcas que se superponen con los estados: DEV-002 es Operativo y Batería baja; DEV-110 es Fuera de servicio y Averiado. | `HardwareKpis.tsx:11-17` |
| H3 | **Los actuadores figuran "Operativo" sin ninguna evidencia.** No tienen heartbeat, batería ni señal: las columnas quedan en "—" y el estado se asume. Un actuador desconectado nunca va a aparecer caído. | `hardware.ts:124`; `HardwareTable.tsx:55-69` |
| H4 | **"Recambiar" es el único CTA y aparece tarde.** No hay recambio preventivo para batería baja ni para señal intermitente, aunque la intro promete "recambio preventivo". No hay forma de reportar una avería, dar de baja, reasignar ni corregir un serial. | `HardwareTable.tsx:45,84-88`; intro `HardwarePage.tsx:74-78` |
| H5 | **Después del recambio el equipo sigue en "Fuera de servicio" con [Recambiar].** Se borra `ultimoUpdate` y queda "Sin reportes", así que el KPI no baja y el usuario no sabe si funcionó. Falta el estado "Pendiente de primer reporte". | `hardware.ts:264-278`; `hw-recambio-ok.png` |
| H6 | **Un nodo caído muestra datos viejos como si fueran actuales.** DEV-004 lleva 1 día sin reportar y sigue mostrando "90 %" y "−60 dBm" sin atenuar. | `hw` desk; `HardwareTable.tsx:55-67` |
| H7 | **Dar de alta un nodo testigo es imposible con el seed, y la UI no lo dice.** La macro-zona por defecto (MZ-1) ya tiene nodo, igual que todas: el usuario recién se entera al enviar ("La macro-zona «MZ-1» ya tiene un nodo testigo."). El select no marca qué zonas ya tienen nodo. | `AltaHardwareForm.tsx:21,121`; `hw-alta-error.png` |
| H8 | **El sector del actuador es texto libre.** En demo **se acepta cualquier cosa**: "cualquier cosa" se registró y pasó a la lista de incompletos como si fuera un sector. El backend sí valida (`HardwareService.java:231-232`), pero el usuario tiene que adivinar el formato MZ-z-NNN. | `hardware.ts:224-229`; `hw-alta-ok.png` |
| H9 | **Las macro-zonas del filtro y del alta vienen del mock** y en `http` quedan desactualizadas si cambia la topología (§0.2). | `HardwareFilters.tsx:2,65`; `AltaHardwareForm.tsx:4` |
| H10 | **"Sectores con mapeo incompleto" no respeta los filtros:** con "MZ-2 · Fuera de servicio" la tabla queda vacía y la tarjeta sigue listando MZ-1, MZ-3 y "cualquier cosa". | `HardwarePage.tsx:108`; `hw-filtro-vacio.png` |
| H11 | **Estado vacío equivocado:** sin dispositivos (después de regenerar) dice "No hay dispositivos para los criterios seleccionados.", como si hubiera filtros. No hay CTA para dar de alta ni para limpiar filtros. | `HardwareTable.tsx:37-43`; `hw-tras-regenerar.png` |
| H12 | **En demo, el nodo de una zona tiene un valor en el Panel y otro en Hardware.** Panel: MZ-2 "30 % · −56 dBm"; Hardware: DEV-002 "16 % · −78 dBm". Son dos mocks independientes. En el backend vienen de `ZonaEntity.nodoBattery` y de `DispositivoEntity`: hay que verificar que se mantengan sincronizados. | `shots/desk-dashboard.png` vs `desk-hardware.png` |
| H13 | **Tres umbrales de "sin reportes" que nadie explica:** el motor bloquea a los 90 s (`seguridad.antiguedad-max-lectura`), Hardware pasa a "intermitente" a las 2 h y a "fuera de servicio" a las 24 h. Un nodo puede figurar Operativo en Hardware y "Sin datos" en el Panel. | `hardware.ts:48-50`; catálogo |
| H14 | **"Batería Baja" en Title Case** al lado de "Batería baja" en el KPI. "Falla Hidráulica" viene del seed. "Último update" es spanglish. | `HardwareTable.tsx:77`; `hardware.ts:102`; `data.sql:711` |
| H15 | **El feedback de éxito no desaparece nunca** y queda pegado a la izquierda de "Registrar dispositivo" (`hw-recambio-ok.png`). | `HardwarePage.tsx:85` |
| H16 | **El ID de la ubicación no linkea** ni al sector ni a la zona; tampoco los sectores incompletos. | `HardwareTable.tsx:53`; `SectoresIncompletos.tsx:23` |

### 2.4 UX
- **El orden de la tabla es por ID.** Lo urgente (caído, averiado, batería) queda mezclado entre 12 actuadores en "—", sin orden por severidad ni columnas ordenables.
- **No se ve la flota por macro-zona.** Es la unidad mental del vivero: 1 nodo + 100 sectores × 3 actuadores. A escala real (6 nodos + 1.800 actuadores) esta tabla plana es inmanejable y no tiene paginación ni búsqueda por serial o sector.
- **Los filtros no ofrecen "Batería baja" ni "Averiado"** y los KPIs no filtran al hacer clic.
- **El formulario de alta se abre inline sin mover el foco**, entre la toolbar y los incompletos. No hay alta en lote (escanear MACs) ni completado sugerido desde la tarjeta de incompletos ("Registrar bomba en MZ-1-003").
- **Recambio sin trazabilidad:** no muestra el serial anterior ni pide motivo, y no confirma.
- **La señal en dBm se muestra cruda**, sin escala ("−78 dBm" es débil y nadie lo dice).

### 2.5 Visual / UI
- KPIs grandes pero planos y sin contexto: "1 Batería baja" no dice cuál ni lleva a nada.
- La mitad de la tabla son guiones: columnas de batería, señal y último update vacías para 12 de 18 filas.
- Badges de 11 px con contraste justo; "Falla Hidráulica" como texto rojo suelto, sin ícono.
- [Recambiar] es un botón secundario en una columna sin encabezado y ocupa el mismo lugar visual en cada fila donde aparece.

### 2.6 Accesibilidad
- Los selects de filtro y del alta no tienen nombre accesible (`<label>` sin `htmlFor`).
- `formError` y `feedbackOk` no se anuncian (sin `role="alert"` / `status`).
- La tabla no tiene `caption`; las barras de batería no tienen rol, aunque el % textual compensa.
- El estado se transmite por color y texto (bien), pero los badges de "Batería Baja" no llegan a 4,5:1.
- Al abrir el alta, el foco queda en el botón; al cerrar, no vuelve a "Registrar dispositivo".

### 2.7 Responsive (390 px)
- Con el shell: 134 px de ancho. Los KPIs "Operativos" y "Fuera de servicio" (2ª columna de la grilla de `@media 900px`) **quedan recortados fuera de vista** (`shotsD/mob-hw-viewport.png`: sólo se ven 18, 1 y 1).
- Sin sidebar (`mob-noshell-hw.png`): la tabla **se corta en "UBICACIÓN"** porque `.tableCard { overflow: hidden }` (`Hardware.module.css:63-66`) impide el scroll. **Batería, Señal, Estado y Recambiar no se pueden ver ni tocar en mobile.**

### 2.8 Propuestas (Hardware)

**Alta**
1. **Vista por macro-zona con salud de flota.** Una tarjeta por MZ con:
   - el nodo testigo: batería, señal en barras (excelente / buena / débil), último reporte y estado;
   - una mini-grilla de los 100 sectores con un punto por actuador (EV, BP, MS) en verde, gris (sin registrar) o rojo (avería);
   - contadores "3 sin bomba · 1 avería".

   Al hacer clic en la zona se abre el detalle tabular filtrado. La tabla plana queda como vista "Lista", ordenable y con búsqueda por serial o sector.
2. **KPIs que particionen y filtren:** Operativos / Intermitentes / Fuera de servicio (suman el total) y, aparte, "Alertas de mantenimiento": batería baja y averías. Cada KPI aplica su filtro.
3. **Arreglar el KPI del Panel general (H1):** que lea de `/hardware` (nodos testigo fuera de servicio) o que se renombre "Sectores sin señal".
4. **Ciclo de vida completo del dispositivo.** Estados "Pendiente de primer reporte" (después del alta o el recambio), "Operativo", "Intermitente", "Fuera de servicio" y "Averiado". Acciones por fila en un menú ⋯: Recambiar (también preventivo), Reportar avería, Reasignar, Dar de baja. Recambio con confirmación, serial anterior visible y motivo.
5. **Selector de sector, no texto libre:** combobox con búsqueda (MZ → sector) que muestre qué actuadores ya tiene cada sector. Para nodos, deshabilitar las zonas que ya tienen nodo ("MZ-1 · ya tiene DEV-001").
6. **Tabla scrolleable en mobile** (`overflow-x: auto`) o, mejor, tarjetas por dispositivo a menos de 640 px.

**Media**

7. Para los actuadores, mostrar "Sin telemetría" en vez de "Operativo", o el último ACK de comando cuando exista. Atenuar los valores viejos de los nodos caídos ("90 % · hace 1 d").
8. Desde "Mapeo incompleto", ofrecer [Registrar faltante] con sector y tipo precargados, aplicar los filtros a esa tarjeta y linkear el sector.
9. Estados vacíos distintos: "Todavía no hay equipos registrados [Registrar el primero]" y "Ningún equipo coincide [Limpiar filtros]".
10. Leyenda de umbrales: "Intermitente: sin reporte > 2 h · Fuera de servicio: > 24 h · Batería baja: < 20 %", y alinearla con los 90 s del motor.

**Baja**

11. Alta en lote (pegar una lista de MACs o escanear un QR).
12. Corregir capitalización y spanglish ("Batería baja", "Falla hidráulica", "Último reporte").
13. Toast efímero para el feedback.

---

## 3. Topología (`/topologia`)

### 3.1 Propósito y tareas
Quien la usa es el Administrador (HU-18 CA-01). Las tareas son:
- definir cuántas macro-zonas y cuántos sectores por macro-zona tiene el vivero (operación **destructiva**: borra hardware e historial);
- ajustar cómo se dibuja la grilla en el Panel general (macro-zonas y sectores por fila, sin efecto sobre los datos).

### 3.2 Inventario completo

**Encabezado:** "Topología del vivero"; subtítulo con la topología vigente: "6 macro-zonas · 600 sectores".
**Intro:** "Definí la distribución física del vivero indicando la cantidad de macro-zonas y de sectores por macro-zona, y cómo se muestran en pantalla (macro-zonas y sectores por fila). Arrastrá los controles laterales del preview o ajustá los valores. Cambiar las cantidades regenera la grilla; cambiar solo la disposición no afecta el hardware."
**Tarjeta:** "Topología y disposición"; hint "Los sectores nacen fuera de servicio hasta recibir telemetría".

| Campo | Valor | Rango | Efecto |
|---|---|---|---|
| Macro-zonas | 6 | entero 1–50 | destructivo |
| Sectores por macro-zona | 100 | entero 1–500 | destructivo |
| Macro-zonas por fila | 3 | 1–macro-zonas (se acota solo) | visual |
| Sectores por fila | 10 | 1–sectores (se acota solo) | visual |
| Sectores totales | 600 | calculado | — |

**Preview:**
- Caja con scroll (máx. 62vh) y una tarjeta "MZ-n" por macro-zona, cada una con una grilla de celdas grises.
- **Tope de 120 celdas por tarjeta.**
- Solapa gris en el borde derecho de MZ-1, con `title` "Arrastrá para cambiar los sectores por fila".
- Solapa verde en el borde derecho de la caja, con `title` "Arrastrá para cambiar las macro-zonas por fila".
- Animaciones FLIP y de entrada; se desactivan con `prefers-reduced-motion`.

**Popup de guardado** (fijo abajo al centro, aparece sólo si hay cambios):
- Título "Tenés cambios sin guardar", con hint según el caso: "Cambiar las cantidades regenera la grilla del vivero." o "Se actualizará solo la disposición en pantalla; no afecta el hardware.". Botones [Descartar] [Guardar cambios] ("Guardando…").
- Confirmación: "Confirmá la regeneración" / "Reemplazar la grilla descarta los dispositivos registrados y el historial asociado. No se puede deshacer." Botones [Cancelar] [Confirmar regeneración] ("Regenerando…").
- Errores en rojo dentro del popup:
  - "La cantidad de macro-zonas debe estar entre 1 y 50."
  - "La cantidad de sectores por macro-zona debe estar entre 1 y 500."
  - "Las macro-zonas por fila deben estar entre 1 y N."
  - "Los sectores por fila deben estar entre 1 y N."
  - "El vivero ya tiene una topología cargada. Confirmá la regeneración para reemplazarla."
- Éxito, debajo del preview: "Topología generada: 4 macro-zonas × 50 sectores (200 en total)." o "Disposición guardada: 3 macro-zonas por fila · 14 sectores por fila."

**Estados:** "Cargando topología…" y "No se pudo cargar la topología: {msg}".

### 3.3 Bugs e inconsistencias

| # | Hallazgo | Evidencia |
|---|---|---|
| T1 | **El botón de guardar no se ve hasta que hay cambios, y no hay forma de saber que existe.** El usuario no sabe dónde se "aplica". El popup es `position: fixed` centrado en el **viewport**, no en el contenido: queda corrido respecto de la columna principal y **tapa el preview** (MZ-7/MZ-8 en `topo-confirmar.png`). | `TopologiaPage.tsx:268-324`; `Topologia.module.css:142-158` |
| T2 | **Las solapas de arrastre no se explican.** Son `span`s sin texto visible, sin rol, sin foco y sin teclado; sólo tienen un `title` (hover). La de sectores está únicamente en MZ-1, sin decir por qué. No muestran el valor mientras se arrastra. Con macro-zonas vacío o 0, **las dos solapas se superponen** (`topo-empty.png`). | `TopologiaPreview.tsx:202-227`; recorrido de foco: sólo los 4 inputs son focuseables |
| T3 | **El preview dibuja como máximo 120 celdas por zona y no lo dice.** Con 500 sectores muestra 120, igual que con 120 (`topo-500.png`). | `TopologiaPreview.tsx:24,116` |
| T4 | **Con macro-zonas vacío o 0, el preview dibuja igual "MZ-1"** y "Sectores totales" muestra 0: incoherente. | `TopologiaPreview.tsx:115`; `TopologiaPage.tsx:58-60`; `topo-empty.png` |
| T5 | **La confirmación destructiva no cuantifica nada.** No dice cuántos dispositivos ni cuántos eventos se pierden: en demo se borraron 18 equipos y todos los diagnósticos (el badge de Diagnósticos pasó de 414 a 0). No pide tipear para confirmar. Tampoco avisa que **cambian los IDs** de zonas y sectores (MZ-7… aparecen; MZ-5/6 desaparecen). | `TopologiaPage.tsx:276`; `mockRepository.ts:170-199` |
| T6 | **"Sectores totales" es un `<label>` sin control** y el feedback de éxito se esconde apenas hay un cambio nuevo. | `TopologiaPage.tsx:247-250,264` |
| T7 | **El popup se declara `role="dialog"` sin `aria-modal` ni `aria-label`** y al mismo tiempo `aria-live`: un lector de pantalla anuncia el diálogo entero en cada cambio de input. | `TopologiaPage.tsx:269` |
| T8 | **Los IDs de zona se dibujan "MZ-n" sin nombre ni orientación.** El seed tiene "Macro-zona n · Sector norte/centro/sur" y la topología no permite nombrar zonas ni ver su ubicación física. | `TopologiaPreview.tsx:191`; `topologia.ts:27` |
| T9 | **Hay dos scrolls anidados:** la caja del preview (62vh) adentro de `<main>`. En pantallas bajas hay que scrollear dos contenedores. | `TopologiaPreview.module.css:19-24` |

### 3.4 UX
- **Mezcla una operación destructiva con una cosmética en el mismo formulario y en el mismo botón.** El diseño unificado está en la spec, pero visualmente no hay ninguna separación entre "Estructura física (peligro)" y "Cómo se ve en pantalla".
- **No hay vista "antes / después":** el subtítulo muestra la topología vigente (6 · 600) y el formulario la propuesta (8 · 800), pero no se comparan.
- **El preview no se parece al Panel general**, a pesar de que la spec dice "replica el aspecto": usa celdas grises, sin nombre de zona ni nodo testigo.
- **Falta un camino guiado después de regenerar:** el Hardware queda vacío y no se ofrece "Registrar nodos testigo para las 4 zonas nuevas".

### 3.5 Visual / UI
- La caja del preview tiene `width: fit-content`: a la derecha queda un espacio en blanco enorme (`desk-topologia.png`).
- La solapa verde se confunde con un botón primario recortado.
- Las celdas grises a 1,94:1 sobre el fondo se ven "deshabilitadas", no "vista previa".
- El input de "Sectores por macro-zona" tiene 120 px y la etiqueta de 2 líneas desalinea la fila.

### 3.6 Accesibilidad
- Las solapas no son operables por teclado ni lector de pantalla. Cumplen la spec ("arrastrando… o inputs") sólo porque hay inputs equivalentes, pero no tienen `role="slider"`, `aria-valuenow` ni flechas.
- Diálogo de confirmación sin manejo de foco ni `aria-modal`. "Confirmar regeneración" depende del color rojo.
- El preview no tiene alternativa textual ("Vista previa: 2 filas de 3 macro-zonas, 10 sectores por fila").

### 3.7 Responsive (390 px)
- Con el shell, el formulario queda en una columna de 134 px y el preview es una franja vertical (`shots/mob-topologia.png`).
- Sin sidebar (`mob-noshell-topo-savebar.png`):
  - el popup de guardado pone el título en 3 líneas y el hint **en una columna de una palabra por línea** a la izquierda de los botones;
  - el popup tapa el preview;
  - **en táctil, las solapas de 13–15 px no se pueden usar** (el mínimo recomendado es 44 px).

### 3.8 Propuestas (Topología)

**Alta**
1. **Separar en dos tarjetas con dos acciones:**
   - **"Estructura del vivero"**, con zona de peligro: inputs, diff "Actual 6 × 100 = 600 → Propuesta 8 × 100 = 800", lista de impacto (p. ej. "se eliminarán 18 dispositivos, N eventos de historial y 414 diagnósticos; cambian los IDs MZ-5…MZ-6") y botón rojo "Regenerar grilla…". Abre un modal que pide tipear "REGENERAR".
   - **"Disposición en pantalla"**, sin riesgo: inputs o steppers y preview, con guardado propio y "Restablecer disposición".
2. **Barra de guardado siempre visible, en la columna de contenido** (no `fixed` en el viewport), deshabilitada cuando no hay cambios. Reutilizar la misma barra que Configuración y Motor (§1.8-4).
3. **Controles de disposición explícitos:** steppers con − y + ("3 por fila") y presets (2, 3, 6 por fila). Las solapas pasan a ser un extra, con texto visible ("⇔ sectores por fila: 10"), `role="slider"`, teclado y un área de toque de 44 px.

**Media**

4. **Preview fiel al Panel:** las tarjetas MZ con su nombre, el ícono de nodo testigo y un recuento ("100 sectores"). Si se recorta, decirlo ("mostrando 120 de 500").
5. **Siguiente paso guiado** después de regenerar: ir a Hardware con las zonas nuevas listas para dar de alta sus nodos.
6. **Nombrar macro-zonas** ("MZ-1 · Norte") y, a futuro, su orientación física.

**Baja**

7. Animación del diff (celdas que entran o salen en otro color).
8. Leyenda del preview.

---

## 4. Demo Expo (`/demo-expo`)

### 4.1 Propósito y tareas
Sirve para mostrar en una expo, en vivo y a distancia, la cadena real: dashboard → backend → MQTT → ESP32 (riel) → celular (foto) → IA (diagnóstico). Las tareas son:
- iniciar una pasada;
- seguirla paso a paso;
- cancelarla;
- mostrar fotos y diagnósticos;
- explicar qué pasó si algo falla.

### 4.2 Inventario completo

**Acceso:**
- La pestaña aparece en el sidebar, dentro de "Principal" y después de "Diagnósticos de IA", **sólo si el switch de Configuración está encendido**.
- La ruta existe siempre. Apagada muestra la tarjeta "La sección Demo Expo está desactivada." / "Activala en **Configuración**." (link).
- Mientras lee el switch: "Cargando…".

**Encabezado:** "Demo Expo"; subtítulo "Pasada del riel: del dashboard al ESP32, al celular y a la IA".

**Panel superior:**
- Titular grande, 30 px, con `role="status"`. Variantes:
  - "Consultando el riel…"
  - "Todavía no hubo ninguna pasada", con la bajada "Iniciá una pasada: el riel va a recorrer dos sectores, el celular va a sacar una foto en cada uno y la IA los va a diagnosticar."
  - "Preparando el siguiente paso…"
  - "El riel se está moviendo al sector MZ-1-001…" (o "…a la posición N…")
  - "Esperando al celular para sacar la foto del sector MZ-1-001…"
  - "Sacando la foto del sector MZ-1-001…"
  - "El riel vuelve a home…"
  - "Cancelación en curso: el riel vuelve a home…"
  - "Pasada completa" / "Pasada completa. Esperando el diagnóstico de la IA…" / "Pasada completa: la IA ya diagnosticó las 2 fotos" (o "la foto")
  - "La pasada falló"
  - "Pasada cancelada: el riel volvió a home"
- Badge: "En curso" (azul), "Completada" (verde), "Falló" (rojo), "Cancelada" (gris), con el agregado " · cancelando…".
- [Cancelar] (borde rojo), visible si está en curso y no se pidió cancelar. **No pide confirmación.**
- [Iniciar pasada], que pasa a "Iniciando…" y queda deshabilitado mientras está en curso.
- Barra de progreso de 6 px. Cuenta los pasos OK, OMITIDO y ERROR sobre 5, con el color del estado.

**Banners de error:** el mensaje del backend tal cual ("Ya hay una pasada en curso.", "No hay ningún dispositivo de captura conectado.", "No hay una pasada en curso.") o "No se pudo contactar al backend". Si falló, también `pasada.error`.

**Lista de 5 pasos** (`<ol>`, tarjetas con ícono de 46 px):

| n | Título | Pendiente | En curso | OK | Omitido/Error |
|---|---|---|---|---|---|
| 1 | "Riel → posición 1" | número gris + "Pendiente" | spinner + "Moviendo…" (borde azul) | ✓ "Llegó" + "4 s" | × "Cancelada por el operador" / ⚠ detalle del backend (o "Falló") en rojo |
| 2 | "Foto del sector MZ-1-001" | idem | "Esperando al celular" → "El celular está sacando la foto" → "Sacando la foto…" | ✓ "Foto recibida" + "3 s" + miniatura 160×107 + diagnóstico | idem |
| 3 | "Riel → posición 2" | | "Moviendo…" | "Llegó" | |
| 4 | "Foto del sector MZ-1-002" | | | "Foto recibida" | |
| 5 | "Riel → home" | | "Volviendo a home…" | "En home" + "6 s" | |

- Diagnóstico de cada captura: "● **Esperando diagnóstico de IA** (se analiza tras 1 min sin fotos nuevas)". Cuando llega: "**Clorosis** 87 % [Media] Ver en Diagnósticos de IA" y "**Estrés solar** 91 % [Alta] Ver en Diagnósticos de IA".
- Si la imagen no carga: ícono de cámara.
- Polling: cada 1 s en curso; cada 3 s mientras falte un diagnóstico, hasta 5 min después de terminar.
- Mock: 4 + 3 + 4 + 3 + 6 = 20 s; los diagnósticos llegan 5 s después.

### 4.3 Bugs e inconsistencias

| # | Hallazgo | Evidencia |
|---|---|---|
| D1 | **Desactivada pero accesible por URL**, con una tarjeta suelta que manda a otra página (Configuración agronómica) a buscar un switch al final de un formulario largo. No hay botón "Activar acá". | `DemoExpoPage.tsx:24-33,118-121`; `desk-demo-expo.png` |
| D2 | **La barra llega al 100 % aunque la pasada se haya cancelado** (los OMITIDO cuentan como terminados) y también con "Esperando el diagnóstico de la IA…" (verde lleno con la IA pendiente). | `DemoExpoPage.tsx:39`; `demo-desk-6`, `demo-desk-9` |
| D3 | **[Cancelar] corta un proceso físico sin confirmación** y está a 12 px de [Iniciar pasada]. | `DemoExpoPage.tsx:66-78` |
| D4 | **Vocabulario mezclado:** el titular dice "se está moviendo **al sector** MZ-1-001" y el paso dice "Riel → **posición** 1". Nunca se explica que la posición 1 es MZ-1-001. | `pasadaPresentacion.ts:38,61` |
| D5 | **"Ver en Diagnósticos de IA" lleva a la lista general**, no al diagnóstico ni al sector fotografiado. | `PasoItem.tsx:81` |
| D6 | **"(se analiza tras 1 min sin fotos nuevas)"** queda fijo y no cuenta el tiempo. En la demo tarda 5 s y en real ~1–1,5 min: no hay countdown ni "hace 40 s". | `PasoItem.tsx:68` |
| D7 | **No se ve cuándo fue la pasada:** `iniciadaEn` y `finalizadaEn` existen pero no se muestran. Al volver a la pestaña, "Pasada cancelada…" puede ser de hace horas. | `DemoExpoPage.tsx` |
| D8 | **"Iniciar pasada" deshabilitado sigue siendo el botón más grande** del panel mientras está en curso; el foco se pierde al deshabilitarse. | `DemoExpoPage.tsx:71-78` |
| D9 | **El puntito titilante de "Esperando…" no respeta `prefers-reduced-motion`** (`ybBlink`). El spinner sólo se frena a 3 s, no se detiene. | `PasoItem.module.css:253-260,192-196` |
| D10 | **Prerrequisitos invisibles:** si no hay celular conectado, el usuario se entera **después** de apretar, por un 409. No se ve el estado del riel, del celular, del broker ni del servicio de IA. | `usePasada.ts:88-101` |

### 4.4 UX
- **El "proyectable" se queda corto:**
  - todo vive dentro del shell (sidebar oscuro, topbar con clima y usuario);
  - el ancho está topado en 1100 px y alineado a la izquierda;
  - las fotos miden 160 × 107;
  - el titular, de 30 px, está bien para una laptop pero no para un proyector a 4 m.
- **No hay narrativa de la cadena:** el subtítulo promete "dashboard → ESP32 → celular → IA", pero la UI es una lista de pasos. No se ve **quién** está trabajando en cada momento (backend, ESP32, celular, IA), que es justamente lo que se quiere demostrar.
- **El final es pobre:** con dos diagnósticos con foto, severidad y confianza hay material para un "resultado" grande (foto + diagnóstico + acción sugerida). Hoy son dos renglones de 18 px.
- **Error y cancelación** están bien resueltos en contenido (detalle del backend en rojo, omitidos tachados), pero no muestran en qué eslabón se cortó.

### 4.5 Visual / UI
- Sólida y coherente con el sistema: tokens, íconos de estado claros y borde azul en el paso activo.
- Problemas:
  - la duración "4 s" va en `--faint` (2,6:1);
  - "Llegó" y "Foto recibida" van en `--ok` (3,26:1);
  - "En curso" va en azul `--info`, un color que no aparece en ningún otro lado del producto salvo en el DAG;
  - el titular de 2 líneas ("Pasada completa. Esperando el diagnóstico de la IA…") empuja los botones;
  - en el estado vacío queda un panel solo con mucho blanco debajo.

### 4.6 Accesibilidad
- Bien: titular con `role="status"`, errores con `role="alert"`, `<ol>` semántico, íconos `aria-hidden` con el estado en texto, `alt` en las fotos.
- A mejorar:
  - el titular cambia cada segundo, lo que puede resultar verboso para un lector de pantalla;
  - la barra es `aria-hidden` y no hay un "Paso 2 de 5" textual;
  - el foco se pierde al deshabilitar "Iniciar";
  - [Cancelar] sin confirmación;
  - D9 (movimiento).

### 4.7 Responsive (390 px)
- Con el shell: el titular se parte letra por letra ("Saca / del s / 002…") y los botones quedan fuera de vista (`shotsD/demo-mob-4-foto2.png`).
- Sin sidebar (`mob-noshell-demo-*.png`):
  - [Iniciar pasada] queda recortado a la derecha mientras está en curso;
  - en los pasos con foto, la miniatura de 160 px deja **una columna de ~60 px** para el texto ("Foto / del / sector / MZ-1- / 001") y el diagnóstico se parte en 5 renglones.

  La miniatura tiene que pasar abajo del texto cuando el ancho es chico.

### 4.8 Propuestas (Demo Expo)

**Alta**
1. **Stepper vivo, grande y proyectable.** Un "modo presentación" (botón ⛶ / tecla F) que oculte sidebar y topbar y ocupe todo el ancho. Arriba va un **diagrama de la cadena** con 5 nodos: Dashboard → Backend → ESP32/Riel → Celular → IA. El que está trabajando pulsa y entre nodos se anima el mensaje ("comando MQTT", "orden SSE", "foto REST", "diagnóstico"). Abajo, un **riel horizontal** con Home, Pos. 1 (MZ-1-001) y Pos. 2 (MZ-1-002) y el carro moviéndose según el paso.

   ```
   [Dashboard]──MQTT──▶[ESP32 riel]──SSE──▶[Celular]──REST──▶[Backend]──▶[IA]
                              ●  (activo)

   HOME ●━━━━━━━━━━━━━━━━━━━━━━━━━[📷]━━━━━━━━━━━━━━━━○ Pos 2
                         Pos 1 · MZ-1-001                   MZ-1-002
   ```
2. **Prerrequisitos antes de iniciar.** Checklist "Celular de captura conectado ✓ · Riel en línea ✓ · IA disponible ✓", con el botón deshabilitado y el motivo si falta algo. En el sistema real requiere endpoints de estado (dispositivo de captura / riel), que podrían salir de `nursery/rail/event` y de los dispositivos de cámara.
3. **Arreglar la barra (D2):**
   - segmentada en 5 partes, con el color de cada paso: gris omitido, rojo error;
   - una sexta parte "IA" que se completa recién con los diagnósticos;
   - texto "Paso 3 de 5" accesible.
4. **Resultado final destacado:** dos tarjetas grandes con foto (≥ 360 px, zoom al hacer clic), diagnóstico, confianza en barra, severidad y link al **diagnóstico o sector concreto**.

**Media**

5. **Confirmar la cancelación** ("¿Cancelar la pasada? El riel vuelve a home") y separarla de "Iniciar". Mientras corre, mostrar sólo [Cancelar]; [Iniciar pasada] reaparece al terminar como "Nueva pasada".
6. **Countdown real de la IA:** "La IA analiza 1 min después de la última foto · faltan 0:42".
7. **Timestamps y duración total:** "Iniciada 14:32:05 · duró 21 s".
8. **Vocabulario único:** "Riel → MZ-1-001 (posición 1)".
9. **Desactivada:** reemplazar la tarjeta por un estado vacío con [Activar Demo Expo] en el lugar (si tiene permiso) o redirigir. Y sacar el switch de Configuración agronómica.

**Baja**

10. Historial de las últimas N pasadas de la sesión.
11. Respetar `prefers-reduced-motion` en el puntito y en el spinner.
12. Sonido o vibración opcional al llegar el diagnóstico (modo expo).

---

## 5. Hoja de ruta consolidada (para portar al sistema real)

### Prioridad ALTA
| # | Qué | Pantalla | Toca backend |
|---|---|---|---|
| 1 | Shell responsive (sidebar colapsable / bottom nav < 900 px); tablas con `overflow-x: auto` o tarjetas | todas | No |
| 2 | KPI "Hardware fuera de servicio" del Panel: contar dispositivos (o renombrar) | Panel / Hardware | Puede (si se quiere desde `/hardware`) |
| 3 | Intervalos: arreglar la conversión de unidades, vacío y 0; renombrar "Sensado" a "Ciclo de lectura y riego" con rango 1–6 h validado | Configuración | No (el rango ya existe en el backend) |
| 4 | Latencia de seguimiento: una sola fuente (no hardcodear "2 h") | Sector / Configuración | No (o `evo` desde el backend) |
| 5 | Sacar `data/mock/*` de las vistas: fábrica de configuración, envolvente, zonas para Hardware | Config / Hardware / Topología | Sí: endpoint de fábrica; zonas desde `/topologia` |
| 6 | Umbrales como regla visual con las marcas del Motor superpuestas; provisionales señaladas | Configuración | No (el catálogo ya se lee) |
| 7 | Barra de guardado común (cambios, errores, Descartar, Guardar) + aviso al salir | Config / Topología / Motor | No |
| 8 | Hardware: KPIs que particionen y filtren; estado "Pendiente de primer reporte"; actuadores "Sin telemetría"; selector de sector | Hardware | Parcial (estado pendiente) |
| 9 | Topología: separar estructura (destructiva, con impacto cuantificado y tipear para confirmar) de disposición; guardado visible | Topología | Sí, para contar el impacto |
| 10 | Demo Expo: modo presentación con diagrama de la cadena y riel; prerrequisitos; barra segmentada | Demo Expo | Sí, para los prerrequisitos |

### Prioridad MEDIA
- Plan de rustificación como línea de tiempo con "hoy" (exponer la fecha de siembra) y detección de huecos.
- Hardware agrupado por macro-zona con salud de flota; ciclo de vida completo (reportar avería, reasignar, dar de baja, recambio preventivo).
- Confirmación de cancelación, countdown de la IA, timestamps y links a diagnósticos concretos en Demo Expo.
- Errores por campo con `aria-invalid` / `aria-describedby` / `role="alert"` en todos los formularios.
- Resolver la duplicación Configuración ↔ Motor: "Seguimiento post-acción" y "dosis máx." en un solo lugar, con links cruzados.
- Revisar contraste: oscurecer `--faint` (≥ 4,5:1 sobre `--card`) y usar `--ok-ink`, `--info-ink` y `--crit-ink` para texto.
- Sacar el switch Demo Expo de Configuración agronómica.

### Prioridad BAJA
- Mayúsculas y spanglish ("Frecuencia operativa", "Batería baja", "Falla hidráulica", "Último reporte"); jerga ("Watchdog", "warn/crit" en los mensajes).
- Índice de secciones en Configuración; toasts efímeros para el feedback.
- Nombres de macro-zona editables; alta de hardware en lote.
- Sincronizar los dos mocks del nodo testigo (Panel vs Hardware) y el texto del feed de rustificación con el plan.
- Actualizar la spec `configuracion-agronomica` (10 métricas; límites de riego movidos al Motor).
