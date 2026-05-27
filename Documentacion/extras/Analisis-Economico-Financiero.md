# Análisis Económico-Financiero — Yerbanalytics

> **Propósito:** Documentar TODOS los supuestos, decisiones y resultados cargados en la planilla
> `Copia de Análisis Económico Financiero Ejemplo - Aplica a todas las metodologías.xlsx`.
> **Moneda:** Pesos argentinos (ARS), valores 2026.
> **Fecha:** 2026-05-27 · **Estado:** Para debate de equipo.

---

## 0. Resumen ejecutivo

| Indicador | Caso base (moderado) |
|---|---|
| **Inversión inicial** | **$79.000.000 ARS** (~USD 56.000) |
| Resultado neto acumulado Año 0 (startup) | +$12,7M (financiado por la inversión) |
| Resultado neto Año 1 | **−$35,7M** (fase de quema / inversión comercial) |
| Resultado neto Año 2 | **+$79,5M** (rentable) |
| **VAN (tasa de corte 30%)** | **−$41,7M** |
| **TIR** | **−10%** (no recupera en 2 años) |
| **Payback** | **~inicio del Año 3** (justo fuera de la ventana de la planilla) |

**Lectura honesta:** con el escenario **moderado** que elegiste (3→7 viveros), el VAN a 2 años da **negativo**, porque la planilla de cátedra evalúa solo 2 años comerciales y **el repago cae a inicios del Año 3**. Esto **no indica inviabilidad**: es el patrón típico de un negocio de hardware + SaaS intensivo en capital (se invierte fuerte, el Año 1 se quema, el Año 2 ya es rentable). Con un ramp algo más ambicioso o suscripción a valor pleno, el VAN se vuelve **positivo** (ver sensibilidad, sección 5).

---

## 1. Decisiones estructurales (acordadas con el equipo)

| Decisión | Elección | Implicancia |
|---|---|---|
| Moneda | **ARS (pesos 2026)** | Se reemplazaron los valores de pesos viejos (~2015) de la planilla original. |
| Unidad de venta | **Instalación completa de vivero grande (~100.000 plantines)** | 1 venta = topología completa del OLA (1.000 microaspersores, 10 nodos sensores, 1 Gantry). Ticket alto + suscripción. |
| Ramp comercial | **Moderado: 3 viveros Año 1 → 7 Año 2** | Suscripciones acumuladas: 3 (Año 1) → 10 (Año 2). |
| Estrategia de márgenes | **Hardware a margen bajo (~12%) + SaaS rentable** | Coherente con el Business Model: el hardware baja la barrera de entrada; la suscripción es el motor de rentabilidad. |

---

## 2. Anclajes de mercado (investigados, 2026)

| Variable | Valor usado | Fuente |
|---|---|---|
| Tipo de cambio USD/ARS oficial | ~$1.400 | Dólar oficial mayo 2026 (compra 1.380 / venta 1.430) |
| Sueldo dev semi-senior / senior | $1,6M / $3,5M brutos/mes | Glassdoor / relevamientos IT Argentina 2026 |
| Starlink (plan mensual) | ~$65.000/mes | iProfesional / Infobae mayo 2026 |
| Starlink (kit antena) | ~$375.000 | iProfesional / Infobae mayo 2026 |

> Nota: los sueldos cargados son **netos** (la planilla les suma cargas sociales 19% + aguinaldo + vacaciones + merma + ausentismo → factor ~1,56x para llegar al costo laboral total).

---

## 3. Supuestos cargados por hoja

### 3.1 Inversiones — Muebles y equipamiento (setup oficina + lab MVP)
- Escritorios (4), sillas (4), armarios (2), networking → **$1,0M** (Muebles y Útiles).
- 5 notebooks de desarrollo ($6,0M), servidor/NAS dev ($0,7M), router ($60k), **impresora 3D + banco de prototipado MVP** (placas, sensores de prueba) ($1,5M) → **$8,26M** (Equipamiento).
- El **banco de prototipado MVP** captura el costo del prototipo de banco/escala chica, separado de la unidad comercial.

### 3.2 Inversiones — BOM de UNA unidad comercial (vivero ~100k plantines)
Costo total de producir/instalar una unidad = **$23.785.000 ARS** (~USD 17.000):

