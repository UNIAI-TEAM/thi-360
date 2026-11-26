import { cn, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { SiteBaseInfo } from "@/lib/site-url";
import { SITE_CONTACT } from "@/lib/contact";

export function PrivacyPolicyView({ site }: { site: SiteBaseInfo }) {
  const legal = useTranslations("legal");
  const t = useTranslations("legal.privacy");
  const vars = {
    siteUrl: site.siteUrl,
    siteHost: site.siteHost,
    email: SITE_CONTACT.email,
    phone: SITE_CONTACT.hotline,
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

      <article className="flex flex-col gap-6 rounded-2xl border border-border bg-surface p-6 shadow-sm sm:p-10">
        <header className="flex flex-col gap-3 border-b border-border pb-5">
          <h1 className="text-h2 font-bold text-primary-900">{t("heading")}</h1>
          <p className="text-body text-text-secondary">{t("intro", vars)}</p>
        </header>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s1.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s1.p1", vars)}</p>
          <p className="text-body text-text-secondary">{t("s1.p2")}</p>
          <p className="text-body text-text-secondary">{t("s1.p3", vars)}</p>
          <p className="text-body text-text-secondary">{t("s1.p4")}</p>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s2.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s2.intro", vars)}</p>
          <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
            <li>{t("s2.b1")}</li>
            <li>{t("s2.b2")}</li>
            <li>{t("s2.b3")}</li>
            <li>{t("s2.b4")}</li>
            <li>{t("s2.b5")}</li>
          </ul>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s3.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s3.p1", vars)}</p>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s4.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s4.intro")}</p>
          <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
            <li>{t("s4.b1")}</li>
            <li>{t("s4.b2")}</li>
          </ul>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s5.heading")}</h2>
          <div className="flex flex-col gap-1.5 rounded-xl border border-border bg-subtle/60 p-4 text-body text-text-secondary">
            <p className="font-semibold text-text">{t("s5.company")}</p>
            <p>{t("s5.hanoi")}</p>
            <p>{t("s5.hcmc")}</p>
            <p>
              <span className="font-medium text-text">{t("s5.phoneLabel")}</span>{" "}
              <a href={`tel:${SITE_CONTACT.hotlineTel}`} className={cn("text-primary-text hover:underline", focusRing)}>
                {SITE_CONTACT.hotline}
              </a>
            </p>
            <p>
              <span className="font-medium text-text">{t("s5.emailLabel")}</span>{" "}
              <a href={`mailto:${SITE_CONTACT.email}`} className={cn("text-primary-text hover:underline", focusRing)}>
                {SITE_CONTACT.email}
              </a>
            </p>
            <p>
              <span className="font-medium text-text">{t("s5.websiteLabel")}</span>{" "}
              <a href={site.siteUrl} className={cn("text-primary-text hover:underline", focusRing)}>
                {site.siteUrl}
              </a>
            </p>
          </div>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s6.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s6.p1", vars)}</p>
          <p className="text-body text-text-secondary">{t("s6.p2", vars)}</p>
        </section>

        <section className="flex flex-col gap-2.5">
          <h2 className="text-h3 font-bold text-text">{t("s7.heading")}</h2>
          <p className="text-body text-text-secondary">{t("s7.intro", vars)}</p>
          <ul className="list-disc space-y-1.5 pl-6 text-body text-text-secondary">
            <li>{t("s7.b1")}</li>
            <li>{t("s7.b2")}</li>
            <li>{t("s7.b3")}</li>
          </ul>
          <p className="text-body text-text-secondary">{t("s7.p2", vars)}</p>
        </section>
      </article>
    </div>
  );
}
