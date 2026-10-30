import './bootstrap.mjs';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const args = process.argv.slice(2);
const result = spawnSync(
  process.execPath,
  [require.resolve('@dcloudio/vite-plugin-uni/bin/uni.js'), ...args, '-p', 'mp-weixin'],
  {
    stdio: 'inherit',
    // c12 watches config even in production builds; polling also works in restricted macOS workspaces.
    env: { ...process.env, CHOKIDAR_USEPOLLING: 'true' },
  },
);
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
