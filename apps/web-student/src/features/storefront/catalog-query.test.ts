import { describe, expect, it } from "vitest";
import {
  catalogSearch,
  EMPTY_CATALOG_QUERY,
  hasActiveFilters,
  parseCatalogQuery,
  toApiQuery,
} from "./catalog-query";

describe("điều kiện danh mục trên URL", () => {
  it("URL không tham số là điều kiện mặc định, và mặc định không ghi ngược lên URL", () => {
    expect(parseCatalogQuery({})).toEqual(EMPTY_CATALOG_QUERY);
    expect(catalogSearch(EMPTY_CATALOG_QUERY)).toBe("");
    expect(hasActiveFilters(EMPTY_CATALOG_QUERY)).toBe(false);
  });

  it("đọc rồi ghi lại cho đúng cùng một URL, thứ tự khóa cố định", () => {
    const query = parseCatalogQuery({
      page: "3",
      sort: "PRICE_DESC",
      part: "TOAN",
      kind: "HSA",
      format: "PART",
      q: " hsa full ",
    });

    expect(query).toEqual({ q: "hsa full", kind: "HSA", format: "PART", subject: "", part: "TOAN", sort: "PRICE_DESC", page: 3 });
    expect(catalogSearch(query)).toBe("?q=hsa+full&kind=HSA&format=PART&part=TOAN&sort=PRICE_DESC&page=3");
    expect(parseCatalogQuery(Object.fromEntries(new URLSearchParams(catalogSearch(query)))).q).toBe("hsa full");
  });

  it("giá trị lạ bị bỏ thay vì văng lỗi: hình thức, sắp xếp, số trang", () => {
    const query = parseCatalogQuery({ format: "WEIRD", sort: "CHEAPEST", page: "-4" });

    expect(query.format).toBe("");
    expect(query.sort).toBe("NEWEST");
    expect(query.page).toBe(1);
    expect(parseCatalogQuery({ page: "abc" }).page).toBe(1);
  });

  it("mã phần không kèm kỳ thi thì bỏ (mã phần chỉ có nghĩa trong một kỳ thi)", () => {
    expect(parseCatalogQuery({ part: "TOAN" }).part).toBe("");
    expect(catalogSearch({ ...EMPTY_CATALOG_QUERY, part: "TOAN" })).toBe("");
  });

  it("tham số lặp lấy giá trị đầu", () => {
    expect(parseCatalogQuery({ kind: ["QDA", "HSA"] }).kind).toBe("QDA");
  });

  it("gọi API với trang tính từ 0 và cỡ trang 12", () => {
    expect(toApiQuery({ ...EMPTY_CATALOG_QUERY, kind: "HSA", page: 3 })).toEqual({
      q: undefined,
      kind: "HSA",
      format: undefined,
      subject: undefined,
      part: undefined,
      sort: "NEWEST",
      page: 2,
      size: 12,
    });
  });

  it("đổi sắp xếp cũng là điều kiện khác mặc định", () => {
    expect(hasActiveFilters({ ...EMPTY_CATALOG_QUERY, sort: "PRICE_ASC" })).toBe(true);
  });
});
