import { request as playwrightRequest, type APIRequestContext } from "@playwright/test";

const WEB_URL = process.env.THI360_WEB_URL ?? "http://localhost:3000";
const ADMIN_URL = process.env.THI360_ADMIN_URL ?? "http://localhost:3001";
const PLATFORM_PASSWORD = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";

// Shape returned by APIRequestContext.storageState() (Playwright types it
// anonymously). Matches the storageState option of request.newContext().
export interface PlatformSessionState {
  cookies: Array<{
    name: string;
    value: string;
    domain: string;
    path: string;
    expires: number;
    httpOnly: boolean;
    secure: boolean;
    sameSite: "Strict" | "Lax" | "None";
  }>;
  origins: Array<{
    origin: string;
    localStorage: Array<{ name: string; value: string }>;
  }>;
}

// The backend rate-limits /auth/login per IP, so the suite logs in once per
// worker process and every test gets its own request context cloned from the
// shared session cookies instead of re-authenticating.
let sessionPromise: Promise<{ storageState: PlatformSessionState; xsrf: string }> | undefined;

function platformApiSession() {
  sessionPromise ??= (async () => {
    const loginApi = await playwrightRequest.newContext({ baseURL: ADMIN_URL });
    const loginRes = await loginApi.post(`${WEB_URL}/api/v1/auth/login`, {
      headers: { origin: WEB_URL },
      data: { identifier: "platform", password: PLATFORM_PASSWORD },
    });
    if (!loginRes.ok()) {
      await loginApi.dispose();
      throw new Error(`Platform API login failed: ${loginRes.status()} ${await loginRes.text()}`);
    }
    const storageState = await loginApi.storageState();
    const xsrf = storageState.cookies.find((cookie) => cookie.name === "thi360_xsrf")?.value ?? "";
    await loginApi.dispose();
    return { storageState, xsrf };
  })();
  return sessionPromise;
}

export async function createPlatformApiContext(): Promise<{ api: APIRequestContext; xsrf: string }> {
  const { storageState, xsrf } = await platformApiSession();
  const api = await playwrightRequest.newContext({ baseURL: ADMIN_URL, storageState });
  return { api, xsrf };
}
