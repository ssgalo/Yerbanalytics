# Rediseño de la interfaz (prototipo)

Propuesta de rediseño de **todas las pantallas** del dashboard, armada después de revisar el
sistema actual pantalla por pantalla. Es material de referencia para ir portando cambios al
frontend real (`Desarrollo/frontend/`). **No modifica nada del sistema** y no tiene
funcionalidad: los datos son ilustrativos y nada se conecta al backend.

| Archivo | Qué es |
|---|---|
| `yerbanalytics-rediseno.html` | Prototipo navegable en un solo archivo (fuente e imágenes embebidas). Se abre con doble clic, sin servidor ni internet. |
| `auditorias/` | La revisión del sistema actual que motivó cada cambio: bugs con `archivo:línea`, inventario de datos por pantalla, contraste, responsive y propuestas priorizadas. |

## Cómo se usa el prototipo

- Arranca en el **Índice**: cambios transversales, mapa de pantallas, qué pasar por OpenSpec y
  un orden sugerido para portar.
- La **barra violeta** de abajo es del prototipo (no del diseño): permite saltar a cualquier
  pantalla o estado, abrir las **Notas** de la pantalla (cada nota dice qué cambió, qué había
  antes y qué archivo tocar, y numera la zona a la que se refiere) y **Ver en celular** (390 × 844).
- El **Sistema de diseño** (`#/sistema`) tiene los tokens listos para `src/styles/tokens.css` y
  una tabla de equivalencias con los nombres de hoy (conviene conservar los nombres y cambiar los
  valores, para no tocar cada CSS Module).

## Los datos cuentan una sola historia

Todo el prototipo muestra el mismo momento (martes 6 de octubre, 14:32) y cada pantalla lee la
misma tabla de datos, así que los números cruzan: la pasada del riel de las 09:00 da la hora de
cada foto; los 7 sectores con daño biótico confirmado son los que se dosificaron (y MZ-6-014 queda
bloqueado por el tope diario); las 38 acciones del Panel son las del feed del Historial; MZ-4 riega
por tandas y cada sector sabe si está regado, regando o en cola; la electroválvula de MZ-4-005
falló en la tanda 1 y aparece igual en su sector, en Alertas, en Equipos y en el Inspector. El
seguimiento de cada acción usa el criterio de `reglas_v2` §9 (riego: lectura de verificación a los
30 min y +8 puntos; fitosanitario: 7 días; la mediasombra no se evalúa).

Antes de cerrar se hizo una revisión de QA del propio prototipo (coherencia de datos, estados que
faltaban, accesibilidad, notas que no coincidían con la pantalla) y se corrigió todo lo que
encontró. Lo que no tiene pantalla quedó listado en el Índice, en "Pendiente" y en "Antes de
implementar, pasar por OpenSpec".

## Material relacionado

`Desarrollo/frontend/propuestas-diseno/` tiene, de otra sesión de trabajo, cinco propuestas
estéticas alternativas del Panel general y un reporte UI/UX. Son exploraciones de estilo; este
prototipo es la propuesta completa, pantalla por pantalla.

## Auditorías

| Archivo | Alcance |
|---|---|
| `A-shell-dashboard.md` | Shell (sidebar, barra superior, alertas, átomos, tokens) y Panel general |
| `B-zona-sector-diagnosticos.md` | Macro-zona, detalle de sector y Diagnósticos de IA |
| `C-historial-reglas.md` | Historial y Motor de reglas (parámetros e inspector) |
| `D-config-hardware-topologia-demo.md` | Configuración, Hardware, Topología y Demo Expo |
| `E-requisitos-usuarios.md` | Personas, HU cubiertas y faltantes, decisiones a respetar, vocabulario y arquitectura de información |

Las capturas que citan las auditorías (`shots*/`) se tomaron durante la revisión y no se versionan.
