import { XSRF_COOKIE_NAME, XSRF_HEADER_NAME, readCookieValue } from "./client";

export const ACCESS_TOKEN_COOKIE_NAME = "thi360_at";
export const REFRESH_TOKEN_COOKIE_NAME = "thi360_rt";

const REFRESH_PATH = "/api/v1/auth/refresh";
const REFRESH_TIMEOUT_MS = 15_000;
/**
 * Kết quả làm mới thành công được dùng lại trong khoảng ngắn cho các request đến sau vẫn mang
 * refresh token cũ (trình duyệt gửi song song trước khi nhận cookie mới), để một đợt hết hạn chỉ
 * gọi Keycloak một lần trong mỗi tiến trình web.
 *
 * Giới hạn đã biết: sổ gộp nằm trong từng tiến trình (web học viên và web admin là hai tiến trình),
 * nên cách này dựa vào việc realm KHÔNG bật "Revoke Refresh Token" (mặc định của Keycloak và của
 * `infra/keycloak/thi-360-realm.json`). Nếu bật thu hồi refresh token đã dùng, lượt làm mới thứ hai
 * với token cũ sẽ bị từ chối và người dùng bị đăng xuất.
 */
const REUSE_WINDOW_MS = 10_000;

export interface ServerRefreshInput {
  /** Gốc URL của backend, không có dấu `/` cuối. */
  apiBase: string;
  /** Header `Cookie` nguyên văn của request đến. */
  cookieHeader: string;
  locale?: string;
  requestId?: string;
  clientIp?: string | null;
  origin?: string | null;
}

export type ServerRefreshResult =
  | {
      ok: true;
      /** Các `Set-Cookie` backend trả, chuyển nguyên văn về trình duyệt. */
      setCookies: string[];
      /** Header `Cookie` sau khi áp cookie mới, dùng cho request gửi tiếp tới backend. */
      cookieHeader: string;
    }
  | {
      ok: false;
      /** Mã HTTP của backend; `null` khi không gọi được (mạng, quá thời gian, thiếu refresh token). */
      status: number | null;
      /** `Set-Cookie` xóa phiên khi backend từ chối refresh token; rỗng ở các lỗi khác. */
      setCookies: string[];
    };

interface SharedRefresh {
  expiresAt: number;
  promise: Promise<{ ok: true; setCookies: string[] } | { ok: false; status: number | null; setCookies: string[] }>;
}

/** Refresh token của Keycloak dài khoảng 1 KB; giá trị dài bất thường không được dùng làm khóa. */
const MAX_REFRESH_TOKEN_LENGTH = 8192;

const REGISTRY_KEY = Symbol.for("thi360.web-kit.session-refresh");

/**
 * Middleware trang và route `/api/[...path]` được Next.js đóng gói riêng; ở Next 16.2 chúng chạy chung
 * một tiến trình Node nên sổ gộp đặt trên `globalThis` để dùng chung một lượt làm mới. Đây chỉ là tối
 * ưu: nếu Next tách hai nơi này ra thì mỗi nơi tự làm mới một lần, kết quả vẫn đúng.
 */
function registry(): Map<string, SharedRefresh> {
  const holder = globalThis as unknown as Record<symbol, Map<string, SharedRefresh> | undefined>;
  let map = holder[REGISTRY_KEY];
  if (!map) {
    map = new Map();
    holder[REGISTRY_KEY] = map;
  }
  return map;
}

/** Chỉ dùng trong test. */
export function resetSessionRefreshRegistry(): void {
  registry().clear();
}

function parseSetCookie(setCookie: string): { name: string; value: string; removed: boolean } | null {
  const first = setCookie.split(";", 1)[0] ?? "";
  const eq = first.indexOf("=");
  if (eq <= 0) return null;
  const name = first.slice(0, eq).trim();
  const value = first.slice(eq + 1).trim();
  if (!name) return null;
  const removed = value === "" || /;\s*max-age\s*=\s*0\s*(;|$)/i.test(setCookie);
  return { name, value, removed };
}

