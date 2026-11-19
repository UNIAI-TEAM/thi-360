import { expect, request as playwrightRequest, type Page } from "@playwright/test";
import { crc32, deflateSync } from "node:zlib";

/**
 * Đọc thư của hệ thống qua `GET /api/v1/test/mail` (chỉ có ở profile `e2e`, plan A6, task ML): trả thư mới nhất theo
 * người nhận và loại thư, không cần đăng nhập. Thư được xếp hàng rồi worker gửi sau phản hồi, nên hàm đọc thăm dò tới
 * khi có thư (404 là chưa có).
 */
const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");

export type TestMailKind = "EMAIL_VERIFY" | "EMAIL_CHANGE" | "EMAIL_CHANGED_NOTICE" | "PASSWORD_RESET";

export interface TestMail {
  id: string;
  kind: TestMailKind;
  to: string;
  subject: string;
  text: string;
  html: string;
}

export interface WaitForMailOptions {
  /** Bỏ qua thư có `id` này (lần gửi lại: đợi thư mới, không lấy thư cũ). */
  exceptId?: string;
  timeoutMs?: number;
}

export async function waitForMail(to: string, kind: TestMailKind, options: WaitForMailOptions = {}): Promise<TestMail> {
  const api = await playwrightRequest.newContext();
  try {
    let found: TestMail | undefined;
    await expect
      .poll(
        async () => {
          const response = await api.get(`${API_URL}/api/v1/test/mail`, { params: { to, kind } });
          if (response.status() === 404) return false;
          expect(response.status(), `GET /api/v1/test/mail trả ${response.status()}: bật profile e2e cho backend`).toBe(200);
          const mail = (await response.json()) as TestMail;
          if (options.exceptId && mail.id === options.exceptId) return false;
          found = mail;
          return true;
        },
        { message: `thư ${kind} tới ${to}`, timeout: options.timeoutMs ?? 30_000, intervals: [250, 500, 1000] },
      )
      .toBe(true);
    return found as TestMail;
  } finally {
    await api.dispose();
  }
}

/** Mã OTP 6 số trong thân thư `EMAIL_VERIFY` và `EMAIL_CHANGE`. */
export function readOtp(mail: TestMail): string {
  const match = /(?<!\d)(\d{6})(?!\d)/.exec(mail.text);
  expect(match, `không thấy mã 6 số trong thư ${mail.kind}`).not.toBeNull();
  return match![1]!;
}

/** `token` trong liên kết `/reset-password?token=…` của thư `PASSWORD_RESET` (E16: liên kết chỉ có `token`). */
export function readResetToken(mail: TestMail): string {
  const match = /\/reset-password\?token=([^\s"'<>&]+)/.exec(mail.text);
  expect(match, "không thấy liên kết đặt lại mật khẩu trong thư PASSWORD_RESET").not.toBeNull();
  return decodeURIComponent(match![1]!);
}

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
