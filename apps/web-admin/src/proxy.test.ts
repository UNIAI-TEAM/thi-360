// @vitest-environment node
import { CTX_COOKIE_NAME, signCtxCookie } from "@thi360/web-kit/ctx";
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { config, proxy } from "./proxy";

const SECRET = "ctx-dev-only-change-me-32-bytes!!";
const USER_ID = "00000000-0000-4000-8000-000000000002";

beforeEach(() => {
  vi.stubEnv("THI360_CTX_SECRET", SECRET);
  vi.stubEnv("THI360_WEB_URL", "http://localhost:3000");
  vi.stubEnv("THI360_ADMIN_URL", "http://localhost:3001");
});

describe("web-admin proxy.ts", () => {
  it("chưa có thi360_ctx hợp lệ: chuyển về web-student /dang-nhap kèm callbackUrl", async () => {
    const response = await proxy(new NextRequest("http://localhost:3001/users?page=1"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "http://localhost:3000/dang-nhap?callbackUrl=http%3A%2F%2Flocalhost%3A3001%2Fusers%3Fpage%3D1",
    );
  });

  it("access = EXAM: chuyển tới /forbidden và cho phép hiển thị /forbidden", async () => {
    const examToken = await signCtxCookie(
      {
        userId: USER_ID,
        realm: "APP",
        access: "EXAM",
        version: 1,
        expiresAt: Math.floor(Date.now() / 1000) + 300,
      },
      SECRET,
    );

    const blocked = await proxy(
      new NextRequest("http://localhost:3001/users", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${examToken}` },
      }),
    );
    expect(blocked.status).toBe(307);
    expect(blocked.headers.get("location")).toBe("http://localhost:3001/forbidden");

    const forbidden = await proxy(
      new NextRequest("http://localhost:3001/forbidden", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${examToken}` },
      }),
    );
    expect(forbidden.status).toBe(200);
  });

  it("access = MANAGE: cho phép đi tiếp", async () => {
    const manageToken = await signCtxCookie(
      {
        userId: USER_ID,
        realm: "APP",
        access: "MANAGE",
        version: 1,
        expiresAt: Math.floor(Date.now() / 1000) + 300,
      },
      SECRET,
    );

    const allowed = await proxy(
      new NextRequest("http://localhost:3001/users", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${manageToken}` },
      }),
    );
    expect(allowed.status).toBe(200);
  });

  it("dựng callbackUrl và /forbidden từ THI360_WEB_URL/THI360_ADMIN_URL khi chạy sau proxy nội bộ http", async () => {
    vi.stubEnv("THI360_WEB_URL", "https://thi360.vn");
    vi.stubEnv("THI360_ADMIN_URL", "https://admin.thi360.vn");

    const unauth = await proxy(new NextRequest("http://127.0.0.1:3001/users?page=1"));
    expect(unauth.status).toBe(307);
    expect(unauth.headers.get("location")).toBe(
      "https://thi360.vn/dang-nhap?callbackUrl=https%3A%2F%2Fadmin.thi360.vn%2Fusers%3Fpage%3D1",
    );

    const examToken = await signCtxCookie(
      {
        userId: USER_ID,
        realm: "APP",
        access: "EXAM",
        version: 1,
        expiresAt: Math.floor(Date.now() / 1000) + 300,
      },
      SECRET,
    );
    const forbidden = await proxy(
      new NextRequest("http://127.0.0.1:3001/users/ops.admin", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${examToken}` },
      }),
    );
    expect(forbidden.status).toBe(307);
    expect(forbidden.headers.get("location")).toBe("https://admin.thi360.vn/forbidden");
  });

  it("matcher khớp route có dấu chấm như /users/ops.admin nhưng bỏ qua tài nguyên tĩnh", () => {
    const matcher = new RegExp(`^${config.matcher[0]}$`);
    expect(matcher.test("/users/ops.admin")).toBe(true);
    expect(matcher.test("/")).toBe(true);
    expect(matcher.test("/logo.svg")).toBe(false);
    expect(matcher.test("/_next/static/chunks/app.js")).toBe(false);
    expect(matcher.test("/api/v1/me")).toBe(false);
  });
});

