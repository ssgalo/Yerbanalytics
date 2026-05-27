# 3. Product Backlog

**Proyecto:** Yerbanalytics - Sistema de Monitoreo Inteligente de Yerba Mate

## Convenciones

- **Rol único:** todas las historias se redactan desde la perspectiva del **Cliente** (productor, vivero o cooperativa unificados). Las funcionalidades de actuación autónoma del sistema se expresan como *"Como Cliente quiero que el sistema..."*.
- **Prioridad:** Alta / Media / Baja.
- **Status:** todas las historias inician en `To Do` (backlog inicial sin desarrollar).
- **IDs:** formato `#HU-0XX`.

---

## 3.1 Tabla resumen del Product Backlog

| ID Historia de usuario | Como | Quiero | Para | Prioridad | Status |
| --- | --- | --- | --- | --- | --- |
| #HU-001 | Cliente | iniciar sesión de forma segura en la plataforma | acceder a la información de mi producción protegida | Alta | To Do |
| #HU-002 | Cliente | visualizar las métricas de sustrato y ambiente | conocer el estado fisiológico de mis plantines | Alta | To Do |
| #HU-003 | Cliente | que el sistema capture periódicamente las variables mediante nodos testigo | inferir el estado de bloques completos sin medir cada tubete | Alta | To Do |
| #HU-004 | Cliente | que el sistema analice las imágenes de los plantines con IA | detectar anomalías propias de la yerba mate en etapa temprana | Alta | To Do |
| #HU-005 | Cliente | consultar los diagnósticos con su nivel de confianza y severidad | entender el estado sanitario de mi producción | Alta | To Do |
| #HU-006 | Cliente | que el sistema ejecute riego por microaspersión solo en los sectores que lo requieran | mantener condiciones óptimas del sustrato sin desperdiciar agua | Alta | To Do |
| #HU-007 | Cliente | que el sistema dosifique nutrientes y fitosanitarios solo en los sectores afectados | tratar el problema sin desperdiciar insumos | Alta | To Do |
| #HU-008 | Cliente | que el sistema controle la mediasombra de forma gradual | ejecutar el proceso de rustificación lumínica controlada | Media | To Do |
| #HU-009 | Cliente | que el sistema integre pronósticos meteorológicos externos | anticipar condiciones de riesgo y ajustar sus decisiones | Alta | To Do |
| #HU-010 | Cliente | recibir alertas cuando el sistema detecta una anomalía o ejecuta una acción crítica | estar informado sin recorrer el vivero | Alta | To Do |
| #HU-011 | Cliente | consultar el historial completo de acciones junto a la condición que las originó | auditar y respaldar mis controles de calidad | Alta | To Do |
| #HU-012 | Cliente | ver el estado del plantín después de cada acción ejecutada | evaluar su efectividad | Media | To Do |
| #HU-013 | Cliente | que el sistema siga operando y registrando datos sin conexión y sincronice luego | no perder protección ni información ante cortes de conectividad | Alta | To Do |
| #HU-014 | Cliente | visualizar el estado de la producción organizado por sectores y macro-zonas | ubicar rápidamente dónde está el problema | Media | To Do |
| #HU-015 | Cliente | configurar los umbrales agronómicos del cultivo | adaptar las decisiones del sistema a las condiciones de mi vivero | Media | To Do |
| #HU-016 | Cliente | exportar reportes de trazabilidad | respaldar auditorías y controles de calidad | Media | To Do |
| #HU-017 | Cliente | acceder a la plataforma desde cualquier dispositivo | supervisar mi producción desde donde esté | Baja | To Do |

> **Total: 17 historias de usuario.** Ver nota sobre el rango 15-20 en `aclaraciones_backlog.md`.

---

## 3.2 Historias de usuario y criterios de aceptación

