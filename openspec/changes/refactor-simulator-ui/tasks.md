## 1. Cliente API y Tipos del Simulador

- [ ] 1.1 Extender `Desarrollo/simulador/src/types.ts` con tipos de pasadas, secuencias, catálogo de parámetros y dispositivos de hardware
- [ ] 1.2 Incorporar funciones de API para pasadas (`getPasadaActual`, `iniciarPasada`, `cancelarPasada`) en `Desarrollo/simulador/src/api.ts`
- [ ] 1.3 Incorporar funciones de API para secuencias (`getSecuenciaActual`, `iniciarSecuencia`, `cancelarSecuencia`) en `Desarrollo/simulador/src/api.ts`
- [ ] 1.4 Incorporar consulta del catálogo de parámetros (`getParametrosCatalogo`) en `Desarrollo/simulador/src/api.ts`
- [ ] 1.5 Incorporar registro de hardware en el backend (`registrarHardware`) en `Desarrollo/simulador/src/api.ts`

## 2. Apartado 1: Configuración General

- [ ] 2.1 Refactorizar `Topology.tsx` para integrarse fluidamente en la sección de configuración general
- [ ] 2.2 Ampliar `NewSensor.tsx` con la opción de registrar simultáneamente el dispositivo en la flota de Hardware del backend
- [ ] 2.3 Crear componente `OperationalThresholds.tsx` para visualizar los umbrales y parámetros vigentes leídos del backend
- [ ] 2.4 Unificar en `GeneralConfigSection.tsx` la topología, los sensores simulados, el registro de hardware y los umbrales vigentes

## 3. Apartado 2: Telemetría, Reglas y Clima

- [ ] 3.1 Reorganizar las tarjetas de sensores simulados (`SensorCard.tsx`) para el envío puntual por métrica individual o conjunto de 10 métricas con fecha/hora
- [ ] 3.2 Integrar el botón de pedido de telemetría / pulso (`ReadNowButton.tsx`) y el control de emisión automática (`AutoEmission.tsx`)
- [ ] 3.3 Consolidar el panel de control de reglas demo (`RuleToggles.tsx`) con switches ON/OFF, cooldowns, bypasses y reseteo general
- [ ] 3.4 Consolidar el panel de simulación de pronóstico meteorológico (`WeatherSimulation.tsx`) con presets y restablecimiento a Open-Meteo
- [ ] 3.5 Agrupar los controles en `TelemetryRulesSection.tsx`

## 4. Apartado 3: Automatización de Telemetría para Ejecución de Reglas

- [ ] 4.1 Crear el módulo de cálculo `ruleConditions.ts` con el catálogo de condiciones requeridas por regla y valores de fallback
- [ ] 4.2 Crear el componente `RuleSelector.tsx` con checkboxes por regla agronómica (R-01, R-02, R-04, R-03, ShadingRule, SupplyRule)
- [ ] 4.3 Implementar validación de compatibilidad visual ante combinaciones contradictorias de humedad
- [ ] 4.4 Implementar la orquestación de disparo automático: cooldowns a 0 en demo, inyección de clima si aplica, publicación MQTT al broker y pulso con motor (`POST /api/pulsos`)
- [ ] 4.4.1 Registrar el diagnóstico de IA previo (`captureFlow.ts`: orden → imagen sintética como dispositivo → alta) cuando se selecciona SupplyRule, antes del pulso, con test del módulo puro
- [ ] 4.5 Construir la tarjeta de feedback y traza de ejecución en `RuleAutomationSection.tsx`

## 5. Apartado 4: Sección de la Cámara

- [ ] 5.1 Reorganizar `CameraPanel.tsx` optimizando la disposición del dispositivo de captura enrolado y generación de código de vinculación
- [ ] 5.2 Mejorar la ergonomía de la subida directa auto-simulada de fotografías por sector
- [ ] 5.3 Mantener el flujo de emisión de órdenes de captura cenital por SSE/polling con previsualización
- [ ] 5.4 Mantener el formulario de carga manual de diagnósticos conectado al pipeline del sistema

## 6. Apartado 5: Demo Expo en el Simulador

- [ ] 6.1 Crear el hook `usePasadaSimulator` adaptado al cliente API del simulador con polling dinámico (1 s en curso / 3 s diagnóstico)
- [ ] 6.2 Crear el hook `useSecuenciaSimulator` adaptado al cliente API del simulador
- [ ] 6.3 Implementar el panel de la pasada del riel en vivo (`PasadaSimulatorPanel.tsx`) con titular, barra de progreso, miniaturas y diagnósticos
- [ ] 6.4 Implementar las tarjetas de secuencias directas (`SecuenciasSimulatorPanel.tsx`) para Riego, Mediasombra y Lectura de sensores con o sin motor
- [ ] 6.5 Manejar estados de ocupado y código HTTP 409 cuando pasada y secuencias concurren
- [ ] 6.6 Ensamblar `DemoExpoSection.tsx` en el simulador

## 7. Navegación y Shell del Simulador

- [ ] 7.1 Crear el componente `NavigationTabs.tsx` para alternar entre los 5 apartados con soporte de hash en URL
- [ ] 7.2 Refactorizar `App.tsx` del simulador integrando la barra de estado superior y el renderizado condicional del apartado activo
- [ ] 7.3 Actualizar `styles.css` con estilos modernos, tarjetas legibles, badges de estado y diseño responsivo

## 8. Limpieza en el Frontend Productivo

- [ ] 8.1 Eliminar la ruta `/demo-expo` de `Desarrollo/frontend/src/router.tsx`
- [ ] 8.2 Eliminar el enlace condicional a Demo Expo en `Desarrollo/frontend/src/components/layout/Sidebar.tsx`
- [ ] 8.3 Eliminar el interruptor de Demo Expo en `Desarrollo/frontend/src/features/configuracion/PreferencesPage.tsx` y `DemoExpoSwitch.tsx`
- [ ] 8.4 Eliminar el directorio `Desarrollo/frontend/src/features/demo-expo/` y sus archivos de tests
- [ ] 8.5 Limpiar referencias no utilizadas en `DataRepository` y catálogos de seguridad del frontend

## 9. Pruebas y Validación

- [ ] 9.1 Ejecutar suite de pruebas y linters del frontend (`npm test`, `npm run lint`, `tsc -b`) verificando cero errores
- [ ] 9.2 Ejecutar pruebas unitarias y typecheck del simulador (`npm test`, `npm run typecheck`)
- [ ] 9.3 Ejecutar suite de pruebas del backend (`./mvnw test`) garantizando cero regresiones
- [ ] 9.4 Validar en navegador la operación de los 5 apartados del simulador en `http://localhost:5180`
