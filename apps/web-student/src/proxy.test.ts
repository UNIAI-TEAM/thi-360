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
  it("cho phép truy cập trang công khai / và /login khi chưa đăng nhập", async () => {
    const landing = await proxy(new NextRequest("http://localhost:3000/"));
    expect(landing.status).toBe(200);

    const login = await proxy(new NextRequest("http://localhost:3000/login"));
    expect(login.status).toBe(200);
  });

  it("chuyển về /login kèm callbackUrl khi chưa có thi360_ctx hợp lệ", async () => {
    const response = await proxy(new NextRequest("http://localhost:3000/home?tab=1"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Fhome%3Ftab%3D1",
    );
  });

  it("chuyển người đã đăng nhập vào /login sang homeUrl theo access", async () => {
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
      new NextRequest("http://localhost:3000/login", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${studentToken}` },
      }),
    );
    expect(studentRes.status).toBe(307);
    expect(studentRes.headers.get("location")).toBe("http://localhost:3000/home");

    const adminRes = await proxy(
      new NextRequest("http://localhost:3000/login", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${adminToken}` },
      }),
    );
    expect(adminRes.status).toBe(307);
    expect(adminRes.headers.get("location")).toBe("http://localhost:3001/");
  });

  it("cho phép người có thi360_ctx hợp lệ vào /home và /change-password", async () => {
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
      "https://thi360.vn/login?callbackUrl=https%3A%2F%2Fthi360.vn%2Fhome%3Ftab%3D2",
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

  describe("đường tài khoản của FS-2 (AD-71)", () => {
    async function ctxCookie(access: "EXAM" | "MANAGE") {
      const token = await signCtxCookie(
        { userId: USER_ID, realm: "APP", access, version: 1, expiresAt: Math.floor(Date.now() / 1000) + 300 },
        SECRET,
      );
      return `${CTX_COOKIE_NAME}=${token}`;
    }

    it.each(["/register", "/forgot-password", "/reset-password?token=tok"])(
      "khách mở %s không bị chuyển sang /login",
      async (path) => {
        const response = await proxy(new NextRequest(`http://localhost:3000${path}`));
        expect(response.status).toBe(200);
      },
    );

    it("/verify-email và /account/profile cần phiên: khách sang /login kèm callbackUrl", async () => {
      const verify = await proxy(new NextRequest("http://localhost:3000/verify-email?callbackUrl=%2Fhome"));
      expect(verify.status).toBe(307);
      expect(verify.headers.get("location")).toBe(
        "http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Fverify-email%3FcallbackUrl%3D%252Fhome",
      );

      const profile = await proxy(new NextRequest("http://localhost:3000/account/profile"));
      expect(profile.status).toBe(307);
      expect(profile.headers.get("location")).toBe(
        "http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Faccount%2Fprofile",
      );
    });

    it("người có phiên vào được /verify-email và /account/profile", async () => {
      const cookie = await ctxCookie("EXAM");
      for (const path of ["/verify-email", "/account/profile"]) {
        const response = await proxy(new NextRequest(`http://localhost:3000${path}`, { headers: { cookie } }));
        expect(response.status).toBe(200);
      }
    });

    it("đã đăng nhập mở /register thì về trang chủ theo quyền, bỏ qua callbackUrl", async () => {
      const student = await proxy(
        new NextRequest("http://localhost:3000/register?callbackUrl=%2Fcheckout%2FBCA-FULL", {
          headers: { cookie: await ctxCookie("EXAM") },
        }),
      );
      expect(student.status).toBe(307);
      expect(student.headers.get("location")).toBe("http://localhost:3000/home");

      const admin = await proxy(
        new NextRequest("http://localhost:3000/register", { headers: { cookie: await ctxCookie("MANAGE") } }),
      );
      expect(admin.status).toBe(307);
      expect(admin.headers.get("location")).toBe("http://localhost:3001/");
    });
  });

  describe("đường công khai của FS-1 (AC-12, AC-13)", () => {
    async function ctxCookie(access: "EXAM" | "MANAGE") {
      const token = await signCtxCookie(
        { userId: USER_ID, realm: "APP", access, version: 1, expiresAt: Math.floor(Date.now() / 1000) + 300 },
        SECRET,
      );
      return `${CTX_COOKIE_NAME}=${token}`;
    }

    it.each([
      "/",
      "/exams/HSA-FULL-01",
      "/exams/QDA-S-TOAN-004",
      "/forgot-password",
      "/reset-password",
      "/checkout/result",
      "/checkout/result/app",
      "/dieu-khoan-giao-dich-chung",
      "/chinh-sach-bao-mat-thong-tin",
    ])(
      "khách mở %s đi tiếp, không sang /login",
      async (path) => {
        const response = await proxy(new NextRequest(`http://localhost:3000${path}`));
        expect(response.status).toBe(200);
        expect(response.headers.get("location")).toBeNull();
      },
    );

    it.each([
      ["/exams", "http%3A%2F%2Flocalhost%3A3000%2Fexams"],
      ["/examsx", "http%3A%2F%2Flocalhost%3A3000%2Fexamsx"],
      ["/checkout/HSA-FULL-01", "http%3A%2F%2Flocalhost%3A3000%2Fcheckout%2FHSA-FULL-01"],
      ["/verify-email", "http%3A%2F%2Flocalhost%3A3000%2Fverify-email"],
      ["/home", "http%3A%2F%2Flocalhost%3A3000%2Fhome"],
    ])("khách mở %s bị chuyển sang /login kèm callbackUrl", async (path, encoded) => {
      const response = await proxy(new NextRequest(`http://localhost:3000${path}`));
      expect(response.status).toBe(307);
      expect(response.headers.get("location")).toBe(`http://localhost:3000/login?callbackUrl=${encoded}`);
    });

    it("trang công khai không gọi backend dù có thi360_rt, cả /exams/<code> lẫn /checkout/result", async () => {
      const fetchSpy = vi.spyOn(globalThis, "fetch");
      for (const path of ["/exams/HSA-FULL-01", "/checkout/result?vnp_TxnRef=1"]) {
        const response = await proxy(
          new NextRequest(`http://localhost:3000${path}`, { headers: { cookie: "thi360_rt=refresh-token" } }),
        );
        expect(response.status).toBe(200);
      }
      expect(fetchSpy).not.toHaveBeenCalled();
    });

    it("người đã đăng nhập vẫn mở được /exams/<code> và /checkout/<code>", async () => {
      const cookie = await ctxCookie("EXAM");
      for (const path of ["/exams/HSA-FULL-01", "/checkout/HSA-FULL-01?v=3&p=199000"]) {
        const response = await proxy(new NextRequest(`http://localhost:3000${path}`, { headers: { cookie } }));
        expect(response.status).toBe(200);
      }
    });

    it("khách mở /register đi tiếp; người đã đăng nhập về /home (AC-13)", async () => {
      const guest = await proxy(new NextRequest("http://localhost:3000/register"));
      expect(guest.status).toBe(200);

      const student = await proxy(
        new NextRequest("http://localhost:3000/register", { headers: { cookie: await ctxCookie("EXAM") } }),
      );
      expect(student.status).toBe(307);
      expect(student.headers.get("location")).toBe("http://localhost:3000/home");
    });
  });

  describe("đường công khai cho app mobile (WEB-1, UNI-2799)", () => {
    it("matcher khớp cả ba đường dẫn bảng màu mobile và xác minh deep link", () => {
      const matcher = new RegExp(`^${config.matcher[0]}$`);
      expect(matcher.test("/mobile/appearance-tokens.json")).toBe(true);
      expect(matcher.test("/.well-known/assetlinks.json")).toBe(true);
      expect(matcher.test("/.well-known/apple-app-site-association")).toBe(true);
    });

    it.each([
      "/mobile/appearance-tokens.json",
      "/mobile/appearance-tokens.json?v=1",
      "/.well-known/assetlinks.json",
      "/.well-known/apple-app-site-association",
      "/checkout/result/app",
      "/checkout/result/app?vnp_TxnRef=1&vnp_ResponseCode=00",
      "/dieu-khoan-giao-dich-chung",
      "/chinh-sach-bao-mat-thong-tin",
    ])("cho phép truy cập %s không cần đăng nhập và không gọi backend refresh dù có thi360_rt", async (path) => {
      const fetchSpy = vi.spyOn(globalThis, "fetch");
      const response = await proxy(
        new NextRequest(`http://localhost:3000${path}`, {
          headers: { cookie: "thi360_rt=refresh-token" },
        }),
      );
      expect(response.status).toBe(200);
      expect(response.headers.get("location")).toBeNull();
      expect(fetchSpy).not.toHaveBeenCalled();
    });

    it.each([
      ["/mobile", "http%3A%2F%2Flocalhost%3A3000%2Fmobile"],
      ["/mobile/other", "http%3A%2F%2Flocalhost%3A3000%2Fmobile%2Fother"],
      ["/.well-known", "http%3A%2F%2Flocalhost%3A3000%2F.well-known"],
      ["/home", "http%3A%2F%2Flocalhost%3A3000%2Fhome"],
      ["/account/profile", "http%3A%2F%2Flocalhost%3A3000%2Faccount%2Fprofile"],
      ["/exams", "http%3A%2F%2Flocalhost%3A3000%2Fexams"],
    ])("đường cần phiên %s vẫn chuyển 307 sang /login kèm callbackUrl", async (path, encoded) => {
      const response = await proxy(new NextRequest(`http://localhost:3000${path}`));
      expect(response.status).toBe(307);
      expect(response.headers.get("location")).toBe(`http://localhost:3000/login?callbackUrl=${encoded}`);
    });
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

  it("/login kèm callbackUrl khi còn thi360_rt: làm mới rồi quay lại đúng trang trong callbackUrl", async () => {
    fetchMock.mockResolvedValueOnce(await refreshedResponse("MANAGE"));

    const response = await proxy(
      new NextRequest("http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3001%2Fclasses", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3001/classes");
    expect(response.headers.getSetCookie()).toHaveLength(4);
  });

  it("/login không có callbackUrl khi còn thi360_rt: làm mới rồi về trang chủ theo access", async () => {
    fetchMock.mockResolvedValueOnce(await refreshedResponse("MANAGE"));

    const response = await proxy(
      new NextRequest("http://localhost:3000/login", {
        headers: { cookie: `thi360_rt=${rt}; thi360_xsrf=x1` },
      }),
    );

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3001/");
  });

  it("/login với thi360_ctx còn hạn: callbackUrl hợp lệ được dùng, callbackUrl ngoài hệ thống bị bỏ qua", async () => {
    const valid = await signCtxCookie(
      { userId: USER_ID, realm: "APP", access: "MANAGE", version: 1, expiresAt: Math.floor(Date.now() / 1000) + 300 },
      SECRET,
    );
    const open = (callbackUrl: string) =>
      proxy(
        new NextRequest(`http://localhost:3000/login?callbackUrl=${encodeURIComponent(callbackUrl)}`, {
          headers: { cookie: `${CTX_COOKIE_NAME}=${valid}` },
        }),
      );

    expect((await open("http://localhost:3001/users?q=a")).headers.get("location")).toBe(
      "http://localhost:3001/users?q=a",
    );
    expect((await open("/home")).headers.get("location")).toBe("http://localhost:3000/home");
    expect((await open("https://trang-la.example/x")).headers.get("location")).toBe("http://localhost:3001/");
    expect((await open("/login")).headers.get("location")).toBe("http://localhost:3001/");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("/login khi refresh token bị từ chối: hiện trang đăng nhập và xóa cookie cũ", async () => {
    const headers = new Headers();
    headers.append("set-cookie", "thi360_rt=; Path=/; Max-Age=0; HttpOnly");
    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 401, headers }));

    const response = await proxy(
      new NextRequest("http://localhost:3000/login", {
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
