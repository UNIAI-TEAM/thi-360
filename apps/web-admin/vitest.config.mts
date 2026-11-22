import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const FAKE_CLIENT_TESTS = [
  "src/features/content/**/*.test.{ts,tsx}",
  "src/features/questions/**/*.test.{ts,tsx}",
  "src/features/question-import/**/*.test.{ts,tsx}",
];

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    testTimeout: 15_000,
    hookTimeout: 15_000,
    restoreMocks: true,
    unstubEnvs: true,
    projects: [
      {
        extends: true,
        test: {
          name: "fake-client",
          setupFiles: ["./src/test/setup.ts", "./src/test/setup-fake-client.ts"],
          include: FAKE_CLIENT_TESTS,
        },
      },
      {
        extends: true,
        test: {
          name: "default",
          setupFiles: ["./src/test/setup.ts"],
          include: ["src/**/*.test.{ts,tsx}"],
          exclude: [...FAKE_CLIENT_TESTS, "**/node_modules/**"],
        },
      },
    ],
  },
});
