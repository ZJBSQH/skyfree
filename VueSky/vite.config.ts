import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const serverEnv = loadEnv(mode, '.', '')
  const agentHeaders = serverEnv.AGENTSKY_API_TOKEN
    ? { 'X-AgentSky-Token': serverEnv.AGENTSKY_API_TOKEN }
    : undefined

  return {
    plugins: [vue()],
    test: {
      environment: 'jsdom'
    },
    server: {
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/agentsky-api': {
          target: 'http://127.0.0.1:8765',
          changeOrigin: true,
          headers: agentHeaders,
          rewrite: (path) => path.replace(/^\/agentsky-api/, '')
        }
      }
    }
  }
})
