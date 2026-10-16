## REMOVED Requirements

### Requirement: Vista Demo Expo
**Reason**: Migrada al simulador de hardware (`Desarrollo/simulador/`) bajo la capacidad `simulador-demo-expo` para cumplir estrictamente con el principio de desacoplamiento de `CLAUDE.md` (§4 y §6.2): ningún control ni interfaz de demostración debe vivir en el frontend productivo.
**Migration**: La pasada del riel en vivo se opera y visualiza desde el apartado "Demo Expo" del simulador en `:5180`.

### Requirement: Secuencias en la vista Demo Expo
**Reason**: Migrada al simulador de hardware (`Desarrollo/simulador/`) bajo la capacidad `simulador-demo-expo`.
**Migration**: Las secuencias directas de actuadores (riego, mediasombra y lectura) se operan desde el apartado "Demo Expo" del simulador en `:5180`.

### Requirement: Interruptor Demo Expo
**Reason**: Al retirarse la vista `/demo-expo` del dashboard de producción, el interruptor de visibilidad en Preferencias y el estado dinámico en el Sidebar quedan obsoletos.
**Migration**: La consola de Demo Expo está siempre disponible en el simulador sin requerir conmutación en la configuración del backend.
