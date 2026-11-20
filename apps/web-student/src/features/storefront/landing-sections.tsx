import { Button, cn, focusRing } from "@thi360/ui";
import {
  BookOpen,
  ChevronDown,
  ChartLine,
  ChevronsDown,
  ListChecks,
  MessageCircle,
  PenLine,
  Target,
  LayoutGrid,
} from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";
import { CATALOG_ANCHOR_ID } from "./catalog-query";
import { hasSupportContact, readSupportContact } from "./support";

/** Mã bảy kỳ thi (nhãn kỹ thuật, không dịch). */
const EXAM_KINDS = ["HSA", "QDA", "BCA", "V-ACT", "SPT", "SP2E", "THPTQG"] as const;
const FAQ_KEYS = ["formats", "attempts", "otherAccount", "paidNoExam", "refund", "voucher"] as const;
/** Hai câu hỏi đầu mở sẵn; bốn câu còn lại gập. */
const FAQ_OPEN_BY_DEFAULT: readonly (typeof FAQ_KEYS)[number][] = ["formats", "attempts"];

const sectionInner = "mx-auto flex w-full max-w-[1170px] flex-col gap-6 px-4 py-14 sm:px-6";
const itemCard = "flex flex-col gap-2.5 rounded-xl border border-border bg-page p-6";

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

/** Khối 1: giới thiệu, bảy kỳ thi, nút "Khám phá bài thi" (TM 11.2). */
export function HeroSection() {
  const t = useTranslations("landing.hero");
  const formats = [
    { key: "full", icon: <BookOpen /> },
    { key: "part", icon: <LayoutGrid /> },
    { key: "subject", icon: <PenLine /> },
  ] as const;

  return (
    <section aria-labelledby="hero-title" className="bg-primary-50">
      <div className="mx-auto flex w-full max-w-[1170px] flex-wrap items-center gap-10 px-4 py-14 sm:px-6 sm:py-18">
        <div className="flex min-w-0 flex-[999_1_32rem] flex-col gap-5">
          <h1 id="hero-title" className="max-w-[42rem] text-display font-bold text-primary-900">
            {t("title")}
          </h1>
          <p className="max-w-[38rem] text-title text-text-secondary">{t("description")}</p>
          <ul aria-label={t("kindsLabel")} className="flex flex-wrap gap-2">
            {EXAM_KINDS.map((kind) => (
              <li
                key={kind}
                className="rounded-full border border-primary-200 bg-surface px-3 py-1 text-field-label font-semibold text-primary-800"
              >
                {kind}
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
        <div className="flex min-w-0 flex-[1_1_21rem] flex-col gap-4 rounded-xl border border-border-primary bg-surface p-6 shadow-xs">
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
    <section aria-labelledby="why-title" className="border-y border-border bg-surface">
      <div className={sectionInner}>
        <SectionTitle id="why-title">{t("title")}</SectionTitle>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(min(16rem,100%),1fr))] gap-5">
          {items.map(({ key, icon }) => (
            <article key={key} className={itemCard}>
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
            <li key={key} className="flex flex-col gap-2.5 rounded-xl border border-border bg-surface p-6">
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
    <section id="faq" aria-labelledby="faq-title" className="border-y border-border bg-surface">
      <div className="mx-auto flex w-full max-w-[1170px] flex-wrap gap-8 px-4 py-14 sm:px-6">
        <div className="flex min-w-0 flex-[1_1_17.5rem] flex-col gap-2.5">
          <SectionTitle id="faq-title">{t("title")}</SectionTitle>
          <p className="text-text-muted">{t("intro")}</p>
        </div>
        <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-2.5">
          {FAQ_KEYS.map((key) => (
            <details
              key={key}
              open={FAQ_OPEN_BY_DEFAULT.includes(key)}
              className="group rounded-lg border border-border bg-surface"
            >
              <summary
                className={cn(
                  "flex min-h-13 cursor-pointer list-none items-center justify-between gap-3 rounded-lg px-4 py-3.5 font-semibold [&::-webkit-details-marker]:hidden",
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
    <section id="support" aria-labelledby="support-title" className="mx-auto w-full max-w-[1170px] px-4 pt-14 pb-16 sm:px-6">
      <div className="flex flex-wrap items-start gap-8 rounded-2xl border border-border-primary bg-primary-50 p-6 sm:p-8">
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
          <dl className="flex min-w-0 flex-[1_1_20rem] flex-col rounded-xl border border-border-primary bg-surface px-5 py-2">
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
