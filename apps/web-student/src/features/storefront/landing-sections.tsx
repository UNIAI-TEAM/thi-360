import { Button, cn, focusRing } from "@thi360/ui";
import {
  BookOpen,
  ChartLine,
  ChevronDown,
  ChevronsDown,
  LayoutGrid,
  ListChecks,
  MessageCircle,
  PenLine,
  Target,
} from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";
import { CATALOG_ANCHOR_ID } from "./catalog-query";
import { hasSupportContact, readSupportContact } from "./support";

/** Danh sách mã kỳ thi dự phòng theo nhóm danh mục hệ thống EXAM khi chưa tải được bộ lọc. */
export const DEFAULT_EXAM_KINDS = [
  "HSA",
  "QDA",
  "BCA",
  "VACT",
  "SPT",
  "SP2E",
  "THPTQG",
  "HSCA",
  "VSAT",
] as const;

const FAQ_KEYS = ["formats", "attempts", "otherAccount", "paidNoExam", "refund", "voucher"] as const;
/** Hai câu hỏi đầu mở sẵn; bốn câu còn lại gập. */
const FAQ_OPEN_BY_DEFAULT: readonly (typeof FAQ_KEYS)[number][] = ["formats", "attempts"];

const landingContainer = "mx-auto w-full px-4 sm:w-[90%] sm:max-w-[90%] sm:px-0";
const sectionInner = `${landingContainer} flex flex-col gap-6 py-14`;
const glassCard = "flex flex-col gap-2.5 rounded-2xl border border-border bg-surface/88 p-6 backdrop-blur-xs";
const surfaceGlassBand = "border-y border-border/75 bg-surface/45 backdrop-blur-xs";

function SectionTitle({ id, children }: { id: string; children: ReactNode }) {
  return (
    <h2 id={id} className="text-h2 font-bold text-text">
      {children}
    </h2>
  );
}

function IconTile({ children }: { children: ReactNode }) {
  return (
    <span
      aria-hidden
      className="flex size-10 shrink-0 items-center justify-center rounded-md bg-primary-50 text-primary-text [&_svg]:size-5"
    >
      {children}
    </span>
  );
}

