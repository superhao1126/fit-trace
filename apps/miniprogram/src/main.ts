import { createSSRApp } from 'vue';
import '@tdesign/uniapp/theme.css';
import App from './App.vue';
export function createApp() {
  return { app: createSSRApp(App) };
}
