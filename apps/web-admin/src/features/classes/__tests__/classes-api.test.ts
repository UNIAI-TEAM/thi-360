import { describe, expect, it, vi } from "vitest";
import { listAllClasses, type ClassSummary, type ListClassesQuery, type PageResponseClassSummary } from "../api";

function makeClass(index: number): ClassSummary {
  return {
    id: `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`,
    code: `LOP-${String(index).padStart(3, "0")}`,
    name: `Lớp ${index}`,
    status: "ACTIVE",
    memberCount: 0,
    version: 0,
    createdAt: "2026-11-10T08:00:00Z",
    updatedAt: "2026-11-10T08:00:00Z",
  };
}

describe("listAllClasses", () => {
  it("gộp toàn bộ trang khi totalPages > 1", async () => {
    const fetchPage = vi.fn(async (query: ListClassesQuery): Promise<PageResponseClassSummary> => {
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      const total = 150;
      const items = Array.from({ length: Math.min(size, total - page * size) }, (_, i) =>
        makeClass(page * size + i + 1),
      );
      return {
        items,
        page,
        size,
        totalItems: total,
        totalPages: Math.ceil(total / size),
      };
    });

    const result = await listAllClasses("ACTIVE", fetchPage);
    expect(result).toHaveLength(150);
    expect(fetchPage).toHaveBeenCalledTimes(2);
    expect(fetchPage).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ status: "ACTIVE", page: 0, size: 100, sort: "code,asc" }),
    );
    expect(fetchPage).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ status: "ACTIVE", page: 1, size: 100, sort: "code,asc" }),
    );
  });

  it("chỉ gọi 1 lần khi totalPages ≤ 1", async () => {
    const fetchPage = vi.fn(async (query: ListClassesQuery): Promise<PageResponseClassSummary> => {
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      return {
        items: [makeClass(1)],
        page,
        size,
        totalItems: 1,
        totalPages: 1,
      };
    });

    const result = await listAllClasses(undefined, fetchPage);
    expect(result).toHaveLength(1);
    expect(fetchPage).toHaveBeenCalledTimes(1);
    expect(fetchPage).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 100 }),
    );
    // Không truyền status → query không có trường status
    const firstCall = fetchPage.mock.calls[0]?.[0];
    expect(firstCall).not.toHaveProperty("status");
  });
});