/** Khối 1: giới thiệu, danh sách các kỳ thi từ danh mục EXAM, nút "Khám phá bài thi" (TM 11.2). */
export function HeroSection({ examKinds = DEFAULT_EXAM_KINDS }: { examKinds?: readonly string[] }) {
  const t = useTranslations("landing.hero");
  const formats = [
    { key: "full", icon: <BookOpen /> },
    { key: "part", icon: <LayoutGrid /> },
    { key: "subject", icon: <PenLine /> },
  ] as const;

  return (
    <section
      aria-labelledby="hero-title"
      className="border-b border-border-primary/60 bg-primary-50/35 backdrop-blur-xs"
    >
      <div className={cn(landingContainer, "flex flex-wrap items-center gap-10 py-14 sm:py-16")}>
        <div className="flex min-w-0 flex-[999_1_32rem] flex-col gap-5">
          <h1 id="hero-title" className="max-w-[42rem] text-display font-bold text-primary-900">
            {t("title")}
          </h1>
          <p className="max-w-[42rem] text-title text-text-secondary">{t("description")}</p>
          <ul aria-label={t("kindsLabel")} className="flex flex-wrap gap-2">
            {examKinds.map((kind) => (
              <li key={kind}>
                <Link
                  href={`/?kind=${encodeURIComponent(kind)}#${CATALOG_ANCHOR_ID}`}
                  prefetch={false}
                  className={cn(
                    "inline-flex min-h-8.5 items-center rounded-full border border-primary-200 bg-surface/90 px-3 py-1 text-field-label font-semibold text-primary-800 transition-colors hover:border-primary-600 hover:bg-primary-50 pointer-coarse:min-h-11",
                    focusRing,
                  )}
                >
                  {kind}
                </Link>
              </li>
            ))}
          </ul>
          <div>
            <Button asChild size="lg">
              <Link href={`#${CATALOG_ANCHOR_ID}`}>
                {t("cta")}
                <ChevronsDown aria-hidden />
              </Link>
            </Button>
          </div>
        </div>
        <div className="flex min-w-0 flex-[1_1_21rem] flex-col gap-4 rounded-2xl border border-border-primary bg-surface/88 p-6 shadow-xs backdrop-blur-md">
          <h2 className="text-overline font-semibold tracking-overline text-primary-text uppercase">
            {t("formatsTitle")}
          </h2>
          {formats.map(({ key, icon }) => (
            <div key={key} className="flex items-start gap-3">
              <IconTile>{icon}</IconTile>
              <div>
                <div className="font-semibold text-text">{t(`formats.${key}.title`)}</div>
                <div className="text-text-muted">{t(`formats.${key}.description`)}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/** Khối 3: "Vì sao luyện thi tại đây?". */
export function WhySection() {
  const t = useTranslations("landing.why");
  const items = [
    { key: "structure", icon: <ListChecks /> },
    { key: "choose", icon: <Target /> },
    { key: "results", icon: <ChartLine /> },
  ] as const;

  return (
    <section aria-labelledby="why-title" className={surfaceGlassBand}>
      <div className={sectionInner}>
        <SectionTitle id="why-title">{t("title")}</SectionTitle>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(min(16rem,100%),1fr))] gap-5">
          {items.map(({ key, icon }) => (
            <article key={key} className={glassCard}>
              <IconTile>{icon}</IconTile>
              <h3 className="text-title font-semibold text-text">{t(`items.${key}.title`)}</h3>
              <p className="text-text-muted">{t(`items.${key}.description`)}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

/** Khối 4: "Từ chọn bài đến xem kết quả", bốn bước và nút "Chọn bài thi". */
export function StepsSection() {
  const t = useTranslations("landing.steps");
  const keys = ["choose", "pay", "start", "result"] as const;

  return (
    <section aria-labelledby="steps-title">
      <div className={sectionInner}>
        <SectionTitle id="steps-title">{t("title")}</SectionTitle>
        <ol className="grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-5">
          {keys.map((key, index) => (
            <li key={key} className={glassCard}>
              <span
                aria-hidden
                className="flex size-9 items-center justify-center rounded-full bg-primary-fill font-bold text-on-primary"
              >
                {index + 1}
              </span>
              <h3 className="text-title font-semibold text-text">{t(`items.${key}.title`)}</h3>
              <p className="text-text-muted">{t(`items.${key}.description`)}</p>
            </li>
          ))}
        </ol>
        <div>
          <Button asChild size="lg">
            <Link href={`#${CATALOG_ANCHOR_ID}`}>{t("cta")}</Link>
          </Button>
        </div>
      </div>
    </section>
  );
}

/** Khối 5: câu hỏi thường gặp. Chữ là bản tạm chờ đơn vị vận hành xác nhận (TM C7). Không có nội dung về thời hạn (C8). */
export function FaqSection() {
  const t = useTranslations("landing.faq");

  return (
    <section id="faq" aria-labelledby="faq-title" className={surfaceGlassBand}>
      <div className={cn(landingContainer, "flex flex-wrap gap-8 py-14")}>
        <div className="flex min-w-0 flex-[1_1_17.5rem] flex-col gap-2.5">
          <SectionTitle id="faq-title">{t("title")}</SectionTitle>
          <p className="text-text-muted">{t("intro")}</p>
        </div>
        <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-2.5">
          {FAQ_KEYS.map((key) => (
            <details
              key={key}
              open={FAQ_OPEN_BY_DEFAULT.includes(key)}
              className="group rounded-xl border border-border bg-surface/88 backdrop-blur-xs"
            >
              <summary
                className={cn(
                  "flex min-h-13 cursor-pointer list-none items-center justify-between gap-3 rounded-xl px-4 py-3.5 font-semibold [&::-webkit-details-marker]:hidden",
                  focusRing,
                )}
              >
                {t(`items.${key}.question`)}
                <ChevronDown
                  aria-hidden
                  className="size-4.5 shrink-0 text-primary-text transition-transform duration-150 group-open:rotate-180 motion-reduce:transition-none"
                />
              </summary>
              <p className="px-4 pb-4 text-text-secondary">{t(`items.${key}.answer`)}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}

/** Khối 6: "Bạn cần hỗ trợ?". Kênh liên hệ từ `NEXT_PUBLIC_SUPPORT_*`; biến trống thì không bịa số (TM 11.2). */
export function SupportSection() {
  const t = useTranslations("landing.support");
  const contact = readSupportContact();
  const configured = hasSupportContact(contact);
  const contactHref = contact.email ? `mailto:${contact.email}` : contact.phone ? `tel:${contact.phone}` : null;
  const rows = [
    { key: "phone", value: contact.phone },
    { key: "email", value: contact.email },
    { key: "hours", value: contact.hours },
  ].filter((row) => row.value);

  return (
    <section id="support" aria-labelledby="support-title" className={cn(landingContainer, "pt-14 pb-16")}>
      <div className="flex flex-wrap items-start gap-8 rounded-2xl border border-border-primary bg-primary-50/75 p-6 backdrop-blur-sm sm:p-8">
        <div className="flex min-w-0 flex-[1_1_20rem] flex-col gap-3">
          <SectionTitle id="support-title">{t("title")}</SectionTitle>
          {configured ? (
            <>
              <p className="text-text-secondary">{t("description")}</p>
              {contactHref && (
                <div>
                  <Button asChild variant="outline" size="lg">
                    <a href={contactHref}>
                      <MessageCircle aria-hidden />
                      {t("contact")}
                    </a>
                  </Button>
                </div>
              )}
            </>
          ) : (
            <p className="text-text-secondary">{t("pending")}</p>
          )}
        </div>
        {configured && (
          <dl className="flex min-w-0 flex-[1_1_20rem] flex-col rounded-xl border border-border-primary bg-surface/92 px-5 py-2">
            {rows.map((row, index) => (
              <div
                key={row.key}
                className={cn("flex flex-wrap justify-between gap-2 py-3", index < rows.length - 1 && "border-b border-border")}
              >
                <dt className="text-text-muted">{t(`labels.${row.key}`)}</dt>
                <dd className="font-semibold text-text">{row.value}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </section>
  );
}
