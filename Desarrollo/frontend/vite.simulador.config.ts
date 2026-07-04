import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// Sirve simulador.html en la raíz (`/`) para que `localhost:5180` abra directamente el
// simulador y no el index.html del dashboard principal.
function simuladorEnRaiz(): Plugin {
  const rewrite = (req: { url?: string }, _res: unknown, next: () => void) => {
    if (req.url === '/' || req.url === '/index.html') {
      req.url = '/simulador.html';
    }
    next();
  };
  return {
    name: 'simulador-en-raiz',
    configureServer(server) {
      server.middlewares.use(rewrite);
    },
    configurePreviewServer(server) {
      server.middlewares.use(rewrite);
    },
  };
}

// Config del simulador standalone: corre en su propio puerto, aislado del
// dashboard principal, y sólo sirve el panel de simulación (simulador.html).
// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), simuladorEnRaiz()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5180,
    open: true,
  },
  build: {
    outDir: 'dist-simulador',
    rollupOptions: {
      input: fileURLToPath(new URL('./simulador.html', import.meta.url)),
    },
  },
});
