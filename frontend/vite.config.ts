import path from 'node:path'
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const rootDir = fileURLToPath(new URL('.', import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    // `@/shared/api/http` 같은 절대 경로 import를 허용한다.
    alias: {
      '@': path.resolve(rootDir, 'src'),
    },
  },
  server: {
    // 개발 환경에서 쿠키 인증(Credentials)을 쓸 때 CORS 문제를 피하기 위한 proxy.
    // 프론트 `/api/v1/**` -> 백엔드 `http://localhost:8080/api/v1/**`.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