### Historia de Usuario: #HU-001
*Como Cliente quiero iniciar sesión de forma segura en la plataforma para acceder a la información de mi producción protegida.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un Cliente registrado con credenciales válidas | ingresa usuario y contraseña correctos | el sistema lo autentica y muestra su tablero principal |
| CA-02 | un Cliente que ingresa credenciales incorrectas | intenta iniciar sesión | el sistema rechaza el acceso y muestra un mensaje de error sin revelar cuál dato es inválido |
| CA-03 | un Cliente con sesión iniciada | permanece inactivo por el tiempo configurado | el sistema cierra la sesión automáticamente y solicita reautenticación |

---

### Historia de Usuario: #HU-002
*Como Cliente quiero visualizar las métricas de sustrato y ambiente para conocer el estado fisiológico de mis plantines.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | que existen lecturas recientes de los nodos sensores | el Cliente abre el panel de métricas | el sistema muestra humedad de sustrato, temperatura, humedad ambiental, nivel de nutrientes y radiación/luz con su fecha y hora de medición |
| CA-02 | un conjunto de lecturas históricas | el Cliente selecciona un rango temporal | el sistema muestra la evolución de cada variable en ese período |
| CA-03 | que un nodo sensor no reporta datos dentro del intervalo esperado | el Cliente consulta el panel | el sistema indica que la métrica está desactualizada o no disponible |
| CA-04 | un valor que supera un umbral crítico configurado | se muestra la métrica | el sistema la resalta visualmente como condición de riesgo |

---

### Historia de Usuario: #HU-003
*Como Cliente quiero que el sistema capture periódicamente las variables del sustrato y ambiente mediante nodos testigo para inferir el estado de bloques completos sin medir cada tubete.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un nodo sensor testigo operativo en una macro-zona | se cumple el intervalo de medición configurado | el sistema registra las variables y las asocia a los sectores que esa macro-zona representa |
| CA-02 | un nodo en modo de bajo consumo (deep sleep) | llega el momento de medir | el nodo despierta, toma la lectura, la envía o almacena, y vuelve a reposo |
| CA-03 | un nodo que deja de reportar | transcurre más de un intervalo sin datos | el sistema marca la macro-zona como sin cobertura y lo notifica |

---

### Historia de Usuario: #HU-004 *(CORE)*
*Como Cliente quiero que el sistema analice las imágenes de los plantines con IA para detectar anomalías propias de la yerba mate en etapa temprana.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el sistema de visión sobre riel recorriendo los sectores | captura la imagen de un sector | asocia la imagen al sector y al momento de captura |
| CA-02 | una imagen capturada | el modelo de IA la procesa | clasifica el estado en una de las categorías: sano, estrés solar/quemadura, clorosis por déficit nutricional, daño fúngico o plaga foliar |
| CA-03 | un diagnóstico generado | el modelo lo emite | incluye el nivel de confianza y la severidad de la anomalía |
| CA-04 | un nivel de confianza por debajo del umbral mínimo | el modelo no puede clasificar con certeza | el sistema marca el diagnóstico como no concluyente y no dispara una acción correctiva automática |
| CA-05 | que no hay conectividad | se captura una imagen | el modelo ejecuta la inferencia localmente en el hardware y almacena el diagnóstico para sincronizar luego |

---

### Historia de Usuario: #HU-005
*Como Cliente quiero consultar los diagnósticos emitidos por el modelo con su nivel de confianza y severidad para entender el estado sanitario de mi producción.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un diagnóstico generado por el sistema | el Cliente abre la vista de diagnósticos | visualiza la categoría detectada, el sector afectado, la confianza y la severidad |
| CA-02 | un diagnóstico con imagen asociada | el Cliente lo selecciona | puede ver la imagen del plantín que originó la clasificación |
| CA-03 | un conjunto de diagnósticos | el Cliente filtra por tipo de anomalía o por sector | el sistema muestra solo los diagnósticos que cumplen el filtro |

---

