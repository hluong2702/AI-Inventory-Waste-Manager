import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
    globals: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: [
        'src/auth/**/*.ts',
        'src/guards/**/*.tsx',
        'src/services/**/*.ts',
        'src/stores/**/*.ts',
        'src/utils/**/*.ts',
        'src/components/{DailyActionCenter,Pagination,StateView}.tsx',
        'src/pages/{LoginPage,RegisterPage}.tsx',
      ],
      thresholds: {
        statements: 35,
        branches: 30,
        functions: 40,
        lines: 35,
      },
    },
  },
})
