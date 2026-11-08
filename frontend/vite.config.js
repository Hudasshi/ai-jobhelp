import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 开发阶段通过 Vite 代理访问后端，浏览器只请求当前前端地址，
 * 因此不需要后端额外开放 CORS；生产部署应由 Nginx 做同样的 /api 转发。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
