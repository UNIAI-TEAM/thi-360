// @vitest-environment node
import { CTX_COOKIE_NAME, signCtxCookie } from "@thi360/web-kit/ctx";
import { createTranslator } from "next-intl";
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { proxy } from "@/proxy";

const SECRET = "ctx-dev-only-change-me-32-bytes!!";
const USER_ID = "00000000-0000-4000-8000-000000000001";

const localeState = vi.hoisted(() => ({ locale: "vi" as "vi" | "en" }));

vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: string) =>
    createTranslator({
      locale: localeState.locale,
      messages: localeState.locale === "vi" ? viMessages : enMessages,
      namespace: namespace as "account.deletion",
    }),
}));

beforeEach(() => {
  localeState.locale = "vi";
  vi.stubEnv("THI360_CTX_SECRET", SECRET);
  vi.stubEnv("THI360_WEB_URL", "http://localhost:3000");
  vi.stubEnv("THI360_ADMIN_URL", "http://localhost:3001");
  vi.stubEnv("THI360_API_URL", "http://api.internal:8080");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("account deletion route guard (AC-4, AC-8)", () => {
  it("chưa đăng nhập mở /account/delete bị chuyển 307 sang /login kèm callbackUrl là /account/delete", async () => {
    const response = await proxy(new NextRequest("http://localhost:3000/account/delete"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Faccount%2Fdelete",
    );
  });

  it("đã đăng nhập với thi360_ctx hợp lệ thì vào được /account/delete", async () => {
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

    const response = await proxy(
      new NextRequest("http://localhost:3000/account/delete", {
        headers: { cookie: `${CTX_COOKIE_NAME}=${token}` },
      }),
    );
    expect(response.status).toBe(200);
  });

  it("#afterDeletion: sau khi xóa không còn phiên, mở /account/profile hay /account/delete đều bị đưa về /login", async () => {
    const profileRes = await proxy(new NextRequest("http://localhost:3000/account/profile"));
    expect(profileRes.status).toBe(307);
    expect(profileRes.headers.get("location")).toBe(
      "http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Faccount%2Fprofile",
    );

    const deleteRes = await proxy(new NextRequest("http://localhost:3000/account/delete"));
    expect(deleteRes.status).toBe(307);
    expect(deleteRes.headers.get("location")).toBe(
      "http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Faccount%2Fdelete",
    );

    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response("{}", { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);
    const revokedRes = await proxy(
      new NextRequest("http://localhost:3000/account/delete", {
        headers: { cookie: "thi360_rt=revoked-after-deletion" },
      }),
    );
    expect(revokedRes.status).toBe(307);
    expect(revokedRes.headers.get("location")).toBe(
      "http://localhost:3000/login?callbackUrl=http%3A%2F%2Flocalhost%3A3000%2Faccount%2Fdelete",
    );
  });

  it("metadata của /account/delete trả tiêu đề tiếng Việt và tiếng Anh khớp mẫu '<title> · Thi360'", async () => {
    const pageModule = await import("@/app/account/delete/page");
    expect((await pageModule.generateMetadata()).title).toBe("Xóa tài khoản");

    localeState.locale = "en";
    expect((await pageModule.generateMetadata()).title).toBe("Delete account");
  });
});
