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

const defaultHandlers = handlers;

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function lastHeaders() {
  const call = fetchMock.mock.calls.at(-1);
  if (!call) throw new Error("fetch chưa được gọi");
  return new Headers(call[1]?.headers);
}

function callUrl(index: number) {
  return String(fetchMock.mock.calls[index]?.[0]);
}

function callHeaders(index: number) {
  return new Headers(fetchMock.mock.calls[index]?.[1]?.headers);
}

function refreshedResponse() {
  const headers = new Headers();
  headers.append("set-cookie", "thi360_at=at2; Path=/; Max-Age=300; HttpOnly");
  headers.append("set-cookie", "thi360_rt=rt2; Path=/; Max-Age=28800; HttpOnly");
  headers.append("set-cookie", "thi360_ctx=ctx2; Path=/; Max-Age=300; HttpOnly");
  headers.append("set-cookie", "thi360_xsrf=x2; Path=/; Max-Age=28800");
  return new Response("{}", { status: 200, headers });
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

describe("proxyApiRequest Authorization: Bearer của app học viên (UNI-2816)", () => {
  const JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl";
  const handlers = createProxyHandlers({
    messages: {
      notFound: "Không tìm thấy.",
      proxyNotConfigured: "Chưa cấu hình API.",
      upstreamTimeout: "Quá thời gian.",
      upstreamUnavailable: "Không kết nối được.",
    },
    forwardBearerAuthorization: true,
  });

  beforeEach(() => {
    resetSessionRefreshRegistry();
  });

  it("mặc định (không bật forwardBearerAuthorization) không chuyển tiếp Authorization", async () => {
    await defaultHandlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: { authorization: `Bearer ${JWT}` },
      }),
      context("v1", "me"),
    );

    expect(lastHeaders().get("authorization")).toBeNull();
  });

  it("chuyển tiếp Authorization dạng Bearer tới upstream", async () => {
    const res = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: { authorization: `Bearer ${JWT}` },
      }),
      context("v1", "me"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(callUrl(0)).toBe(`${API}/api/v1/me`);
    expect(lastHeaders().get("authorization")).toBe(`Bearer ${JWT}`);
    expect(res.status).toBe(200);
  });

  it("không chuyển tiếp Authorization dạng khác Bearer hoặc sai định dạng", async () => {
    for (const value of ["Basic dXNlcjpwYXNz", "Bearer", "Bearer a b", "Token abc"]) {
      fetchMock.mockClear();
      await handlers.GET(
        new NextRequest("http://localhost:3000/api/v1/me", { headers: { authorization: value } }),
        context("v1", "me"),
      );
      expect(lastHeaders().get("authorization"), value).toBeNull();
    }
  });

  it("không chuyển tiếp Authorization khi nó nằm trong header hop-by-hop Connection", async () => {
    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: { authorization: `Bearer ${JWT}`, connection: "authorization" },
      }),
      context("v1", "me"),
    );

    expect(lastHeaders().get("authorization")).toBeNull();
  });

  it("request có Bearer không tự làm mới phiên bằng cookie, kể cả khi thiếu thi360_at", async () => {
    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: { authorization: `Bearer ${JWT}`, cookie: "thi360_rt=rt1; thi360_xsrf=x1" },
      }),
      context("v1", "me"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(callUrl(0)).toBe(`${API}/api/v1/me`);
  });

  it("backend trả 401 cho request có Bearer: trả nguyên 401, không làm mới, không gửi lại", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ code: "UNAUTHENTICATED" }, { status: 401 }));

    const res = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: { authorization: `Bearer ${JWT}`, cookie: "thi360_at=at1; thi360_rt=rt1" },
      }),
      context("v1", "me"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(res.status).toBe(401);
    expect(res.headers.getSetCookie()).toEqual([]);
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
  function rejectedRefresh() {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_rt=; Path=/; Max-Age=0; HttpOnly");
    return new Response(JSON.stringify({ code: "SESSION_REVOKED" }), { status: 401, headers });
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

describe("proxyApiRequest byte range (UNI-2698)", () => {
  beforeEach(() => {
    resetSessionRefreshRegistry();
  });

  it("chuyển tiếp header Range tới upstream", async () => {
    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/question-assets/asset-1", {
        headers: { range: "bytes=0-99" },
      }),
      context("v1", "admin", "question-assets", "asset-1"),
    );

    expect(lastHeaders().get("range")).toBe("bytes=0-99");
  });

  it("upstream trả 206: client nhận 206 kèm Content-Range, Accept-Ranges, Content-Length và đúng byte", async () => {
    const partial = new Uint8Array([10, 20, 30, 40]);
    fetchMock.mockResolvedValueOnce(
      new Response(partial, {
        status: 206,
        headers: {
          "content-type": "audio/mpeg",
          "content-range": "bytes 0-3/10",
          "accept-ranges": "bytes",
          "content-length": "4",
        },
      }),
    );

    const res = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/question-assets/asset-1", {
        headers: { range: "bytes=0-3" },
      }),
      context("v1", "admin", "question-assets", "asset-1"),
    );

    expect(res.status).toBe(206);
    expect(res.headers.get("content-range")).toBe("bytes 0-3/10");
    expect(res.headers.get("accept-ranges")).toBe("bytes");
    expect(res.headers.get("content-length")).toBe("4");
    expect(new Uint8Array(await res.arrayBuffer())).toEqual(partial);
  });

  it("upstream trả 200 kèm Accept-Ranges: bytes thì Content-Length và Accept-Ranges tới được client", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(new Uint8Array([1, 2, 3]), {
        status: 200,
        headers: {
          "content-type": "audio/mpeg",
          "accept-ranges": "bytes",
          "content-length": "3",
        },
      }),
    );

    const res = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/question-assets/asset-1"),
      context("v1", "admin", "question-assets", "asset-1"),
    );

    expect(res.status).toBe(200);
    expect(res.headers.get("accept-ranges")).toBe("bytes");
    expect(res.headers.get("content-length")).toBe("3");
  });

  it("upstream nén (Content-Encoding khác identity) thì không chuyển tiếp Content-Length", async () => {
    // Độ dài trên dây mô tả body còn nén; proxy gửi body đã giải mã nên length đó sai.
    fetchMock.mockResolvedValueOnce(
      new Response(new Uint8Array([1, 2, 3]), {
        status: 200,
        headers: {
          "content-type": "audio/mpeg",
          "content-encoding": "gzip",
          "accept-ranges": "bytes",
          "content-length": "23",
        },
      }),
    );

    const res200 = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/question-assets/asset-1"),
      context("v1", "admin", "question-assets", "asset-1"),
    );

    expect(res200.status).toBe(200);
    expect(res200.headers.get("accept-ranges")).toBe("bytes");
    expect(res200.headers.get("content-length")).toBeNull();

    fetchMock.mockResolvedValueOnce(
      new Response(new Uint8Array([10, 20, 30, 40]), {
        status: 206,
        headers: {
          "content-type": "audio/mpeg",
          "content-encoding": "gzip",
          "content-range": "bytes 0-3/10",
          "accept-ranges": "bytes",
          "content-length": "19",
        },
      }),
    );

    const res206 = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/question-assets/asset-1", {
        headers: { range: "bytes=0-3" },
      }),
      context("v1", "admin", "question-assets", "asset-1"),
    );

    expect(res206.status).toBe(206);
    expect(res206.headers.get("content-range")).toBe("bytes 0-3/10");
    expect(res206.headers.get("content-length")).toBeNull();

    // Content-Encoding: identity không đổi byte nào — Content-Length vẫn chuyển tiếp.
    fetchMock.mockResolvedValueOnce(
      new Response(new Uint8Array([1, 2, 3]), {
        status: 200,
        headers: {
          "content-type": "audio/mpeg",
          "content-encoding": "identity",
          "accept-ranges": "bytes",
          "content-length": "3",
        },
      }),
    );

    const resIdentity = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/question-assets/asset-1"),
      context("v1", "admin", "question-assets", "asset-1"),
    );

    expect(resIdentity.status).toBe(200);
    expect(resIdentity.headers.get("content-length")).toBe("3");
  });

  it("phản hồi JSON thường không mang Accept-Ranges: Content-Length vẫn bị bỏ như cũ", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ items: [] }), {
        status: 200,
        headers: { "content-type": "application/json", "content-length": "13" },
      }),
    );

    const res = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/users"),
      context("v1", "admin", "users"),
    );

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ items: [] });
    expect(res.headers.get("content-length")).toBeNull();
    expect(res.headers.get("accept-ranges")).toBeNull();
  });

  it("thi360_at hết hạn: sau khi làm mới phiên, lượt gửi lại vẫn mang đúng Range", async () => {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_at=at2; Path=/; Max-Age=300; HttpOnly");
    headers.append("set-cookie", "thi360_rt=rt2; Path=/; Max-Age=28800; HttpOnly");
    headers.append("set-cookie", "thi360_ctx=ctx2; Path=/; Max-Age=300; HttpOnly");
    headers.append("set-cookie", "thi360_xsrf=x2; Path=/; Max-Age=28800");
    fetchMock
      .mockResolvedValueOnce(Response.json({ code: "UNAUTHENTICATED" }, { status: 401 }))
      .mockResolvedValueOnce(new Response("{}", { status: 200, headers }))
      .mockResolvedValueOnce(
        new Response(new Uint8Array([7]), {
          status: 206,
          headers: {
            "content-range": "bytes 5-5/9",
            "accept-ranges": "bytes",
            "content-length": "1",
          },
        }),
      );

    const res = await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/admin/question-assets/asset-1", {
        headers: {
          cookie: "thi360_at=at1; thi360_rt=rt1; thi360_xsrf=x1",
          range: "bytes=5-5",
        },
      }),
      context("v1", "admin", "question-assets", "asset-1"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(3);
    const retryHeaders = new Headers(fetchMock.mock.calls[2]?.[1]?.headers);
    expect(retryHeaders.get("range")).toBe("bytes=5-5");
    expect(res.status).toBe(206);
    expect(res.headers.get("content-range")).toBe("bytes 5-5/9");
  });
});

