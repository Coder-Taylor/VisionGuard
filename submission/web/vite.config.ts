import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: '/vg/',
  server: {
    proxy: {
      '/vg': {
        target: 'http://47.94.146.53',
        changeOrigin: true,
      },
    },
  },
})
