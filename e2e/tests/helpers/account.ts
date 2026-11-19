import { expect, request as playwrightRequest, type Page } from "@playwright/test";
import { deflateSync, crc32 } from "node:zlib";
import { readOtp, waitForMail } from "./mail";

/** Dùng chung cho các spec `account-*`: tài khoản học viên mới mỗi lần chạy (hậu tố ngẫu nhiên), đăng ký qua UI hoặc API. */
export const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");

/** Đạt đủ quy tắc mật khẩu (8–64 ký tự, hoa, thường, số, ký tự đặc biệt) và không trùng tên đăng nhập. */
export const ACCOUNT_PASSWORD = "Thi360@e2e1";
export const NEW_PASSWORD = "Thi360@moi2";
export const TERMS_VERSION = "2026-11";

export interface E2eAccount {
  username: string;
  fullName: string;
  email: string;
  password: string;
}

let counter = 0;

export function newAccount(prefix: string): E2eAccount {
  counter += 1;
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}${counter}`;
  const username = `${prefix}.${suffix}`.slice(0, 30);
  return {
    username,
    fullName: "E2E Student",
    email: `${username.replace(/[^a-z0-9.]/gi, "")}@e2e.thi360.vn`,
    password: ACCOUNT_PASSWORD,
  };
}

export type SessionCookies = Awaited<ReturnType<Awaited<ReturnType<typeof playwrightRequest.newContext>>["storageState"]>>["cookies"];

/** Đăng ký qua API công khai của web (cùng đường với biểu mẫu); trả cookie phiên vừa được tạo. */
export async function registerViaApi(account: E2eAccount): Promise<SessionCookies> {
  const api = await playwrightRequest.newContext();
  try {
    const response = await api.post(`${WEB_URL}/api/v1/auth/register`, {
      headers: { origin: WEB_URL },
      data: {
        username: account.username,
        fullName: account.fullName,
        email: account.email,
        password: account.password,
        acceptTerms: true,
        termsVersion: TERMS_VERSION,
      },
    });
    expect(response.status(), `đăng ký ${account.username}: ${await response.text()}`).toBe(201);
    return (await api.storageState()).cookies;
  } finally {
    await api.dispose();
  }
}

/** Xác minh email của tài khoản vừa đăng ký bằng mã OTP đọc từ thư thử. */
export async function verifyEmailViaApi(cookies: SessionCookies, account: E2eAccount): Promise<void> {
  const mail = await waitForMail(account.email, "EMAIL_VERIFY");
  const xsrf = cookies.find((cookie) => cookie.name === "thi360_xsrf")?.value ?? "";
  const api = await playwrightRequest.newContext({ storageState: { cookies, origins: [] } });
  try {
    const response = await api.post(`${WEB_URL}/api/v1/me/email/verify`, {
      headers: { origin: WEB_URL, "X-XSRF-TOKEN": xsrf },
      data: { code: readOtp(mail) },
    });
    expect(response.status(), `xác minh email ${account.email}: ${await response.text()}`).toBe(204);
  } finally {
    await api.dispose();
  }
}

/** Tài khoản đã đăng ký và đã xác minh email, trả cookie phiên để nạp vào context của trình duyệt. */
export async function createVerifiedAccount(prefix: string): Promise<{ account: E2eAccount; cookies: SessionCookies }> {
  const account = newAccount(prefix);
  const cookies = await registerViaApi(account);
  await verifyEmailViaApi(cookies, account);
  return { account, cookies };
}

export async function fillRegisterForm(page: Page, account: E2eAccount): Promise<void> {
  await page.getByLabel(/^Tên đăng nhập/).fill(account.username);
  await page.getByLabel(/^Họ và tên/).fill(account.fullName);
  await page.getByLabel(/^Email/).fill(account.email);
  await page.getByLabel(/^Mật khẩu/).fill(account.password);
  await page.getByRole("checkbox").check();
}

export async function submitLoginForm(page: Page, identifier: string, password: string): Promise<void> {
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

/** Chờ URL có đường dẫn `path` và `callbackUrl` đúng `callbackUrl` (so sau khi giải mã, không phụ thuộc cách mã hóa). */
export async function expectUrlWithCallback(page: Page, path: string, callbackUrl: string | null): Promise<void> {
  await page.waitForURL((url) => url.pathname === path && url.searchParams.get("callbackUrl") === callbackUrl);
}

function pngChunk(type: string, data: Buffer): Buffer {
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, checksum]);
}

/** PNG đơn sắc `size` × `size` px, đủ để máy chủ đọc và cắt vuông (không cần thư viện ảnh). */
export function solidPng(size: number): Buffer {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // độ sâu bit
  header[9] = 2; // RGB
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(size * 3, 0x66)]);
  const raw = Buffer.concat(Array.from({ length: size }, () => row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk("IHDR", header),
    pngChunk("IDAT", deflateSync(raw)),
    pngChunk("IEND", Buffer.alloc(0)),
  ]);
}