describe("proxyApiRequest X-Attempt-Session (UNI-2734)", () => {
  beforeEach(() => {
    resetSessionRefreshRegistry();
  });

  it("chuyển tiếp X-Attempt-Session tới upstream trên các lệnh PUT/POST của lượt thi và giữ nguyên chặn header ngoài danh sách", async () => {
    await handlers.PUT(
      new NextRequest("http://localhost:3000/api/v1/me/attempts/att-1/answers", {
        method: "PUT",
        body: JSON.stringify({ answers: [{ questionId: "q-1", answer: "A" }] }),
        headers: {
          "content-type": "application/json",
          "X-Attempt-Session": "attempt-sess-123",
          "x-unlisted-secret": "should-not-forward",
        },
      }),
      context("v1", "me", "attempts", "att-1", "answers"),
    );

    expect(lastHeaders().get("x-attempt-session")).toBe("attempt-sess-123");
    expect(lastHeaders().get("x-unlisted-secret")).toBeNull();

    await handlers.POST(
      new NextRequest("http://localhost:3000/api/v1/me/attempts/att-1/heartbeat", {
        method: "POST",
        headers: {
          "X-Attempt-Session": "attempt-sess-456",
          "x-unlisted-secret": "should-not-forward",
        },
      }),
      context("v1", "me", "attempts", "att-1", "heartbeat"),
    );

    expect(lastHeaders().get("x-attempt-session")).toBe("attempt-sess-456");
    expect(lastHeaders().get("x-unlisted-secret")).toBeNull();
  });

  it("vẫn loại bỏ X-Attempt-Session khi được liệt kê trong header hop-by-hop Connection và chặn header ngoài danh sách", async () => {
    await handlers.PUT(
      new NextRequest("http://localhost:3000/api/v1/me/attempts/att-1/answers", {
        method: "PUT",
        body: JSON.stringify({ answers: [] }),
        headers: {
          "content-type": "application/json",
          Connection: "keep-alive, X-Attempt-Session",
          "X-Attempt-Session": "attempt-sess-123",
          "x-unlisted-secret": "should-not-forward",
        },
      }),
      context("v1", "me", "attempts", "att-1", "answers"),
    );

    const forwarded = lastHeaders();
    expect(forwarded.get("x-attempt-session")).toBeNull();
    expect(forwarded.get("connection")).toBeNull();
    expect(forwarded.get("x-unlisted-secret")).toBeNull();
  });

  it("giữ nguyên X-Attempt-Session khi proxy làm mới phiên thi360_at hết hạn trước khi chuyển tiếp request ghi PUT/POST", async () => {
    const cases = [
      {
        method: "PUT" as const,
        tail: "answers",
        payload: JSON.stringify({ answers: [{ questionId: "q-1", answer: "B" }] }),
      },
      {
        method: "POST" as const,
        tail: "submit",
        payload: JSON.stringify({ confirm: true }),
      },
    ];

    for (const testCase of cases) {
      resetSessionRefreshRegistry();
      fetchMock.mockReset();

      let receivedBody = "";
      fetchMock.mockResolvedValueOnce(refreshedResponse()).mockImplementationOnce(async (_url, init) => {
        receivedBody = await new Response(init?.body ?? null).text();
        return Response.json({ ok: true });
      });

      const handler = handlers[testCase.method];
      const res = await handler(
        new NextRequest(`http://localhost:3001/api/v1/me/attempts/att-1/${testCase.tail}`, {
          method: testCase.method,
          body: testCase.payload,
          headers: {
            cookie: "thi360_rt=rt1; thi360_xsrf=x1",
            "content-type": "application/json",
            "x-xsrf-token": "x1",
            "X-Attempt-Session": "attempt-sess-789",
          },
        }),
        context("v1", "me", "attempts", "att-1", testCase.tail),
      );

      expect(res.status).toBe(200);
      expect(fetchMock).toHaveBeenCalledTimes(2);
      expect(callUrl(0)).toBe(`${API}/api/v1/auth/refresh`);
      expect(callHeaders(0).get("x-attempt-session")).toBeNull();
      expect(callUrl(1)).toBe(`${API}/api/v1/me/attempts/att-1/${testCase.tail}`);
      expect(receivedBody).toBe(testCase.payload);
      expect(callHeaders(1).get("x-attempt-session")).toBe("attempt-sess-789");
      expect(callHeaders(1).get("x-xsrf-token")).toBe("x2");
      expect(callHeaders(1).get("cookie")).toContain("thi360_at=at2");
    }
  });
});