### Historia de Usuario: #HU-006 *(CORE)*
*Como Cliente quiero que el sistema ejecute riego por microaspersión solo en los sectores que lo requieran para mantener condiciones óptimas del sustrato sin desperdiciar agua.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un sector cuya humedad de sustrato cae por debajo del umbral mínimo | el motor de decisiones lo evalúa | ordena activar el microaspersor de ese sector |
| CA-02 | un riego ordenado | se activa la electroválvula maestra y el microaspersor del sector | el sistema riega únicamente ese sector y deja el resto sin intervención |
| CA-03 | un pronóstico de lluvia inminente por encima del umbral configurado | se evalúa una orden de riego | el sistema suspende o pospone el riego para evitar saturación hídrica |
| CA-04 | una acción de riego ejecutada | finaliza | el sistema registra el evento con sector, duración y condición que lo originó |

---

### Historia de Usuario: #HU-007 *(CORE)*
*Como Cliente quiero que el sistema dosifique nutrientes, acaricidas y fungicidas por línea de irrigación solo en los sectores afectados para tratar el problema sin desperdiciar insumos.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un diagnóstico de déficit nutricional, plaga o daño fúngico con confianza suficiente | el motor de decisiones lo procesa | ordena la dosificación del insumo correspondiente en el sector afectado |
| CA-02 | una dosificación ordenada | se activa la bomba peristáltica | aplica el insumo únicamente en la línea de irrigación del sector afectado |
| CA-03 | una acción de dosificación | se ejecuta | el sistema registra insumo, dosis, sector y diagnóstico que la originó |
| CA-04 | un sector sano | se evalúan acciones | el sistema no aplica ningún insumo sobre ese sector |

---

### Historia de Usuario: #HU-008
*Como Cliente quiero que el sistema despliegue o retire la mediasombra de forma gradual para ejecutar el proceso de rustificación lumínica controlada.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un plan de rustificación activo | avanza el cronograma de exposición | el sistema ajusta gradualmente la mediasombra según el nivel de exposición previsto |
| CA-02 | un pronóstico de pico de radiación UV por encima del umbral | se evalúa el control lumínico | el sistema posterga o reduce la exposición para proteger los plantines |
| CA-03 | un ajuste de mediasombra ejecutado | finaliza | el sistema registra la acción y la condición que la motivó |

---

### Historia de Usuario: #HU-009
*Como Cliente quiero que el sistema integre pronósticos meteorológicos externos para anticipar condiciones de riesgo y ajustar sus decisiones.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | la disponibilidad de la API meteorológica | el sistema consulta el pronóstico geolocalizado | incorpora variables como probabilidad de lluvia y radiación UV al motor de decisiones |
| CA-02 | un pronóstico de lluvia o pico UV | el sistema decide una acción de riego o exposición | ajusta la acción según el riesgo previsto |
| CA-03 | una caída o falta de respuesta de la API climática | el sistema necesita decidir | opera con las últimas variables disponibles y registra que decidió sin pronóstico actualizado |

---

### Historia de Usuario: #HU-010
*Como Cliente quiero recibir alertas cuando el sistema detecta una anomalía o ejecuta una acción crítica para estar informado sin recorrer el vivero.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | una anomalía detectada con severidad relevante | el sistema la registra | genera una alerta visible en la plataforma |
| CA-02 | una acción correctiva ejecutada | finaliza | el sistema notifica al Cliente con el detalle del evento |
| CA-03 | una alerta generada | el Cliente la visualiza | puede marcarla como leída/atendida |

---

### Historia de Usuario: #HU-011 *(CORE)*
*Como Cliente quiero consultar el historial completo de acciones ejecutadas junto a la condición que las originó para auditar y respaldar mis controles de calidad.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | una acción ejecutada por el sistema | se registra | queda asociada al sector, fecha/hora, tipo de acción y la detección o condición que la originó |
| CA-02 | el historial registrado | el Cliente lo consulta | puede filtrar por sector, tipo de acción y rango de fechas |
| CA-03 | un evento del historial | el Cliente lo abre | ve la cadena completa: lectura/diagnóstico → decisión → acción ejecutada |
| CA-04 | el historial | los registros se almacenan | no pueden modificarse, garantizando la integridad de la trazabilidad |

---

