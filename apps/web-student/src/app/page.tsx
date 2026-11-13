import { Button, Card } from "@thi360/ui";
import { BarChart3, CheckSquare, TrendingUp } from "lucide-react";
import { getTranslations } from "next-intl/server";
import Link from "next/link";
import { StudentHeader } from "@/components/student-header";

export default async function LandingPage() {
  const t = await getTranslations("landing");
  const common = await getTranslations("common");
  const features = [
    {
      icon: CheckSquare,
      title: t("features.structureTitle"),
      description: t("features.structureDescription"),
    },
    {
      icon: TrendingUp,
      title: t("features.analysisTitle"),
      description: t("features.analysisDescription"),
    },
    {
      icon: BarChart3,
      title: t("features.practiceTitle"),
      description: t("features.practiceDescription"),
    },
  ];
  return (
    <div className="flex min-h-screen flex-col bg-page text-text">
      <StudentHeader
        actions={
          <Button asChild size="md">
            <Link href="/dang-nhap">{t("ctaHeader")}</Link>
          </Button>
        }
      />

      <section className="bg-primary-50">
        <div className="mx-auto flex max-w-[1170px] flex-col items-start gap-5 px-6 py-18">
          <span className="rounded-full border border-primary-200 bg-surface px-3.5 py-1.5 text-field-label font-semibold text-primary-800">
            {t("badge")}
          </span>
          <h1 className="max-w-[720px] text-display font-bold text-primary-900">{t("heroTitle")}</h1>
          <p className="max-w-[620px] text-title font-normal text-text-secondary">
            {t("heroDescription")}
          </p>
          <Button asChild size="lg">
            <Link href="/dang-nhap">{t("ctaHero")}</Link>
          </Button>
        </div>
      </section>

      <section className="mx-auto flex w-full max-w-[1170px] flex-col gap-6 px-6 py-14">
        <h2 className="text-h2 font-bold text-text">{t("featuresTitle")}</h2>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-5">
          {features.map((feature) => {
            const Icon = feature.icon;
            return (
              <Card key={feature.title} className="flex flex-col gap-2.5 p-6">
                <span
                  aria-hidden
                  className="flex size-10 items-center justify-center rounded-md bg-primary-50 text-primary-text"
                >
                  <Icon className="size-5" />
                </span>
                <h3 className="text-title font-semibold text-text">{feature.title}</h3>
                <p className="text-body text-text-muted">{feature.description}</p>
              </Card>
            );
          })}
        </div>
      </section>

      <footer className="mt-auto bg-primary-fill text-on-primary">
        <div className="mx-auto flex max-w-[1170px] flex-wrap items-center justify-between gap-2 px-6 py-5">
          <span className="font-semibold">{common("appName")}</span>
          <span>{t("footerNote")}</span>
        </div>
      </footer>
    </div>
  );
}
