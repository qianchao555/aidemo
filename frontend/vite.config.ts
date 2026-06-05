import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': resolve(__dirname, 'src') }
  },
  server: {
    port: 5173,
    proxy: {
      '/agent': {
        target: 'http://localhost:18989',
        bypass(req) {
          // SPA 页面路由（如 /agent/chat）不走代理
          if (req.url && !/^\/agent\/(rag-qa|sessions)/.test(req.url)) {
            return '/index.html'
          }
        }
      },
      '/faq': {
        target: 'http://localhost:18989',
        bypass(req) {
          // SPA 页面路由不走代理，回退到 index.html
          if (req.url && !/^\/faq\/(faq|create-faq)/.test(req.url)) {
            return '/index.html'
          }
        }
      },
      '/knowledge-base': 'http://localhost:18989',
      '/quality': {
        target: 'http://localhost:18989',
        bypass(req) {
          if (req.url && !/^\/quality\/(overview|trend|low-rated|blind-spots|department-stats)/.test(req.url)) {
            return '/index.html'
          }
        }
      },
      '/user': 'http://localhost:18989',
      '/auth': 'http://localhost:18989'
    }
  }
})
