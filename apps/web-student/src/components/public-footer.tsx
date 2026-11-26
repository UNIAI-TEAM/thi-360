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

function MoitBadge({ label, line1, line2 }: { label: string; line1: string; line2: string }) {
  return (
    <div
      role="img"
      aria-label={label}
      className="inline-flex items-center select-none"
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
    </div>
  );
}

function FacebookIcon() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="size-4 fill-current">
      <path d="M14 13.5h2.5l1-4H14v-2c0-1.03 0-2 2-2h1.5V2.14c-.326-.043-1.557-.14-2.857-.14C11.928 2 10 3.657 10 6.7v2.8H7v4h3V22h4v-8.5Z" />
    </svg>
  );
}

function YoutubeIcon() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="size-4 fill-current">
      <path d="M21.58 7.19a2.51 2.51 0 0 0-1.77-1.77C18.25 5 12 5 12 5s-6.25 0-7.81.42a2.51 2.51 0 0 0-1.77 1.77C2 8.75 2 12 2 12s0 3.25.42 4.81a2.51 2.51 0 0 0 1.77 1.77C5.75 19 12 19 12 19s6.25 0 7.81-.42a2.51 2.51 0 0 0 1.77-1.77C22 15.25 22 12 22 12s0-3.25-.42-4.81ZM10 15.25v-6.5L15.5 12 10 15.25Z" />
    </svg>
  );
}

function TiktokIcon() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="size-4 fill-current">
      <path d="M16.6 5.82A4.28 4.28 0 0 1 15.54 3h-3.09v12.4a2.59 2.59 0 0 1-2.59 2.5c-1.42 0-2.6-1.16-2.6-2.6 0-1.72 1.66-3.01 3.37-2.48V9.66c-3.45-.46-6.47 2.22-6.47 5.64 0 3.33 2.76 5.7 5.69 5.7 3.14 0 5.69-2.55 5.69-5.7V9.01a7.35 7.35 0 0 0 4.3 1.38V7.3s-1.88.09-3.24-1.48Z" />
    </svg>
  );
}

function ColumnHeading({ children }: { children: string }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-label font-bold tracking-[0.04em] text-on-primary uppercase">{children}</h3>
      <span aria-hidden className="h-0.5 w-8 rounded-full bg-white/40" />
    </div>
  );
}

