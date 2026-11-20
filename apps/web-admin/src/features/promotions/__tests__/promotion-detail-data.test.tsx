import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionDetailView } from "../components/promotion-detail-view";
import { PromotionHistoryCard } from "../components/promotion-history-card";
import {
  EXAMS_PATH,
  PROMO_DETAIL_PATH,
  PROMO_ID_1,
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
});
