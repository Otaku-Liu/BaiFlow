import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 头像静态资源：开发环境由后端 AvatarWebConfig 映射服务
      '/avatars': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
