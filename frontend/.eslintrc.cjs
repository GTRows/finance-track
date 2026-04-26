/* eslint-env node */
module.exports = {
  root: true,
  env: { browser: true, es2022: true, node: true },
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    ecmaFeatures: { jsx: true },
    project: ['./tsconfig.json'],
    tsconfigRootDir: __dirname,
  },
  plugins: ['@typescript-eslint', 'react-hooks', 'react-refresh'],
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
  ],
  ignorePatterns: ['dist', 'node_modules', 'public', '*.cjs', '*.config.ts', '*.config.js'],
  settings: { react: { version: '18' } },
  rules: {
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    '@typescript-eslint/no-floating-promises': 'error',
    '@typescript-eslint/consistent-type-imports': ['error', { prefer: 'type-imports' }],
    '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    '@typescript-eslint/no-explicit-any': 'warn',
  },
  overrides: [
    {
      files: ['**/*.test.ts', '**/*.test.tsx'],
      rules: {
        '@typescript-eslint/no-floating-promises': 'off',
        '@typescript-eslint/no-explicit-any': 'off',
      },
    },
  ],
};
