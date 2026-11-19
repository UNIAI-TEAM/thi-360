import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { FULL_CARD, PART_CARD } from "@/test/storefront-fixtures";
import { renderWithProviders } from "@/test/render";
import { EMPTY_CATALOG_QUERY } from "./catalog-query";
import { CatalogResults, CatalogSection } from "./catalog-section";
import type { Loaded, ProductPage, StorefrontFilters } from "./types";

const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router, usePathname: () => "/" }));
vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return { ...actual, loadOptionalSession: async () => null };
});

const FILTERS: Loaded<StorefrontFilters> = {
  status: "ok",
  data: {
    kinds: [
      { code: "HSA", nameVi: "Danh gia nang luc", nameEn: "Aptitude", count: 5 },
      { code: "QDA", nameVi: "Quan doi", nameEn: "Army", count: 2 },
    ],
    formats: [
      { code: "FULL", count: 3 },
      { code: "PART", count: 2 },
    ],
    parts: [{ kindCode: "HSA", code: "TOAN", name: "Toan hoc va Xu li so lieu", count: 2 }],
    subjects: [{ code: "TOAN", nameVi: "Toan", nameEn: "Math", count: 4 }],
  },
};

function page(overrides: Partial<ProductPage> = {}): Loaded<ProductPage> {
  return {
    status: "ok",
    data: { items: [FULL_CARD, PART_CARD], page: 0, size: 12, totalItems: 30, totalPages: 3, ...overrides },
  };
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  router.refresh.mockReset();
});

