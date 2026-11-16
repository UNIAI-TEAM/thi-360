import { vi } from "vitest";

// Chỉ các test của content, questions và question-import nạp file này (vitest.config.mts, project "fake-client"): mọi lời
// gọi API đi qua client giả (`@/test/fake-api`); test đăng ký phản hồi bằng `fakeApi`, lời gọi chưa đăng ký trả 500
// `UNSTUBBED`. Test của màn khác (session, users...) vẫn dùng client thật của chúng, nên không nạp ở `setup.ts` chung.
vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
