"use client";

import { Button, cn, EmptyState, Input, Pagination, Select, type SelectOption, Skeleton } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { FileText, Search, SearchX, TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, useTransition, type ReactNode } from "react";
import { touchFieldText } from "@/lib/styles";
import { AccessAction } from "./access-action";
import {
  CATALOG_ANCHOR_ID,
  CATALOG_FORMATS,
  CATALOG_PAGE_SIZE,
  CATALOG_SORTS,
  type CatalogQuery,
  catalogSearch,
  EMPTY_CATALOG_QUERY,
  hasActiveFilters,
} from "./catalog-query";
import { useExamAccess } from "./exam-access";
import { ProductCard } from "./product-card";
import type { Loaded, ProductPage, StorefrontFilters } from "./types";

const ALL = "ALL";
const SEARCH_DEBOUNCE_MS = 400;

export type CatalogView =
  | { kind: "loading" }
  | { kind: "error" }
  /** Chưa có bài nào đang mở bán (không kèm điều kiện lọc): bộ lọc ẩn. */
  | { kind: "empty" }
  | { kind: "noResults" }
  | { kind: "ready"; page: ProductPage };

const gridClass = "grid grid-cols-[repeat(auto-fill,minmax(min(250px,100%),1fr))] gap-5";

/** Khung thẻ có kích thước cố định bằng thẻ thật: trang không nhảy khi dữ liệu về (TM 11.3). */
function CatalogSkeleton({ label }: { label: string }) {
  return (
    <div role="status" aria-busy="true" aria-label={label} className={gridClass}>
      {Array.from({ length: 6 }, (_, index) => (
        <div key={index} className="flex h-[26rem] flex-col overflow-hidden rounded-xl border border-border bg-surface">
          <Skeleton className="aspect-video w-full rounded-none" />
          <div className="flex flex-1 flex-col gap-3 p-4">
            <Skeleton className="h-5 w-1/2" />
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="mt-auto h-11 w-full rounded-lg" />
          </div>
        </div>
      ))}
    </div>
  );
}

function CatalogGrid({ page }: { page: ProductPage }) {
  const access = useExamAccess(page.items.map((item) => item.code));
  return (
    <div className={gridClass}>
      {page.items.map((product) => (
        <ProductCard
          key={product.code}
          product={product}
          action={
            <AccessAction
              variant="card"
              product={{ code: product.code, versionNo: product.versionNo, price: product.price }}
              view={access.view(product.code)}
              onReload={access.reload}
            />
          }
        />
      ))}
    </div>
  );
}

/** Phần kết quả của danh mục: đủ bốn trạng thái (đang tải, rỗng, lỗi, có dữ liệu) cộng "không có kết quả". */
export function CatalogResults({
  view,
  onRetry,
  onClearFilters,
  onPageChange,
  currentPage,
}: {
  view: CatalogView;
  onRetry: () => void;
  onClearFilters: () => void;
  onPageChange: (page: number) => void;
  currentPage: number;
}) {
  const t = useTranslations("storefront.catalog");
  const common = useTranslations("common");

  switch (view.kind) {
    case "loading":
      return <CatalogSkeleton label={t("loading")} />;
    case "error":
      return (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-lg border border-danger/30 bg-danger-bg px-4 py-3.5 text-danger"
        >
          <TriangleAlert aria-hidden className="size-5 shrink-0" />
          <span className="min-w-0 flex-1 basis-52">{t("errorMessage")}</span>
          <Button variant="secondary" className="h-11 text-danger" onClick={onRetry}>
            {common("retry")}
          </Button>
        </div>
      );
    case "empty":
      return <EmptyState headingLevel={3} icon={<FileText />} title={t("emptyTitle")} />;
    case "noResults":
      return (
        <EmptyState
          headingLevel={3}
          icon={<SearchX />}
          title={t("noResultsTitle")}
          description={t("noResultsDescription")}
          action={
            <Button variant="secondary" className="h-11" onClick={onClearFilters}>
              {t("clearFilters")}
            </Button>
          }
        />
      );
    case "ready": {
      const { page } = view;
      const from = Math.min(page.page * page.size + 1, page.totalItems);
      const to = Math.min((page.page + 1) * page.size, page.totalItems);
      return (
        <>
          <p role="status" className="text-text-secondary">
            {t("showing", { from, to, total: page.totalItems })}
          </p>
          <CatalogGrid page={page} />
          {page.totalPages > 1 && (
            <Pagination
              className="justify-center pt-2"
              page={currentPage}
              pageSize={page.size || CATALOG_PAGE_SIZE}
              total={page.totalItems}
              onPageChange={onPageChange}
              labels={{ nav: t("paginationLabel"), range: () => "" }}
            />
          )}
        </>
      );
    }
  }
}

