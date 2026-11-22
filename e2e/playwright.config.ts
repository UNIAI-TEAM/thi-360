import { defineConfig, devices } from "@playwright/test";

const isWindows = process.platform === "win32";
const defaultBackendCmd = isWindows
  ? "mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev,e2e"
  : "./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev,e2e";

const apiUrl = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const webUrl = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const adminUrl = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");

function portFromUrl(rawUrl: string, fallback: string): string {
  try {
    const parsed = new URL(rawUrl);
    return parsed.port || fallback;
  } catch {
    return fallback;
  }
}

const apiPort = portFromUrl(apiUrl, "8080");
const webPort = portFromUrl(webUrl, "3000");
const adminPort = portFromUrl(adminUrl, "3001");

const webMode = process.env.E2E_USE_BUILT === "1" ? "start" : "dev";
const webEnv: Record<string, string> = {
  THI360_API_URL: apiUrl,
  THI360_CTX_SECRET: process.env.THI360_CTX_SECRET ?? "ctx-dev-only-change-me-32-bytes!!",
  THI360_WEB_URL: webUrl,
  THI360_ADMIN_URL: adminUrl,
  NEXT_PUBLIC_THI360_WEB_URL: webUrl,
  NEXT_PUBLIC_THI360_ADMIN_URL: adminUrl,
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
      env: {
        ...process.env,
        THI360_API_PORT: process.env.THI360_API_PORT ?? apiPort,
        THI360_WEB_URL: webUrl,
        THI360_ADMIN_URL: adminUrl,
        THI360_VNPAY_MOCKPAYURL: process.env.THI360_VNPAY_MOCKPAYURL ?? `${apiUrl}/mock-vnpay`,
        // Relax the per-IP login rate limit (default 20/min) so the suite's
        // platform logins cannot trip RateLimitFilter. Applies only to the
        // backend spawned for e2e runs.
        THI360_SECURITY_RATELIMIT_LOGINPERIPLIMIT: process.env.THI360_SECURITY_RATELIMIT_LOGINPERIPLIMIT ?? "200",
        // Relax the per-user live-session cap (default 5,
        // thi360.security.session.max-per-user): one run logs `platform` in
        // from several specs and SessionStore evicts the oldest session
        // beyond the cap, which revoked the cached helper session.
        THI360_SECURITY_SESSION_MAXPERUSER: process.env.THI360_SECURITY_SESSION_MAXPERUSER ?? "50",
        // Relax the per-IP register (E19: 30/hour, 100/day) and forgot/reset (E16: 60/min) limits: the account-*
        // specs create a fresh account per test and the suite may be rerun within the hour.
        THI360_REGISTER_IP_HOUR_LIMIT: process.env.THI360_REGISTER_IP_HOUR_LIMIT ?? "1000",
        THI360_REGISTER_IP_DAY_LIMIT: process.env.THI360_REGISTER_IP_DAY_LIMIT ?? "5000",
        THI360_FORGOT_PER_IP_LIMIT: process.env.THI360_FORGOT_PER_IP_LIMIT ?? "600",
        THI360_RESET_PER_IP_LIMIT: process.env.THI360_RESET_PER_IP_LIMIT ?? "600",
        // GET /api/v1/public/** is limited to 120 requests a minute per IP (TM 10.2); the e2e web server renders the
        // catalog from loopback, so exempt it (plan B6, FS-1). The suite must never see a 429 from the catalog.
        THI360_SECURITY_RATELIMIT_EXEMPTIPS: process.env.THI360_SECURITY_RATELIMIT_EXEMPTIPS ?? "127.0.0.1,::1",
      },
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
