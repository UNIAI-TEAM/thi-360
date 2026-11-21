import { createTranslator } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";

const state = vi.hoisted(() => ({ locale: "vi" as "vi" | "en" }));

vi.mock("next-intl/server", () => ({
  getLocale: async () => state.locale,
  getTranslations: async (namespace: string) =>
    createTranslator({
      locale: state.locale,
      messages: state.locale === "vi" ? viMessages : enMessages,
      namespace: namespace as "metadata",
    }),
}));
vi.mock("next/font/google", () => ({ Be_Vietnam_Pro: () => ({ variable: "--font-be-vietnam-pro" }) }));
vi.mock("next/headers", () => ({ cookies: async () => ({ get: () => undefined }) }));

describe("web-student tiêu đề trang", () => {
  it("layout gốc đặt mẫu '<tên trang> · Thi 360'; các trang con chỉ trả tên trang, not-found tự ghép theo cùng mẫu", async () => {
    const layout = await (await import("./layout")).generateMetadata();
    expect(layout.title).toEqual({ default: "Thi 360 — Luyện thi trực tuyến", template: "%s · Thi 360" });
    expect(layout.description).not.toMatch(/HSA và QDA/);

    expect((await (await import("./login/page")).generateMetadata()).title).toBe("Đăng nhập");
    expect((await (await import("./change-password/page")).generateMetadata()).title).toBe("Đặt mật khẩu mới");
    expect((await (await import("./home/page")).generateMetadata()).title).toBe("Bài thi của tôi");
    expect((await (await import("./forbidden/page")).generateMetadata()).title).toBe(
      "Bạn không có quyền vào trang này",
    );
    expect((await (await import("./not-found")).generateMetadata()).title).toBe("Không tìm thấy trang · Thi 360");
  });

  it("bản tiếng Anh dùng cùng mẫu", async () => {
    state.locale = "en";
    try {
      const layout = await (await import("./layout")).generateMetadata();
      expect(layout.title).toMatchObject({ template: "%s · Thi 360" });
      expect((await (await import("./home/page")).generateMetadata()).title).toBe("My exams");
      expect((await (await import("./not-found")).generateMetadata()).title).toBe(
        `${enMessages.notFound.title} · Thi 360`,
      );
    } finally {
      state.locale = "vi";
    }
  });
});
