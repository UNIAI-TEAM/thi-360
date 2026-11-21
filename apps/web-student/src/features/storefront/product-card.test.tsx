import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import enMessages from "@/messages/en.json";
import { FULL_CARD, PART_CARD, REVIEW_CARD, VI_ONLY_CARD } from "@/test/storefront-fixtures";
import { renderWithProviders } from "@/test/render";
import { AccessAction } from "./access-action";
import { ProductCard } from "./product-card";
import type { ProductCard as ProductCardData } from "./types";

const BUNDLE_CARD: ProductCardData = {
  code: "BO-SP2E-001",
  name: "Bộ đề SP2E khối A00",
  summary: "Luyện ba môn Toán, Vật lí, Hóa học",
  versionNo: 1,
  attemptsGranted: 2,
  buildMode: "STANDARD",
  productType: "BUNDLE",
  format: "BUNDLE",
  examKind: { code: "SP2E", nameVi: "ĐHSP Hà Nội 2", nameEn: "HNUE2" },
  listPrice: 349000,
  price: 349000,
  questionCount: { min: 96, max: 96 },
  durationMinutes: { min: 210, max: 210 },
};

const action = <button type="button">Hanh dong</button>;

function card(name: string) {
  return screen.getByRole("heading", { name }).closest("article")!;
}

