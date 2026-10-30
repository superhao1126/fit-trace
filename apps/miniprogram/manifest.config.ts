import { loadEnv } from 'vite';
const env = loadEnv(process.env.NODE_ENV || 'production', process.cwd());
import { defineManifestConfig } from '@uni-helper/vite-plugin-uni-manifest';
export default defineManifestConfig({
  name: '循形Fitness',
  description: '循形Fitness 微信训练记录',
  appid: '',
  versionName: '0.1.0',
  versionCode: '100',
  'mp-weixin': {
    appid: env.VITE_WECHAT_APP_ID || '',
    setting: { urlCheck: true },
    requiredPrivateInfos: [],
  },
});
