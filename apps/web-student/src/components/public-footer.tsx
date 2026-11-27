import { BrandWordmark, cn, focusRing } from "@thi360/ui";
import {
  BookOpen,
  BookOpenText,
  FileBadge2,
  Files,
  FileText,
  Mail,
  MapPin,
  Newspaper,
  Phone,
} from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { moitRegistrationUrl, SITE_CONTACT } from "@/lib/contact";

function MoitBadge({ href, label, line1, line2 }: { href: string; label: string; line1: string; line2: string }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={label}
      className={cn("inline-flex items-center rounded-md select-none", focusRing)}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 212 66"
        className="h-12 w-auto"
        aria-hidden
      >
        <rect x="34" y="13" width="174" height="40" rx="6" fill="#0d7cc7" />
        <rect x="36" y="15" width="170" height="36" rx="5" fill="none" stroke="#ffffff" strokeOpacity="0.25" />
        <circle cx="33" cy="33" r="30" fill="#ffffff" />
        <circle cx="33" cy="33" r="24" fill="#0d7cc7" />
        <path
          d="M22 33.5 L29.5 41 L45 24.5"
          fill="none"
          stroke="#ffffff"
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <text
          x="122"
          y="30"
          textAnchor="middle"
          fill="#ffffff"
          fontSize="13"
          fontWeight="700"
          fontFamily="sans-serif"
          letterSpacing="0.4"
        >
          {line1}
        </text>
        <text
          x="122"
          y="45"
          textAnchor="middle"
          fill="#d9f0ff"
          fontSize="10.5"
          fontWeight="600"
          fontFamily="sans-serif"
          letterSpacing="0.3"
        >
          {line2}
        </text>
      </svg>
    </a>
  );
}

function ColumnHeading({ children }: { children: string }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-label font-bold tracking-[0.04em] text-on-primary uppercase [[data-theme='dark']_&]:text-text">
        {children}
      </h3>
      <span aria-hidden className="h-0.5 w-8 rounded-full bg-white/40 [[data-theme='dark']_&]:bg-primary-text/50" />
    </div>
  );
}

