import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import {
  EXAM_KIND_OPTIONS,
  KIND_SPT_ID,
  makeBundleListPage,
  makeBundleSummary,
  renderBundlesUi,
  resetBundleNavigation,
} from "./test/bundle-fixtures";
import { BundleListView } from "./components/bundle-list-view";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
vi.mock("next/navigation", async () => {
  const fixtures = await import("./test/bundle-fixtures");
  return fixtures.createBundleNextNavMock();
});
withRealApi();

describe("BundleListView (AC-5)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetBundleNavigation("/bundles", "");
    fakeApi.get("/api/v1/catalog/options", () => ok(EXAM_KIND_OPTIONS));
  });

  it("hiện đủ trạng thái đang tải (skeleton 20 dòng), rỗng có nút Tạo (khi có EXAM.MODIFY), lỗi kèm Thử lại", async () => {
    // 1. Loading
    fakeApi.pending("/api/v1/admin/bundles");
    const { unmount } = renderBundlesUi(<BundleListView />);
    const loadingTable = await screen.findByRole("table", { name: "Danh sách bộ đề" });
    expect(loadingTable).toHaveAttribute("aria-busy", "true");
    expect(loadingTable.querySelectorAll("tbody tr")).toHaveLength(20);
    unmount();

    // 2. Empty with EXAM.MODIFY
    fakeApi.get("/api/v1/admin/bundles", () => ok(makeBundleListPage([])));
    const { unmount: unmountEmpty } = renderBundlesUi(<BundleListView />);
    expect(await screen.findByText("Chưa có bộ đề nào")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Tạo bộ đề" }).length).toBeGreaterThanOrEqual(1);
    unmountEmpty();

    // 3. Error + Retry recovers
    let calls = 0;
    fakeApi.get("/api/v1/admin/bundles", () => {
      calls += 1;
      if (calls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(makeBundleListPage([makeBundleSummary()]));
    });
    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText("Không tải được danh sách bộ đề")).toBeInTheDocument();
    await user.click(within(alert).getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("Bộ đề SP2E khối A00")).toBeInTheDocument();
    expect(calls).toBe(2);
  });

  it("hiện đủ các trạng thái chip, UNAVAILABLE, hasUnpublishedChanges, data-exam-content và hỗ trợ lọc/tìm kiếm debounce", async () => {
    const queries: Record<string, unknown>[] = [];
    fakeApi.get("/api/v1/admin/bundles", (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> } | undefined)?.query ?? {};
      queries.push(q);
      return ok(
        makeBundleListPage([
          makeBundleSummary({
            id: "b-1",
            code: "BO-SP2E-001",
            name: "Bộ đề SP2E khối A00",
            status: "PUBLISHED",
            saleState: "ON_SALE",
            hasUnpublishedChanges: true,
          }),
          makeBundleSummary({
            id: "b-2",
            code: "BO-SP2E-002",
            name: "Bộ đề SP2E khối C00",
            status: "PUBLISHED",
            saleState: "UNAVAILABLE",
            hasUnpublishedChanges: false,
          }),
          makeBundleSummary({
            id: "b-3",
            code: "BO-SPT-001",
            name: "Bộ đề SPT nháp",
            status: "DRAFT",
            saleState: null,
            hasUnpublishedChanges: false,
            listPriceVnd: null,
            attemptsPerExam: null,
          }),
          makeBundleSummary({
            id: "b-4",
            code: "BO-SP2E-OLD",
            name: "Bộ đề đã ngừng",
            status: "DISCONTINUED",
            saleState: "DISCONTINUED",
            hasUnpublishedChanges: false,
          }),
        ]),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    const table = screen.getByRole("table", { name: "Danh sách bộ đề" });
    const nameEl = within(table).getByText("Bộ đề SP2E khối A00");
    expect(nameEl).toHaveAttribute("data-exam-content");
    expect(within(table).getByText("BO-SP2E-001")).toHaveAttribute("data-exam-content");
    expect(within(table).getByText("Có thay đổi chưa công bố")).toBeInTheDocument();
    expect(within(table).getByText("Không bán được")).toBeInTheDocument();
    expect(within(table).getByText("Nháp")).toBeInTheDocument();
    expect(within(table).getByText("Ngừng bán")).toBeInTheDocument();
    expect(within(table).getAllByText("Đang bán").length).toBe(2);
    expect(within(table).getAllByText("349.000 đ")).toHaveLength(3);

    // Filter by status radio
    await user.click(screen.getByRole("radio", { name: "Nháp" }));
    await waitFor(() => {
      expect(queries.some((q) => q.status === "DRAFT")).toBe(true);
    });

    // Search debounce 300ms
    const search = screen.getByRole("searchbox", { name: "Tìm theo tên hoặc mã bộ đề" });
    await user.type(search, "SP2E");
    await waitFor(
      () => {
        expect(queries.some((q) => q.q === "SP2E")).toBe(true);
      },
      { timeout: 1500 },
    );

    // Exam kind filter
    await user.click(screen.getByRole("combobox", { name: "Kỳ thi" }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội" }));
    await waitFor(() => {
      expect(queries.some((q) => q.examKind === "SPT")).toBe(true);
    });
  });

  it("chế độ en dùng pickName cho kỳ thi (nameEn khi có và rơi về nameVi khi rỗng), không bọc data-exam-content, và khung hẹp 358px chuyển sang danh sách thẻ", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    fakeApi.get("/api/v1/admin/bundles", () =>
      ok(
        makeBundleListPage([
          makeBundleSummary({
            id: "b-en-1",
            code: "BO-SP2E-001",
            name: "Bộ đề SP2E",
            examKindCode: "SP2E",
            examKindNameVi: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2",
            examKindNameEn: "HNUE2 Assessment",
          }),
          makeBundleSummary({
            id: "b-en-2",
            code: "BO-SPT-001",
            name: "Bộ đề SPT",
            examKindItemId: KIND_SPT_ID,
            examKindCode: "SPT",
            examKindNameVi: "Đánh giá năng lực ĐH Sư phạm Hà Nội",
            examKindNameEn: "",
          }),
        ]),
      ),
    );

    renderBundlesUi(<BundleListView />, { locale: "en" });

    await screen.findByText("Bộ đề SP2E");
    const list = screen.getByRole("list", { name: "Bundles table" });
    expect(screen.queryByRole("table")).toBeNull();
    const cards = within(list).getAllByRole("listitem");
    expect(cards).toHaveLength(2);

    // Card 1: nameEn !== nameVi -> uses nameEn, not wrapped in data-exam-content
    const kindTag1 = within(cards[0]!).getByText("HNUE2 Assessment");
    expect(kindTag1.closest("[data-exam-content]")).toBeNull();
    // Card 2: empty nameEn -> falls back to nameVi
    const kindTag2 = within(cards[1]!).getByText("Đánh giá năng lực ĐH Sư phạm Hà Nội");
    expect(kindTag2.closest("[data-exam-content]")).toBeNull();
    // VND formatted in en locale
    expect(within(cards[0]!).getByText("349,000 VND")).toBeInTheDocument();
  });
});