| Componente | Unitario | Cant. | Subtotal |
|---|---:|---:|---:|
| Sistema Gantry de visión (riel+motores+estructura) | 2.800.000 | 1 | 2.800.000 |
| Conjunto microaspersor + solenoide por sector | 12.000 | 1.000 | 12.000.000 |
| Nodo sensor testigo (ESP32 + humedad/EC/pH/temp/HR/luz + gabinete) | 190.000 | 10 | 1.900.000 |
| Electroválvula maestra (macro-zona) | 48.000 | 10 | 480.000 |
| Bomba peristáltica de dosificación | 65.000 | 10 | 650.000 |
| Módulo de visión + cómputo edge (Jetson + cámara) | 380.000 | 1 | 380.000 |
| Actuador motorizado de mediasombra | 130.000 | 10 | 1.300.000 |
| Gateway / controlador central | 450.000 | 1 | 450.000 |
| Kit energía solar + baterías (respaldo autónomo) | 950.000 | 1 | 950.000 |
| Kit Starlink (antena + instalación) | 375.000 | 1 | 375.000 |
| Tablero eléctrico + cableado + protecciones | 700.000 | 1 | 700.000 |
| Hidráulica (caños, filtros, conectores, tanques) | 1.100.000 | 1 | 1.100.000 |
| Consumibles e imprevistos de instalación | 700.000 | 1 | 700.000 |
| **TOTAL (D44)** | | | **23.785.000** |

> **Decisión técnica:** la fila 29 (Gantry, cantidad 1) se ubicó primero a propósito, porque la planilla calcula el costo unitario como `D44 / C29`. Con C29 = 1, el costo unitario = costo total de la unidad. ✔

### 3.3 RRHH — equipo lean que escala con la facturación
| Rol | Neto/mes | Startup | Año 1 | Año 2 |
|---|---:|:---:|:---:|:---:|
| Líder de Proyecto / PO (fundador) | 900.000 | ✔ | 1 | 1 |
| Desarrollador (full-stack / IoT) | 1.000.000 | ✔ (1) | 1 | 2 |
| Ingeniero de IA / ML (fundador) | 1.200.000 | ✔ | 1 | 1 |
| Técnico de Soporte e Instalación | 900.000 | mes 7 | 1 | 2 |
| Responsable Comercial / Marketing | 1.000.000 | — | — | 1 |

> **Por qué lean:** un startup real no contrata 6 asalariados para vender 3 sistemas. Arranca con los fundadores haciendo todo (incluida la parte comercial el Año 1) y suma personal a medida que la facturación lo justifica. Costo laboral total: **RRHH startup $40,0M · Año 1 $89,7M · Año 2 $185,6M** (la planilla aplica +20% al costo laboral en Año 2 y +20% adicional en Año 3).

### 3.4 Egresos (OPEX)
- **COGS hardware:** 1 unidad piloto en startup ($23,8M); 3 unidades Año 1 ($71,4M); 7 unidades Año 2 ($216,4M, con costo +30% por inflación de la planilla).
- **OPEX recurrente** (anual): alquiler $4,2M, viáticos/movilidad $2,4M→$5,6M (clave: instalaciones y mantenimiento en Misiones), internet oficina $1,2M, **proveedores (nube + API clima + tooling) $1,8M→$3,2M**, marketing (ferias yerbateras, demos) $2,0M→$3,0M, seguros, servicios, etc.
- **Total OPEX:** startup $5,8M · Año 1 $14,0M · Año 2 $21,4M.

> **Supuesto de conectividad:** el **Starlink del cliente** se incluye en el BOM (antena) y el **abono mensual lo paga el vivero** directamente (es infraestructura en su sitio). Por eso NO infla el OPEX de la empresa. La línea "Proveedores" cubre nube, API de clima y herramientas SaaS.

### 3.5 Ingresos
- **Hardware:** precio Año 1 = costo × 1,12 = **$26.639.000** por instalación (Año 2: +30% = $34,6M). Cantidades: 3 / 7.
- **Suscripción mensual:** **$1.800.000 por vivero** (Año 2: $2.340.000). Suscripciones activas: 3 (Año 1) → 10 (Año 2, acumuladas).
- **Capacitación:** $0,6M (Año 1) / $1,0M (Año 2).

> **Justificación de la suscripción ($1,8M/mes):** es valor-basada. Un vivero de 100.000 plantines maneja una producción de decenas de millones de ARS por ciclo; reducir mortalidad e insumos justifica este abono para un sistema industrial con nube + soporte humano + mantenimiento en campo. **Es la principal palanca de rentabilidad** (ver sensibilidad).

### 3.6 Inversión inicial
**$79.000.000 ARS** = capital (aporte de socios) dimensionado para cubrir la quema del período startup (RRHH 7 meses + 1 unidad piloto + setup + OPEX). Sin esto, el Año 0 no cierra.

---

## 4. Resultados del caso base

```
Flujo de fondos:  [ -79,0M ;  +12,7M (fin Año 0) ;  -35,7M (Año 1) ;  +79,5M (Año 2) ]
VAN (30%) = -41,7M     TIR = -10%     Payback ≈ inicio Año 3
```