### Historia de Usuario: #HU-012
*Como Cliente quiero ver el estado del plantín después de cada acción ejecutada para evaluar su efectividad.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | una acción correctiva ejecutada sobre un sector | transcurre el período de seguimiento configurado | el sistema asocia las nuevas lecturas/diagnósticos de ese sector al evento original |
| CA-02 | un seguimiento post-acción | el Cliente lo consulta | compara el estado antes y después de la acción |
| CA-03 | una acción que no mejoró la condición | se evalúa el seguimiento | el sistema lo señala como acción sin efectividad evidenciada |

---

### Historia de Usuario: #HU-013 *(CORE)*
*Como Cliente quiero que el sistema siga operando y registrando datos sin conexión y sincronice al recuperar conectividad para no perder protección ni información.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | una pérdida de conectividad | el hardware necesita decidir | continúa ejecutando monitoreo, inferencia y acciones de forma autónoma local |
| CA-02 | el período sin conexión | el sistema opera | almacena localmente lecturas, diagnósticos y acciones ejecutadas |
| CA-03 | la recuperación de conectividad | se restablece la conexión | el sistema sincroniza los datos almacenados con el servidor sin duplicarlos ni perderlos |
| CA-04 | la sincronización completada | el Cliente accede a la plataforma | visualiza los eventos ocurridos durante el período offline con su marca temporal real |

---

### Historia de Usuario: #HU-014
*Como Cliente quiero visualizar el estado de la producción organizado por sectores y macro-zonas para ubicar rápidamente dónde está el problema.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el mapa/listado de sectores | el Cliente abre la vista | ve el estado sanitario y de actuación de cada sector y macro-zona |
| CA-02 | un sector con anomalía activa | el Cliente lo visualiza | se distingue visualmente de los sectores sanos |
| CA-03 | un sector seleccionado | el Cliente lo abre | ve sus métricas, diagnósticos y acciones asociadas |

---

### Historia de Usuario: #HU-015
*Como Cliente quiero configurar los umbrales agronómicos del cultivo para adaptar las decisiones del sistema a las condiciones de mi vivero.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un parámetro configurable (ej. humedad mínima de sustrato) | el Cliente lo modifica dentro de los rangos permitidos | el sistema usa el nuevo valor en sus próximas decisiones |
| CA-02 | un valor fuera de los rangos permitidos | el Cliente intenta guardarlo | el sistema lo rechaza y explica el rango válido |
| CA-03 | un cambio de configuración guardado | se aplica | el sistema registra quién y cuándo lo modificó |

---

### Historia de Usuario: #HU-016
*Como Cliente quiero exportar reportes de trazabilidad para respaldar auditorías y controles de calidad.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un rango de fechas y sectores seleccionados | el Cliente solicita un reporte | el sistema genera un documento exportable con diagnósticos y acciones del período |
| CA-02 | un reporte generado | se exporta | conserva la trazabilidad condición → decisión → acción de cada evento |
| CA-03 | que no hay datos en el rango solicitado | el Cliente pide el reporte | el sistema informa que no hay registros para ese criterio |

---

### Historia de Usuario: #HU-017
*Como Cliente quiero acceder a la plataforma desde cualquier dispositivo para supervisar mi producción desde donde esté.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un Cliente con sesión válida | accede desde un navegador de escritorio o móvil | la plataforma adapta la visualización al tamaño de pantalla manteniendo las funciones principales |
| CA-02 | una conexión de baja velocidad | el Cliente carga el tablero | la plataforma prioriza la información crítica (alertas y estado de sectores) |

---

## 3.3 Criterios de Completado (Definition of Done)

Consideramos que una historia está **DONE** cuando se cumplen las siguientes condiciones:

- La documentación de la historia y el código tuvieron revisiones de pares.
- Se verificó el Checklist para documentación de User Stories.
- El código de la historia está mergeado en el branch de QA.
- Todos los tipos de tests automatizados que se definieron sobre la historia funcionan correctamente.
- Los CA principales están automatizados contra mocks.
- Se realizó el Test Funcional End to End de la historia apuntando a los servicios reales.
- La historia no tiene bugs.
- La historia fue mostrada y aprobada por el PO.
