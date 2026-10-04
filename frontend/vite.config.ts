import { defineConfig } from 'vite';

// Vanilla TS frontend — no React plugin, no JSX.
// Vite only used as dev server + production bundler.
export default defineConfig({
  server: {
    port: 5173,
    strictPort: false,
    host: '127.0.0.1',
    proxy: {
      // Forward /api/* to the mu-server backend during dev
      // (vite dev defaults to 5173, backend runs on :8080)
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    target: 'es2022',
    sourcemap: true,
    minify: 'esbuild',
    cssMinify: true,
    rollupOptions: {
      output: {
        manualChunks: undefined,  // single chunk — bundle should be tiny
      },
    },
  },
});
