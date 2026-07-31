import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setupTests.js',
    css: true,
    // Node 25+는 내장 webstorage가 기본 활성이고, 그 전역이 jsdom의 localStorage를 가려
    // setupTests.js의 localStorage.clear()가 터진다. 워커에 플래그를 직접 넣어
    // 실행할 때마다 NODE_OPTIONS를 붙이지 않아도 되게 한다.
    // (Vitest 4에서 execArgv는 test 최상위 옵션이다 — v3의 poolOptions 중첩이 아니다.)
    execArgv: ['--no-experimental-webstorage'],
  },
})
