import { describe, expect, it, vi } from "vitest";
import { CTX_COOKIE_NAME, resolveCtxSecret, signCtxCookie, verifyCtxCookie } from "./ctx";

const TEST_SECRET = "ctx-test-secret-must-be-at-least-32-bytes!!";
const USER_ID = "00000000-0000-4000-8000-000000000001";

describe("verifyCtxCookie", () => {
  it("xác minh token hợp lệ và trả claims chuẩn", async () => {
    const nowSec = Math.floor(Date.now() / 1000);
    const token = await signCtxCookie(
      {
        userId: USER_ID,
        realm: "APP",
        access: "MANAGE",
        version: 3,
        expiresAt: nowSec + 300,
      },
      TEST_SECRET,
    );

    const claims = await verifyCtxCookie(token, { secret: TEST_SECRET, nowMs: nowSec * 1000 });
    expect(claims).toEqual({
      userId: USER_ID,
      realm: "APP",
      access: "MANAGE",
      version: 3,
      expiresAt: nowSec + 300,
    });
    expect(CTX_COOKIE_NAME).toBe("thi360_ctx");
  });

  it("từ chối token hết hạn, sai chữ ký, sai định dạng hoặc thiếu secret ở production", async () => {
    const exp = 1_700_000_000;
    const token = await signCtxCookie(
      {
        userId: USER_ID,
        realm: "MASTER",
        access: "MANAGE",
        version: 1,
        expiresAt: exp,
      },
      TEST_SECRET,
    );

    expect(await verifyCtxCookie(token, { secret: TEST_SECRET, nowMs: exp * 1000 + 1 })).toBeNull();
    expect(await verifyCtxCookie(token, { secret: TEST_SECRET, nowMs: exp * 1000 })).not.toBeNull();
    expect(await verifyCtxCookie(`${token}tamper`, { secret: TEST_SECRET, nowMs: exp * 1000 })).toBeNull();
    expect(await verifyCtxCookie("not-a-valid-token", { secret: TEST_SECRET })).toBeNull();
    expect(await verifyCtxCookie(null, { secret: TEST_SECRET })).toBeNull();

    vi.stubEnv("THI360_CTX_SECRET", "");
    expect(resolveCtxSecret(undefined, "production")).toBeNull();
    expect(resolveCtxSecret(undefined, "development")).toBe("ctx-dev-only-change-me-32-bytes!!");
    expect(resolveCtxSecret("short-secret", "development")).toBeNull();
  });

  it("giữ nguyên khoảng trắng đầu cuối của THI360_CTX_SECRET khi không rỗng (khớp CtxCookieSigner)", async () => {
    const paddedSecret = "  ctx-padded-secret-must-keep-exact-bytes!!  ";
    const token = await signCtxCookie(
      {
        userId: USER_ID,
        realm: "APP",
        access: "MANAGE",
        version: 2,
        expiresAt: Math.floor(Date.now() / 1000) + 300,
      },
      paddedSecret,
    );

    vi.stubEnv("THI360_CTX_SECRET", paddedSecret);
    const claims = await verifyCtxCookie(token);
    expect(claims?.userId).toBe(USER_ID);
    expect(claims?.version).toBe(2);
    expect(resolveCtxSecret(undefined, "production")).toBe(paddedSecret);

    vi.stubEnv("THI360_CTX_SECRET", "    ");
    expect(resolveCtxSecret(undefined, "production")).toBeNull();
    expect(resolveCtxSecret(undefined, "development")).toBe("ctx-dev-only-change-me-32-bytes!!");
  });
});
