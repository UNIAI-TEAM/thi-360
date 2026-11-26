import { createTranslator } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import viMessages from "@/messages/vi.json";

vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: string) =>
    createTranslator({ locale: "vi", messages: viMessages, namespace: namespace as "metadata" }),
}));

describe("web-admin tiêu đề trang", () => {
  it("Bảng điều khiển, trang 403 và trang 404 cùng mẫu '<tên trang> · Thi360'", async () => {
    expect((await (await import("./(shell)/page")).generateMetadata()).title).toBe("Bảng điều khiển · Thi360");
    expect((await (await import("./forbidden/page")).generateMetadata()).title).toBe(
      "Bạn không có quyền vào trang này · Thi360",
    );
    expect((await (await import("./not-found")).generateMetadata()).title).toBe("Không tìm thấy trang · Thi360");
  });
});
