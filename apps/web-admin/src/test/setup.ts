import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// Mọi lời gọi API đi qua client giả (`@/test/fake-api`); test đăng ký phản hồi bằng `fakeApi`, lời gọi chưa đăng ký trả
// 500 `UNSTUBBED`. Màn còn chạy dữ liệu mẫu (cờ bật) không đụng tới client.
vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

// Các màn đợt 2 chạy trên dữ liệu mẫu trong test (`features/**/api.ts` chọn `api.mock.ts` theo cờ, plan B7). Test tách cờ
// đặt lại từng cờ bằng `vi.stubEnv`.
process.env.NEXT_PUBLIC_THI360_CONTENT_MOCK = "true";
process.env.NEXT_PUBLIC_THI360_GRADING_MOCK = "true";

afterEach(() => {
  cleanup();
});

// jsdom thiếu các API mà Radix và sonner gọi.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub;

if (typeof Element !== "undefined") {
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.setPointerCapture ??= () => {};
  Element.prototype.releasePointerCapture ??= () => {};
  Element.prototype.scrollIntoView ??= () => {};
}

if (typeof window !== "undefined") {
  window.matchMedia ??= (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

// jsdom không đo layout: ProseMirror/TipTap gọi getClientRects khi scrollToSelection/paste.
const emptyDomRectList = {
  length: 0,
  item: () => null,
  [Symbol.iterator]: function* () {},
} as unknown as DOMRectList;

if (typeof Range !== "undefined") {
  Range.prototype.getClientRects ??= () => emptyDomRectList;
  Range.prototype.getBoundingClientRect ??= () => new DOMRect();
}
if (typeof Element !== "undefined") {
  Element.prototype.getClientRects ??= () => emptyDomRectList;
}
