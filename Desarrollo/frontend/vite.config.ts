import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // Sin `open`: el navegador lo abre `start-all`, que es el único que sabe cuántas
    // pestañas hacen falta (dashboard y, si está instalado, simulador). Con `open: true`
    // acá se abría una pestaña de más cada vez que se levantaba todo junto.
  },
});
