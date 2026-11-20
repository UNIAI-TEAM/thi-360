import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import LandingPage from "./page";

vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return { ...actual, loadOptionalSession: async () => null };
});

vi.mock("@/features/storefront/server-api", () => ({
  loadFiltersOnServer: async () => ({ status: "ok", data: { kinds: [], formats: [], parts: [], subjects: [] } }),
  loadProductsOnServer: async () => ({
    status: "ok",
    data: { items: [], page: 0, size: 12, totalItems: 0, totalPages: 0 },
  }),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

async function renderLanding(options?: Parameters<typeof renderWithProviders>[1]) {
  const tree = await LandingPage({ searchParams: Promise.resolve({}) });
  return renderWithProviders(tree, options);
}

describe("web-student landing page", () => {
  it("có đúng một <main> chứa đủ 6 khối; header và footer nằm ngoài <main> (AC-15)", async () => {
    await renderLanding();

    const mains = screen.getAllByRole("main");
    expect(mains).toHaveLength(1);
    const main = mains[0]!;
    expect(main).toContainElement(screen.getByRole("heading", { level: 1 }));
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Luyện thi trực tuyến theo đúng cấu trúc đề");
    for (const name of [
      "Chọn bài thi",
      "Vì sao luyện thi tại đây?",
      "Từ chọn bài đến xem kết quả",
      "Câu hỏi thường gặp",
      "Bạn cần hỗ trợ?",
    ]) {
      expect(main).toContainElement(screen.getByRole("heading", { level: 2, name }));
    }
    // Khối 1 là vùng có tên theo tiêu đề h1; khối danh mục có neo #exams.
    expect(main.querySelector("section#exams")).not.toBeNull();
    expect(main).not.toContainElement(screen.getByRole("banner"));
    expect(main).not.toContainElement(screen.getByRole("contentinfo"));
  });

  it("đủ 6 câu hỏi thường gặp, hai câu đầu mở sẵn; 4 bước; nút Khám phá bài thi trỏ tới #exams (AC-15)", async () => {
    await renderLanding();

    const faq = screen.getByRole("region", { name: "Câu hỏi thường gặp" });
    const items = faq.querySelectorAll("details");
    expect(items).toHaveLength(6);
    expect(Array.from(items).map((item) => item.open)).toEqual([true, true, false, false, false, false]);
    expect(faq).toHaveTextContent("Khi nào được hoàn tiền?");

    const steps = screen.getByRole("region", { name: "Từ chọn bài đến xem kết quả" });
    expect(within(steps).getAllByRole("listitem")).toHaveLength(4);
    expect(within(steps).getByRole("link", { name: "Chọn bài thi" })).toHaveAttribute("href", "#exams");
    expect(screen.getByRole("link", { name: /Khám phá bài thi/ })).toHaveAttribute("href", "#exams");
  });

  it("không câu trả lời nào của FAQ là chuỗi giữ chỗ trong ngoặc vuông, ở cả hai ngôn ngữ", async () => {
    for (const options of [undefined, { locale: "en", messages: enMessages }] as const) {
      const view = await renderLanding(options);
      const answers = Array.from(view.container.querySelectorAll("#faq details > :not(summary)"));
      expect(answers).toHaveLength(6);
      for (const answer of answers) expect(answer.textContent?.trim()).not.toMatch(/^\[.*\]$/);
      view.unmount();
    }
    for (const messages of [viMessages, enMessages]) {
      for (const item of Object.values(messages.landing.faq.items)) expect(item.answer).not.toMatch(/^\[.*\]$/);
      expect(messages.storefront.detail.policy.refundPending).not.toMatch(/^\[.*\]$/);
    }
  });

  it("không còn chữ chỉ nói HSA và QDA, không nói tới thời hạn sử dụng (C8), ở cả hai ngôn ngữ", async () => {
    const vi_ = await renderLanding();
    const viText = vi_.container.textContent ?? "";
    vi_.unmount();
    const en_ = await renderLanding({ locale: "en", messages: enMessages });
    const enText = en_.container.textContent ?? "";

    for (const text of [viText, enText]) {
      expect(text).not.toMatch(/HSA (và|and|·|&) QDA/);
      expect(text).not.toMatch(/thời hạn|hạn sử dụng|expir|validity/i);
    }
    expect(JSON.stringify(viMessages.landing)).not.toMatch(/HSA (và|·) QDA/);
    expect(JSON.stringify(viMessages.metadata)).not.toMatch(/HSA (và|·|,) ?QDA/);
  });

  it("bảy kỳ thi nằm trong danh sách ở khối giới thiệu", async () => {
    await renderLanding();

    const kinds = screen.getByRole("list", { name: "Các kỳ thi" });
    expect(within(kinds).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "HSA",
      "QDA",
      "BCA",
      "V-ACT",
      "SPT",
      "SP2E",
      "THPTQG",
    ]);
  });
});
