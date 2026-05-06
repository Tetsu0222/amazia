import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Market 顧客 API・住所マスタ API は Core(8080) 直アクセス
      // 本番では CloudFront Behavior `/api/customer/*` で同等の振り分けを行う
      '/api/customer': {
        target: 'http://amazia-core:8080',
        changeOrigin: true,
      },
      // 商品系 API は Console(8000) のプロキシ経由（既存）
      '/api': {
        target: 'http://amazia-console:8000',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
  },
})
