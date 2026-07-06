/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';
import browserslist from 'browserslist';
import {browserslistToTargets} from 'lightningcss';

// Cible d'anciennes WebView Android : Tailwind v4 émet du CSS moderne
// (oklch(), color-mix(), @property) qui ne se parse que sur Chrome/WebView 111+.
// Lightning CSS transpile vers ces cibles en générant des fallbacks rgb/rgba,
// pour que l'APK s'affiche correctement même sur une WebView système ancienne.
const cssTargets = browserslistToTargets(
  browserslist('Android >= 6, Chrome >= 61, iOS >= 12, Firefox >= 60'),
);

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
    css: {
      transformer: 'lightningcss' as const,
      lightningcss: {
        targets: cssTargets,
      },
    },
    build: {
      cssMinify: 'lightningcss' as const,
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modifyâfile watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
    },
    test: {
      // jsdom est nécessaire pour les tests de composants/hooks React.
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});