/** Áp danh sách `Set-Cookie` lên một header `Cookie`: ghi đè giá trị cũ, bỏ cookie bị xóa. */
export function applySetCookies(cookieHeader: string, setCookies: readonly string[]): string {
  const jar = new Map<string, string>();
  for (const part of cookieHeader.split(";")) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    const eq = trimmed.indexOf("=");
    if (eq <= 0) continue;
    jar.set(trimmed.slice(0, eq), trimmed.slice(eq + 1));
  }
  for (const setCookie of setCookies) {
    const parsed = parseSetCookie(setCookie);
    if (!parsed) continue;
    if (parsed.removed) {
      jar.delete(parsed.name);
    } else {
      jar.set(parsed.name, parsed.value);
    }
  }
  return [...jar].map(([name, value]) => `${name}=${value}`).join("; ");
}

/** Token XSRF do backend sinh chỉ gồm ký tự ASCII in được; giá trị khác không được đưa vào header. */
const HEADER_SAFE_TOKEN = /^[!-~]{1,512}$/;

type RefreshCallResult =
  | { ok: true; setCookies: string[] }
  | { ok: false; status: number | null; setCookies: string[] };

async function callRefresh(input: ServerRefreshInput): Promise<RefreshCallResult> {
  let response: Response;
  try {
    const headers = new Headers();
    headers.set("cookie", input.cookieHeader);
    headers.set("accept", "application/json");
    if (input.locale) headers.set("accept-language", input.locale);
    if (input.requestId) headers.set("x-request-id", input.requestId);
    if (input.clientIp) headers.set("x-forwarded-for", input.clientIp);
    if (input.origin) headers.set("origin", input.origin);
    // Backend kiểm tra double-submit: header phải trùng cookie `thi360_xsrf` của chính request này.
    const xsrf = readCookieValue(input.cookieHeader, XSRF_COOKIE_NAME);
    if (xsrf && HEADER_SAFE_TOKEN.test(xsrf)) headers.set(XSRF_HEADER_NAME, xsrf);

    response = await fetch(`${input.apiBase}${REFRESH_PATH}`, {
      method: "POST",
      headers,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(REFRESH_TIMEOUT_MS),
    });
  } catch {
    return { ok: false, status: null, setCookies: [] };
  }

  const setCookies = response.headers.getSetCookie();
  try {
    await response.body?.cancel();
  } catch {
    // thân phản hồi không cần đọc
  }
  if (response.ok) {
    return { ok: true, setCookies };
  }
  // Chỉ chuyển lệnh xóa cookie về trình duyệt khi backend thực sự từ chối phiên.
  return { ok: false, status: response.status, setCookies: response.status === 401 ? setCookies : [] };
}

/**
 * Làm mới phiên ở phía máy chủ bằng refresh token trong cookie của request. Các request cùng refresh
 * token dùng chung một lượt gọi backend.
 */
export async function refreshSessionOnServer(input: ServerRefreshInput): Promise<ServerRefreshResult> {
  const refreshToken = readCookieValue(input.cookieHeader, REFRESH_TOKEN_COOKIE_NAME);
  if (!refreshToken || refreshToken.length > MAX_REFRESH_TOKEN_LENGTH) {
    return { ok: false, status: null, setCookies: [] };
  }

  const shared = registry();
  const now = Date.now();
  let entry = shared.get(refreshToken);
  if (entry && entry.expiresAt <= now) {
    shared.delete(refreshToken);
    entry = undefined;
  }
  if (!entry) {
    const created: SharedRefresh = {
      expiresAt: Number.POSITIVE_INFINITY,
      promise: callRefresh(input)
        .catch((): RefreshCallResult => ({ ok: false, status: null, setCookies: [] }))
        .then((result) => {
        if (result.ok) {
          created.expiresAt = Date.now() + REUSE_WINDOW_MS;
          const timer = setTimeout(() => {
            if (shared.get(refreshToken) === created) shared.delete(refreshToken);
          }, REUSE_WINDOW_MS);
          (timer as { unref?: () => void }).unref?.();
        } else if (shared.get(refreshToken) === created) {
          shared.delete(refreshToken);
        }
        return result;
      }),
    };
    shared.set(refreshToken, created);
    entry = created;
  }

  const result = await entry.promise;
  if (!result.ok) return result;
  return {
    ok: true,
    setCookies: result.setCookies,
    cookieHeader: applySetCookies(input.cookieHeader, result.setCookies),
  };
}
