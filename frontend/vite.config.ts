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
      '/agent': 'http://localhost:18989',
      '/faq': 'http://localhost:18989',
      '/knowledge-base': 'http://localhost:18989',
      '/user': 'http://localhost:18989'
    }
  }
})
