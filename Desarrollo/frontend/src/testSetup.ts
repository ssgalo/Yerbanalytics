/* Configuración común de los tests (vitest `setupFiles`).
 *
 * Las búsquedas asíncronas de Testing Library (`findBy*`, `waitFor`) esperan 1 s por defecto. Con la suite completa
 * corriendo en paralelo, y la pantalla Motor de reglas más pesada, ese segundo no siempre alcanza y fallaba un test
 * distinto en cada corrida. 5 s sólo alarga la espera de un test que de todos modos iba a fallar: no oculta errores. */
import { configure } from '@testing-library/dom';

configure({ asyncUtilTimeout: 5000 });
