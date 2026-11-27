import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { GLOBAL_ERROR_MESSAGES } from "@/messages/global-error";
import viMessages from "@/messages/vi.json";
import GlobalError from "./global-error";

describe("web-admin global error page", () => {
  it("tự dựng html/body với một <main>, tiêu đề, mã yêu cầu và hai nút; bản máy chủ mặc định tiếng Việt, chế độ sáng", () => {
    const error = Object.assign(new Error("boom"), { digest: "digest-123" });
    const html = renderToStaticMarkup(<GlobalError error={error} reset={vi.fn()} />);

    expect(html).toMatch(/^<html lang="vi" data-theme="light">/);
    expect(html.match(/<main/g)).toHaveLength(1);
    expect(html).toContain("<h1");
    expect(html).toContain(viMessages.errorPage.title);
    expect(html).toContain("Khi cần hỗ trợ, gửi kèm mã này: digest-123");
    expect(html).toContain(viMessages.common.retry);
    expect(html).toContain(viMessages.common.backHome);
  });

  it("chuỗi của trang lỗi toàn cục trùng với tệp dịch của app ở cả hai ngôn ngữ", () => {
    for (const [locale, messages] of [
      ["vi", viMessages],
      ["en", enMessages],
    ] as const) {
      expect(GLOBAL_ERROR_MESSAGES[locale]).toEqual({
        eyebrow: messages.errorPage.eyebrow,
        title: messages.errorPage.title,
        description: messages.errorPage.description,
        traceLabel: messages.errorPage.traceLabel,
        retry: messages.common.retry,
        backHome: messages.common.backHome,
      });
    }
  });
});