describe("CatalogResults · bốn trạng thái (AC-10)", () => {
  const handlers = { onRetry: vi.fn(), onClearFilters: vi.fn(), onPageChange: vi.fn(), currentPage: 1 };

  it("đang tải: khung thẻ có kích thước cố định, báo trạng thái bận", () => {
    renderWithProviders(<CatalogResults view={{ kind: "loading" }} {...handlers} />);

    const loading = screen.getByRole("status", { name: "Đang tải danh mục" });
    expect(loading).toHaveAttribute("aria-busy", "true");
    expect(loading.children.length).toBeGreaterThan(0);
    for (const placeholder of Array.from(loading.children)) expect(placeholder.className).toContain("h-[26rem]");
  });

  it("chưa có bài nào: 'Hiện chưa có bài thi nào đang mở bán'", () => {
    renderWithProviders(<CatalogResults view={{ kind: "empty" }} {...handlers} />);

    expect(screen.getByRole("heading", { name: "Hiện chưa có bài thi nào đang mở bán" })).toBeInTheDocument();
  });

  it("không có kết quả: 'Không tìm thấy bài thi phù hợp' kèm Xóa bộ lọc", async () => {
    const onClearFilters = vi.fn();
    renderWithProviders(<CatalogResults view={{ kind: "noResults" }} {...handlers} onClearFilters={onClearFilters} />);

    expect(screen.getByRole("heading", { name: "Không tìm thấy bài thi phù hợp" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Xóa bộ lọc" }));
    expect(onClearFilters).toHaveBeenCalledTimes(1);
  });

  it("lỗi tải: báo lỗi và có nút Thử lại", async () => {
    const onRetry = vi.fn();
    renderWithProviders(<CatalogResults view={{ kind: "error" }} {...handlers} onRetry={onRetry} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Chưa tải được danh mục bài thi. Bộ lọc của bạn vẫn được giữ.");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

describe("CatalogSection · điều kiện nằm trên URL (AC-2, AC-3, AC-10)", () => {
  it("có dữ liệu: dòng tóm tắt, thẻ bài thi, phân trang", () => {
    renderWithProviders(<CatalogSection query={EMPTY_CATALOG_QUERY} filters={FILTERS} products={page()} />);

    expect(screen.getByText(/Hiển thị 1–12/)).toHaveTextContent("Hiển thị 1–12 trong 30 bài thi đang mở bán");
    expect(screen.getAllByRole("article")).toHaveLength(2);
    expect(screen.getByRole("navigation", { name: "Phân trang danh mục" })).toBeInTheDocument();
  });

  it("chuyển trang giữ điều kiện và ghi lên URL; thẻ không mất bộ lọc", async () => {
    renderWithProviders(
      <CatalogSection query={{ ...EMPTY_CATALOG_QUERY, kind: "HSA", sort: "PRICE_ASC" }} filters={FILTERS} products={page()} />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Trang 2" }));

    expect(router.push).toHaveBeenCalledWith("/?kind=HSA&sort=PRICE_ASC&page=2", { scroll: false });
  });

  it("đổi điều kiện thì về trang 1", async () => {
    renderWithProviders(
      <CatalogSection query={{ ...EMPTY_CATALOG_QUERY, kind: "HSA", page: 3 }} filters={FILTERS} products={page({ page: 2 })} />,
    );

    await userEvent.click(screen.getByRole("combobox", { name: "Hình thức" }));
    await userEvent.click(await screen.findByRole("option", { name: "Bài thi FULL" }));

    expect(router.replace).toHaveBeenCalledWith("/?kind=HSA&format=FULL", { scroll: false });
  });

  it("chọn một phần thì kỳ thi theo phần đó (mã phần chỉ có nghĩa trong một kỳ thi)", async () => {
    renderWithProviders(<CatalogSection query={EMPTY_CATALOG_QUERY} filters={FILTERS} products={page()} />);

    await userEvent.click(screen.getByRole("combobox", { name: "Phần/môn" }));
    await userEvent.click(await screen.findByRole("option", { name: "HSA · Toan hoc va Xu li so lieu" }));

    expect(router.replace).toHaveBeenCalledWith("/?kind=HSA&part=TOAN", { scroll: false });
  });

  it("ô tìm kiếm ghi URL sau khi ngừng gõ và về trang 1", async () => {
    renderWithProviders(
      <CatalogSection query={{ ...EMPTY_CATALOG_QUERY, page: 2 }} filters={FILTERS} products={page({ page: 1 })} />,
    );

    await userEvent.type(screen.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" }), "hsa");

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/?q=hsa", { scroll: false }), { timeout: 2000 });
    expect(router.replace).toHaveBeenCalledTimes(1);
  });

  it("Xóa bộ lọc đưa mọi điều kiện về mặc định", async () => {
    renderWithProviders(
      <CatalogSection
        query={{ ...EMPTY_CATALOG_QUERY, q: "hsa", kind: "HSA", format: "FULL", sort: "PRICE_DESC", page: 2 }}
        filters={FILTERS}
        products={page({ items: [], totalItems: 0, totalPages: 0 })}
      />,
    );

    expect(screen.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" })).toHaveValue("hsa");
    expect(screen.getByRole("heading", { name: "Không tìm thấy bài thi phù hợp" })).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole("search")).getByRole("button", { name: "Xóa bộ lọc" }));

    expect(router.replace).toHaveBeenCalledWith("/", { scroll: false });
    expect(screen.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" })).toHaveValue("");
  });

  it("lỗi tải danh mục: giữ bộ lọc đang chọn, Thử lại làm mới trang", async () => {
    renderWithProviders(
      <CatalogSection
        query={{ ...EMPTY_CATALOG_QUERY, q: "hsa" }}
        filters={FILTERS}
        products={{ status: "error" }}
      />,
    );

    expect(screen.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" })).toHaveValue("hsa");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(router.refresh).toHaveBeenCalledTimes(1);
  });

  it("chưa có bài nào và không lọc: ẩn bộ lọc, chỉ có thông báo", () => {
    renderWithProviders(
      <CatalogSection
        query={EMPTY_CATALOG_QUERY}
        filters={FILTERS}
        products={page({ items: [], totalItems: 0, totalPages: 0 })}
      />,
    );

    expect(screen.getByRole("heading", { name: "Hiện chưa có bài thi nào đang mở bán" })).toBeInTheDocument();
    expect(screen.queryByRole("search")).toBeNull();
  });

  it("một trang kết quả thì không hiện phân trang", () => {
    renderWithProviders(
      <CatalogSection query={EMPTY_CATALOG_QUERY} filters={FILTERS} products={page({ totalItems: 2, totalPages: 1 })} />,
    );

    expect(screen.queryByRole("navigation", { name: "Phân trang danh mục" })).toBeNull();
  });
});
