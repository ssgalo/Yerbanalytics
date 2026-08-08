import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// El simulador no levanta su propio servidor de desarrollo: Vite corre en modo middleware
// dentro del Express de `server/index.ts`, para que la UI, la API interna y el proxy al
// backend compartan un solo proceso y un solo puerto. Por eso acá no hay bloque `server`.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: 'dist',
  },
});
