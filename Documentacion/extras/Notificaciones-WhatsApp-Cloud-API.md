# Integración de Notificaciones vía WhatsApp Cloud API

> **Estado:** Documento de análisis técnico para debate de equipo
> **Decisión tomada:** Se usará **WhatsApp Cloud API** (oficial de Meta) como canal de notificaciones/alertas, tanto para el MVP como para el producto final. Se descartó Telegram.
> **Contexto:** Tesis de Ingeniería Informática — Proyecto Yerbanalytics (monitoreo inteligente de plantines de yerba mate).
> **Última actualización:** 2026-05-27

---

## 0. Resumen ejecutivo

La complejidad de WhatsApp **no está en el código** (enviar un mensaje son ~15 líneas), sino en el **modelo de negocio y la burocracia de Meta** que lo rodea. Este documento desarma esa complejidad en bloques concretos para poder planificar la implementación y justificar las decisiones de arquitectura ante el tribunal.

Puntos críticos que el equipo debe asumir:

- WhatsApp **no es Telegram**: no existe el "bot que le manda a cualquier número". Hay reglas estrictas de consentimiento (opt-in), ventana de 24h y plantillas pre-aprobadas.
- Las alertas del sistema (estrés hídrico, daño fúngico, etc.) son mensajes **iniciados por el negocio** → **obligatorio usar plantillas (templates) aprobadas, categoría Utility**.
- Requiere planificar tiempos: la **verificación de negocio** tarda de 2 a 10 días hábiles.
- Decisión de arquitectura clave a debatir: **integración directa con Cloud API vs. usar un proveedor (BSP)**.

---

## 1. El modelo mental: la jerarquía de entidades de Meta

WhatsApp Cloud API no es "una API y ya". Es una pila de entidades anidadas que hay que crear y conectar **en orden**:

```
Meta Business Account        (el negocio/proyecto en Business Manager)
   └── Meta App               (la "aplicación" de desarrollador, tipo Business)
         └── WhatsApp Business Account — WABA   (la cuenta de WhatsApp)
               └── Phone Number                  (número dedicado + Phone Number ID)
                     └── Message Templates       (las plantillas aprobadas)
```

Cada entidad tiene un **ID** que se usa en las llamadas a la API:
- `App ID`
- `WABA ID` (WhatsApp Business Account ID)
- `Phone Number ID`

> **Para la tesis:** esta jerarquía da material directo para un diagrama en el marco teórico / arquitectura.

---

## 2. El número de teléfono dedicado (decisión irreversible)

- El número que se conecta a la API **se "consume"**: deja de funcionar en la app normal de WhatsApp.
- Para arrancar, Meta provee un **número de test/sandbox GRATIS**, pero solo permite enviar a números **pre-registrados** (hasta 5).
  - **Para el MVP esto es ideal:** se registra el número del productor de prueba y listo, sin costo ni verificación.
- Para producción se necesita un **número propio dedicado**.
- ⚠️ Cuidado con los *"555 numbers"* de onboarding rápido: **no son migrables** después → no sirven para producción permanente.

---

## 3. Autenticación — GOTCHA #1 (el error más común)

- Al arrancar, Meta entrega un **token temporal que dura 24 HORAS**.
- Mucha gente monta todo, funciona en la demo, y al día siguiente "se rompió". **Causa: el token expiró.**

**Para producción se necesita un Permanent Access Token vía System User:**

1. Business Settings → **System Users** → crear uno.
2. Asignarle la **WABA** con permisos de WhatsApp.
3. Generar el token (que **no expira**).
4. Guardarlo en `.env` — **NUNCA** en el código ni en el repositorio.

**Buenas prácticas de seguridad (puntos valorables en la tesis):**
- Usar siempre System User token.
- Token en variables de entorno, jamás expuesto.
- Limitar permisos solo a WhatsApp.
- Rotar el token solo si se compromete.

---

## 4. Webhooks — para RECIBIR (no solo enviar)

Enviar es un `POST` simple. Pero un sistema serio necesita **webhooks**: Meta hace un `POST` a **nuestro servidor** cuando ocurre un evento (mensaje entrante, o estado de entrega: enviado / entregado / leído / fallido).

**Validación inicial del webhook:**
- Al registrar la URL, Meta envía un `GET` con `hub.mode`, `hub.verify_token` y `hub.challenge`.
- El endpoint debe validar el token y **responder con el valor de `hub.challenge`**. Si no, Meta no valida el webhook.

