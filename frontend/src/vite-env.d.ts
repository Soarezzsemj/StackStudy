/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base da API; padrão `/api` (mesma origem). */
  readonly VITE_API_URL?: string;
  /** URL opcional para acordar o backend no carregamento. */
  readonly VITE_WAKE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
