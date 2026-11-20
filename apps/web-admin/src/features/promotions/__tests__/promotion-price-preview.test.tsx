import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionForm } from "../components/promotion-form";
import { PromotionPricePreviewCard } from "../components/promotion-price-preview";
import {
  EXAM_ID_1,
  EXAM_ID_2,
  EXAM_KINDS_PATH,
  EXAMS_PATH,
  OPS_PROMO_PERMISSIONS,
  PROMO_ID_1,
  PROMO_PREVIEW_PATH,
  promotionDetailFixture,
  renderWithPermissions,
} from "./promotion-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => `/promotions/${PROMO_ID_1}/edit`,
  useSearchParams: () => new URLSearchParams(),
}));

withRealApi();

afterEach(() => {
  vi.restoreAllMocks();
});

describe("PromotionPricePreviewCard (AC-5)", () => {
  it("AC-5: khi tạo mới (chưa lưu) khóa chọn bài thi và hiện giải thích gắn aria-describedby", () => {
    renderWithPermissions(<PromotionPricePreviewCard />);

    const select = screen.getByRole("combobox", { name: "Chọn bài thi để kiểm tra" });
    expect(select).toBeDisabled();
    const describedById = select.getAttribute("aria-describedby");
    expect(describedById).toBeTruthy();
    expect(document.getElementById(describedById!)).toHaveTextContent(/lưu khuyến mãi trước/i);
    expect(fakeApi.calls).toHaveLength(0);
  });

  it("gọi POST /price-preview khi đã có promotionId, hiển thị giá sàn và lỗi 410 ngừng bán", async () => {
    fakeApi.get(
      EXAMS_PATH,
      ok({
        items: [
          { id: EXAM_ID_1, code: "HSA-01", name: "HSA Test 1", kind: "HSA", scope: "FULL", status: "PUBLISHED" },
          { id: EXAM_ID_2, code: "HSA-02", name: "HSA Test 2", kind: "HSA", scope: "FULL", status: "PUBLISHED" },
        ],
        page: 0,
        size: 20,
        totalItems: 2,
        totalPages: 1,
      }),
    );

    fakeApi.post(PROMO_PREVIEW_PATH, (call) => {
      const body = call.options?.body as { examId?: string };
      if (body.examId === EXAM_ID_2) {
        return fail(410, { code: "PRODUCT_DISCONTINUED", message: "Stopped on backend" });
      }
      return ok({
        attemptsGranted: 3,
        changed: true,
        discountApplied: "PROMOTION_ONLY",
        listPrice: 100000,
        finalPrice: 50000,
        productCode: "HSA-01",
        productName: "HSA Test 1",
        versionNo: 2,
        voucherNotBetter: false,
      });
    });

    const user = userEvent.setup();
    renderWithPermissions(
      <PromotionPricePreviewCard
        promotionId={PROMO_ID_1}
        capWarnings={[
          {
            examId: EXAM_ID_1,
            examName: "HSA Test 1",
            listPrice: 100000,
            floorPrice: 50000,
            discountedPrice: 50000,
          },
        ]}
      />,
    );

    expect(await screen.findByText("100.000 đ")).toBeInTheDocument();
    expect(screen.getByText("-50.000 đ")).toBeInTheDocument();
    expect(screen.getByText("Đã giữ ở giá sàn")).toBeInTheDocument();

    // Chọn bài thứ 2 gặp 410 -> hiện thông báo bản địa hóa, không rò rỉ thông điệp gốc
    const select = screen.getByRole("combobox", { name: "Chọn bài thi để kiểm tra" });
    await user.click(select);
    await user.click(await screen.findByRole("option", { name: /HSA-02/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/không còn bán/i);
    expect(screen.queryByText("Stopped on backend")).not.toBeInTheDocument();
  });

  it("ops.admin không có EXAM.VIEW vẫn xem trước được từ scopeItems gắn sẵn", async () => {
    fakeApi.post(PROMO_PREVIEW_PATH, ok({
      attemptsGranted: 2,
      changed: true,
      discountApplied: "VOUCHER_ONLY",
      listPrice: 200000,
      finalPrice: 160000,
      productCode: "E1",
      productName: "Scoped Exam",
      versionNo: 1,
      voucherNotBetter: false,
    }));

    renderWithPermissions(
      <PromotionPricePreviewCard
        promotionId={PROMO_ID_1}
        scopeItems={[{ id: EXAM_ID_1, code: "E1", name: "Scoped Exam", type: "EXAM" }]}
      />,
      { permissions: OPS_PROMO_PERMISSIONS },
    );

    await waitFor(() => {
      expect(screen.getByText("160.000 đ")).toBeInTheDocument();
    });
    expect(fakeApi.calls.some((c) => c.path === EXAMS_PATH)).toBe(false);
  });

  it("chế độ en-GB: hiển thị tiền VND, hàng giá sàn dùng bg-warning-bg (>= 4.5:1) và không rò rỉ tên đề tiếng Việt qua Select trong form sửa", async () => {
    fakeApi.get(EXAM_KINDS_PATH, ok([]));
    fakeApi.get(
      EXAMS_PATH,
      ok({
        items: [
          {
            id: EXAM_ID_1,
            code: "HSA-01",
            name: "Đề luyện tổng hợp HSA số 1",
            kind: "HSA",
            scope: "FULL",
            status: "PUBLISHED",
          },
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      }),
    );
    fakeApi.post(
      PROMO_PREVIEW_PATH,
      ok({
        attemptsGranted: 1,
        changed: true,
        discountApplied: "PROMOTION_ONLY",
        listPrice: 19000,
        finalPrice: 5000,
        productCode: "HSA-01",
        productName: "Đề luyện tổng hợp HSA số 1",
        versionNo: 1,
        voucherNotBetter: false,
      }),
    );

    const { container } = renderWithPermissions(
      <PromotionForm
        mode="edit"
        initialDetail={promotionDetailFixture({
          id: PROMO_ID_1,
          kind: "AUTO",
          discountType: "AMOUNT",
          discountValue: 26000,
          capWarnings: [
            {
              examId: EXAM_ID_1,
              examName: "Đề luyện tổng hợp HSA số 1",
              listPrice: 19000,
              floorPrice: 5000,
              discountedPrice: 5000,
            },
          ],
        })}
      />,
      { locale: "en-GB" },
    );

    const floorPriceValue = await screen.findAllByText("5,000 VND");
    expect(floorPriceValue.length).toBeGreaterThanOrEqual(2);
    const floorRow = floorPriceValue[0]!.closest("div");
    expect(floorRow?.className).toContain("bg-warning-bg");

    // Ensure no <option> or text node outside [data-exam-content] contains Vietnamese exam title
    const leakedVietnamese = Array.from(container.querySelectorAll("*"))
      .filter((el) => !el.closest("[data-exam-content]") && el.children.length === 0)
      .map((el) => el.textContent ?? "")
      .filter((txt) => /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i.test(txt));
    expect(leakedVietnamese).toEqual([]);
  });
});
