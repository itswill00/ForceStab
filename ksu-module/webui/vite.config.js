import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  define: {
    '__VUE_PROD_DEVTOOLS__': false,
    '__VUE_PROD_HYDRATION_MISMATCH_DETAILS__': false
  },
  plugins: [
    vue(),
    viteSingleFile()
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    target: 'es2020',
    cssCodeSplit: false,
    minify: 'esbuild'
  }
})
