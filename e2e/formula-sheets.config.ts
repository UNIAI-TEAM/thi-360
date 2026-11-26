import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./formula-sheets",
  // Keep Playwright's run artifacts out of ./test-results root so it does not wipe test-results/formula-sheets/html.
  outputDir: "./test-results/formula-sheets-run",
  fullyParallel: false,
  workers: 1,
  timeout: 300_000,
  expect: {
    timeout: 15_000,
  },
  retries: 0,
  reporter: [["list"]],
  use: {
    trace: "off",
    screenshot: "off",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 390, height: 844 },
        deviceScaleFactor: 1,
        colorScheme: "light",
      },
    },
  ],
});
