// @vitest-environment node
import { CTX_COOKIE_NAME, signCtxCookie } from "@thi360/web-kit/ctx";
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { proxy } from "./proxy";

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
});
