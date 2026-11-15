// @vitest-environment node
import { CTX_COOKIE_NAME, signCtxCookie } from "@thi360/web-kit/ctx";
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { config, proxy } from "./proxy";

const SECRET = "ctx-dev-only-change-me-32-bytes!!";
const USER_ID = "00000000-0000-4000-8000-000000000001";

beforeEach(() => {
  vi.stubEnv("THI360_CTX_SECRET", SECRET);
  vi.stubEnv("THI360_WEB_URL", "http://localhost:3000");
  vi.stubEnv("THI360_ADMIN_URL", "http://localhost:3001");
});

describe("web-student proxy.ts", () => {
  it("cho phép truy cập trang công khai / và /dang-nhap khi chưa đăng nhập", async () => {
    const landing = await proxy(new NextRequest("http://localhost:3000/"));
    expect(landing.status).toBe(200);

    const login = await proxy(new NextRequest("http://localhost:3000/dang-nhap"));
    expect(login.status).toBe(200);
  });

  it("chuyển về /dang-nhap kèm callbackUrl khi chưa có thi360_ctx hợp lệ", async () => {
    const response = await proxy(new NextRequest("http://localhost:3000/home?tab=1"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "http://localhost:3000/dang-nhap?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Fhome%3Ftab%3D1",
    );
  });

  it("chuyển người đã đăng nhập vào /dang-nhap sang homeUrl theo access", async () => {
    const exp = Math.floor(Date.now() / 1000) + 300;
    const studentToken = await signCtxCookie(
      { userId: USER_ID, realm: "APP", access: "EXAM", version: 1, expiresAt: exp },
      SECRET,
    );
    const adminToken = await signCtxCookie(
      { userId: USER_ID, realm: "APP", access: "MANAGE", version: 1, expiresAt: exp },
      SECRET,
    );

    const studentRes = await proxy(
      new NextRequest("http://localhost:3000/dang-nhap", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${studentToken}` },
      }),
    );
    expect(studentRes.status).toBe(307);
    expect(studentRes.headers.get("location")).toBe("http://localhost:3000/home");

    const adminRes = await proxy(
      new NextRequest("http://localhost:3000/dang-nhap", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${adminToken}` },
      }),
    );
    expect(adminRes.status).toBe(307);
    expect(adminRes.headers.get("location")).toBe("http://localhost:3001/");
  });

  it("cho phép người có thi360_ctx hợp lệ vào /home và /doi-mat-khau", async () => {
    const token = await signCtxCookie(
      {
        userId: USER_ID,
        realm: "APP",
        access: "EXAM",
        version: 1,
        expiresAt: Math.floor(Date.now() / 1000) + 300,
      },
      SECRET,
    );

    const home = await proxy(
      new NextRequest("http://localhost:3000/home", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${token}` },
      }),
    );
    expect(home.status).toBe(200);
  });

  it("dựng callbackUrl từ THI360_WEB_URL khi request đến qua origin nội bộ http", async () => {
    vi.stubEnv("THI360_WEB_URL", "https://thi360.vn");
    vi.stubEnv("THI360_ADMIN_URL", "https://admin.thi360.vn");

    const response = await proxy(new NextRequest("http://127.0.0.1:3000/home?tab=2"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "https://thi360.vn/dang-nhap?callbackUrl=https%3A%2F%2Fthi360.vn%2Fhome%3Ftab%3D2",
    );
  });

  it("matcher khớp route có dấu chấm như /users/ops.admin nhưng bỏ qua tài nguyên tĩnh", () => {
    const matcher = new RegExp(`^${config.matcher[0]}$`);
    expect(matcher.test("/users/ops.admin")).toBe(true);
    expect(matcher.test("/home")).toBe(true);
    expect(matcher.test("/logo.svg")).toBe(false);
    expect(matcher.test("/_next/static/chunks/app.js")).toBe(false);
    expect(matcher.test("/api/v1/me")).toBe(false);
  });
});

describe("web-student proxy.ts tự làm mới phiên khi thi360_ctx hết hạn (UNI-2574)", () => {
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
    return new Response("{}", { status: 200, headers });
  }

  beforeEach(() => {
    rt = `rt-${crypto.randomUUID()}`;
    fetchMock.mockReset();
    vi.stubEnv("THI360_API_URL", API);
    vi.stubGlobal("fetch", fetchMock);
  });

  it("/home khi thi360_ctx đã hết hạn nhưng còn thi360_rt: làm mới rồi cho đi tiếp", async () => {
    fetchMock.mockResolvedValueOnce(await refreshedResponse("EXAM"));

    const response = await proxy(
      new NextRequest("http://localhost:3000/home", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`${API}/api/v1/auth/refresh`);
    expect(response.status).toBe(200);
    expect(response.headers.getSetCookie()).toHaveLength(4);
  });

  it("/dang-nhap kèm callbackUrl khi còn thi360_rt: làm mới rồi quay lại đúng trang trong callbackUrl", async () => {
    fetchMock.mockResolvedValueOnce(await refreshedResponse("MANAGE"));

    const response = await proxy(
      new NextRequest("http://localhost:3000/dang-nhap?callbackUrl=http%3A%2F%2Flocalhost%3A3001%2Flop-hoc", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3001/lop-hoc");
    expect(response.headers.getSetCookie()).toHaveLength(4);
  });

  it("/dang-nhap không có callbackUrl khi còn thi360_rt: làm mới rồi về trang chủ theo access", async () => {
    fetchMock.mockResolvedValueOnce(await refreshedResponse("MANAGE"));

    const response = await proxy(
      new NextRequest("http://localhost:3000/dang-nhap", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3001/");
  });

  it("/dang-nhap với thi360_ctx còn hạn: callbackUrl hợp lệ được dùng, callbackUrl ngoài hệ thống bị bỏ qua", async () => {
    const valid = await signCtxCookie(
      { userId: USER_ID, realm: "APP", access: "MANAGE", version: 1, expiresAt: Math.floor(Date.now() / 1000) + 300 },
      SECRET,
    );
    const open = (callbackUrl: string) =>
      proxy(
        new NextRequest(`http://localhost:3000/dang-nhap?callbackUrl=${encodeURIComponent(callbackUrl)}`, {
          headers: { cookie: `${CTX_COOKIE_NAME}=${valid}` },
        }),
      );

    expect((await open("http://localhost:3001/tai-khoan?q=a")).headers.get("location")).toBe(
      "http://localhost:3001/tai-khoan?q=a",
    );
    expect((await open("/home")).headers.get("location")).toBe("http://localhost:3000/home");
    expect((await open("https://trang-la.example/x")).headers.get("location")).toBe("http://localhost:3001/");
    expect((await open("/dang-nhap")).headers.get("location")).toBe("http://localhost:3001/");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("/dang-nhap khi refresh token bị từ chối: hiện trang đăng nhập và xóa cookie cũ", async () => {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_rt=; Path=/; Max-Age=0; HttpOnly");
    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 401, headers }));

    const response = await proxy(
      new NextRequest("http://localhost:3000/dang-nhap", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(200);
    expect(response.headers.getSetCookie()).toEqual(["thi360_rt=; Path=/; Max-Age=0; HttpOnly"]);
  });

  it("trang công khai / không gọi backend dù có thi360_rt", async () => {
    const response = await proxy(
      new NextRequest("http://localhost:3000/", { headers: { cookie: `thi360_rt=${rt}` } }),
    );

    expect(response.status).toBe(200);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
