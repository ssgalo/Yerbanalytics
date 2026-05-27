# Aclaraciones del Product Backlog

Documento de observaciones, decisiones y supuestos tomados durante la construcción del Product Backlog (`3_ProductBacklog.md`). Todo lo aquí listado surge de los documentos `1_ResumenPreliminar.md` y `2_ModeloDeNegocio.md` y de las decisiones acordadas con el equipo.

## 1. Decisiones acordadas (definidas con el equipo)

1. **Alcance: ecosistema completo.** El backlog NO se limita a la plataforma web. Incluye historias que modelan el comportamiento autónomo del sistema IoT + IA (riego, dosificación, control de mediasombra, inferencia local, sincronización offline).
2. **Rol único "Cliente".** Se unificaron los tres perfiles de cliente del modelo de negocio (productor yerbatero, vivero dedicado y cooperativa) en un solo rol genérico llamado **Cliente**. No se crearon roles separados para Técnico/Instalador ni Administrador.
3. **Definition of Done global.** Hay un único bloque de Criterios de Completado que aplica a TODAS las historias (sección 3.3), en lugar de un DoD por historia.
4. **Prioridad: Alta / Media / Baja.** Y **Status inicial: `To Do`** para todas las historias (backlog sin desarrollar).

## 2. Observación importante sobre la combinación "ecosistema completo" + "rol único Cliente"

Las funcionalidades autónomas del hardware (riego, dosificación, mediasombra, etc.) no tienen un actor humano que las dispare; las ejecuta el sistema solo. Para respetar el **rol único Cliente** sin perder el alcance de ecosistema completo, esas historias se redactaron con la fórmula:

> *"Como Cliente quiero **que el sistema** [riegue / dosifique / ...] para [beneficio]"*

Es decir, el Cliente expresa el valor de negocio y el sistema es el ejecutor dentro de la funcionalidad. Si prefieren modelar al **"Sistema"** como actor explícito (ej. *"Como Sistema quiero..."*), se puede reescribir, pero eso rompería la decisión de rol único. **Avisar si quieren cambiarlo.**

## 3. Sobre el rango de 15 a 20 historias

Se generaron **17 historias**, dentro del rango pedido. Se priorizó la calidad sobre el relleno: no se inventaron historias inútiles. Historias adicionales que se evaluaron y se descartaron o fusionaron para no inflar el backlog artificialmente:

- **Gestión de usuarios / roles:** descartada. Con un único rol "Cliente" no hay administración de roles que justifique una historia.
- **Onboarding / calibración inicial en campo:** descartada. Corresponde al rol Técnico/Instalador, que se decidió NO incluir.
- **Inferencia IA local como historia independiente:** fusionada dentro de #HU-004 (CA-05) y #HU-013, porque es una capacidad transversal y no una funcionalidad de negocio aislada.
- **Acceso multidispositivo:** se mantuvo como #HU-017 con prioridad Baja, ya que el documento lo menciona explícitamente ("desde cualquier dispositivo"), aunque es candidata a fusionarse con #HU-001/#HU-002 si quieren reducir el total.

Si quieren llegar a 18-20, se pueden agregar historias como: gestión de notificaciones por WhatsApp/email, panel de salud del hardware (estado de batería/conectividad de cada nodo), o gestión de múltiples viveros/unidades de producción por Cliente. **No se agregaron por ahora para no asumir alcance no confirmado.**

## 4. Historias marcadas como CORE (con más criterios de aceptación)

Se identificaron como núcleo del negocio (de la propuesta de valor del Modelo de Negocio: detección temprana + respuesta automática + trazabilidad) y por eso llevan 4-5 criterios en lugar del promedio de 3:

- **#HU-004** – Diagnóstico por visión artificial e IA (5 CA).
- **#HU-006** – Riego automático de precisión (4 CA).
- **#HU-007** – Dosificación localizada de insumos (4 CA).
- **#HU-011** – Historial de acciones y trazabilidad (4 CA).
- **#HU-013** – Operación offline y sincronización (4 CA).

El resto mantiene un promedio de 3 criterios de aceptación.

## 5. Supuestos técnicos que conviene validar (no asumidos como definitivos)

Estos puntos se redactaron en los criterios de aceptación de forma genérica para no comprometer decisiones de diseño todavía no tomadas. **Conviene confirmarlos con el equipo / PO:**

1. **Umbrales y parámetros agronómicos:** se asume que serán configurables por el Cliente (#HU-015). Falta definir QUIÉN fija los valores por defecto (¿el ingeniero agrónomo? ¿la empresa?) y qué parámetros son editables por el Cliente vs. fijos del sistema.
2. **Nivel de confianza mínimo de la IA:** #HU-004 (CA-04) asume que por debajo de un umbral el diagnóstico es "no concluyente" y NO dispara acción automática. Falta definir ese umbral y si requiere validación humana.
3. **Inmutabilidad del historial:** #HU-011 (CA-04) asume que los registros de trazabilidad no se pueden modificar. Confirmar si es requisito de auditoría.
4. **Notificaciones:** #HU-010 asume alertas dentro de la plataforma. El Modelo de Negocio menciona WhatsApp como canal; falta definir si las alertas llegan también por WhatsApp/email o solo in-app.
5. **Frecuencia de medición y de recorrido del riel:** #HU-003 y #HU-004 hablan de intervalos "configurados" sin fijar valores. Definir cadencias concretas.
6. **Autenticación:** #HU-001 asume usuario/contraseña con timeout de sesión. No se definió MFA, recuperación de contraseña ni gestión de altas de Clientes (¿la hace la empresa al instalar?).
7. **Análisis económico-financiero:** la sección 2.5 del Modelo de Negocio está incompleta (faltan VAN, TIR, Payback de la planilla de cátedra). No impacta el backlog funcional, pero queda pendiente.

## 6. Trazabilidad: de dónde sale cada historia

| Historia | Origen en la documentación |
| --- | --- |
| #HU-001, #HU-017 | Plataforma web accesible desde cualquier dispositivo (Resumen 1.2, Modelo 2.2.2) |
| #HU-002, #HU-003 | Monitoreo de variables del sustrato/ambiente y nodos testigo (Resumen 1.2, 1.2.1, Alcance técnico) |
| #HU-004, #HU-005 | Modelo de IA y visión computacional sobre riel (Resumen 1.2, objetivos específicos) |
| #HU-006, #HU-007, #HU-008 | Actuación automática: riego, dosificación, mediasombra/rustificación (Resumen 1.2, 1.2.1) |
| #HU-009 | Integración de APIs climáticas externas (Resumen 1.2.1, Modelo 2.2.8) |
| #HU-010, #HU-014 | Alertas y panel centralizado por sectores (Resumen 1.2, Beneficios) |
| #HU-011, #HU-012, #HU-016 | Trazabilidad, historial y seguimiento post-acción (Resumen 1.2.1, Beneficios, Modelo 2.2.2) |
| #HU-013 | Estrategia offline / sincronización posterior (Resumen 1.5 riesgos, Modelo 2.2.2) |
| #HU-015 | Lógica de reglas de negocio y validación agronómica (Modelo 2.2.6, 2.2.7) |
