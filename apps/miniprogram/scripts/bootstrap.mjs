// uni CLI reads manifest before Vite runs. Seed it from the same config sources.
import { writeFileSync } from 'node:fs';
import { loadConfigFromFile } from 'vite';
const env = { command: 'build', mode: process.env.NODE_ENV || 'production' };
const manifest = await loadConfigFromFile(env, 'manifest.config.ts');
const pages = await loadConfigFromFile(env, 'pages.config.ts');
writeFileSync(new URL('../src/manifest.json', import.meta.url), JSON.stringify(manifest.config));
writeFileSync(
  new URL('../src/pages.json', import.meta.url),
  JSON.stringify({ ...pages.config, pages: [{ path: 'pages/index/index' }] }),
);
