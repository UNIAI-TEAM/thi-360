import { vi } from "vitest";

// Chỉ các test của content, questions và question-import nạp file này (vitest.config.mts, project "fake-client"): mọi lời
// gọi API đi qua client giả (`@/test/fake-api`); test đăng ký phản hồi bằng `fakeApi`, lời gọi chưa đăng ký trả 500
// `UNSTUBBED`. Test của màn khác (session, users...) vẫn dùng client thật của chúng, nên không nạp ở `setup.ts` chung.
vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

// Project fake-client chạy nhánh API thật: cả hai cờ dữ liệu mẫu tắt cho mọi test (plan B7). `setup.ts` chung bật cờ
// cho các project khác; file này ghi đè sau nó. Test của mã chỉ có bản mẫu tự bật lại bằng `vi.stubEnv` trong file đó.
process.env.NEXT_PUBLIC_THI360_CONTENT_MOCK = "false";
process.env.NEXT_PUBLIC_THI360_GRADING_MOCK = "false";
