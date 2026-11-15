import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createProxyHandlers } from "./proxy";
import { resetSessionRefreshRegistry } from "./session-refresh";

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

describe("proxyApiRequest multipart stream", () => {
  it("stream body multipart/form-data 4 MB (không parse JSON) kèm content-type và idempotency-key", async () => {
    const fourMb = 4 * 1024 * 1024;
    const payload = new Uint8Array(fourMb);
    payload[0] = 0x50;
    payload[1] = 0x4b;
    payload[fourMb - 1] = 0x21;

    let receivedFile: unknown = null;
    let capturedInit: (RequestInit & { duplex?: string }) | undefined;
    fetchMock.mockImplementationOnce(async (_url: unknown, init?: RequestInit) => {
      capturedInit = init as RequestInit & { duplex?: string };
      const parsedForm = await new Response(init?.body ?? null, {
        headers: init?.headers,
      }).formData();
      receivedFile = parsedForm.get("file");
      return Response.json({ ok: true });
    });

    const form = new FormData();
    form.append(
      "file",
      new File([payload], "catalog-4mb.xlsx", {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      }),
    );

    const req = new NextRequest(
      "http://localhost:3000/api/v1/admin/catalog/groups/g1/items/import/preview",
      {
        method: "POST",
        body: form,
        headers: {
          "idempotency-key": "idem-upload-4mb",
        },
      },
    );

    const res = await handlers.POST(
      req,
      context("v1", "admin", "catalog", "groups", "g1", "items", "import", "preview"),
    );

    expect(res.status).toBe(200);
    expect(capturedInit?.duplex).toBe("half");
    expect(capturedInit?.body).toBeInstanceOf(ReadableStream);
    expect(lastHeaders().get("content-type")).toMatch(/^multipart\/form-data;/);
    expect(lastHeaders().get("idempotency-key")).toBe("idem-upload-4mb");
    expect(receivedFile).toBeInstanceOf(File);
    expect((receivedFile as File).size).toBe(fourMb);
    expect((receivedFile as File).name).toBe("catalog-4mb.xlsx");
  });
});