describe("web-admin proxy.ts tự làm mới phiên khi thi360_ctx hết hạn (UNI-2574)", () => {
  const API = "http://api.internal:8080";
  const fetchMock = vi.fn<typeof fetch>();
  let rt = "";

  async function refreshedResponse(access: "MANAGE" | "EXAM") {
    const ctx = await signCtxCookie(
      { userId: USER_ID, realm: "APP", access, version: 2, expiresAt: Math.floor(Date.now() / 1000) + 300 },
      SECRET,
    );
    const headers = new Headers();
    headers.append("set-cookie", "thi360_at=at2; Path=/; Max-Age=300; HttpOnly");
    headers.append("set-cookie", "thi360_rt=rt2; Path=/; Max-Age=28800; HttpOnly");
    headers.append("set-cookie", `${CTX_COOKIE_NAME}=${ctx}; Path=/; Max-Age=300; HttpOnly`);
    headers.append("set-cookie", "thi360_xsrf=x2; Path=/; Max-Age=28800");
    return { ctx, response: new Response("{}", { status: 200, headers }) };
  }

  beforeEach(() => {
    rt = `rt-${crypto.randomUUID()}`;
    fetchMock.mockReset();
    vi.stubEnv("THI360_API_URL", API);
    vi.stubGlobal("fetch", fetchMock);
  });

  it("thi360_ctx hết hạn nhưng còn thi360_rt: làm mới rồi cho đi tiếp, trả cookie mới, không về trang đăng nhập", async () => {
    const { ctx, response: refreshed } = await refreshedResponse("MANAGE");
    fetchMock.mockResolvedValueOnce(refreshed);
    const expired = await signCtxCookie(
      { userId: USER_ID, realm: "APP", access: "MANAGE", version: 1, expiresAt: Math.floor(Date.now() / 1000) - 5 },
      SECRET,
    );

    const response = await proxy(
      new NextRequest("http://localhost:3001/lop-hoc?page=2", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${expired}; thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`${API}/api/v1/auth/refresh`);
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("x-xsrf-token")).toBe("x1");
    expect(response.status).toBe(200);
    expect(response.headers.get("location")).toBeNull();
    expect(response.headers.getSetCookie()).toHaveLength(4);
    // Phần xử lý phía sau thấy cookie mới.
    expect(response.headers.get("x-middleware-request-cookie")).toContain(`${CTX_COOKIE_NAME}=${ctx}`);
  });

  it("không còn thi360_ctx (trình duyệt đã bỏ cookie) nhưng còn thi360_rt: vẫn làm mới và đi tiếp", async () => {
    const { response: refreshed } = await refreshedResponse("MANAGE");
    fetchMock.mockResolvedValueOnce(refreshed);

    const response = await proxy(
      new NextRequest("http://localhost:3001/tai-khoan", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(200);
    expect(response.headers.getSetCookie()).toHaveLength(4);
  });

  it("làm mới xong mà access = EXAM: chuyển tới /forbidden kèm cookie mới", async () => {
    const { response: refreshed } = await refreshedResponse("EXAM");
    fetchMock.mockResolvedValueOnce(refreshed);

    const response = await proxy(
      new NextRequest("http://localhost:3001/tai-khoan", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3001/forbidden");
    expect(response.headers.getSetCookie()).toHaveLength(4);
  });

  it("refresh token bị từ chối: về trang đăng nhập kèm callbackUrl và lệnh xóa cookie", async () => {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_rt=; Path=/; Max-Age=0; HttpOnly");
    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 401, headers }));

    const response = await proxy(
      new NextRequest("http://localhost:3001/tai-khoan", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "http://localhost:3000/dang-nhap?callbackUrl=http%3A%2F%2Flocalhost%3A3001%2Ftai-khoan",
    );
    expect(response.headers.getSetCookie()).toEqual(["thi360_rt=; Path=/; Max-Age=0; HttpOnly"]);
  });

  it("thi360_ctx còn hạn: không gọi backend", async () => {
    const valid = await signCtxCookie(
      { userId: USER_ID, realm: "APP", access: "MANAGE", version: 1, expiresAt: Math.floor(Date.now() / 1000) + 300 },
      SECRET,
    );

    const response = await proxy(
      new NextRequest("http://localhost:3001/tai-khoan", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${valid}; thi360_rt=${rt}` },
      }),
    );

    expect(response.status).toBe(200);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("không gọi được backend khi làm mới (lỗi mạng): về trang đăng nhập, không gửi lệnh xóa cookie", async () => {
    fetchMock.mockRejectedValueOnce(new Error("ECONNREFUSED"));

    const response = await proxy(
      new NextRequest("http://localhost:3001/tai-khoan", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "http://localhost:3000/dang-nhap?callbackUrl=http%3A%2F%2Flocalhost%3A3001%2Ftai-khoan",
    );
    expect(response.headers.getSetCookie()).toEqual([]);
  });

  it("làm mới trả thi360_ctx sai chữ ký: không coi là đã đăng nhập, về trang đăng nhập", async () => {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_at=at2; Path=/; Max-Age=300; HttpOnly");
    headers.append("set-cookie", `${CTX_COOKIE_NAME}=${USER_ID}.APP.MANAGE.2.9999999999.AAAA; Path=/; Max-Age=300; HttpOnly`);
    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 200, headers }));

    const response = await proxy(
      new NextRequest("http://localhost:3001/tai-khoan", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toContain("/dang-nhap?callbackUrl=");
  });
});
