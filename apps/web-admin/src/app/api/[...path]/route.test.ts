// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DELETE, GET, PATCH, POST, PUT } from "./route";

const API = "http://api.internal:8080";

const fetchMock = vi.fn<typeof fetch>();

function request(path: string, init: ConstructorParameters<typeof NextRequest>[1] = {}) {
  return new NextRequest(`http://localhost:3102/api/${path}`, init);
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

describe("proxy /api/[...path]", () => {
  it("chuyển tiếp GET tới backend kèm query, cookie, XSRF, Origin và X-Request-Id", async () => {
    fetchMock.mockResolvedValue(Response.json({ id: "u1", username: "ops.admin" }));

    const response = await GET(
      request("v1/admin/users?page=0&size=20", {
        headers: {
          cookie: "thi360_at=at; thi360_xsrf=xs",
          "x-xsrf-token": "xs",
          origin: "http://localhost:3102",
          "x-request-id": "req-123",
          "accept-language": "vi",
        },
      }),
      context("v1", "admin", "users"),
    );

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ id: "u1", username: "ops.admin" });
    expect(response.headers.get("x-request-id")).toBe("req-123");

    const { url, headers, init } = lastCall();
    expect(url).toBe(`${API}/api/v1/admin/users?page=0&size=20`);
    expect(init.method).toBe("GET");
    expect(init.redirect).toBe("manual");
    expect(headers.get("cookie")).toBe("thi360_at=at; thi360_xsrf=xs");
    expect(headers.get("x-xsrf-token")).toBe("xs");
    expect(headers.get("origin")).toBe("http://localhost:3102");
    expect(headers.get("x-request-id")).toBe("req-123");
    expect(headers.get("accept-language")).toBe("vi");
  });

  it("không chuyển tiếp header ngoài danh sách cho phép", async () => {
    await GET(
      request("v1/me", {
        headers: { authorization: "Bearer stolen", host: "evil.example", "x-custom": "1" },
      }),
      context("v1", "me"),
    );

    const { headers } = lastCall();
    expect(headers.get("authorization")).toBeNull();
    expect(headers.get("host")).toBeNull();
    expect(headers.get("x-custom")).toBeNull();
  });

  it("không chuyển tiếp X-Forwarded-For của trình duyệt (danh sách chuyển tiếp chỉ gồm B10)", async () => {
    await GET(
      request("v1/me", { headers: { "x-forwarded-for": "203.0.113.9", cookie: "thi360_at=at" } }),
      context("v1", "me"),
    );

    const { headers } = lastCall();
    expect(headers.get("x-forwarded-for")).toBeNull();
    expect(headers.get("cookie")).toBe("thi360_at=at");
  });

  it("bỏ header cho phép mà request nêu trong Connection, giữ phần còn lại", async () => {
    await POST(
      request("v1/admin/users", {
        method: "POST",
        body: "{}",
        headers: {
          connection: "Keep-Alive, ORIGIN ,x-xsrf-token",
          origin: "http://localhost:3102",
          "x-xsrf-token": "xs",
          cookie: "thi360_xsrf=xs",
          "accept-language": "vi",
        },
      }),
      context("v1", "admin", "users"),
    );

    const { headers } = lastCall();
    expect(headers.get("origin")).toBeNull();
    expect(headers.get("x-xsrf-token")).toBeNull();
    expect(headers.get("connection")).toBeNull();
    expect(headers.get("cookie")).toBe("thi360_xsrf=xs");
    expect(headers.get("accept-language")).toBe("vi");
  });

  it("giữ nguyên Origin lạ để backend từ chối, và không tự đặt Origin khi trình duyệt không gửi", async () => {
    await POST(
      request("v1/admin/users", { method: "POST", body: "{}", headers: { origin: "https://evil.example" } }),
      context("v1", "admin", "users"),
    );
    expect(lastCall().headers.get("origin")).toBe("https://evil.example");

    await POST(request("v1/admin/users", { method: "POST", body: "{}" }), context("v1", "admin", "users"));
    expect(lastCall().headers.get("origin")).toBeNull();
  });

  it.each([
    ["POST", POST],
    ["PUT", PUT],
    ["PATCH", PATCH],
    ["DELETE", DELETE],
  ] as const)("%s chuyển tiếp phương thức và thân", async (method, handler) => {
    // Mô phỏng fetch thật của Node: dựng Request gốc từ (target, init) trước khi đọc thân.
    // Thiếu `duplex: "half"` với thân dạng stream thì dòng này ném lỗi và proxy trả 502.
    fetchMock.mockImplementation(async (url, init) => {
      const received = await new Request(url, init).text();
      return Response.json({ received }, { status: 201 });
    });

    const response = await handler(
      request("v1/admin/roles/7", {
        method,
        body: JSON.stringify({ name: "Theo dõi lớp" }),
        headers: { "content-type": "application/json" },
      }),
      context("v1", "admin", "roles", "7"),
    );

    expect(response.status).toBe(201);
    expect(await response.json()).toEqual({ received: '{"name":"Theo dõi lớp"}' });
    const { init, headers } = lastCall();
    expect(init.method).toBe(method);
    expect(headers.get("content-type")).toBe("application/json");
  });

  it("trả mỗi Set-Cookie thành một header riêng, kể cả khi Expires có dấu phẩy", async () => {
    const upstream = new Response(null, { status: 200 });
    upstream.headers.append("set-cookie", "thi360_at=a; Path=/; HttpOnly; Expires=Wed, 13 Nov 2026 08:00:00 GMT");
    upstream.headers.append("set-cookie", "thi360_rt=r; Path=/; HttpOnly");
    upstream.headers.append("set-cookie", "thi360_xsrf=x; Path=/");
    fetchMock.mockResolvedValue(upstream);

    const response = await POST(request("v1/auth/login", { method: "POST", body: "{}" }), context("v1", "auth", "login"));

    expect(response.headers.getSetCookie()).toEqual([
      "thi360_at=a; Path=/; HttpOnly; Expires=Wed, 13 Nov 2026 08:00:00 GMT",
      "thi360_rt=r; Path=/; HttpOnly",
      "thi360_xsrf=x; Path=/",
    ]);
  });

  it("chép trạng thái và thân lỗi của backend nguyên vẹn", async () => {
    const body = { code: "FORBIDDEN", message: "Không đủ quyền", fieldErrors: [], traceId: "t-1" };
    fetchMock.mockResolvedValue(Response.json(body, { status: 403 }));

    const response = await GET(request("v1/admin/users"), context("v1", "admin", "users"));

    expect(response.status).toBe(403);
    expect(await response.json()).toEqual(body);
  });

  it("không tự đi theo chuyển hướng 3xx của backend", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { location: "/elsewhere" } }));

    const response = await GET(request("v1/x"), context("v1", "x"));

    expect(response.status).toBe(302);
    expect(response.headers.get("location")).toBe("/elsewhere");
  });

  it("bỏ content-encoding và content-length vì thân đã được fetch giải nén", async () => {
    fetchMock.mockResolvedValue(
      new Response("{}", { headers: { "content-encoding": "gzip", "content-length": "20", "x-keep": "yes" } }),
    );

    const response = await GET(request("v1/x"), context("v1", "x"));

    expect(response.headers.get("content-encoding")).toBeNull();
    expect(response.headers.get("content-length")).not.toBe("20");
    expect(response.headers.get("x-keep")).toBe("yes");
  });

  it.each(["connection", "keep-alive", "te", "trailer", "transfer-encoding", "upgrade", "proxy-authenticate", "proxy-authorization"])(
    "không chép header hop-by-hop chuẩn %s của backend sang phản hồi",
    async (name) => {
      fetchMock.mockResolvedValue(new Response("{}", { headers: { [name]: "value", "x-keep": "yes" } }));

      const response = await GET(request("v1/x"), context("v1", "x"));

      expect(response.headers.get(name)).toBeNull();
      expect(response.headers.get("x-keep")).toBe("yes");
    },
  );

  it("bỏ header backend nêu trong Connection (không phân biệt hoa thường, có khoảng trắng), giữ header end-to-end", async () => {
    const upstream = new Response("{}", {
      headers: {
        connection: "Close,  X-Hop-Only ,SET-COOKIE",
        "x-hop-only": "secret",
        "x-keep": "yes",
      },
    });
    upstream.headers.append("set-cookie", "thi360_at=a; Path=/");
    fetchMock.mockResolvedValue(upstream);

    const response = await GET(request("v1/x"), context("v1", "x"));

    expect(response.headers.get("connection")).toBeNull();
    expect(response.headers.get("x-hop-only")).toBeNull();
    expect(response.headers.getSetCookie()).toEqual([]);
    expect(response.headers.get("x-keep")).toBe("yes");
  });

  it("vẫn trả Set-Cookie khi backend không nêu nó trong Connection", async () => {
    const upstream = new Response("{}", { headers: { connection: "close" } });
    upstream.headers.append("set-cookie", "thi360_at=a; Path=/; HttpOnly");
    fetchMock.mockResolvedValue(upstream);

    const response = await GET(request("v1/x"), context("v1", "x"));

    expect(response.headers.getSetCookie()).toEqual(["thi360_at=a; Path=/; HttpOnly"]);
  });

  it("sinh X-Request-Id khi trình duyệt không gửi hoặc gửi giá trị không hợp lệ", async () => {
    const first = await GET(request("v1/x"), context("v1", "x"));
    const generated = lastCall().headers.get("x-request-id");
    expect(generated).toMatch(/^[0-9a-f-]{36}$/);
    expect(first.headers.get("x-request-id")).toBe(generated);

    await GET(request("v1/x", { headers: { "x-request-id": "bad id\twith space" } }), context("v1", "x"));
    expect(lastCall().headers.get("x-request-id")).not.toContain(" ");
  });

  it.each([
    [["..", "actuator", "env"]],
    [[".", "v1", "me"]],
    [["v1", "../x"]],
    [["v1", "a/b"]],
    [["v1", "a\\b"]],
    [["v1", "a\u0000b"]],
  ])("từ chối đường dẫn thoát khỏi /api/: %j", async (path) => {
    const response = await GET(request("v1/x"), context(...path));

    expect(response.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("mã hóa lại từng đoạn đường dẫn", async () => {
    await GET(request("v1/x"), context("v1", "tên đăng nhập", "a?b"));

    expect(lastCall().url).toBe(`${API}/api/v1/t%C3%AAn%20%C4%91%C4%83ng%20nh%E1%BA%ADp/a%3Fb`);
  });

  it("trả 502 theo ApiError khi backend không kết nối được", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));

    const response = await GET(request("v1/me", { headers: { "x-request-id": "req-9" } }), context("v1", "me"));

    expect(response.status).toBe(502);
    expect(await response.json()).toMatchObject({
      code: "UPSTREAM_UNAVAILABLE",
      fieldErrors: [],
      traceId: "req-9",
    });
  });

  it("trả 504 khi backend không trả header trong thời hạn", async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementation((_url, init) => {
      const { promise, reject } = Promise.withResolvers<Response>();
      init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      return promise;
    });

    const pending = GET(request("v1/me"), context("v1", "me"));
    await vi.advanceTimersByTimeAsync(60_000);
    const response = await pending;

    expect(response.status).toBe(504);
    expect(await response.json()).toMatchObject({ code: "UPSTREAM_TIMEOUT" });
  });

  it("trả 499 khi trình duyệt hủy request", async () => {
    const controller = new AbortController();
    fetchMock.mockImplementation(async (_url, init) => {
      controller.abort();
      init?.signal?.throwIfAborted();
      return Response.json({});
    });

    const response = await GET(
      new NextRequest("http://localhost:3102/api/v1/me", { signal: controller.signal }),
      context("v1", "me"),
    );

    expect(response.status).toBe(499);
  });

  it("dùng http://localhost:8080 ở môi trường dev khi chưa đặt THI360_API_URL", async () => {
    vi.stubEnv("THI360_API_URL", "");

    await GET(request("v1/me"), context("v1", "me"));

    expect(lastCall().url).toBe("http://localhost:8080/api/v1/me");
  });

  it("ở production thiếu THI360_API_URL thì trả 500 thay vì trỏ về localhost", async () => {
    vi.stubEnv("THI360_API_URL", "");
    vi.stubEnv("NODE_ENV", "production");

    const response = await GET(request("v1/me"), context("v1", "me"));

    expect(response.status).toBe(500);
    expect(await response.json()).toMatchObject({ code: "INTERNAL_ERROR" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("bỏ dấu gạch chéo cuối của THI360_API_URL", async () => {
    vi.stubEnv("THI360_API_URL", `${API}//`);

    await GET(request("v1/me"), context("v1", "me"));

    expect(lastCall().url).toBe(`${API}/api/v1/me`);
  });
});
