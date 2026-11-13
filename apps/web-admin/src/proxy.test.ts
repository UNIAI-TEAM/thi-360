// @vitest-environment node
import { CTX_COOKIE_NAME, signCtxCookie } from "@thi360/web-kit/ctx";
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { proxy } from "./proxy";

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
});
