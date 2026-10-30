import eslint from '@eslint/js';
import eslintConfigPrettier from 'eslint-config-prettier';
import vue from 'eslint-plugin-vue';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: ['**/dist/**', '**/node_modules/**', '**/generated/**', 'apps/android/**'],
  },
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['apps/h5/**/*.{ts,vue}', 'apps/miniprogram/**/*.{ts,vue}'],
    languageOptions: {
      globals: { ...globals.browser, uni: 'readonly' },
      parserOptions: { parser: tseslint.parser, extraFileExtensions: ['.vue'] },
    },
    rules: { 'vue/multi-word-component-names': 'off' },
  },
  {
    files: ['apps/miniprogram/src/**/*.{ts,vue}'],
    languageOptions: { globals: { getCurrentPages: 'readonly' } },
  },
  {
    files: ['apps/api/**/*.ts', 'apps/miniprogram/*.config.ts'],
    languageOptions: { globals: globals.node },
  },
  {
    files: ['deploy/**/*.mjs', 'apps/miniprogram/**/*.mjs'],
    languageOptions: { globals: globals.node },
  },
  { files: ['apps/api/test/**/*.mjs'], languageOptions: { globals: { URL: 'readonly' } } },
  eslintConfigPrettier,
);
