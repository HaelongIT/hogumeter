/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // core는 CORS를 설정하지 않았다(1인용·사설망 전제). 개발 중엔 프록시로 우회한다 —
    // 그래야 web 때문에 core 설정을 건드리지 않는다. 배포 시엔 같은 오리진에서 서빙하거나
    // core에 CORS를 추가한다(pre-deploy §C).
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