export function PublicFooter({ variant = "default" }: { variant?: "default" | "landing" }) {
  const common = useTranslations("common");
  const t = useTranslations("footer");
  const isLanding = variant === "landing";
  const containerClass = cn(
    "mx-auto px-4",
    isLanding ? "w-full sm:w-[90%] sm:max-w-[90%] sm:px-0" : "max-w-[1170px] sm:px-6",
  );

  const linkClass = cn(
    "inline-flex items-center gap-2 text-body text-on-primary/90 transition-colors hover:text-on-primary hover:underline",
    focusRing,
  );
  const socialTileClass = cn(
    "inline-flex size-9 items-center justify-center rounded-lg bg-white/15 text-on-primary transition-colors hover:bg-white/25",
    focusRing,
  );

  return (
    <footer className="bg-primary-fill text-on-primary">
      <div className={cn(containerClass, "py-12")}>
        <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-[1.45fr_0.85fr_1fr_1fr]">
          <div className="flex flex-col items-start gap-4">
            <Link
              href="/"
              className={cn("inline-flex rounded-xl bg-white px-3 py-1.5 shadow-sm", focusRing)}
            >
              <BrandWordmark alt={common("appName")} className="h-9 [[data-theme='dark']_&]:bg-transparent [[data-theme='dark']_&]:p-0" />
            </Link>

            <p className="text-label font-bold tracking-[0.02em] text-on-primary uppercase">
              {t("company.name")}
            </p>

            <ul className="flex flex-col gap-2.5 text-field-label text-on-primary/90">
              <li className="flex items-start gap-2.5">
                <FileBadge2 aria-hidden className="mt-0.5 size-4 shrink-0 text-on-primary/80" />
                <span>{t("company.taxId")}</span>
              </li>
              <li className="flex items-start gap-2.5">
                <MapPin aria-hidden className="mt-0.5 size-4 shrink-0 text-on-primary/80" />
                <span>{t("company.hanoiOffice")}</span>
              </li>
              <li className="flex items-start gap-2.5">
                <MapPin aria-hidden className="mt-0.5 size-4 shrink-0 text-on-primary/80" />
                <span>{t("company.hcmcOffice")}</span>
              </li>
              <li className="flex items-center gap-2.5">
                <Mail aria-hidden className="size-4 shrink-0 text-on-primary/80" />
                <a href={`mailto:${t("company.email")}`} className={cn("hover:underline", focusRing)}>
                  {t("company.email")}
                </a>
              </li>
              <li className="flex items-center gap-2.5">
                <Phone aria-hidden className="size-4 shrink-0 text-on-primary/80" />
                <a href="tel:0988371194" className={cn("hover:underline", focusRing)}>
                  {t("company.phone")}
                </a>
              </li>
            </ul>

            <div className="flex items-center gap-2 pt-1">
              <a href="#support" aria-label={t("social.facebook")} className={socialTileClass}>
                <FacebookIcon />
              </a>
              <a href="#support" aria-label={t("social.youtube")} className={socialTileClass}>
                <YoutubeIcon />
              </a>
              <a href="#support" aria-label={t("social.tiktok")} className={socialTileClass}>
                <TiktokIcon />
              </a>
            </div>
          </div>

          <div className="flex flex-col gap-4">
            <ColumnHeading>{t("categories.title")}</ColumnHeading>
            <ul className="flex flex-col gap-2.5">
              <li>
                <Link href="/#exams" className={linkClass}>
                  <BookOpenText aria-hidden className="size-4 shrink-0 text-on-primary/80" />
                  <span>{t("categories.courses")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#exams" className={linkClass}>
                  <FileText aria-hidden className="size-4 shrink-0 text-on-primary/80" />
                  <span>{t("categories.exams")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#faq" className={linkClass}>
                  <Newspaper aria-hidden className="size-4 shrink-0 text-on-primary/80" />
                  <span>{t("categories.news")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#exams" className={linkClass}>
                  <BookOpen aria-hidden className="size-4 shrink-0 text-on-primary/80" />
                  <span>{t("categories.books")}</span>
                </Link>
              </li>
              <li>
                <Link href="/#exams" className={linkClass}>
                  <Files aria-hidden className="size-4 shrink-0 text-on-primary/80" />
                  <span>{t("categories.documents")}</span>
                </Link>
              </li>
            </ul>
          </div>

          <div className="flex flex-col gap-4">
            <ColumnHeading>{t("support.title")}</ColumnHeading>
            <div className="flex flex-col gap-2.5 text-body text-on-primary/90">
              <p>{t("support.subtitle")}</p>
              <p>
                <strong className="font-semibold text-on-primary">{t("support.coursesContactLabel")}</strong>{" "}
                <a href="tel:0961167606" className={cn("hover:underline", focusRing)}>
                  {t("support.coursesContactPhone")}
                </a>
              </p>
              <p>
                <strong className="font-semibold text-on-primary">{t("support.booksContactLabel")}</strong>{" "}
                <a href="tel:0961167606" className={cn("hover:underline", focusRing)}>
                  {t("support.booksContactPhone")}
                </a>
              </p>
              <p>
                <strong className="font-semibold text-on-primary">{t("support.hoursLabel")}</strong>{" "}
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
            <div className="pt-1">
              <MoitBadge
                label={t("moitBadge.ariaLabel")}
                line1={t("moitBadge.line1")}
                line2={t("moitBadge.line2")}
              />
            </div>
          </div>
        </div>
      </div>

      <div className="border-t border-white/10 bg-black/20">
        <div className={cn(containerClass, "flex flex-wrap items-center justify-between gap-3 py-4 text-field-label text-on-primary/85")}>
          <span>{t("bottom.copyright")}</span>
          <div className="flex items-center gap-5">
            <Link href="/dieu-khoan-giao-dich-chung" className={cn("hover:text-on-primary hover:underline", focusRing)}>
              {t("bottom.terms")}
            </Link>
            <Link href="/chinh-sach-bao-mat-thong-tin" className={cn("hover:text-on-primary hover:underline", focusRing)}>
              {t("bottom.privacy")}
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
