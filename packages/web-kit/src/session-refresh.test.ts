import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  applySetCookies,
  refreshSessionOnServer,
  resetSessionRefreshRegistry,
} from "./session-refresh";

const API = "http://api.internal:8080";
const fetchMock = vi.fn<typeof fetch>();

function refreshedResponse(suffix = "2") {
  const headers = new Headers();
  headers.append("set-cookie", `thi360_at=at${suffix}; Path=/; Max-Age=300; HttpOnly`);
  headers.append("set-cookie", `thi360_rt=rt${suffix}; Path=/; Max-Age=28800; HttpOnly`);
  headers.append("set-cookie", `thi360_ctx=ctx${suffix}; Path=/; Max-Age=300; HttpOnly`);
  headers.append("set-cookie", `thi360_xsrf=xsrf${suffix}; Path=/; Max-Age=28800`);
  return new Response(JSON.stringify({ redirectTo: "http://localhost:3000/home", mustChangePassword: false }), {
    status: 200,
    headers,
  });
}

function rejectedResponse() {
  const headers = new Headers();
  headers.append("set-cookie", "thi360_at=; Path=/; Max-Age=0; HttpOnly");
  headers.append("set-cookie", "thi360_rt=; Path=/; Max-Age=0; HttpOnly");
  return new Response(JSON.stringify({ code: "SESSION_REVOKED" }), { status: 401, headers });
}

beforeEach(() => {
  resetSessionRefreshRegistry();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.useRealTimers();
});

describe("applySetCookies", () => {
  it("ghi đè cookie cũ, thêm cookie mới, bỏ cookie bị xóa và giữ cookie không liên quan", () => {
    const next = applySetCookies("thi360_locale=en; thi360_rt=rt1; thi360_xsrf=x1; thi360_ctx=old", [
      "thi360_at=at2; Path=/; Max-Age=300; HttpOnly",
      "thi360_rt=rt2; Path=/; Max-Age=28800; HttpOnly",
      "thi360_ctx=; Path=/; Max-Age=0; HttpOnly",
    ]);
    expect(next).toBe("thi360_locale=en; thi360_rt=rt2; thi360_xsrf=x1; thi360_at=at2");
  });
});

