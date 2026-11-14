import { defineConfig, devices } from "@playwright/test";

const isWindows = process.platform === "win32";
const defaultBackendCmd = isWindows
  ? "mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev"
  : "./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev";

const webMode = process.env.E2E_USE_BUILT === "1" ? "start" : "dev";
const webEnv: Record<string, string> = {
  THI360_API_URL: process.env.THI360_API_URL ?? "http://localhost:8080",
  THI360_CTX_SECRET: process.env.THI360_CTX_SECRET ?? "ctx-dev-only-change-me-32-bytes!!",
  THI360_WEB_URL: process.env.THI360_WEB_URL ?? "http://localhost:3000",
  THI360_ADMIN_URL: process.env.THI360_ADMIN_URL ?? "http://localhost:3001",
};

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: {
    timeout: 15_000,
  },
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
  ],
  use: {
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: [
    {
      command: process.env.E2E_BACKEND_CMD ?? defaultBackendCmd,
      cwd: "../backend",
      url: "http://localhost:8080/actuator/health",
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      command: `pnpm ${webMode}`,
      cwd: "../apps/web-student",
      url: "http://localhost:3000",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: webEnv,
    },
    {
      command: `pnpm ${webMode}`,
      cwd: "../apps/web-admin",
      url: "http://localhost:3001",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: webEnv,
    },
  ],
});