**Requisitos:**
- URL **pública HTTPS**.
- Desarrollo local: usar `ngrok` (o similar) para exponer el localhost.
- Producción: servidor con certificado SSL válido.

> **Relación con el OLA:** los webhooks permiten confirmar si la alerta **realmente llegó** al productor → alimenta el objetivo de **trazabilidad y registro de eventos** del sistema.

---

## 5. Plantillas (Templates) — el corazón del caso de uso

Las alertas agronómicas son mensajes **iniciados por el negocio**, fuera de toda conversación → **OBLIGATORIO usar plantillas pre-aprobadas por Meta**.

**Reglas a respetar:**

| Aspecto | Regla |
|---|---|
| **Categoría** | **Utility** (utilitario). NO son marketing. Categorizar mal → rechazo o penalización. |
| **Variables** | Formato exacto `{{1}}`, `{{2}}`. Usar `{1}` o `{{1}` → **rechazo automático**. |
| **Contenido** | Claro y específico. Se rechaza lo vago, spammy, con mayúsculas excesivas o medios no soportados. |
| **Aprobación** | De minutos a horas (a veces más). |

**Ejemplo de plantilla Utility para una alerta:**

```
Alerta en sector {{1}}: se detectó {{2}}.
Acción ejecutada: {{3}}.
```

> El **contenido dinámico** de cada alerta va en las variables; la **estructura** va pre-aprobada. Hay que diseñar las plantillas con esto en mente desde el principio.

---

## 6. La ventana de servicio de 24 horas

- Si el usuario (productor) **nos escribe** → se abre una ventana de **24 hs** en la que se le puede enviar **cualquier** tipo de mensaje (y reinicia el contador con cada mensaje suyo).
- Cuando la ventana **se cierra** → solo se pueden enviar **plantillas aprobadas**.
- **Costos (importante para el caso de un único productor):**
  - Las plantillas **Utility son gratis** si se envían dentro de una ventana abierta.
  - Meta otorga **1.000 conversaciones de servicio gratis por mes** por cada WABA.
  - → Para un solo productor (o pocos), el costo es prácticamente **cero**.

---

## 7. Límites de mensajería, tiers y Quality Rating

**Tiers de mensajería:**

| Estado | Límite |
|---|---|
| Sin verificar negocio | **250** mensajes / 24 hs |
| Tier 1 (verificado) | 1.000 clientes únicos / 24 hs |
| Tier 2 | 10.000 / 24 hs |
| Tier 3 | 100.000 / 24 hs |
| Tier 4 | Ilimitado |

- El escalado de tiers es **automático** si se mantiene buena calidad y volumen.

**Quality Rating (Verde / Amarillo / Rojo):**
- Se calcula según **bloqueos y reportes** de los usuarios en los últimos 7 días.
- Si los usuarios bloquean/reportan → baja el rating → bajan los límites → Meta puede **suspender el número**.
- Por eso el **opt-in** y la **relevancia** de los mensajes son críticos.

**Throughput técnico:**
- ~**80 mensajes/segundo** por número por defecto (ampliable hasta 1.000 MPS en tier ilimitado).
- Para la escala del proyecto (uno o varios productores) **sobra ampliamente**.

---

## 8. Verificación de negocio (planificar el tiempo)

- Para superar el límite de 250 mensajes/24h se requiere **verificar el negocio** en Meta Business Manager.
- Pide **documentación legal** (CUIT/Tax ID, comprobantes, etc.).
- Tarda de **2 a 10 días hábiles**.
- ⚠️ **No dejar esto para la última semana.** Con respaldo institucional de la facultad o una empresa es viable, pero hay que planificarlo.

---

## 9. Decisión de arquitectura: Cloud API directo vs. BSP

Esta es **la decisión más importante a debatir y justificar en la tesis.**

| Criterio | **Cloud API directo (Meta)** | **BSP (Twilio, 360dialog, etc.)** |
|---|---|---|
| Costo | Solo lo de Meta | Markup del proveedor encima |
| Control | Total | Capa de abstracción del proveedor |
| Esfuerzo de ingeniería | Construimos webhooks, manejo de templates, tokens, compliance | Infra + UI ya provistas |
| Aprendizaje del funcionamiento | **Sí, a fondo** | Lo oculta el proveedor |
| Verificación previa de templates | Manual (nuestra) | El BSP la chequea antes de enviar |
| **Valor académico** | **ALTO** | Bajo |