describe("proxyApiRequest tự làm mới phiên (UNI-2574)", () => {
  function refreshedResponse() {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_at=at2; Path=/; Max-Age=300; HttpOnly");
    headers.append("set-cookie", "thi360_rt=rt2; Path=/; Max-Age=28800; HttpOnly");
    headers.append("set-cookie", "thi360_ctx=ctx2; Path=/; Max-Age=300; HttpOnly");
    headers.append("set-cookie", "thi360_xsrf=x2; Path=/; Max-Age=28800");
    return new Response("{}", { status: 200, headers });
  }

  function rejectedRefresh() {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_rt=; Path=/; Max-Age=0; HttpOnly");
    return new Response(JSON.stringify({ code: "SESSION_REVOKED" }), { status: 401, headers });
  }

  function callUrl(index: number) {
    return String(fetchMock.mock.calls[index]?.[0]);
  }

  function callHeaders(index: number) {
    return new Headers(fetchMock.mock.calls[index]?.[1]?.headers);
  }

  beforeEach(() => {
    resetSessionRefreshRegistry();
  });

  it("thiếu thi360_at nhưng còn thi360_rt: làm mới trước, gửi request với cookie mới và trả Set-Cookie về trình duyệt", async () => {
    fetchMock.mockResolvedValueOnce(refreshedResponse()).mockResolvedValueOnce(Response.json({ items: [] }));

    const res = await handlers.GET(
      new NextRequest("http://localhost:3001/api/v1/admin/users?q=a", {
        headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1; thi360_locale=en" },
      }),
      context("v1", "admin", "users"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(callUrl(0)).toBe(`${API}/api/v1/auth/refresh`);
    expect(callHeaders(0).get("x-xsrf-token")).toBe("x1");
    expect(callUrl(1)).toBe(`${API}/api/v1/admin/users?q=a`);
    expect(callHeaders(1).get("cookie")).toBe(
      "thi360_rt=rt2; thi360_xsrf=x2; thi360_locale=en; thi360_at=at2; thi360_ctx=ctx2",
    );
    expect(res.status).toBe(200);
    expect(res.headers.getSetCookie()).toEqual([
      "thi360_at=at2; Path=/; Max-Age=300; HttpOnly",
      "thi360_rt=rt2; Path=/; Max-Age=28800; HttpOnly",
      "thi360_ctx=ctx2; Path=/; Max-Age=300; HttpOnly",
      "thi360_xsrf=x2; Path=/; Max-Age=28800",
    ]);
  });

  it("request ghi có thân: làm mới trước, thân chỉ gửi một lần, X-XSRF-TOKEN đổi theo cookie mới khi cặp cũ khớp", async () => {
    let receivedBody = "";
    fetchMock.mockResolvedValueOnce(refreshedResponse()).mockImplementationOnce(async (_url, init) => {
      receivedBody = await new Response(init?.body ?? null).text();
      return Response.json({ id: "u1" }, { status: 201 });
    });

    const res = await handlers.POST(
      new NextRequest("http://localhost:3001/api/v1/admin/users", {
        method: "POST",
        body: JSON.stringify({ username: "moi" }),
        headers: {
          cookie: "thi360_rt=rt1; thi360_xsrf=x1",
          "content-type": "application/json",
          "x-xsrf-token": "x1",
        },
      }),
      context("v1", "admin", "users"),
    );

    expect(res.status).toBe(201);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(receivedBody).toBe(JSON.stringify({ username: "moi" }));
    expect(callHeaders(1).get("x-xsrf-token")).toBe("x2");
    expect(callHeaders(1).get("cookie")).toContain("thi360_xsrf=x2");
  });

  it("X-XSRF-TOKEN không khớp cookie cũ thì giữ nguyên để backend từ chối", async () => {
    fetchMock
      .mockResolvedValueOnce(refreshedResponse())
      .mockResolvedValueOnce(Response.json({ code: "CSRF_INVALID" }, { status: 403 }));

    const res = await handlers.POST(
      new NextRequest("http://localhost:3001/api/v1/admin/users", {
        method: "POST",
        body: "{}",
        headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1", "x-xsrf-token": "forged" },
      }),
      context("v1", "admin", "users"),
    );

    expect(res.status).toBe(403);
    expect(callHeaders(1).get("x-xsrf-token")).toBe("forged");
  });

  it("còn thi360_at nhưng backend trả 401 cho GET: làm mới rồi gửi lại đúng một lần", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ code: "UNAUTHENTICATED" }, { status: 401 }))
      .mockResolvedValueOnce(refreshedResponse())
      .mockResolvedValueOnce(Response.json({ username: "sys.admin" }));

    const res = await handlers.GET(
      new NextRequest("http://localhost:3001/api/v1/me", {
        headers: { cookie: "thi360_at=at1; thi360_rt=rt1; thi360_xsrf=x1" },
      }),
      context("v1", "me"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(callUrl(1)).toBe(`${API}/api/v1/auth/refresh`);
    expect(callHeaders(2).get("cookie")).toContain("thi360_at=at2");
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ username: "sys.admin" });
    expect(res.headers.getSetCookie()).toHaveLength(4);
  });

  it("còn thi360_at, backend trả 401 cho request có thân: không gửi lại, trả nguyên 401", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ code: "UNAUTHENTICATED" }, { status: 401 }));

    const res = await handlers.POST(
      new NextRequest("http://localhost:3001/api/v1/admin/users", {
        method: "POST",
        body: "{}",
        headers: { cookie: "thi360_at=at1; thi360_rt=rt1; thi360_xsrf=x1", "x-xsrf-token": "x1" },
      }),
      context("v1", "admin", "users"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(res.status).toBe(401);
  });

  it("refresh token bị từ chối: trả 401 của backend kèm lệnh xóa cookie, không gọi lại", async () => {
    fetchMock
      .mockResolvedValueOnce(rejectedRefresh())
      .mockResolvedValueOnce(Response.json({ code: "UNAUTHENTICATED" }, { status: 401 }));

    const res = await handlers.GET(
      new NextRequest("http://localhost:3001/api/v1/me", {
        headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1" },
      }),
      context("v1", "me"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(callHeaders(1).get("cookie")).toBe("thi360_rt=rt1; thi360_xsrf=x1");
    expect(res.status).toBe(401);
    expect(res.headers.getSetCookie()).toEqual(["thi360_rt=; Path=/; Max-Age=0; HttpOnly"]);
  });

  it("không làm mới thay cho login, refresh, logout và khi không có thi360_rt", async () => {
    for (const tail of ["login", "refresh", "logout"]) {
      fetchMock.mockClear();
      fetchMock.mockResolvedValueOnce(Response.json({ code: "INVALID_CREDENTIALS" }, { status: 401 }));
      const res = await handlers.POST(
        new NextRequest(`http://localhost:3000/api/v1/auth/${tail}`, {
          method: "POST",
          headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1", "x-xsrf-token": "x1" },
        }),
        context("v1", "auth", tail),
      );
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(callUrl(0)).toBe(`${API}/api/v1/auth/${tail}`);
      expect(res.status).toBe(401);
    }

    fetchMock.mockClear();
    fetchMock.mockResolvedValueOnce(Response.json({ code: "UNAUTHENTICATED" }, { status: 401 }));
    const anonymous = await handlers.GET(new NextRequest("http://localhost:3000/api/v1/me"), context("v1", "me"));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(anonymous.status).toBe(401);
  });

  it("bốn request song song sau khi hết hạn chỉ làm mới một lần", async () => {
    fetchMock.mockImplementation(async (url) =>
      String(url).endsWith("/api/v1/auth/refresh") ? refreshedResponse() : Response.json({ ok: true }),
    );

    const responses = await Promise.all(
      ["users", "roles", "classes", "audit-events"].map((name) =>
        handlers.GET(
          new NextRequest(`http://localhost:3001/api/v1/admin/${name}`, {
            headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1" },
          }),
          context("v1", "admin", name),
        ),
      ),
    );

    expect(responses.map((r) => r.status)).toEqual([200, 200, 200, 200]);
    const refreshCalls = fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/api/v1/auth/refresh"));
    expect(refreshCalls).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledTimes(5);
  });

  it("làm mới xong nhưng gửi tiếp tới backend lỗi mạng: phản hồi 502 vẫn mang cookie mới", async () => {
    fetchMock.mockResolvedValueOnce(refreshedResponse()).mockRejectedValueOnce(new Error("ECONNRESET"));

    const res = await handlers.GET(
      new NextRequest("http://localhost:3001/api/v1/admin/users", {
        headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1" },
      }),
      context("v1", "admin", "users"),
    );

    expect(res.status).toBe(502);
    expect(res.headers.getSetCookie()).toHaveLength(4);
    expect(res.headers.get("cache-control")).toBe("private, no-store");
  });

  it("request ghi không gửi X-XSRF-TOKEN: sau khi làm mới vẫn không có header, backend tự từ chối", async () => {
    fetchMock
      .mockResolvedValueOnce(refreshedResponse())
      .mockResolvedValueOnce(Response.json({ code: "CSRF_INVALID" }, { status: 403 }));

    const res = await handlers.POST(
      new NextRequest("http://localhost:3001/api/v1/admin/users", {
        method: "POST",
        body: "{}",
        headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1" },
      }),
      context("v1", "admin", "users"),
    );

    expect(res.status).toBe(403);
    expect(callHeaders(1).get("x-xsrf-token")).toBeNull();
  });

  it("phản hồi cho phép cache công khai mà kèm cookie phiên mới thì đổi thành private, no-store và bỏ ETag", async () => {
    fetchMock.mockResolvedValueOnce(refreshedResponse()).mockResolvedValueOnce(
      Response.json({ mode: "light" }, { headers: { "cache-control": "public, max-age=60", etag: '"v1"' } }),
    );

    const refreshed = await handlers.GET(
      new NextRequest("http://localhost:3001/api/v1/appearance", {
        headers: { cookie: "thi360_rt=rt1; thi360_xsrf=x1" },
      }),
      context("v1", "appearance"),
    );
    expect(refreshed.headers.get("cache-control")).toBe("private, no-store");
    expect(refreshed.headers.get("etag")).toBeNull();
    expect(refreshed.headers.getSetCookie()).toHaveLength(4);

    fetchMock.mockResolvedValueOnce(
      Response.json({ mode: "light" }, { headers: { "cache-control": "public, max-age=60", etag: '"v1"' } }),
    );
    const anonymous = await handlers.GET(
      new NextRequest("http://localhost:3001/api/v1/appearance"),
      context("v1", "appearance"),
    );
    expect(anonymous.headers.get("cache-control")).toBe("public, max-age=60");
    expect(anonymous.headers.get("etag")).toBe('"v1"');
  });
});