function FilterField({ id, label, children, className }: { id: string; label: string; children: ReactNode; className: string }) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-1.5", className)}>
      <label htmlFor={id} className="text-field-label font-medium text-text-secondary">
        {label}
      </label>
      {children}
    </div>
  );
}

function partValue(kind: string, code: string) {
  return `P:${kind}:${code}`;
}

/**
 * Khối "Chọn bài thi" của trang giới thiệu (TM 11.2). Điều kiện nằm trên URL: đổi điều kiện thì ghi URL mới và máy
 * chủ dựng lại danh sách (dữ liệu cache 60 giây ở máy chủ web); đổi điều kiện về trang 1, chuyển trang giữ điều kiện.
 */
export function CatalogSection({
  query,
  filters,
  products,
}: {
  query: CatalogQuery;
  filters: Loaded<StorefrontFilters>;
  products: Loaded<ProductPage>;
}) {
  const t = useTranslations("storefront.catalog");
  const formatT = useTranslations("storefront.format");
  const locale = useLocale();
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [search, setSearch] = useState(query.q);
  const pushedSearch = useRef(query.q);

  function go(next: CatalogQuery, mode: "replace" | "push" = "replace") {
    const url = `/${catalogSearch(next)}`;
    startTransition(() => {
      if (mode === "push") router.push(url, { scroll: false });
      else router.replace(url, { scroll: false });
    });
  }

  // Ô tìm kiếm áp dụng sau khi ngừng gõ; đổi điều kiện tìm thì về trang 1.
  useEffect(() => {
    if (search === query.q) return;
    const handle = window.setTimeout(() => {
      pushedSearch.current = search;
      go({ ...query, q: search, page: 1 });
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(handle);
    // `go` đọc `query` mới nhất ở mỗi lần dựng lại; chỉ gõ chữ mới đặt lại hẹn giờ.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  // URL đổi từ nơi khác (Xóa bộ lọc, nút Back) thì ô tìm kiếm theo URL; trừ giá trị chính nó vừa ghi (đang gõ tiếp).
  useEffect(() => {
    if (query.q !== pushedSearch.current) {
      pushedSearch.current = query.q;
      setSearch(query.q);
    }
  }, [query.q]);

  const filtersData = filters.status === "ok" ? filters.data : null;
  const kindOptions: SelectOption[] = [
    { value: ALL, label: t("kindAll") },
    ...(filtersData?.kinds ?? []).map((kind) => ({ value: kind.code, label: kind.code })),
  ];
  const formatOptions: SelectOption[] = [
    { value: ALL, label: t("formatAll") },
    ...CATALOG_FORMATS.filter((code) => (filtersData?.formats ?? []).some((item) => item.code === code)).map((code) => ({
      value: code,
      label: formatT(code),
    })),
  ];
  const partOptions: SelectOption[] = [
    { value: ALL, label: t("partAll") },
    ...(filtersData?.parts ?? [])
      .filter((part) => !query.kind || part.kindCode === query.kind)
      .map((part) => ({
        value: partValue(part.kindCode, part.code),
        label: query.kind ? part.name : `${part.kindCode} · ${part.name}`,
      })),
    ...(filtersData?.subjects ?? []).map((subject) => ({
      value: `S:${subject.code}`,
      label: pickName(subject, locale),
    })),
  ];
  const partSelected = query.part ? partValue(query.kind, query.part) : query.subject ? `S:${query.subject}` : ALL;

  function onPartChange(value: string) {
    if (value === ALL) return go({ ...query, part: "", subject: "", page: 1 });
    if (value.startsWith("S:")) return go({ ...query, part: "", subject: value.slice(2), page: 1 });
    const [, kind = "", code = ""] = value.split(":");
    // Mã phần chỉ có nghĩa trong một kỳ thi: chọn phần thì kỳ thi theo phần đó.
    go({ ...query, kind, part: code, subject: "", page: 1 });
  }

  // Trang vượt trang cuối (bookmark cũ, danh mục đã nhỏ lại): đưa về trang cuối và hiện khung tải cho tới khi dữ liệu về.
  const overLastPage =
    products.status === "ok" && products.data.totalPages >= 1 && query.page > products.data.totalPages;
  useEffect(() => {
    if (overLastPage && products.status === "ok") {
      go({ ...query, page: products.data.totalPages });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [overLastPage, query, products]);

  const emptyCatalog =
    products.status === "ok" && products.data.totalItems === 0 && !hasActiveFilters(query);
  const view: CatalogView = pending || overLastPage
    ? { kind: "loading" }
    : products.status === "error" || filters.status === "error"
      ? { kind: "error" }
      : emptyCatalog
        ? { kind: "empty" }
        : products.data.totalItems === 0
          ? { kind: "noResults" }
          : { kind: "ready", page: products.data };

  function clearFilters() {
    pushedSearch.current = "";
    setSearch("");
    go(EMPTY_CATALOG_QUERY);
  }

  function changePage(page: number) {
    go({ ...query, page }, "push");
    document.getElementById(CATALOG_ANCHOR_ID)?.scrollIntoView({ block: "start" });
  }

  return (
    <section
      id={CATALOG_ANCHOR_ID}
      aria-labelledby="catalog-title"
      className="mx-auto flex w-full max-w-[1170px] scroll-mt-4 flex-col gap-5 px-4 py-14 sm:px-6"
    >
      <div className="flex items-center gap-3">
        <span aria-hidden className="h-7 w-1 rounded-sm bg-primary-fill" />
        <h2 id="catalog-title" className="text-h2 font-bold text-text">
          {t("title")}
        </h2>
      </div>

      {!emptyCatalog && (
        <form
          role="search"
          aria-label={t("filtersLabel")}
          onSubmit={(event) => event.preventDefault()}
          className="flex flex-col gap-3.5 rounded-xl border border-border bg-surface p-5"
        >
          <div className="flex flex-wrap items-end gap-3">
            <FilterField id="catalog-q" label={t("searchLabel")} className="flex-[2_1_17.5rem]">
              <Input
                id="catalog-q"
                type="search"
                size="lg"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={t("searchPlaceholder")}
                leadingIcon={<Search />}
                className={touchFieldText}
              />
            </FilterField>
            <FilterField id="catalog-kind" label={t("kindLabel")} className="flex-[1_1_9.5rem]">
              <Select
                id="catalog-kind"
                size="lg"
                options={kindOptions}
                value={query.kind || ALL}
                onValueChange={(value) =>
                  go({ ...query, kind: value === ALL ? "" : value, part: "", page: 1 })
                }
              />
            </FilterField>
            <FilterField id="catalog-format" label={t("formatLabel")} className="flex-[1_1_10.5rem]">
              <Select
                id="catalog-format"
                size="lg"
                aria-describedby="catalog-format-hint"
                options={formatOptions}
                value={query.format || ALL}
                onValueChange={(value) =>
                  go({ ...query, format: value === ALL ? "" : (value as CatalogQuery["format"]), page: 1 })
                }
              />
            </FilterField>
            <FilterField id="catalog-part" label={t("partLabel")} className="flex-[1_1_10.5rem]">
              <Select id="catalog-part" size="lg" options={partOptions} value={partSelected} onValueChange={onPartChange} />
            </FilterField>
            <FilterField id="catalog-sort" label={t("sortLabel")} className="flex-[1_1_9.5rem]">
              <Select
                id="catalog-sort"
                size="lg"
                options={CATALOG_SORTS.map((sort) => ({ value: sort, label: t(`sort.${sort}`) }))}
                value={query.sort}
                onValueChange={(value) => go({ ...query, sort: value as CatalogQuery["sort"], page: 1 })}
              />
            </FilterField>
            <Button type="button" variant="secondary" className="h-12" onClick={clearFilters}>
              {t("clearFilters")}
            </Button>
          </div>
          <p id="catalog-format-hint" className="text-field-label text-text-secondary">
            <strong className="font-semibold text-text-secondary">{t("formatHintLabel")}</strong> {t("formatHint")}
          </p>
        </form>
      )}

      <CatalogResults
        view={view}
        currentPage={query.page}
        onRetry={() => startTransition(() => router.refresh())}
        onClearFilters={clearFilters}
        onPageChange={changePage}
      />
    </section>
  );
}
