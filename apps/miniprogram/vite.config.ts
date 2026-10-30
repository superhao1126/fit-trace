import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';
import Pages from '@uni-helper/vite-plugin-uni-pages';
import Manifest from '@uni-helper/vite-plugin-uni-manifest';
export default defineConfig({
  plugins: [
    Pages(),
    Manifest(),
    (typeof uni === 'function' ? uni : (uni as { default: typeof uni }).default)(),
  ],
  esbuild: { tsconfigRaw: { compilerOptions: { target: 'ES2022' } } },
  resolve: {
    alias: {
      '@fit-trace/shared': new URL('../../packages/shared/src/index.ts', import.meta.url).pathname,
    },
  },
});