describe("refreshSessionOnServer", () => {
  it("không gọi backend khi request không có thi360_rt", async () => {
    const result = await refreshSessionOnServer({ apiBase: API, cookieHeader: "thi360_locale=vi" });
    expect(result).toEqual({ ok: false, status: null, setCookies: [] });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("gọi POST /api/v1/auth/refresh kèm cookie và X-XSRF-TOKEN trùng cookie thi360_xsrf, trả cookie mới", async () => {
    fetchMock.mockResolvedValueOnce(refreshedResponse());

    const result = await refreshSessionOnServer({
      apiBase: API,
      cookieHeader: "thi360_rt=rt1; thi360_xsrf=x1; thi360_locale=en",
      locale: "en",
      requestId: "req-1",
      clientIp: "203.0.113.7",
      origin: "https://admin.thi360.vn",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(`${API}/api/v1/auth/refresh`);
    expect(init?.method).toBe("POST");
    const sent = new Headers(init?.headers);
    expect(sent.get("cookie")).toBe("thi360_rt=rt1; thi360_xsrf=x1; thi360_locale=en");
    expect(sent.get("x-xsrf-token")).toBe("x1");
    expect(sent.get("accept-language")).toBe("en");
    expect(sent.get("x-forwarded-for")).toBe("203.0.113.7");
    expect(sent.get("origin")).toBe("https://admin.thi360.vn");

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.setCookies).toHaveLength(4);
    expect(result.cookieHeader).toBe(
      "thi360_rt=rt2; thi360_xsrf=xsrf2; thi360_locale=en; thi360_at=at2; thi360_ctx=ctx2",
    );
  });

  it("các request song song cùng refresh token chỉ gọi backend một lần", async () => {
    let release: (value: Response) => void = () => {};
    fetchMock.mockReturnValueOnce(
      new Promise<Response>((resolve) => {
        release = resolve;
      }),
    );

    const first = refreshSessionOnServer({ apiBase: API, cookieHeader: "thi360_rt=rt1; thi360_xsrf=x1" });
    const second = refreshSessionOnServer({ apiBase: API, cookieHeader: "thi360_rt=rt1; thi360_xsrf=x1; a=b" });
    release(refreshedResponse());
    const [a, b] = await Promise.all([first, second]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(a.ok && b.ok).toBe(true);
    if (!a.ok || !b.ok) return;
    expect(a.cookieHeader).toContain("thi360_at=at2");
    // Mỗi request giữ các cookie riêng của mình.
    expect(b.cookieHeader).toContain("a=b");
  });

  it("request đến sau vẫn mang refresh token cũ dùng lại kết quả trong 10 giây, sau đó gọi lại", async () => {
    vi.useFakeTimers();
    fetchMock.mockResolvedValueOnce(refreshedResponse("2")).mockResolvedValueOnce(refreshedResponse("3"));
    const input = { apiBase: API, cookieHeader: "thi360_rt=rt1; thi360_xsrf=x1" };

    const first = await refreshSessionOnServer(input);
    vi.advanceTimersByTime(5_000);
    const straggler = await refreshSessionOnServer(input);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(first.ok && straggler.ok && straggler.cookieHeader.includes("thi360_at=at2")).toBe(true);

    vi.advanceTimersByTime(6_000);
    const later = await refreshSessionOnServer(input);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(later.ok && later.cookieHeader.includes("thi360_at=at3")).toBe(true);
  });

  it("backend từ chối (401): trả lệnh xóa cookie và không giữ kết quả lỗi cho lần sau", async () => {
    fetchMock.mockResolvedValueOnce(rejectedResponse()).mockResolvedValueOnce(refreshedResponse());
    const input = { apiBase: API, cookieHeader: "thi360_rt=rt1; thi360_xsrf=x1" };

    const failed = await refreshSessionOnServer(input);
    expect(failed.ok).toBe(false);
    if (failed.ok) return;
    expect(failed.status).toBe(401);
    expect(failed.setCookies).toHaveLength(2);

    const retried = await refreshSessionOnServer(input);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(retried.ok).toBe(true);
  });

  it("lỗi khác 401 (CSRF 403, 5xx) hoặc lỗi mạng: không chuyển Set-Cookie về trình duyệt", async () => {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_rt=; Path=/; Max-Age=0");
    fetchMock
      .mockResolvedValueOnce(new Response("{}", { status: 403, headers }))
      .mockRejectedValueOnce(new Error("ECONNREFUSED"));
    const input = { apiBase: API, cookieHeader: "thi360_rt=rt1" };

    expect(await refreshSessionOnServer(input)).toEqual({ ok: false, status: 403, setCookies: [] });
    expect(await refreshSessionOnServer(input)).toEqual({ ok: false, status: null, setCookies: [] });
  });

  it("cookie thi360_xsrf chứa ký tự không hợp lệ cho header: không ném lỗi, không gửi header, không kẹt sổ gộp", async () => {
    fetchMock
      .mockResolvedValueOnce(new Response("{}", { status: 403 }))
      .mockResolvedValueOnce(refreshedResponse());
    const input = { apiBase: API, cookieHeader: "thi360_rt=rt-bad-xsrf; thi360_xsrf=%E4%B8%AD%0D%0A" };

    const first = await refreshSessionOnServer(input);
    expect(first).toEqual({ ok: false, status: 403, setCookies: [] });
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("x-xsrf-token")).toBeNull();

    const second = await refreshSessionOnServer(input);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(second.ok).toBe(true);
  });

  it("lỗi ném ra khi chuẩn bị hoặc gọi request: trả lỗi thường và lần sau gọi lại được", async () => {
    fetchMock.mockImplementationOnce(() => {
      throw new TypeError("Invalid header value");
    });
    fetchMock.mockResolvedValueOnce(refreshedResponse());
    const input = { apiBase: API, cookieHeader: "thi360_rt=rt-throws; thi360_xsrf=x1" };

    expect(await refreshSessionOnServer(input)).toEqual({ ok: false, status: null, setCookies: [] });
    expect((await refreshSessionOnServer(input)).ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("refresh token dài bất thường không được gọi backend và không vào sổ gộp", async () => {
    const result = await refreshSessionOnServer({
      apiBase: API,
      cookieHeader: `thi360_rt=${"a".repeat(9000)}; thi360_xsrf=x1`,
    });
    expect(result).toEqual({ ok: false, status: null, setCookies: [] });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
