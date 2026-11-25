import { PublicShell } from "@/components/public-shell";
import { parseCatalogQuery } from "@/features/storefront/catalog-query";
import { CatalogSection } from "@/features/storefront/catalog-section";
import {
  FaqSection,
  HeroSection,
  StepsSection,
  SupportSection,
  WhySection,
} from "@/features/storefront/landing-sections";
import { loadFiltersOnServer, loadProductsOnServer } from "@/features/storefront/server-api";

type SearchParams = Record<string, string | string[] | undefined>;

/** Trang giới thiệu 6 khối (TM 11.2): giới thiệu, chọn bài thi (`#exams`), vì sao, các bước, câu hỏi thường gặp, hỗ trợ. */
export default async function LandingPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  // Điều kiện của danh mục nằm trên URL; trang đọc cookie giao diện, ngôn ngữ nên luôn dựng theo từng request,
  // còn dữ liệu danh mục được cache 60 giây ở lời gọi API (`revalidate: 60`).
  const query = parseCatalogQuery(await searchParams);
  const [filters, products] = await Promise.all([loadFiltersOnServer(), loadProductsOnServer(query)]);
  const examKinds =
    filters.status === "ok"
      ? filters.data.catalogKinds && filters.data.catalogKinds.length > 0
        ? filters.data.catalogKinds.map((item) => item.code)
        : filters.data.kinds.length > 0
          ? filters.data.kinds.map((item) => item.code)
          : undefined
      : undefined;

  return (
    <PublicShell variant="landing">
      <HeroSection examKinds={examKinds} />
      <CatalogSection query={query} filters={filters} products={products} />
      <WhySection />
      <StepsSection />
      <FaqSection />
      <SupportSection />
    </PublicShell>
  );
}
