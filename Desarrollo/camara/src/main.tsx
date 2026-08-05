import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import '@/styles/global.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

/*
 * El service worker se registra SÓLO en producción.
 *
 * En desarrollo sería una trampa: su estrategia es cache-first sobre el mismo origen, así que
 * se quedaría con los módulos que sirve Vite y el teléfono seguiría ejecutando código viejo
 * después de cada cambio. Depurar eso desde un iPhone montado en un riel —sin devtools a
 * mano— es exactamente el escenario que esta app trata de evitar.
 *
 * Y como el registro persiste en el dispositivo, no alcanza con no registrarlo: hay que dar
 * de baja el que ya esté instalado y limpiar sus cachés.
 */
if ('serviceWorker' in navigator) {
  if (import.meta.env.PROD) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch((e) => {
        // Sin SW la app funciona igual: sólo pierde el arranque offline del shell.
        console.warn('No se pudo registrar el service worker:', e);
      });
    });
  } else {
    void navigator.serviceWorker.getRegistrations().then(async (registros) => {
      if (registros.length === 0) return;
      await Promise.all(registros.map((r) => r.unregister()));
      if ('caches' in window) {
        const claves = await caches.keys();
        await Promise.all(claves.map((c) => caches.delete(c)));
      }
      console.warn('Service worker dado de baja (modo desarrollo). Recargá para tomar el código nuevo.');
      location.reload();
    });
  }
}
