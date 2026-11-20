import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionCapWarningCard } from "../components/promotion-cap-warning-card";
import { PromotionListView } from "../components/promotion-list-view";
import {
  EXAM_ID_1,
  EXAM_ID_2,
  PROMO_ID_1,
  PROMO_LIST_PATH,
  pageOfPromotions,
  promotionSummaryFixture,
  renderWithPermissions,
} from "./promotion-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/promotions",
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

withRealApi();

beforeEach(() => {
  window.history.replaceState(null, "", "/promotions");
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Promotion cap warnings on list (AC-15)", () => {
  it("hiện huy hiệu chạm giá sàn và tên bài thi trong danh sách khi capWarnings khác rỗng", async () => {
    fakeApi.get(
      PROMO_LIST_PATH,
      ok(
        pageOfPromotions([
          promotionSummaryFixture({
            id: PROMO_ID_1,
            kind: "AUTO",
            name: "Giảm sâu mùa thi",
            discountType: "PERCENT",
            discountValue: 80,
            capWarnings: [
              {
                examId: EXAM_ID_1,
                examName: "HSA Full Test 01",
                listPrice: 100000,
                floorPrice: 50000,
                discountedPrice: 50000,
              },
              {
                examId: EXAM_ID_2,
                examName: "QDA Practice 02",
                listPrice: 80000,
                floorPrice: 40000,
                discountedPrice: 40000,
              },
              {
                examId: "33333333-3333-4333-8333-333333333333",
                examName: "TSA Sprint 03",
                listPrice: 90000,
                floorPrice: 30000,
                discountedPrice: 30000,
              },
            ],
          }),
        ]),
      ),
    );

    renderWithPermissions(<PromotionListView />);

    expect(await screen.findByText("Chạm giá sàn")).toBeInTheDocument();
    expect(screen.getByText("HSA Full Test 01")).toBeInTheDocument();
    expect(screen.getByText("QDA Practice 02")).toBeInTheDocument();
    expect(screen.getByText(/\+1 bài khác/)).toBeInTheDocument();
  });

  it("không hiện huy hiệu cảnh báo khi capWarnings rỗng", async () => {
    fakeApi.get(
      PROMO_LIST_PATH,
      ok(pageOfPromotions([promotionSummaryFixture({ id: PROMO_ID_1, name: "No Cap Deal", capWarnings: [] })])),
    );

    renderWithPermissions(<PromotionListView />);

    await screen.findByText("No Cap Deal");
    expect(screen.queryByText("Chạm giá sàn")).not.toBeInTheDocument();
  });
});

describe("PromotionCapWarningCard on detail (AC-15)", () => {
  it("hiện bảng cảnh báo chạm giá sàn kèm listPrice, floorPrice, discountedPrice ở trang chi tiết", () => {
    renderWithPermissions(
      <PromotionCapWarningCard
        warnings={[
          {
            examId: EXAM_ID_1,
            examName: "HSA Floor Capped Exam",
            listPrice: 120000,
            floorPrice: 50000,
            discountedPrice: 50000,
          },
        ]}
      />,
    );

    expect(screen.getByRole("heading", { name: "Cảnh báo chạm giá sàn (1)" })).toBeInTheDocument();
    expect(screen.getAllByText("HSA Floor Capped Exam").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("120.000 đ").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("50.000 đ").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Chạm giá sàn").length).toBeGreaterThanOrEqual(1);
  });

  it("không render thẻ cảnh báo khi capWarnings rỗng", () => {
    const { container } = renderWithPermissions(<PromotionCapWarningCard warnings={[]} />);
    expect(container.firstChild).toBeNull();
  });
});
