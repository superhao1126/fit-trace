import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.fittrace.app',
  appName: '循形',
  webDir: 'dist',
  android: {
    // 调试包允许局域网 HTTP，正式包只使用 HTTPS。
    allowMixedContent: process.env.FITTRACE_ANDROID_PRODUCTION !== 'true',
    backgroundColor: '#26735b',
  },
  plugins: {
    SystemBars: { insetsHandling: 'css' },
  },
};

export default config;
