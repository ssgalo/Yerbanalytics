/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DATA_SOURCE?: 'mock' | 'http';
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_MOCK_SEED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
