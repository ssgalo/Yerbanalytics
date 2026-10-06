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
- El **Sistema de diseño** (`#/sistema`) tiene los tokens listos para `src/styles/tokens.css`.

## Auditorías

| Archivo | Alcance |
|---|---|
| `A-shell-dashboard.md` | Shell (sidebar, barra superior, alertas, átomos, tokens) y Panel general |
| `B-zona-sector-diagnosticos.md` | Macro-zona, detalle de sector y Diagnósticos de IA |
| `C-historial-reglas.md` | Historial y Motor de reglas (parámetros e inspector) |
| `D-config-hardware-topologia-demo.md` | Configuración, Hardware, Topología y Demo Expo |
| `E-requisitos-usuarios.md` | Personas, HU cubiertas y faltantes, decisiones a respetar, vocabulario y arquitectura de información |

Las capturas que citan las auditorías (`shots*/`) se tomaron durante la revisión y no se versionan.