describe("ProductCard", () => {
  it("thẻ có ảnh, tên, chip kỳ thi và hình thức, 'n câu · m phút · k lượt' và giá (AC-1)", () => {
    const { container } = renderWithProviders(<ProductCard product={FULL_CARD} action={action} />);

    const article = card(FULL_CARD.name);
    expect(container.querySelector("img")).toHaveAttribute("src", FULL_CARD.coverUrl);
    expect(within(article).getByRole("link", { name: FULL_CARD.name })).toHaveAttribute("href", "/exams/HSA-FULL-01");
    expect(within(article).getByText("HSA")).toBeInTheDocument();
    expect(within(article).getByText("Bài thi FULL")).toBeInTheDocument();
    expect(within(article).getByText("150 câu · 195 phút · 3 lượt")).toBeInTheDocument();
    expect(within(article).getByText("199.000 đ")).toBeInTheDocument();
    expect(within(article).getByRole("button", { name: "Hanh dong" })).toBeInTheDocument();
  });

  it("có khuyến mãi: giá gốc gạch ngang, chip mức giảm và 'Kết thúc dd/MM' theo giờ Việt Nam (AC-1)", () => {
    renderWithProviders(<ProductCard product={FULL_CARD} action={action} />);
    const article = card(FULL_CARD.name);

    const listPrice = within(article).getByText("249.000 đ");
    expect(listPrice.closest("s")).not.toBeNull();
    expect(within(article).getByText("Giá gốc", { exact: false })).toHaveClass("sr-only");
    expect(within(article).getByText("Giảm 20%")).toBeInTheDocument();
    // 2026-11-30T16:59Z là 23:59 ngày 30/11 ở UTC+7.
    expect(within(article).getByText("Kết thúc 30/11")).toBeInTheDocument();
  });

  it("không có khuyến mãi thì không có giá gốc, chip giảm giá hay ngày kết thúc", () => {
    renderWithProviders(<ProductCard product={PART_CARD} action={action} />);
    const article = card(PART_CARD.name);

    expect(article.querySelector("s")).toBeNull();
    expect(within(article).queryByText(/Giảm/)).toBeNull();
    expect(within(article).queryByText(/Kết thúc/)).toBeNull();
    expect(within(article).getByText("79.000 đ")).toBeInTheDocument();
  });

  it("bài theo phần ghi tên phần ở chip hình thức, số câu là khoảng khi các đường khác nhau", () => {
    const { container } = renderWithProviders(<ProductCard product={PART_CARD} action={action} />);
    const article = card(PART_CARD.name);

    expect(within(article).getByText(/Bài thi theo phần/)).toHaveTextContent(
      "Bài thi theo phần · Toan hoc va Xu li so lieu",
    );
    expect(within(article).getByText("48–50 câu · 75 phút · 3 lượt")).toBeInTheDocument();
    // Không có ảnh bìa: ảnh mặc định, không có thẻ <img> lỗi.
    expect(container.querySelector("img")).toBeNull();
  });

  it("bài ôn có nhãn 'Bài ôn', bài thường thì không (AC-18)", () => {
    const view = renderWithProviders(<ProductCard product={REVIEW_CARD} action={action} />);
    expect(within(card(REVIEW_CARD.name)).getByText("Bài ôn")).toBeInTheDocument();
    view.unmount();

    renderWithProviders(<ProductCard product={FULL_CARD} action={action} />);
    expect(within(card(FULL_CARD.name)).queryByText("Bài ôn")).toBeNull();
  });

  it("nội dung bài thi gắn data-exam-content: tên bài và tên phần; chữ giao diện thì không", () => {
    renderWithProviders(<ProductCard product={PART_CARD} action={action} />);
    const article = card(PART_CARD.name);

    expect(within(article).getByRole("link", { name: PART_CARD.name })).toHaveAttribute("data-exam-content");
    const scope = article.querySelector("[data-exam-content]:not(a)");
    expect(scope).toHaveTextContent("Toan hoc va Xu li so lieu");
    expect(within(article).getByText("79.000 đ").closest("[data-exam-content]")).toBeNull();
  });

  it("tên môn (SUBJECT) trên chip không gắn data-exam-content: pickName còn nằm trong phép kiểm lẫn ngôn ngữ", () => {
    renderWithProviders(<ProductCard product={REVIEW_CARD} action={action} />);
    const article = card(REVIEW_CARD.name);

    const scope = within(article).getByText("Toan");
    expect(scope.closest("[data-exam-content]")).toBeNull();
  });

  it("giao diện tiếng Anh: nameEn rỗng thì chip hiện nameVi, nameEn khác nameVi thì hiện nameEn (A7)", () => {
    const viOnly = renderWithProviders(<ProductCard product={VI_ONLY_CARD} action={action} />, {
      locale: "en",
      messages: enMessages,
    });
    expect(within(card(VI_ONLY_CARD.name)).getByText(/Subject exam/)).toHaveTextContent("Subject exam · Lich su");
    viOnly.unmount();

    renderWithProviders(<ProductCard product={REVIEW_CARD} action={action} />, {
      locale: "en",
      messages: enMessages,
    });
    expect(within(card(REVIEW_CARD.name)).getByText(/Subject exam/)).toHaveTextContent("Subject exam · Math");
  });


  it("giao diện tiếng Anh: chữ giao diện tiếng Anh, tên môn theo nameEn, số tiếng Anh", () => {
    renderWithProviders(<ProductCard product={REVIEW_CARD} action={action} />, {
      locale: "en",
      messages: enMessages,
    });
    const article = card(REVIEW_CARD.name);

    expect(within(article).getByText("Review exam")).toBeInTheDocument();
    expect(within(article).getByText(/Subject exam/)).toHaveTextContent("Subject exam · Math");
    expect(within(article).getByText("48–50 questions · 75 minutes · 3 attempts")).toBeInTheDocument();
    expect(within(article).getByText("79,000 VND")).toBeInTheDocument();
  });

  it("thẻ bộ đề: hiện dải hero-dark chữ trắng (không dùng text-on-primary), nhãn Bộ đề, lượt mỗi bài và nút Mua bộ đề (AC-10)", () => {
    const { container } = renderWithProviders(
      <ProductCard
        product={BUNDLE_CARD}
        action={
          <AccessAction
            variant="card"
            product={{ code: BUNDLE_CARD.code, versionNo: BUNDLE_CARD.versionNo, price: BUNDLE_CARD.price, isBundle: true }}
            view={{
              kind: "ready",
              row: {
                code: BUNDLE_CARD.code,
                state: "NONE",
                attemptsLeft: 0,
                entitlementId: null,
                activeAttemptId: null,
                hasPendingFulfillment: false,
                pendingOrderCode: null,
              },
            }}
            onReload={() => {}}
          />
        }
      />,
    );

    const article = card(BUNDLE_CARD.name);
    const banner = article.querySelector(".text-white");
    expect(banner).not.toBeNull();
    expect(banner).not.toHaveClass("text-on-primary");
    expect(container.querySelector("img")).toBeNull();
    expect(within(article).getAllByText("Bộ đề").length).toBeGreaterThanOrEqual(1);
    expect(within(article).getByText("96 câu · 210 phút · 2 lượt mỗi bài")).toBeInTheDocument();
    expect(within(article).getByRole("link", { name: BUNDLE_CARD.name })).toHaveAttribute("data-exam-content");
    expect(within(article).getByRole("link", { name: "Mua bộ đề" })).toHaveAttribute(
      "href",
      "/checkout/BO-SP2E-001?v=1&p=349000",
    );
    expect(within(article).getByRole("link", { name: "Xem chi tiết" })).toHaveAttribute("href", "/exams/BO-SP2E-001");
  });
});
