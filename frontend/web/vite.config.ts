import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    // 开发服务器配置
    server: {
      port: Number(env.VITE_PORT) || 5173,
      proxy: {
        // 代理所有/api开头的请求到后端/bbs/api
        [env.VITE_API_BASE_URL || '/api']: {
          target: "http://localhost:8081",
          changeOrigin: true,
        }
      },
    },
    // 构建配置
    base: '/bbs',
  }
})
