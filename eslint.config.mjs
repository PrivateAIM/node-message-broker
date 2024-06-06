import config from "eslint-config-standard";
import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';

export default [
  ...[].concat(config),
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  {
      includes: [
          "src/**/*.ts",
          "commitlint.config.js",
          "release.config.js",
          "rollup.config.mjs",
          "src/**/*.ts",
          "test/**/*.ts",
          "test/**/*.js",
          "test/**/*.spec.ts"
      ],
      ignores: [
          "**/.*",
          "**/dist/*",
          "**/*.d.ts",
          "node_modules/**/*"
      ]
  }
];
