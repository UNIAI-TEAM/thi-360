import { defineConfig, devices } from "@playwright/test";

const isWindows = process.platform === "win32";
const defaultBackendCmd = isWindows
  ? "mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev"
  : "./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev";

const webMode = process.env.E2E_USE_BUILT === "1" ? "start" : "dev";
const apiUrl = process.env.THI360_API_URL ?? "http://localhost:8080";
const webUrl = process.env.THI360_WEB_URL ?? "http://localhost:3000";
const adminUrl = process.env.THI360_ADMIN_URL ?? "http://localhost:3001";
const webPort = new URL(webUrl).port || "3000";
const adminPort = new URL(adminUrl).port || "3001";
const webEnv: Record<string, string> = {
  THI360_API_URL: apiUrl,
  THI360_CTX_SECRET: process.env.THI360_CTX_SECRET ?? "ctx-dev-only-change-me-32-bytes!!",
  THI360_WEB_URL: webUrl,
  THI360_ADMIN_URL: adminUrl,
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
      url: `${apiUrl}/actuator/health`,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      command: `pnpm exec next ${webMode} -p ${webPort}`,
      cwd: "../apps/web-student",
      url: webUrl,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: webEnv,
    },
    {
      command: `pnpm exec next ${webMode} -p ${adminPort}`,
      cwd: "../apps/web-admin",
      url: adminUrl,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: webEnv,
    },
  ],
});
