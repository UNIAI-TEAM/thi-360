/**
 * Điều kiện của danh mục nằm trên URL (TM 11.2): `/?q=&kind=&format=&subject=&part=&sort=&page=#exams`. Giá trị
 * mặc định không ghi lên URL. `page` trên URL tính từ 1 (API tính từ 0).
 */
export const CATALOG_FORMATS = ["FULL", "PART", "SUBJECT", "BUNDLE"] as const;
export const CATALOG_SORTS = ["NEWEST", "PRICE_ASC", "PRICE_DESC"] as const;
export const CATALOG_PAGE_SIZE = 12;
/** Neo của khối danh mục trên trang giới thiệu (`/#exams`). */
export const CATALOG_ANCHOR_ID = "exams";
export const DEFAULT_CATALOG_SORT = "NEWEST";

export type CatalogFormat = (typeof CATALOG_FORMATS)[number];
export type CatalogSort = (typeof CATALOG_SORTS)[number];

export interface CatalogQuery {
  /** Tìm theo tên hoặc mã. */
  q: string;
  /** Mã kỳ thi. */
  kind: string;
  format: CatalogFormat | "";
  /** Mã môn (bài thi theo môn, và FULL có môn đó). */
  subject: string;
  /** Mã phần; chỉ có nghĩa kèm `kind` (API TM 10.2). */
  part: string;
  sort: CatalogSort;
  page: number;
}

export const EMPTY_CATALOG_QUERY: CatalogQuery = {
  q: "",
  kind: "",
  format: "",
  subject: "",
  part: "",
  sort: DEFAULT_CATALOG_SORT,
  page: 1,
};

type RawParams = Record<string, string | string[] | undefined>;

function single(raw: RawParams, key: string): string {
  const value = raw[key];
  return (Array.isArray(value) ? value[0] : value)?.trim() ?? "";
}

function oneOf<T extends string>(values: readonly T[], candidate: string): T | null {
  return values.find((value) => value === candidate) ?? null;
}

/** Đọc `searchParams` của trang: giá trị lạ bị bỏ (về mặc định), không văng lỗi. */
export function parseCatalogQuery(raw: RawParams): CatalogQuery {
  const kind = single(raw, "kind");
  const pageNumber = Number.parseInt(single(raw, "page"), 10);
  return {
    q: single(raw, "q"),
    kind,
    format: oneOf(CATALOG_FORMATS, single(raw, "format")) ?? "",
    subject: single(raw, "subject"),
    // Mã phần không có kỳ thi thì không xác định được phần nào: bỏ.
    part: kind ? single(raw, "part") : "",
    sort: oneOf(CATALOG_SORTS, single(raw, "sort")) ?? DEFAULT_CATALOG_SORT,
    page: Number.isInteger(pageNumber) && pageNumber >= 1 ? pageNumber : 1,
  };
}

/** `?...` của URL trang (rỗng khi mọi điều kiện là mặc định). */
export function catalogSearch(query: CatalogQuery): string {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q);
  if (query.kind) params.set("kind", query.kind);
  if (query.format) params.set("format", query.format);
  if (query.subject) params.set("subject", query.subject);
  if (query.part && query.kind) params.set("part", query.part);
  if (query.sort !== DEFAULT_CATALOG_SORT) params.set("sort", query.sort);
  if (query.page > 1) params.set("page", String(query.page));
  const text = params.toString();
  return text ? `?${text}` : "";
}

/** Có điều kiện nào khác mặc định không (để hiện "Xóa bộ lọc" và phân biệt "chưa có bài" với "không có kết quả"). */
export function hasActiveFilters(query: CatalogQuery): boolean {
  return Boolean(query.q || query.kind || query.format || query.subject || query.part) || query.sort !== DEFAULT_CATALOG_SORT;
}

/** Tham số gọi API danh mục (`page` từ 0). */
export function toApiQuery(query: CatalogQuery) {
  return {
    q: query.q || undefined,
    kind: query.kind || undefined,
    format: query.format || undefined,
    subject: query.subject || undefined,
    part: query.part || undefined,
    sort: query.sort,
    page: query.page - 1,
    size: CATALOG_PAGE_SIZE,
  };
}
