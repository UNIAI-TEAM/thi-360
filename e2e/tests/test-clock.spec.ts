import { type APIRequestContext, expect, request as playwrightRequest, test } from "@playwright/test";

const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const CLOCK_URL = `${API_URL}/api/v1/test/clock`;

interface ClockBody {
  serverNow: string;
}

async function readClock(api: APIRequestContext): Promise<number> {
  const res = await api.get(CLOCK_URL);
  expect(res.status()).toBe(200);
  const body = (await res.json()) as ClockBody;
  return Date.parse(body.serverNow);
}

// Backend chạy với profile e2e (playwright.config.ts). Không cookie, không XSRF: API thử không cần đăng nhập.
test.describe("Đồng hồ thử của profile e2e (UNI-2704)", () => {
  test("tua giờ qua API rồi đọc lại; đặt đúng giờ; đặt lại về giờ hệ thống", async () => {
    const api = await playwrightRequest.newContext();
    try {
      // Bắt đầu từ giờ hệ thống dù lần chạy trước để lại độ lệch.
      const reset = await api.post(CLOCK_URL, { data: { reset: true } });
      expect(reset.status()).toBe(200);

      const before = Date.now();
      const advanceRes = await api.post(CLOCK_URL, { data: { advanceSeconds: 3 * 3600 } });
      expect(advanceRes.status()).toBe(200);
      const advanced = Date.parse(((await advanceRes.json()) as ClockBody).serverNow);
      expect(advanced - before).toBeGreaterThanOrEqual(3 * 3600 * 1000 - 5_000);
      expect(advanced - before).toBeLessThan(3 * 3600 * 1000 + 30_000);

      // Đọc lại qua GET: cùng độ lệch, và giờ vẫn chạy tiếp.
      const read = await readClock(api);
      expect(read).toBeGreaterThanOrEqual(advanced);
      expect(read - advanced).toBeLessThan(30_000);

      const target = "2031-05-06T07:08:09.000Z";
      const setRes = await api.post(CLOCK_URL, { data: { at: target } });
      expect(setRes.status()).toBe(200);
      const afterSet = await readClock(api);
      expect(afterSet - Date.parse(target)).toBeGreaterThanOrEqual(0);
      expect(afterSet - Date.parse(target)).toBeLessThan(30_000);

      const invalid = await api.post(CLOCK_URL, { data: { at: target, advanceSeconds: 5 } });
      expect(invalid.status()).toBe(400);

      const resetAgain = await api.post(CLOCK_URL, { data: { reset: true } });
      expect(resetAgain.status()).toBe(200);
      const afterReset = await readClock(api);
      expect(Math.abs(afterReset - Date.now())).toBeLessThan(30_000);
    } finally {
      await api.post(CLOCK_URL, { data: { reset: true } });
      await api.dispose();
    }
  });
});
