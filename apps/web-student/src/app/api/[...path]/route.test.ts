// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DELETE, GET, PATCH, POST, PUT } from "./route";

const API = "http://api.internal:8080";
const fetchMock = vi.fn<typeof fetch>();

function request(path: string, init: ConstructorParameters<typeof NextRequest>[1] = {}) {
  return new NextRequest(`http://localhost:3206/api/${path}`, init);
}

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function lastCall() {
  const call = fetchMock.mock.calls.at(-1);
  if (!call) throw new Error("fetch chưa được gọi");
  const [url, init] = call;
  return { url: String(url), init: init ?? {}, headers: new Headers(init?.headers) };
}

beforeEach(() => {
  vi.stubEnv("THI360_API_URL", API);
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(Response.json({ ok: true }));
});

afterEach(() => {
  fetchMock.mockReset();
  vi.useRealTimers();
});

describe("web-student proxy /api/[...path]", () => {
  it("chuyển tiếp request tới backend và giữ Set-Cookie riêng lẻ", async () => {
    const upstream = new Response(JSON.stringify({ redirectTo: "http://localhost:3000/home", mustChangePassword: false }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
    upstream.headers.append("set-cookie", "thi360_at=a; Path=/; HttpOnly");
    upstream.headers.append("set-cookie", "thi360_xsrf=x; Path=/");
    fetchMock.mockResolvedValue(upstream);

    const response = await POST(
      request("v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ identifier: "student01", password: "secret" }),
        headers: { "content-type": "application/json", "x-request-id": "req-stu-1" },
      }),
      context("v1", "auth", "login"),
    );

    expect(response.status).toBe(200);
    expect(response.headers.getSetCookie()).toEqual([
      "thi360_at=a; Path=/; HttpOnly",
      "thi360_xsrf=x; Path=/",
    ]);
    expect(lastCall().url).toBe(`${API}/api/v1/auth/login`);
    expect(lastCall().headers.get("x-request-id")).toBe("req-stu-1");
  });

  it("không chuyển tiếp X-Forwarded-For của trình duyệt và chỉ ghi đè từ x-real-ip khi THI360_TRUST_REAL_IP=1", async () => {
    await GET(
      request("v1/me", {
        headers: {
          "x-forwarded-for": "203.0.113.9",
          "x-real-ip": "198.51.100.88",
          cookie: "thi360_at=at",
        },
      }),
      context("v1", "me"),
    );
    expect(lastCall().headers.get("x-forwarded-for")).toBeNull();

    vi.stubEnv("THI360_TRUST_REAL_IP", "1");
    await POST(
      request("v1/auth/login", {
        method: "POST",
        body: "{}",
        headers: { "x-forwarded-for": "203.0.113.9", "x-real-ip": "198.51.100.88" },
      }),
      context("v1", "auth", "login"),
    );
    expect(lastCall().headers.get("x-forwarded-for")).toBe("198.51.100.88");
  });

  it.each([
    ["PUT", PUT],
    ["PATCH", PATCH],
    ["DELETE", DELETE],
  ] as const)("hỗ trợ phương thức %s", async (method, handler) => {
    await handler(request("v1/ping", { method, body: method === "DELETE" ? undefined : "{}" }), context("v1", "ping"));
    expect(lastCall().init.method).toBe(method);
  });
});
