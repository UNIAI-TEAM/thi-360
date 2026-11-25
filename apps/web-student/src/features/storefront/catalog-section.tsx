"use client";

import { Button, cn, EmptyState, focusRing, Input, Pagination, Select, type SelectOption, Skeleton } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { ChevronLeft, ChevronRight, FileText, Search, SearchX, TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useTransition,
  type KeyboardEvent,
  type MouseEvent,
} from "react";
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
import { isBundleProduct } from "./format";
import { ProductCard } from "./product-card";
import type { Loaded, ProductPage, StorefrontFilters } from "./types";

const ALL = "ALL";
const SEARCH_DEBOUNCE_MS = 400;
const SCROLL_STEP_PX = 660;

export type CatalogView =
  | { kind: "loading" }
  | { kind: "error" }
  /** Chưa có bài nào đang mở bán (không kèm điều kiện lọc): bộ lọc ẩn. */
  | { kind: "empty" }
  | { kind: "noResults" }
  | { kind: "ready"; page: ProductPage };

const cardSlotClass = "flex w-[min(19.5rem,82vw)] shrink-0 snap-start sm:w-80";

/** Khung thẻ có kích thước cố định bằng thẻ thật trên 1 hàng ngang: trang không nhảy khi dữ liệu về (TM 11.3). */
function CatalogSkeleton({ label }: { label: string }) {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-label={label}
      className="flex flex-nowrap gap-5 overflow-hidden py-1"
    >
      {Array.from({ length: 5 }, (_, index) => (
        <div
          key={index}
          className="flex h-[26rem] w-[min(19.5rem,82vw)] shrink-0 flex-col overflow-hidden rounded-2xl border border-border bg-surface/90 sm:w-80"
        >
          <Skeleton className="aspect-video w-full rounded-none" />
          <div className="flex flex-1 flex-col gap-3 p-4">
            <Skeleton className="h-5 w-1/2" />
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-9 w-full rounded-lg" />
            <Skeleton className="mt-auto h-11 w-full rounded-lg" />
          </div>
        </div>
      ))}
    </div>
  );
}

