import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["playwright-report/**", "test-results/**"],
  },
  ...tseslint.configs.recommended,
);
