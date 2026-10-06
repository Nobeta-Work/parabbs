import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_BASE_URL || '/bbs/api'
  const backend = env.BBS_PROXY_TARGET || 'http://localhost:8080'
  return {
    plugins: [vue()],
    resolve: { alias: { '@': path.resolve(__dirname, './src') } },
    server: {
      port: Number(env.VITE_PORT) || 5173,
      strictPort: true,
      proxy: {
        // 保留浏览器 Host，OIDC 回调和短期会话都经过同源开发服务器。
        '/bbs/api': { target: backend, changeOrigin: false },
        ...(apiBase !== '/bbs/api' ? {
          [apiBase]: {
            target: backend,
            changeOrigin: false,
            rewrite: (value: string) => '/bbs/api' + value.slice(apiBase.length),
          },
        } : {}),
      },
    },
    base: '/bbs',
  }
})
