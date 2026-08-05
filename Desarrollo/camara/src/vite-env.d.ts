/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Opcional: sólo si el backend NO corre en el mismo host que sirve esta app. */
  readonly VITE_API_BASE_URL?: string;
  /** Opcional: puerto del conector TLS del backend. Default 8443. */
  readonly VITE_API_PORT?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
