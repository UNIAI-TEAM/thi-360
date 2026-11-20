import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionDetailView } from "../components/promotion-detail-view";
import { PromotionHistoryCard } from "../components/promotion-history-card";
import {
  EXAM_ID_1,
  EXAMS_PATH,
  KIND_ID_1,
  PROMO_DETAIL_PATH,
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
  usePathname: () => `/promotions/${PROMO_ID_1}`,
  useSearchParams: () => new URLSearchParams(),
}));

withRealApi();

afterEach(() => {
  vi.restoreAllMocks();
});

describe("PromotionDetail BS §10 metrics & change history (AC-19)", () => {
  it("hiển thị totalDiscountPaid, confirmedCount, reservedCount và lịch sử thay đổi mới nhất trước", async () => {
    fakeApi.get(
      EXAMS_PATH,
      ok({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }),
    );
    fakeApi.get(
      PROMO_DETAIL_PATH,
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          usedCount: 7,
          confirmedCount: 5,
          reservedCount: 2,
          totalLimit: 50,
          totalDiscountPaid: 250000,
          history: [
            {
              id: "h-old",
              action: "CREATED",
              actorUsername: "ops.creator",
              occurredAt: "2026-11-18T02:00:00Z",
              changes: [],
            },
            {
              id: "h-new",
              action: "UPDATED",
              actorUsername: "ops.editor",
              occurredAt: "2026-11-20T04:00:00Z",
              changes: [
                {
                  field: "name",
                  oldValue: "Old Campaign" as unknown as Record<string, never>,
                  newValue: "New Campaign" as unknown as Record<string, never>,
                },
                {
                  field: "totalLimit",
                  oldValue: 20 as unknown as Record<string, never>,
                  newValue: 50 as unknown as Record<string, never>,
                },
              ],
            },
          ],
        }),
      ),
    );

    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    expect(await screen.findByText("250.000 đ")).toBeInTheDocument();
    expect(screen.getByText("5 đã xác nhận · 2 đang giữ")).toBeInTheDocument();
    expect(screen.getByText("7 / 50")).toBeInTheDocument();

    const historyHeading = screen.getByRole("heading", { name: "Lịch sử thay đổi" });
    const historyCard = historyHeading.closest("div")?.parentElement as HTMLElement;
    const listItems = within(historyCard).getAllByRole("listitem");
    expect(listItems[0]).toHaveTextContent("Cập nhật");
    expect(listItems[0]).toHaveTextContent("ops.editor");
    expect(listItems[0]).toHaveTextContent("Old Campaign → New Campaign");
    expect(listItems.some((item) => item.textContent?.includes("Tạo mới"))).toBe(true);
  });

  it("hiển thị trạng thái rỗng khi chưa có lịch sử thay đổi", () => {
    renderWithPermissions(<PromotionHistoryCard history={[]} />);
    expect(screen.getByText("Chưa có lịch sử thay đổi")).toBeInTheDocument();
  });

  it("dịch enum và định dạng số/tiền trong lịch sử thay đổi ở cả vi và en-GB (không lộ mã thô hay bọc data-exam-content)", () => {
    const historyFixture = [
      {
        id: "h-create",
        action: "CREATED" as const,
        actorUsername: "ops.creator",
        occurredAt: "2026-11-18T02:00:00Z",
        changes: [
          {
            field: "kind",
            oldValue: null as unknown as Record<string, never>,
            newValue: "VOUCHER" as unknown as Record<string, never>,
          },
          {
            field: "discountType",
            oldValue: null as unknown as Record<string, never>,
            newValue: "PERCENT" as unknown as Record<string, never>,
          },
          {
            field: "discountValue",
            oldValue: null as unknown as Record<string, never>,
            newValue: 30 as unknown as Record<string, never>,
          },
          {
            field: "maxDiscountVnd",
            oldValue: null as unknown as Record<string, never>,
            newValue: 50000 as unknown as Record<string, never>,
          },
          {
            field: "scope",
            oldValue: null as unknown as Record<string, never>,
            newValue: "ALL" as unknown as Record<string, never>,
          },
          {
            field: "audience",
            oldValue: null as unknown as Record<string, never>,
            newValue: "NEW_ACCOUNT" as unknown as Record<string, never>,
          },
          {
            field: "status",
            oldValue: null as unknown as Record<string, never>,
            newValue: "ACTIVE" as unknown as Record<string, never>,
          },
        ],
      },
      {
        id: "h-stop",
        action: "STOPPED" as const,
        actorUsername: "ops.editor",
        occurredAt: "2026-11-19T03:00:00Z",
        changes: [
          {
            field: "status",
            oldValue: "ACTIVE" as unknown as Record<string, never>,
            newValue: "STOPPED" as unknown as Record<string, never>,
          },
          {
            field: "discountType",
            oldValue: "PERCENT" as unknown as Record<string, never>,
            newValue: "AMOUNT" as unknown as Record<string, never>,
          },
          {
            field: "discountValue",
            oldValue: 30 as unknown as Record<string, never>,
            newValue: 26000 as unknown as Record<string, never>,
          },
        ],
      },
    ];

    const { container, unmount } = renderWithPermissions(
      <PromotionHistoryCard history={historyFixture} />,
      { locale: "vi" },
    );

    const viText = container.textContent ?? "";
    for (const rawEnum of ["VOUCHER", "PERCENT", "AMOUNT", "NEW_ACCOUNT", "ACTIVE", "STOPPED"]) {
      expect(viText).not.toContain(rawEnum);
    }
    expect(viText).toContain("Mã ưu đãi");
    expect(viText).toContain("Phần trăm (%)");
    expect(viText).toContain("30%");
    expect(viText).toContain("50.000 đ");
    expect(viText).toContain("26.000 đ");
    expect(viText).toContain("Tất cả bài thi");
    expect(viText).toContain("Tài khoản mới");
    expect(viText).toContain("Hoạt động");
    expect(viText).toContain("Đã dừng");
    // Enum labels must NOT be hidden inside [data-exam-content]
    expect(container.querySelectorAll("[data-exam-content]").length).toBe(2); // only actorUsername (2 entries)
    unmount();

    const enRender = renderWithPermissions(<PromotionHistoryCard history={historyFixture} />, {
      locale: "en-GB",
    });
    const enText = enRender.container.textContent ?? "";
    for (const rawEnum of ["VOUCHER", "PERCENT", "AMOUNT", "NEW_ACCOUNT", "ACTIVE", "STOPPED"]) {
      expect(enText).not.toContain(rawEnum);
    }
    expect(enText).toContain("Voucher");
    expect(enText).toContain("Percentage (%)");
    expect(enText).toContain("30%");
    expect(enText).toContain("50,000 VND");
    expect(enText).toContain("26,000 VND");
    expect(enText).toContain("All exams");
    expect(enText).toContain("New accounts");
    expect(enText).toContain("Active");
    expect(enText).toContain("Stopped");
    expect(enText).not.toContain("đ");
    expect(enRender.container.querySelectorAll("[data-exam-content]").length).toBe(2);
  });

  it("hiển thị PromotionDetailView đúng chuẩn en-GB (tiền VND, ngày en-GB, pickName cho EXAM_KIND không bọc data-exam-content)", async () => {
    fakeApi.get(
      EXAMS_PATH,
      ok({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }),
    );
    fakeApi.post(
      PROMO_PREVIEW_PATH,
      ok({
        attemptsGranted: 1,
        changed: true,
        discountApplied: "PROMOTION_ONLY",
        listPrice: 100000,
        finalPrice: 50000,
        productCode: "E1",
        productName: "Exam 1",
        versionNo: 1,
        voucherNotBetter: false,
      }),
    );
    fakeApi.get(
      PROMO_DETAIL_PATH,
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          kind: "VOUCHER",
          discountType: "PERCENT",
          discountValue: 15,
          maxDiscountVnd: 50000,
          totalDiscountPaid: 1587400,
          scope: "EXAM_KIND",
          scopeItems: [
            {
              id: KIND_ID_1,
              code: "HSA",
              name: "Đánh giá năng lực ĐHQG Hà Nội (HSA)",
              nameEn: "VNU Hanoi Competency Assessment (HSA)",
              type: "EXAM_KIND",
            },
            {
              id: EXAM_ID_1,
              code: "HSA-01",
              name: "Đề luyện HSA số 1",
              type: "EXAM",
            },
          ],
          history: [
            {
              id: "h1",
              action: "CREATED",
              actorUsername: "ops.admin",
              occurredAt: "2026-11-18T03:30:00Z",
              changes: [
                {
                  field: "status",
                  oldValue: null as unknown as Record<string, never>,
                  newValue: "ACTIVE" as unknown as Record<string, never>,
                },
              ],
            },
          ],
        }),
      ),
    );

    const { container } = renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />, {
      locale: "en-GB",
    });

    expect(await screen.findByText("1,587,400 VND")).toBeInTheDocument();
    expect(screen.getAllByText("-15% (up to 50,000 VND)").length).toBeGreaterThanOrEqual(1);
    const kindChip = screen.getByText("VNU Hanoi Competency Assessment (HSA)");
    expect(kindChip).toBeInTheDocument();
    expect(kindChip.closest("[data-exam-content]")).toBeNull();

    const examChip = screen.getByText("Đề luyện HSA số 1");
    expect(examChip.closest("[data-exam-content]")).not.toBeNull();

    // Outside [data-exam-content], no Vietnamese currency symbol 'đ'
    const textOutsideExamContent = Array.from(container.querySelectorAll("*"))
      .filter((el) => !el.closest("[data-exam-content]") && el.children.length === 0)
      .map((el) => el.textContent ?? "")
      .join(" ");
    expect(textOutsideExamContent).not.toContain(" đ");
    expect(textOutsideExamContent).toContain("Active");
  });
});
