import { defineUniPages } from '@uni-helper/vite-plugin-uni-pages';
export default defineUniPages({
  easycom: { custom: { '^t-(.*)': '@tdesign/uniapp/$1/$1.vue' } },
  globalStyle: {
    navigationStyle: 'custom',
    navigationBarTitleText: '循形Fitness',
    navigationBarTextStyle: 'black',
    navigationBarBackgroundColor: '#F7F8FA',
    backgroundColor: '#F7F8FA',
  },
});
