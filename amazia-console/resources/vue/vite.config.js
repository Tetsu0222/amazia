import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// VITE_BASE_PATH 未設定時は '/'。CloudFront 経由で /console/ 配下に配信する場合は
// ビルド時に VITE_BASE_PATH=/console/ を渡す（フェーズX-3）。
// 末尾スラッシュ必須（Vite/Vue Router の規約）。
const basePath = process.env.VITE_BASE_PATH ?? '/';

export default defineConfig({
  base: basePath,
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE ?? 'http://localhost:8000',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../../public/vue',
    emptyOutDir: true,
  },
});
