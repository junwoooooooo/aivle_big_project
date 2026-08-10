import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3001,
    strictPort: true,
    proxy: { '/api': 'http://127.0.0.1:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setupTests.js',
    css: true,
    // 기본 10초로는 **부하에 따라 결과가 바뀐다.** LandingPage 18개는 단독 실행에서
    // 전부 통과하지만(14.7초), 전체 스위트를 병렬로 돌리면 몇 개가 10초를 넘겨 실패한다.
    // 그러면 test:baseline 의 allowlist 가 실행마다 달라져 게이트가 무작위로 뒤집힌다 —
    // 「깨진 것」과 「느린 것」을 가르지 못하는 게이트는 아무것도 못 잡는다.
    testTimeout: 30000,
    hookTimeout: 30000,
  },
})
