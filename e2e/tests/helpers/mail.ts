import { expect, request as playwrightRequest } from "@playwright/test";

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
