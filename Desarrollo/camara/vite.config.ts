import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * `getUserMedia` exige origen seguro: sin HTTPS no hay cámara, salvo en `localhost`.
 *
 * Orden de resolución de los certificados:
 *   1. `CAMARA_HTTPS_KEY` / `CAMARA_HTTPS_CERT`, si están definidas.
 *   2. `Desarrollo/certs/servidor-{key,}.pem` — los mismos que sirve el backend.
 *   3. Nada: arranca en HTTP y avisa. Sirve para desarrollar en la máquina, pero desde el
 *      iPhone la cámara no va a abrir.
 *
 * Generá los certificados con `Desarrollo/certs/generar-certificados.sh <ip>`.
 */
const CERTS = fileURLToPath(new URL('../certs', import.meta.url));

function https() {
  // Las variables de entorno ganan, para poder apuntar a otros certificados sin tocar código.
  const key = process.env.CAMARA_HTTPS_KEY;
  const cert = process.env.CAMARA_HTTPS_CERT;
  if (key && cert) {
    if (!existsSync(key) || !existsSync(cert)) {
      throw new Error(
        `CAMARA_HTTPS_KEY/CAMARA_HTTPS_CERT apuntan a archivos que no existen:\n  ${key}\n  ${cert}`,
      );
    }
    return { key: readFileSync(key), cert: readFileSync(cert) };
  }

  // Sin variables: se usan los certificados compartidos de `Desarrollo/certs/`, que son los
  // mismos que sirve el backend. Que ande solo importa — si esto no levanta en HTTPS, el
  // iPhone no puede abrir la cámara y no hay forma de darse cuenta hasta ese momento.
  const keyLocal = join(CERTS, 'servidor-key.pem');
  const certLocal = join(CERTS, 'servidor.pem');
  if (existsSync(keyLocal) && existsSync(certLocal)) {
    return { key: readFileSync(keyLocal), cert: readFileSync(certLocal) };
  }

  console.warn(
    '\n  ⚠  Sin certificados: la app va a servirse por HTTP y la cámara NO va a funcionar\n' +
      '     desde el iPhone (getUserMedia exige origen seguro). Generalos con:\n' +
      '       Desarrollo/certs/generar-certificados.sh <ip-de-esta-maquina>\n',
  );
  return undefined;
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5190,
    // El iPhone entra por la IP de la máquina en la LAN, no por localhost.
    host: true,
    // Si el 5190 está ocupado, fallar en vez de saltar a otro puerto en silencio: con la app
    // servida desde un puerto y el teléfono apuntando a otro, el error resultante no se
    // parece en nada a la causa.
    strictPort: true,
    https: https(),
  },
  preview: {
    port: 5190,
    host: true,
    strictPort: true,
    https: https(),
  },
});
