import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createProxyHandlers } from "./proxy";

const API = "http://api.internal:8080";
const fetchMock = vi.fn<typeof fetch>();

const handlers = createProxyHandlers({
  messages: {
    notFound: "Không tìm thấy.",
    proxyNotConfigured: "Chưa cấu hình API.",
    upstreamTimeout: "Quá thời gian.",
    upstreamUnavailable: "Không kết nối được.",
  },
});

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function lastHeaders() {
  const call = fetchMock.mock.calls.at(-1);
  if (!call) throw new Error("fetch chưa được gọi");
  return new Headers(call[1]?.headers);
}

beforeEach(() => {
  vi.stubEnv("THI360_API_URL", API);
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(Response.json({ ok: true }));
});

afterEach(() => {
  fetchMock.mockReset();
});

describe("proxyApiRequest X-Forwarded-For", () => {
  it("không bao giờ chuyển tiếp X-Forwarded-For do client gửi", async () => {
    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: { "x-forwarded-for": "203.0.113.9", "x-real-ip": "203.0.113.10" },
      }),
      context("v1", "me"),
    );

    expect(lastHeaders().get("x-forwarded-for")).toBeNull();
  });

  it("ghi đè X-Forwarded-For bằng tùy chọn resolveClientIp và bỏ qua giá trị client gửi", async () => {
    const customHandlers = createProxyHandlers({
      messages: {
        notFound: "Không tìm thấy.",
        proxyNotConfigured: "Chưa cấu hình API.",
        upstreamTimeout: "Quá thời gian.",
        upstreamUnavailable: "Không kết nối được.",
      },
      resolveClientIp: () => "198.51.100.42",
    });

    await customHandlers.POST(
      new NextRequest("http://localhost:3000/api/v1/auth/login", {
        method: "POST",
        body: "{}",
        headers: { "x-forwarded-for": "203.0.113.9" },
      }),
      context("v1", "auth", "login"),
    );

    expect(lastHeaders().get("x-forwarded-for")).toBe("198.51.100.42");
  });

  it("ghi đè X-Forwarded-For bằng x-real-ip khi THI360_TRUST_REAL_IP=1", async () => {
    vi.stubEnv("THI360_TRUST_REAL_IP", "1");

    await handlers.POST(
      new NextRequest("http://localhost:3000/api/v1/auth/login", {
        method: "POST",
        body: "{}",
        headers: { "x-forwarded-for": "203.0.113.9", "x-real-ip": "198.51.100.77" },
      }),
      context("v1", "auth", "login"),
    );

    expect(lastHeaders().get("x-forwarded-for")).toBe("198.51.100.77");
  });

  it("chuyển tiếp Idempotency-Key tới upstream và vẫn chặn header ngoài danh sách", async () => {
    await handlers.POST(
      new NextRequest("http://localhost:3000/api/v1/admin/authz/bulk", {
        method: "POST",
        body: "{}",
        headers: {
          "idempotency-key": "bulk-req-123",
          "x-unlisted-secret": "should-not-forward",
        },
      }),
      context("v1", "admin", "authz", "bulk"),
    );

    expect(lastHeaders().get("idempotency-key")).toBe("bulk-req-123");
    expect(lastHeaders().get("x-unlisted-secret")).toBeNull();
  });
});

describe("proxyApiRequest Accept-Language", () => {
  it("gửi Accept-Language theo cookie thi360_locale=en và bỏ qua header của trình duyệt", async () => {
    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: {
          cookie: "thi360_at=at; thi360_locale=en",
          "accept-language": "fr-FR,fr;q=0.9,vi;q=0.8",
        },
      }),
      context("v1", "me"),
    );

    expect(lastHeaders().get("accept-language")).toBe("en");
  });

  it("mặc định Accept-Language là vi khi thiếu hoặc sai cookie thi360_locale kể cả khi trình duyệt gửi en-US", async () => {
    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: {
          cookie: "thi360_locale=fr",
          "accept-language": "en-US,en;q=0.9",
        },
      }),
      context("v1", "me"),
    );

    expect(lastHeaders().get("accept-language")).toBe("vi");

    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: {
          "accept-language": "en-US,en;q=0.9",
        },
      }),
      context("v1", "me"),
    );

    expect(lastHeaders().get("accept-language")).toBe("vi");
  });
});

describe("proxyApiRequest header forwarding", () => {
  it("chuyển tiếp Idempotency-Key tới upstream và không chuyển tiếp header ngoài danh sách", async () => {
    await handlers.POST(
      new NextRequest("http://localhost:3000/api/v1/admin/users/bulk-actions", {
        method: "POST",
        body: "{}",
        headers: {
          "Idempotency-Key": "bulk-key-123",
          "x-unlisted-header": "secret-value",
        },
      }),
      context("v1", "admin", "users", "bulk-actions"),
    );

    const forwarded = lastHeaders();
    expect(forwarded.get("idempotency-key")).toBe("bulk-key-123");
    expect(forwarded.get("x-unlisted-header")).toBeNull();
  });
});