- **Año 1 da pérdida** ($-35,7M): solo 3 instalaciones contra el costo fijo del equipo + el hardware a margen casi nulo.
- **Año 2 es rentable** (+$79,5M): la suscripción acumulada (10 viveros) y la mayor escala dan vuelta el resultado.
- **Incluso sin descontar**, la suma de 2 años comerciales no alcanza a repagar los $79M iniciales → el repago ocurre **a inicios del Año 3**, fuera de la ventana de la planilla.

---

## 5. Análisis de sensibilidad

| Escenario | VAN | TIR | Comentario |
|---|---:|---:|---|
| **Base** (moderado 3→7, sub $1,8M, tasa 30%) | −41,7M | −10% | Año 2 ya rentable; payback Año 3 |
| Bajar tasa de corte a 10% | −33,9M | −10% | La tasa **no** es el problema: el flujo a 2 años no repaga la inversión |
| Suscripción $2,5M/mes | −4,3M | 26,7% | Casi breakeven: confirma que el SaaS es la palanca |
| **Ramp agresivo (6→12, equipo +45%)** | **+19,2M** | **43,7%** | ✅ La escala amortiza el costo fijo |
| **Combinado (4→9, sub $2,2M, tasa 20%)** | **+27,6M** | **36,3%** | ✅ Realista-optimista |

**Conclusión de sensibilidad:** la viabilidad a 2 años depende de **(a)** acelerar el ritmo de instalaciones (canal cooperativas que financian a sus asociados) y/o **(b)** sostener el precio de suscripción a valor pleno. La tasa de descuento tiene poco impacto porque el problema es el **horizonte corto** vs. el payback de Año 3.

---

## 6. Observaciones sobre la planilla de cátedra (IMPORTANTE)

Detecté comportamientos en las **fórmulas de la planilla** (que NO modifiqué, por respetar la consigna de no tocar celdas dinámicas). Conviene que el equipo los conozca y decida si los consulta con la cátedra:

1. **Signo de impuestos (IIBB e Imp. a débitos/créditos).** Para los períodos con ganancia, la fórmula `Resultado Bruto − B12 − B13` **suma** esos impuestos en lugar de restarlos (porque B12/B13 están almacenados como negativos). Esto **infla** el resultado de los años rentables. *Nota:* aun con este efecto a favor, el VAN del caso base ya da negativo; corregirlo lo haría algo más negativo, sin cambiar la conclusión cualitativa.
2. **Amortizaciones repetidas.** La amortización de 7 meses (`Amortizaciones!C20`) se imputa **en cada uno de los meses 1-7** del Año 0, no una sola vez. Es marginal porque solo afecta muebles/equipamiento (no el hardware del producto).
3. **Horizonte fijo de 2 años comerciales.** La planilla evalúa Año 0 (7 meses de MVP) + Año 1 + Año 2. No permite mostrar el Año 3, donde este negocio ya repaga y acumula. Es la principal limitación para un negocio intensivo en capital.

---

## 7. Hojas dinámicas (NO se cargaron — se calculan solas)
- `Est. Res. Proy.` (totales, impuestos, VAN, TIR), `Cuadro de Resultados`, `Amortizaciones`, `Intra Extranets`: todas fórmulas.
- `Sub-Total Rubros/Subrubros` y `Total por Provincia`: **tablas de referencia estadística** de cooperativas argentinas (datos preexistentes de la cátedra). Son contexto de dimensionamiento de mercado, no inputs. Se dejaron intactas.

---

## 8. Recomendaciones para la defensa
1. **Presentar el caso base con honestidad** + el gráfico de trayectoria (Año 1 quema → Año 2 rentable → Año 3 repaga). Mostrar que el VAN negativo es por el **horizonte corto**, no por inviabilidad.
2. **Acompañar con la sensibilidad**: el negocio es claramente NPV-positivo con ramp agresivo o suscripción plena. Esto demuestra dominio del modelo.
3. **Defender la palanca SaaS**: el recurrente es el corazón de la rentabilidad; el hardware es la puerta de entrada (margen bajo a propósito).
4. **Validar dos números clave** con datos reales antes de la defensa: (a) el **BOM** (cotizar los componentes principales: Gantry, microaspersores+solenoides, nodos sensores) y (b) la **disposición a pagar** del vivero por la suscripción.

---

## 9. Decisiones abiertas para el equipo
- ¿Se mantiene el ramp moderado (honesto pero VAN negativo a 2 años) o se adopta uno más ambicioso vía cooperativas?
- ¿Se valida el precio de suscripción de $1,8M/mes con un vivero real?
- ¿Se consulta a la cátedra sobre los comportamientos de las fórmulas (sección 6)?
- ¿El piloto del Año 0 es una instalación completa (como se modeló) o un prototipo de escala reducida? (cambia el monto de inversión inicial).
