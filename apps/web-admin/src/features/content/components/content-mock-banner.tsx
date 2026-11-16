"use client";

import { MockDataBanner } from "@/components/mock-data-banner";
import { isContentMockEnabled } from "@/lib/mock/flags";

/**
 * Dải "Dữ liệu mẫu" của các màn Ngân hàng câu hỏi và Nhập câu hỏi: chỉ hiện khi cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK`
 * bật, tức màn đang chạy `api.mock.ts`. Màn đã nối API thật không hiện dải này.
 */
export function ContentMockBanner({ className }: { className?: string }) {
  return isContentMockEnabled() ? <MockDataBanner className={className} /> : null;
}
