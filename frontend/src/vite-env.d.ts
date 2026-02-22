/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_DEV_BYPASS_AUTH?: 'true' | 'false'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
