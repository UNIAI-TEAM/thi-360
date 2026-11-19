import { Button } from "@thi360/ui";
import { getTranslations } from "next-intl/server";
import Link from "next/link";
import { PublicShell } from "@/components/public-shell";
import { parseCatalogQuery } from "@/features/storefront/catalog-query";
import { CatalogSection } from "@/features/storefront/catalog-section";
import { loadFiltersOnServer, loadProductsOnServer } from "@/features/storefront/server-api";

type SearchParams = Record<string, string | string[] | undefined>;

export default async function LandingPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const t = await getTranslations("landing");
  // Điều kiện của danh mục nằm trên URL; trang đọc cookie giao diện, ngôn ngữ nên luôn dựng theo từng request,
  // còn dữ liệu danh mục được cache 60 giây ở lời gọi API (`revalidate: 60`).
  const query = parseCatalogQuery(await searchParams);
  const [filters, products] = await Promise.all([loadFiltersOnServer(), loadProductsOnServer(query)]);

  return (
    <PublicShell>
      <section className="bg-primary-50">
        <div className="mx-auto flex max-w-[1170px] flex-col items-start gap-5 px-6 py-18">
          <h1 className="max-w-[720px] text-display font-bold text-primary-900">{t("heroTitle")}</h1>
          <p className="max-w-[620px] text-title font-normal text-text-secondary">{t("heroDescription")}</p>
          <Button asChild size="lg">
            <Link href="#exams">{t("ctaHero")}</Link>
          </Button>
        </div>
      </section>
      <CatalogSection query={query} filters={filters} products={products} />
    </PublicShell>
  );
}
