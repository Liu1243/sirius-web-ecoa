import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

// 修改此处切换后端端口
const BACKEND_PORT = 8080;
const BACKEND_URL = `http://localhost:${BACKEND_PORT}`;

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  build: {
    minify: mode !== 'development',
  },
  server: {
    proxy: {
      '/api': { target: BACKEND_URL, changeOrigin: true },
      '/graphql': { target: BACKEND_URL, changeOrigin: true },
      '/subscriptions': {
        target: `ws://localhost:${BACKEND_PORT}`,
        ws: true,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    coverage: {
      reporter: ['text', 'html'],
    },
  },
  //We define the process.env to avoid 'Uncaught ReferenceError: process is not defined'.
  //Dependencies (such as react-trello) might expect environment variables to be defined (REDUX_LOGGING in this case).
  define: {
    'process.env': { ...process.env, ...loadEnv(mode, process.cwd()) },
  },
}));