function CatalogCarousel({
  page,
  showingText,
  currentPage,
  onPageChange,
}: {
  page: ProductPage;
  showingText: string;
  currentPage: number;
  onPageChange: (page: number) => void;
}) {
  const t = useTranslations("storefront.catalog");
  const access = useExamAccess(page.items.map((item) => item.code));
  const scrollerRef = useRef<HTMLDivElement | null>(null);
  const dragRef = useRef<{ active: boolean; startX: number; scrollLeft: number }>({
    active: false,
    startX: 0,
    scrollLeft: 0,
  });
  const [scrollUi, setScrollUi] = useState({
    canPrev: false,
    canNext: false,
    hasOverflow: false,
    thumbWidthPct: 100,
    thumbShiftPct: 0,
  });

  const updateScrollUi = useCallback(() => {
    const el = scrollerRef.current;
    if (!el) return;
    const maxScroll = Math.max(0, el.scrollWidth - el.clientWidth);
    const hasOverflow = maxScroll > 4;
    if (!hasOverflow || el.scrollWidth <= 0) {
      setScrollUi((prev) =>
        !prev.canPrev && !prev.canNext && !prev.hasOverflow && prev.thumbWidthPct === 100
          ? prev
          : { canPrev: false, canNext: false, hasOverflow: false, thumbWidthPct: 100, thumbShiftPct: 0 },
      );
      return;
    }
    const visibleRatio = Math.max(0.18, Math.min(1, el.clientWidth / el.scrollWidth));
    const progress = Math.max(0, Math.min(1, el.scrollLeft / maxScroll));
    const thumbWidthPct = Number((visibleRatio * 100).toFixed(1));
    const thumbShiftPct = Number((progress * ((1 / visibleRatio - 1) * 100)).toFixed(1));
    setScrollUi({
      canPrev: el.scrollLeft > 4,
      canNext: el.scrollLeft < maxScroll - 4,
      hasOverflow: true,
      thumbWidthPct,
      thumbShiftPct,
    });
  }, []);

  useEffect(() => {
    const el = scrollerRef.current;
    if (!el) return;
    el.scrollLeft = 0;
    const raf = window.requestAnimationFrame(updateScrollUi);
    const onResize = () => updateScrollUi();
    window.addEventListener("resize", onResize);
    let observer: ResizeObserver | null = null;
    if (typeof ResizeObserver !== "undefined") {
      observer = new ResizeObserver(updateScrollUi);
      observer.observe(el);
    }
    return () => {
      window.cancelAnimationFrame(raf);
      window.removeEventListener("resize", onResize);
      observer?.disconnect();
    };
  }, [page.items, updateScrollUi]);

  function scrollByStep(direction: -1 | 1) {
    scrollerRef.current?.scrollBy({ left: direction * SCROLL_STEP_PX, behavior: "smooth" });
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.target !== event.currentTarget) return;
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      scrollByStep(-1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      scrollByStep(1);
    }
  }

  function handleMouseDown(event: MouseEvent<HTMLDivElement>) {
    const el = scrollerRef.current;
    if (!el || event.button !== 0) return;
    if ((event.target as HTMLElement).closest("a,button,input,select")) return;
    dragRef.current = { active: true, startX: event.pageX, scrollLeft: el.scrollLeft };
  }

  function handleMouseMove(event: MouseEvent<HTMLDivElement>) {
    const el = scrollerRef.current;
    if (!el || !dragRef.current.active) return;
    event.preventDefault();
    el.scrollLeft = dragRef.current.scrollLeft - (event.pageX - dragRef.current.startX) * 1.15;
  }

  function stopDrag() {
    dragRef.current.active = false;
  }

  return (
    <div className="flex flex-col gap-3.5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p role="status" className="text-field-label font-medium text-text-secondary tabular-nums">
          {showingText}
        </p>
        <div className="flex items-center gap-2">
          <span className="hidden text-caption text-text-muted sm:inline">{t("carouselHint")}</span>
          <button
            type="button"
            aria-label={t("scrollPrev")}
            disabled={!scrollUi.canPrev}
            onClick={() => scrollByStep(-1)}
            className={cn(
              "inline-flex size-9 items-center justify-center rounded-full border border-border bg-surface/90 text-text shadow-xs backdrop-blur-xs transition-colors enabled:hover:border-primary-fill enabled:hover:bg-primary-fill enabled:hover:text-on-primary disabled:opacity-40 pointer-coarse:min-h-11 pointer-coarse:min-w-11",
              focusRing,
            )}
          >
            <ChevronLeft aria-hidden className="size-4.5" />
          </button>
          <button
            type="button"
            aria-label={t("scrollNext")}
            disabled={!scrollUi.canNext}
            onClick={() => scrollByStep(1)}
            className={cn(
              "inline-flex size-9 items-center justify-center rounded-full border border-border bg-surface/90 text-text shadow-xs backdrop-blur-xs transition-colors enabled:hover:border-primary-fill enabled:hover:bg-primary-fill enabled:hover:text-on-primary disabled:opacity-40 pointer-coarse:min-h-11 pointer-coarse:min-w-11",
              focusRing,
            )}
          >
            <ChevronRight aria-hidden className="size-4.5" />
          </button>
        </div>
      </div>

      <div
        ref={scrollerRef}
        role="region"
        aria-label={t("carouselLabel")}
        tabIndex={0}
        onScroll={updateScrollUi}
        onKeyDown={handleKeyDown}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={stopDrag}
        onMouseLeave={stopDrag}
        className={cn(
          "flex snap-x snap-mandatory flex-nowrap gap-5 overflow-x-auto scroll-smooth rounded-xl py-1.5 select-none [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
          focusRing,
        )}
      >
        {page.items.map((product) => (
          <div key={product.code} className={cardSlotClass}>
            <ProductCard
              product={product}
              action={
                <AccessAction
                  variant="card"
                  product={{
                    code: product.code,
                    versionNo: product.versionNo,
                    price: product.price,
                    isBundle: isBundleProduct(product),
                  }}
                  view={access.view(product.code)}
                  onReload={access.reload}
                />
              }
            />
          </div>
        ))}
      </div>

      {(scrollUi.hasOverflow || page.totalPages > 1) && (
        <div className="flex flex-wrap items-center justify-between gap-4 pt-1">
          {scrollUi.hasOverflow ? (
            <div aria-hidden="true" className="h-1 w-44 overflow-hidden rounded-full bg-border-strong/65">
              <div
                className="h-full rounded-full bg-primary-600 transition-transform duration-150 ease-out motion-reduce:transition-none"
                style={{
                  width: `${scrollUi.thumbWidthPct}%`,
                  transform: `translateX(${scrollUi.thumbShiftPct}%)`,
                }}
              />
            </div>
          ) : (
            <span />
          )}

          {page.totalPages > 1 && (
            <Pagination
              className="justify-center"
              page={currentPage}
              pageSize={page.size || CATALOG_PAGE_SIZE}
              total={page.totalItems}
              onPageChange={onPageChange}
              labels={{ nav: t("paginationLabel"), range: () => "" }}
            />
          )}
        </div>
      )}
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
          className="flex flex-wrap items-center gap-3 rounded-xl border border-danger/30 bg-danger-bg px-4 py-3.5 text-danger"
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
        <CatalogCarousel
          page={page}
          showingText={t("showing", { from, to, total: page.totalItems })}
          currentPage={currentPage}
          onPageChange={onPageChange}
        />
      );
    }
  }
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
  const latestQuery = useRef(query);
  useEffect(() => {
    latestQuery.current = query;
  });

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
      go({ ...latestQuery.current, q: search, page: 1 });
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(handle);
    // `go` chỉ đọc điều kiện mới nhất qua `latestQuery`; chỉ gõ chữ mới đặt lại hẹn giờ.
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
  const kindItems =
    filtersData?.catalogKinds && filtersData.catalogKinds.length > 0
      ? filtersData.catalogKinds
      : (filtersData?.kinds ?? []);

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
      .map((part) => {
        // Tên phần (PART) là nội dung đề (DESIGN mục 8): bọc data-exam-content như chip trên thẻ; Radix dựng
        // nhãn này cả trong danh sách lẫn ở nút đang chọn, `textValue` giữ gõ tìm theo chữ thuần.
        const text = query.kind ? part.name : `${part.kindCode} · ${part.name}`;
        return { value: partValue(part.kindCode, part.code), label: <span data-exam-content>{text}</span>, textValue: text };
      }),
    // Tên môn (SUBJECT) qua pickName từ danh mục song ngữ: không đánh dấu, phép kiểm lẫn ngôn ngữ vẫn áp.
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

  const chipClass = (active: boolean) =>
    cn(
      "inline-flex min-h-9 items-center gap-1.5 rounded-full border px-3.5 py-1 text-field-label font-semibold transition-colors pointer-coarse:min-h-11",
      active
        ? "border-primary-fill bg-primary-fill text-on-primary shadow-xs"
        : "border-border bg-surface/85 text-text-secondary hover:border-primary-200 hover:bg-primary-50 hover:text-primary-text",
      focusRing,
    );

  return (
    <section
      id={CATALOG_ANCHOR_ID}
      aria-labelledby="catalog-title"
      className="mx-auto flex w-full scroll-mt-4 flex-col gap-5 px-4 py-14 sm:w-[90%] sm:max-w-[90%] sm:px-0"
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
          className="flex flex-col gap-3.5 rounded-2xl border border-border bg-surface/82 p-4 shadow-xs backdrop-blur-md sm:p-5"
        >
          {kindItems.length > 0 && (
            <div role="group" aria-label={t("kindLabel")} className="flex flex-wrap gap-2">
              <button
                type="button"
                aria-pressed={!query.kind}
                onClick={() => go({ ...query, kind: "", part: "", page: 1 })}
                className={chipClass(!query.kind)}
              >
                <span aria-hidden className="size-1.5 rounded-full bg-current opacity-80" />
                {t("kindAll")}
              </button>
              {kindItems.map((kind) => {
                const active = query.kind === kind.code;
                return (
                  <button
                    key={kind.code}
                    type="button"
                    aria-pressed={active}
                    onClick={() => go({ ...query, kind: kind.code, part: "", page: 1 })}
                    className={chipClass(active)}
                  >
                    <span aria-hidden className="size-1.5 rounded-full bg-current opacity-80" />
                    {kind.code}
                  </button>
                );
              })}
            </div>
          )}

          <div
            className={cn(
              "flex flex-wrap items-center gap-2.5",
              kindItems.length > 0 && "border-t border-border/80 pt-3.5",
            )}
          >
            <div className="min-w-0 flex-[2_1_16rem]">
              <label htmlFor="catalog-q" className="sr-only">
                {t("searchLabel")}
              </label>
              <Input
                id="catalog-q"
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={t("searchPlaceholder")}
                leadingIcon={<Search />}
                className={touchFieldText}
              />
            </div>

            <div className="min-w-0 flex-[1_1_10.5rem]">
              <label htmlFor="catalog-format" className="sr-only">
                {t("formatLabel")}
              </label>
              <Select
                id="catalog-format"
                aria-describedby="catalog-format-hint"
                options={formatOptions}
                value={query.format || ALL}
                onValueChange={(value) =>
                  go({ ...query, format: value === ALL ? "" : (value as CatalogQuery["format"]), page: 1 })
                }
              />
            </div>

            <div className="min-w-0 flex-[1_1_11rem]">
              <label htmlFor="catalog-part" className="sr-only">
                {t("partLabel")}
              </label>
              <Select
                id="catalog-part"
                options={partOptions}
                value={partSelected}
                onValueChange={onPartChange}
              />
            </div>

            <div className="min-w-0 flex-[1_1_9.5rem]">
              <label htmlFor="catalog-sort" className="sr-only">
                {t("sortLabel")}
              </label>
              <Select
                id="catalog-sort"
                options={CATALOG_SORTS.map((sort) => ({ value: sort, label: t(`sort.${sort}`) }))}
                value={query.sort}
                onValueChange={(value) => go({ ...query, sort: value as CatalogQuery["sort"], page: 1 })}
              />
            </div>

            <Button
              type="button"
              variant="secondary"
              className="h-10 pointer-coarse:min-h-11"
              onClick={clearFilters}
            >
              {t("clearFilters")}
            </Button>
          </div>

          <p id="catalog-format-hint" className="text-caption text-text-secondary">
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
