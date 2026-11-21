import { onlineManager } from "@tanstack/react-query";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

  afterEach(() => {
    onlineManager.setOnline(true);
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
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
    expect(table.querySelector("[data-col-summary='itemCount']")).not.toBeNull();
    expect(table.querySelector("[data-col-summary='attempts']")).not.toBeNull();
    expect(table.querySelector("[data-col-summary='updatedAt']")).not.toBeNull();
    expect(within(table).getByRole("link", { name: "Sửa bộ đề BO-SP2E-001" })).toBeInTheDocument();
    expect(within(table).getByRole("button", { name: "Thao tác khác với BO-SP2E-001" })).toBeInTheDocument();

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
    // Action aria-labels use code so English UI never mixes Vietnamese bundle names,
    // and card mode omits the duplicate icon link (R1-15) so each card has only 1 link.
    const cardLinks = within(cards[0]!).getAllByRole("link");
    expect(cardLinks).toHaveLength(1);
    expect(cardLinks[0]).toHaveAttribute("href", "/bundles/b-en-1");
    expect(within(cards[0]!).queryByRole("link", { name: "Edit bundle BO-SP2E-001" })).toBeNull();
    expect(within(cards[0]!).getByRole("button", { name: "More actions for BO-SP2E-001" })).toBeInTheDocument();
  });

  it("R1-7/8: ngoại tuyến ở lần tải đầu hiện trạng thái Đang ngoại tuyến + Thử lại thay vì rỗng", async () => {
    onlineManager.setOnline(false);
    fakeApi.get("/api/v1/admin/bundles", () =>
      ok(makeBundleListPage([makeBundleSummary({ name: "Bộ đề khôi phục" })])),
    );

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    expect(await screen.findByText("Đang ngoại tuyến")).toBeInTheDocument();
    expect(screen.queryByText("Chưa có bộ đề nào")).toBeNull();
    expect(screen.queryByText(/Tổng 0 bộ đề/)).toBeNull();

    act(() => {
      onlineManager.setOnline(true);
    });
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("Bộ đề khôi phục")).toBeInTheDocument();
  });

  it("R1-16/17: nhóm radio trạng thái hỗ trợ roving tabIndex, phím mũi tên và lớp forced-colors", async () => {
    const queries: Record<string, unknown>[] = [];
    fakeApi.get("/api/v1/admin/bundles", (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> } | undefined)?.query ?? {};
      queries.push(q);
      return ok(makeBundleListPage([makeBundleSummary()]));
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");

    const allRadio = screen.getByRole("radio", { name: "Tất cả" });
    const draftRadio = screen.getByRole("radio", { name: "Nháp" });
    const publishedRadio = screen.getByRole("radio", { name: "Đang bán" });
    const discontinuedRadio = screen.getByRole("radio", { name: "Ngừng bán" });

    expect(allRadio).toHaveAttribute("tabindex", "0");
    expect(draftRadio).toHaveAttribute("tabindex", "-1");
    expect(publishedRadio).toHaveAttribute("tabindex", "-1");
    expect(discontinuedRadio).toHaveAttribute("tabindex", "-1");
    expect(allRadio).toHaveClass("forced-colors:border-2", "forced-colors:underline");

    allRadio.focus();
    await user.keyboard("{ArrowRight}");

    expect(document.activeElement).toBe(draftRadio);
    expect(draftRadio).toHaveAttribute("aria-checked", "true");
    expect(draftRadio).toHaveAttribute("tabindex", "0");
    expect(allRadio).toHaveAttribute("tabindex", "-1");
    await waitFor(() => {
      expect(queries.some((q) => q.status === "DRAFT")).toBe(true);
    });

    await user.keyboard("{ArrowLeft}");
    expect(document.activeElement).toBe(allRadio);
    expect(allRadio).toHaveAttribute("aria-checked", "true");
  });

  it("R1-21: lỗi tải danh sách kỳ thi để lọc hiện role='alert' kèm Thử lại và khôi phục được", async () => {
    let kindCalls = 0;
    fakeApi.get("/api/v1/catalog/options", () => {
      kindCalls += 1;
      if (kindCalls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(EXAM_KIND_OPTIONS);
    });
    fakeApi.get("/api/v1/admin/bundles", () => ok(makeBundleListPage([makeBundleSummary()])));

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText("Không tải được danh sách kỳ thi để lọc.")).toBeInTheDocument();
    await user.click(within(alert).getByRole("button", { name: "Thử lại" }));

    await waitFor(() => {
      expect(screen.queryByText("Không tải được danh sách kỳ thi để lọc.")).toBeNull();
    });
    await user.click(screen.getByRole("combobox", { name: "Kỳ thi" }));
    expect(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội" })).toBeInTheDocument();
  });

  it("R1-29: gõ dấu cách cuối và gõ tiếp qua ranh giới debounce không bị xóa hoặc hủy tìm kiếm", async () => {
    const queries: Record<string, unknown>[] = [];
    fakeApi.get("/api/v1/admin/bundles", (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> } | undefined)?.query ?? {};
      queries.push(q);
      return ok(makeBundleListPage([makeBundleSummary()]));
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    const search = screen.getByRole("searchbox", { name: "Tìm theo tên hoặc mã bộ đề" });

    await user.type(search, "SP2E ");
    await waitFor(
      () => {
        expect(queries.some((q) => q.q === "SP2E")).toBe(true);
      },
      { timeout: 1500 },
    );
    expect(search).toHaveValue("SP2E ");

    await user.type(search, "TOAN");
    expect(search).toHaveValue("SP2E TOAN");
    await waitFor(
      () => {
        expect(queries.some((q) => q.q === "SP2E TOAN")).toBe(true);
      },
      { timeout: 1500 },
    );
    expect(search).toHaveValue("SP2E TOAN");
  });

  it("R1-30, R1-47, R1-49: ẩn chip thay đổi chưa công bố khi DISCONTINUED, dùng icon Xem + Link trong menu, và kẹp page vượt quá totalPages", async () => {
    resetBundleNavigation("/bundles", "page=99");
    const requestedPages: unknown[] = [];
    fakeApi.get("/api/v1/admin/bundles", (call) => {
      const q = (call.options?.params as { query?: { page?: number } } | undefined)?.query;
      requestedPages.push(q?.page);
      const bundles = [
        makeBundleSummary({
          id: "b-pub-1",
          code: "BO-SP2E-001",
          name: "Bộ đề SP2E khối A00",
          status: "PUBLISHED",
          saleState: "ON_SALE",
          hasUnpublishedChanges: false,
        }),
        makeBundleSummary({
          id: "b-disc-1",
          code: "BO-SP2E-OLD",
          name: "Bộ đề đã ngừng",
          status: "DISCONTINUED",
          saleState: "DISCONTINUED",
          hasUnpublishedChanges: true,
        }),
      ];
      return ok(
        makeBundleListPage(q?.page === 0 ? bundles : [], {
          page: q?.page ?? 0,
          totalItems: 2,
          totalPages: 1,
        }),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    expect(requestedPages).toContain(0);

    // R1-30: DISCONTINUED bundle does not render "Có thay đổi chưa công bố"
    expect(screen.queryByText("Có thay đổi chưa công bố")).toBeNull();

    // R1-47: DISCONTINUED row shows View (Xem) link instead of Edit (Sửa), and dropdown edit item is a real Link
    const table = screen.getByRole("table", { name: "Danh sách bộ đề" });
    expect(within(table).getByRole("link", { name: "Xem bộ đề BO-SP2E-OLD" })).toBeInTheDocument();
    expect(within(table).queryByRole("link", { name: "Sửa bộ đề BO-SP2E-OLD" })).toBeNull();

    await user.click(within(table).getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    const editMenuItem = await screen.findByRole("menuitem", { name: "Sửa bộ đề" });
    expect(editMenuItem.tagName).toBe("A");
    expect(editMenuItem).toHaveAttribute("href", "/bundles/b-pub-1");
  });
});
