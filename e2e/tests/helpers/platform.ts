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
// The access cookie (thi360_at) lives 5 minutes, so a cached session is
// discarded after ~3 minutes and a long suite never reuses an expired cookie.
const SESSION_MAX_AGE_MS = 3 * 60 * 1000;

interface PlatformSession {
  storageState: PlatformSessionState;
  xsrf: string;
}

let cachedSession: { promise: Promise<PlatformSession>; createdAt: number } | undefined;

function platformApiSession(): Promise<PlatformSession> {
  if (cachedSession && Date.now() - cachedSession.createdAt < SESSION_MAX_AGE_MS) {
    return cachedSession.promise;
  }
  return loginPlatformApiSession();
}

function loginPlatformApiSession(): Promise<PlatformSession> {
  const promise = (async () => {
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
  cachedSession = { promise, createdAt: Date.now() };
  // A failed login must not stay cached for the next test.
  promise.catch(() => {
    if (cachedSession?.promise === promise) {
      cachedSession = undefined;
    }
  });
  return promise;
}

// The backend caps live sessions per user and evicts the oldest, so browser
// logins of other specs can revoke the cached session before its age expires.
// A cheap authenticated call tells a live session from an evicted one.
async function openValidatedContext(session: PlatformSession): Promise<APIRequestContext | undefined> {
  const api = await playwrightRequest.newContext({ baseURL: ADMIN_URL, storageState: session.storageState });
  const meRes = await api.get("/api/v1/me");
  if (meRes.status() === 401) {
    await api.dispose();
    return undefined;
  }
  return api;
}

export async function createPlatformApiContext(): Promise<{ api: APIRequestContext; xsrf: string }> {
  let session = await platformApiSession();
  let api = await openValidatedContext(session);
  if (!api) {
    cachedSession = undefined;
    session = await loginPlatformApiSession();
    api = await openValidatedContext(session);
    if (!api) {
      throw new Error("Platform API session rejected with 401 right after login");
    }
  }
  return { api, xsrf: session.xsrf };
}