export function PublicFooter({ variant = "default" }: { variant?: "default" | "landing" }) {
  const common = useTranslations("common");
  const t = useTranslations("footer");
  const isLanding = variant === "landing";
  const moitUrl = moitRegistrationUrl();
  const containerClass = cn(
    "mx-auto px-4",
    isLanding ? "w-full sm:w-[90%] sm:max-w-[90%] sm:px-0" : "max-w-[1440px] sm:px-6",
  );

  const linkClass = cn(
    "inline-flex items-center gap-2 text-body text-on-primary transition-colors hover:underline [[data-theme='dark']_&]:text-text-secondary [[data-theme='dark']_&]:hover:text-text",
    focusRing,
  );
  const iconClass = "size-4 shrink-0 text-on-primary [[data-theme='dark']_&]:text-primary-text";

  return (
    <footer className="bg-primary-fill text-on-primary [[data-theme='dark']_&]:border-t [[data-theme='dark']_&]:border-border [[data-theme='dark']_&]:bg-surface [[data-theme='dark']_&]:text-text">
      <div className={cn(containerClass, "py-12")}>
        <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-[1.45fr_0.85fr_1fr_1fr]">
          <div className="flex flex-col items-start gap-4">
            <Link
              href="/"
              className={cn("inline-flex rounded-xl bg-white px-3 py-1.5 shadow-sm", focusRing)}
            >
              <BrandWordmark alt={common("appName")} className="h-9 [[data-theme='dark']_&]:bg-transparent [[data-theme='dark']_&]:p-0" />
            </Link>

            <p className="text-label font-bold tracking-[0.02em] text-on-primary uppercase [[data-theme='dark']_&]:text-text">
              {t("company.name")}
            </p>

            <ul className="flex flex-col gap-2.5 text-field-label text-on-primary [[data-theme='dark']_&]:text-text-secondary">
              <li className="flex items-start gap-2.5">
                <FileBadge2 aria-hidden className={cn("mt-0.5", iconClass)} />
                <span>{t("company.taxId")}</span>
              </li>
              <li className="flex items-start gap-2.5">
                <MapPin aria-hidden className={cn("mt-0.5", iconClass)} />
                <span>{t("company.hanoiOffice")}</span>
              </li>
              <li className="flex items-start gap-2.5">
                <MapPin aria-hidden className={cn("mt-0.5", iconClass)} />
                <span>{t("company.hcmcOffice")}</span>
              </li>
              <li className="flex items-center gap-2.5">
                <Mail aria-hidden className={iconClass} />
                <a href={`mailto:${SITE_CONTACT.email}`} className={cn("hover:underline", focusRing)}>
                  {SITE_CONTACT.email}
                </a>
              </li>
              <li className="flex items-center gap-2.5">
                <Phone aria-hidden className={iconClass} />
                <a href={`tel:${SITE_CONTACT.hotlineTel}`} className={cn("hover:underline", focusRing)}>
                  {SITE_CONTACT.hotline}
                </a>
              </li>
            </ul>
          </div>

          <div className="flex flex-col gap-4">
            <ColumnHeading>{t("categories.title")}</ColumnHeading>
            <ul className="flex flex-col gap-2.5">
              <li>
                <Link href="/#exams" className={linkClass}>
                  <BookOpenText aria-hidden className={iconClass} />
                  <span>{t("categories.courses")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#exams" className={linkClass}>
                  <FileText aria-hidden className={iconClass} />
                  <span>{t("categories.exams")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#faq" className={linkClass}>
                  <Newspaper aria-hidden className={iconClass} />
                  <span>{t("categories.news")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#exams" className={linkClass}>
                  <BookOpen aria-hidden className={iconClass} />
                  <span>{t("categories.books")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#exams" className={linkClass}>
                  <Files aria-hidden className={iconClass} />
                  <span>{t("categories.documents")}</span>
                </Link>
              </li>
            </ul>
          </div>

          <div className="flex flex-col gap-4">
            <ColumnHeading>{t("support.title")}</ColumnHeading>
            <div className="flex flex-col gap-2.5 text-body text-on-primary [[data-theme='dark']_&]:text-text-secondary">
              <p>{t("support.subtitle")}</p>
              <p>
                <strong className="font-semibold text-on-primary [[data-theme='dark']_&]:text-text">
                  {t("support.coursesContactLabel")}
                </strong>{" "}
                <a href={`tel:${SITE_CONTACT.supportPhoneTel}`} className={cn("hover:underline", focusRing)}>
                  {SITE_CONTACT.supportPhone}
                </a>
              </p>
              <p>
                <strong className="font-semibold text-on-primary [[data-theme='dark']_&]:text-text">
                  {t("support.booksContactLabel")}
                </strong>{" "}
                <a href={`tel:${SITE_CONTACT.supportPhoneTel}`} className={cn("hover:underline", focusRing)}>
                  {SITE_CONTACT.supportPhone}
                </a>
              </p>
              <p>
                <strong className="font-semibold text-on-primary [[data-theme='dark']_&]:text-text">
                  {t("support.hoursLabel")}
                </strong>{" "}
                <span>{t("support.hoursValue")}</span>
              </p>
            </div>
          </div>

          <div className="flex flex-col gap-4">
            <ColumnHeading>{t("policies.title")}</ColumnHeading>
            <ul className="flex flex-col gap-2.5">
              <li>
                <Link href="/dieu-khoan-giao-dich-chung" className={linkClass}>
                  {t("policies.terms")}
                </Link>
              </li>
              <li>
                <Link href="/chinh-sach-bao-mat-thong-tin" className={linkClass}>
                  {t("policies.privacy")}
                </Link>
              </li>
              <li>
                <Link href="/dieu-khoan-giao-dich-chung#delivery" className={linkClass}>
                  {t("policies.shipping")}
                </Link>
              </li>
            </ul>
            {moitUrl && (
              <div className="pt-1">
                <MoitBadge
                  href={moitUrl}
                  label={t("moitBadge.ariaLabel")}
                  line1={t("moitBadge.line1")}
                  line2={t("moitBadge.line2")}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="border-t border-white/15 bg-primary-fill-hover text-on-primary [[data-theme='dark']_&]:border-border [[data-theme='dark']_&]:bg-subtle [[data-theme='dark']_&]:text-text-secondary">
        <div className={cn(containerClass, "flex flex-wrap items-center justify-between gap-3 py-4 text-field-label")}>
          <span>{t("bottom.copyright")}</span>
          <div className="flex items-center gap-5">
            <Link href="/dieu-khoan-giao-dich-chung" className={cn("hover:underline [[data-theme='dark']_&]:hover:text-text", focusRing)}>
              {t("bottom.terms")}
            </Link>
            <Link href="/chinh-sach-bao-mat-thong-tin" className={cn("hover:underline [[data-theme='dark']_&]:hover:text-text", focusRing)}>
              {t("bottom.privacy")}
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
