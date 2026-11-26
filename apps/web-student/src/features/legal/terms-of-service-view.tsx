import { cn, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { SiteBaseInfo } from "@/lib/site-url";

const CONTACT_EMAIL = "hsaeducation.jsc@gmail.com";
const SUPPORT_HOTLINE = "0974187313";

export function TermsOfServiceView({ site }: { site: SiteBaseInfo }) {
  const legal = useTranslations("legal");
  const t = useTranslations("legal.terms");
  const vars = {
    siteUrl: site.siteUrl,
    email: CONTACT_EMAIL,
    phone: SUPPORT_HOTLINE,
  };

  return (
    <div className="mx-auto flex w-full max-w-[880px] flex-col gap-6 px-4 py-8 sm:px-6 sm:py-10">
      <nav aria-label={legal("breadcrumbLabel")}>
        <ol className="flex flex-wrap items-center gap-2 text-field-label text-text-muted">
          <li>
            <Link href="/" className={cn("hover:underline", focusRing)}>
              {legal("breadcrumbHome")}
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li aria-current="page" className="text-text">
            {t("title")}
          </li>
        </ol>
      </nav>

      <article className="flex flex-col gap-7 rounded-2xl border border-border bg-surface p-6 shadow-sm sm:p-10">
        <header className="flex flex-col gap-3 border-b border-border pb-5">
          <h1 className="text-h2 font-bold text-primary-900">{t("heading")}</h1>
          <p className="text-body text-text-secondary">{t("intro")}</p>
        </header>

        <section className="flex flex-col gap-4">
          <h2 className="text-h3 font-bold text-text">{t("s1.heading")}</h2>

          <div className="flex flex-col gap-1.5">
            <h3 className="text-title font-semibold text-text">{t("s1.sub1Title")}</h3>
            <p className="text-body text-text-secondary">{t("s1.sub1Body")}</p>
          </div>

          <div className="flex flex-col gap-1.5">
            <h3 className="text-title font-semibold text-text">{t("s1.sub2Title")}</h3>
            <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s1.sub2B1")}</li>
              <li>{t("s1.sub2B2")}</li>
              <li>{t("s1.sub2B3")}</li>
            </ul>
          </div>

          <div className="flex flex-col gap-1.5">
            <h3 className="text-title font-semibold text-text">{t("s1.sub3Title")}</h3>
            <p className="text-body text-text-secondary">{t("s1.sub3Intro")}</p>
            <ol className="list-decimal space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s1.sub3B1")}</li>
              <li>{t("s1.sub3B2")}</li>
              <li>{t("s1.sub3B3")}</li>
              <li>{t("s1.sub3B4")}</li>
              <li>{t("s1.sub3B5")}</li>
              <li>{t("s1.sub3B6")}</li>
            </ol>
          </div>

          <div className="flex flex-col gap-1.5">
            <h3 className="text-title font-semibold text-text">{t("s1.sub4Title")}</h3>
            <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s1.sub4B1")}</li>
              <li>{t("s1.sub4B2")}</li>
              <li>{t("s1.sub4B3")}</li>
            </ul>
          </div>

          <div className="flex flex-col gap-1.5">
            <h3 className="text-title font-semibold text-text">{t("s1.sub5Title")}</h3>
            <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s1.sub5B1")}</li>
              <li>{t("s1.sub5B2")}</li>
              <li>{t("s1.sub5B3")}</li>
              <li>
                <span>{t("s1.sub5B4")}</span>
                <ul className="mt-1.5 list-disc space-y-1 pl-6">
                  <li>{t("s1.sub5Sub1")}</li>
                  <li>{t("s1.sub5Sub2")}</li>
                  <li>{t("s1.sub5Sub3")}</li>
                </ul>
              </li>
            </ul>
          </div>
        </section>

        <section id="delivery" className="flex flex-col gap-2.5 scroll-mt-24">
          <h2 className="text-h3 font-bold text-text">{t("s2.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s2.p1")}</p>
          <p className="text-body text-text-secondary">{t("s2.p2", vars)}</p>
          <p className="text-body text-text-secondary">{t("s2.p3")}</p>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s3.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s3.intro")}</p>
          <ul className="list-disc space-y-2 pl-6 text-body text-text-secondary">
            <li>
              <p>{t("s3.b1")}</p>
              <ul className="mt-1.5 list-disc space-y-1 pl-6">
                <li>{t("s3.b1Email", vars)}</li>
                <li>{t("s3.b1Hotline", vars)}</li>
              </ul>
              <p className="mt-1.5">{t("s3.b1Note")}</p>
            </li>
            <li>{t("s3.b2")}</li>
            <li>{t("s3.b3")}</li>
          </ul>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s4.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s4.p1")}</p>
          <p className="text-body text-text-secondary">{t("s4.p2")}</p>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s5.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s5.p1", vars)}</p>
        </section>

        <section className="flex flex-col gap-4">
          <h2 className="text-h3 font-bold text-text">{t("s6.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s6.intro")}</p>

          <div className="flex flex-col gap-2.5">
            <h3 className="text-title font-semibold text-text">{t("s6.aTitle")}</h3>
            <div className="overflow-x-auto rounded-xl border border-border">
              <table className="w-full border-collapse text-left text-body">
                <thead>
                  <tr className="border-b border-border bg-subtle text-label font-semibold text-text">
                    <th className="px-4 py-3">{t("s6.colCase")}</th>
                    <th className="px-4 py-3">{t("s6.colCondition")}</th>
                    <th className="px-4 py-3 whitespace-nowrap">{t("s6.colRate")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-text-secondary">
                  <tr>
                    <td className="px-4 py-3">{t("s6.row1Case")}</td>
                    <td className="px-4 py-3">{t("s6.row1Condition")}</td>
                    <td className="px-4 py-3 font-semibold whitespace-nowrap text-primary-text">{t("s6.row1Rate")}</td>
                  </tr>
                  <tr>
                    <td className="px-4 py-3">{t("s6.row2Case")}</td>
                    <td className="px-4 py-3">{t("s6.row2Condition")}</td>
                    <td className="px-4 py-3 font-semibold whitespace-nowrap text-primary-text">{t("s6.row2Rate")}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="text-title font-semibold text-text">{t("s6.bTitle")}</h3>
            <p className="text-body text-text-secondary">{t("s6.bIntro")}</p>
            <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s6.b1")}</li>
              <li>{t("s6.b2")}</li>
              <li>{t("s6.b3")}</li>
              <li>{t("s6.b4")}</li>
              <li>{t("s6.b5")}</li>
              <li>{t("s6.b6")}</li>
              <li>{t("s6.b7")}</li>
            </ul>
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="text-title font-semibold text-text">{t("s6.cTitle")}</h3>
            <ol className="list-decimal space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s6.c1", vars)}</li>
              <li>{t("s6.c2")}</li>
              <li>{t("s6.c3")}</li>
              <li>{t("s6.c4")}</li>
            </ol>
            <p className="text-field-label font-medium text-text-muted">{t("s6.cNote")}</p>
          </div>
        </section>

        <section className="flex flex-col gap-4">
          <h2 className="text-h3 font-bold text-text">{t("s7.heading")}</h2>

          <div className="flex flex-col gap-2">
            <h3 className="text-title font-semibold text-text">{t("s7.aTitle")}</h3>
            <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s7.a1")}</li>
              <li>{t("s7.a2")}</li>
              <li>{t("s7.a3")}</li>
              <li>{t("s7.a4")}</li>
            </ul>
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="text-title font-semibold text-text">{t("s7.bTitle")}</h3>
            <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
              <li>{t("s7.b1")}</li>
              <li>{t("s7.b2")}</li>
              <li>{t("s7.b3")}</li>
              <li>{t("s7.b4")}</li>
              <li>{t("s7.b5")}</li>
            </ul>
          </div>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s8.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s8.p1")}</p>
          <p className="text-body text-text-secondary">{t("s8.p2", vars)}</p>
          <p className="text-body text-text-secondary">{t("s8.p3")}</p>
          <p className="pt-2 text-field-label font-semibold text-text">{t("s8.effectiveDate")}</p>
        </section>
      </article>
    </div>
  );
}
