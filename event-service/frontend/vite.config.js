import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/seat-map/',
  build: {
    outDir: '../src/main/resources/static/seat-map',
    emptyOutDir: true
  },
  server: {
    proxy: {
      '/venues': 'http://localhost:8081',
      '/sections': 'http://localhost:8081',
      '/admin': 'http://localhost:8081'
    }
  }
});
