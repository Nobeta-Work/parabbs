import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    base: '/auth/',
    server: {
      port: Number(env.VITE_PORT) || 5174,
      strictPort: true,
      proxy: {
        '^/auth/(?:api|oauth2|\\.well-known|userinfo|connect)(?:/|\\?|$)': {
          target: env.AUTH_PROXY_TARGET || 'http://localhost:9000',
          // Keep the browser Host so framework redirects stay on the frontend origin.
          changeOrigin: false,
        },
      },
    },
  }
})