**Recomendación (a debatir):** para una **tesis de ingeniería informática**, conviene **Cloud API directo**. El valor de la tesis está en demostrar que el equipo entiende y construye la integración, no en delegarla. El BSP es la elección comercial pragmática; el Cloud API directo es la que **hace aprender** y se puede **defender ante el tribunal**.

---

## 10. Recomendación de diseño (Arquitectura Hexagonal)

**No** dispersar las llamadas a la API de Meta por todo el código. **Aislar WhatsApp detrás de una abstracción:**

- Definir un **puerto/interfaz** `NotificationService` (ej: `enviarAlerta(destino, mensaje)`).
- Implementar un **adaptador** `WhatsAppCloudAdapter`.
- La lógica de alertas agronómicas **NO debe saber** que abajo hay un WhatsApp.

**Beneficios:**
- Si Meta cambia la API (ya cambió el pricing en julio 2025), se toca **un solo adaptador**.
- Permite tener un adaptador de prueba/mock para tests sin pegarle a Meta.
- Demuestra **madurez de diseño** ante el tribunal.

```
[ Motor de decisiones / Alertas ]
              │
              ▼
   «interfaz» NotificationService          ← la lógica solo conoce esto
              │
   ┌──────────┴───────────┐
   ▼                      ▼
WhatsAppCloudAdapter    MockNotifier (tests)
   │
   ▼
WhatsApp Cloud API (Meta)
```

---

## 11. Checklist de implementación

- [ ] Crear Meta Business Account + App (tipo Business)
- [ ] Agregar producto WhatsApp → obtener WABA ID y Phone Number ID (sandbox)
- [ ] Registrar números de prueba en el sandbox (MVP)
- [ ] Generar **Permanent Access Token** vía System User
- [ ] Guardar credenciales en `.env` (fuera del repo)
- [ ] Crear y aprobar plantillas **Utility** para cada tipo de alerta
- [ ] Implementar endpoint de **webhook** (validación `hub.challenge` + recepción de estados)
- [ ] Exponer webhook con HTTPS (`ngrok` en dev)
- [ ] Diseñar la abstracción `NotificationService` + `WhatsAppCloudAdapter`
- [ ] (Producción) Conseguir número dedicado + **verificación de negocio** (2–10 días)

---

## 12. Pendientes / decisiones abiertas para el equipo

1. **Stack/lenguaje del backend** → define el SDK o cliente HTTP a usar y el esqueleto del adaptador.
2. **Cloud API directo vs. BSP** → confirmar la decisión (recomendado: directo, por valor académico).
3. **Modelo de opt-in** → cómo se obtiene y registra el consentimiento del productor.
4. **Catálogo de plantillas** → definir qué alertas tendrán plantilla y su texto/variables.
5. **Número de producción** → definir quién aporta el número dedicado y cuándo se inicia la verificación.

---

## 13. Fuentes

- [WhatsApp Cloud API — Get Started (Meta for Developers)](https://developers.facebook.com/documentation/business-messaging/whatsapp/get-started)
- [Sending messages — Meta Developer Platform](https://developers.facebook.com/documentation/business-messaging/whatsapp/messages/send-messages)
- [Access tokens — Meta Developer Platform](https://developers.facebook.com/documentation/business-messaging/whatsapp/access-tokens/)
- [Business phone numbers — Meta Developer Platform](https://developers.facebook.com/documentation/business-messaging/whatsapp/business-phone-numbers/phone-numbers)
- [WhatsApp Business Messaging Limits — Meta Developer Platform](https://developers.facebook.com/documentation/business-messaging/whatsapp/messaging-limits)
- [WhatsApp Business Messaging Policy](https://business.whatsapp.com/policy)
- [WhatsApp Business API Pricing 2026 — respond.io](https://respond.io/blog/whatsapp-business-api-pricing)
- [WhatsApp API Message Templates: Complete Guide 2026 — gurusup](https://gurusup.com/blog/whatsapp-api-message-templates)
- [WhatsApp API Template Rejections — chakrahq](https://chakrahq.com/article/whatsapp-api-template-rejections-issues-guidelines-coexistence/)
- [BSP vs TSP for WhatsApp API — Heltar](https://www.heltar.com/blogs/what-is-the-difference-between-bsp-and-tsp-for-whatsapp-api)
