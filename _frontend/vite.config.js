import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 开发代理: /api → 后端 z-report-bootstrap (默认 8080, 与后端 server.port 对齐)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.ZREPORT_API_BASE || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
