import { cn, focusRing } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { Check, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";
import { BuyPanel } from "./buy-panel";
import { REVIEW_BUILD_MODE, rangeText } from "./format";
import { FormatChip } from "./product-card";
import { hasSupportContact, readSupportContact } from "./support";
import type { AnswerTypeGroup, DetailChoiceOption, ProductDetail } from "./types";

const ESSAY_TYPE = "ESSAY";
const cardClass = "flex flex-col gap-4 rounded-xl border border-border bg-surface p-6";

function SectionHeading({ id, children }: { id: string; children: ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <span aria-hidden className="h-6 w-1 rounded-sm bg-primary-fill" />
      <h2 id={id} className="text-h3 font-bold text-text">
        {children}
      </h2>
    </div>
  );
}

function YesNo({ value }: { value: boolean }) {
  const t = useTranslations("storefront.detail.rights");
  const Icon = value ? Check : X;
  return (
    <span className="flex items-center gap-1.5">
      <Icon aria-hidden className={value ? "size-4 text-primary-text" : "size-4 text-text-muted"} />
      {value ? t("yes") : t("no")}
    </span>
  );
}

/** Mọi dạng câu của một tập nhóm, cộng số câu theo dạng (TM 10.1); `QUESTION_SET` tính `childAnswerType`. */
function mergeAnswerTypes(groups: AnswerTypeGroup[]): Array<{ type: string; count: number }> {
  const totals = new Map<string, number>();
  for (const group of groups) {
    const type = group.childAnswerType ?? group.questionType;
    totals.set(type, (totals.get(type) ?? 0) + group.questionCount);
  }
  return [...totals].map(([type, count]) => ({ type, count }));
}

/** Mọi nhóm dạng câu của sản phẩm: phần cố định, dạng riêng của lựa chọn và dạng từng môn trong lựa chọn. */
function allAnswerGroups(product: ProductDetail): AnswerTypeGroup[] {
  return [
    ...product.parts.flatMap((part) => part.answerTypes),
    ...product.choices.flatMap((group) =>
      group.options.flatMap((option) => [
        ...option.answerTypes,
        ...option.subjectAnswerTypes.flatMap((subject) => subject.answerTypes),
      ]),
    ),
  ];
}

function ChoiceOption({ option }: { option: DetailChoiceOption }) {
  const t = useTranslations("storefront.detail");
  const locale = useLocale();
  const optionTypes = mergeAnswerTypes([
    ...option.answerTypes,
    ...option.subjectAnswerTypes.flatMap((subject) => subject.answerTypes),
  ]);
  return (
    <div className="flex flex-col gap-2 rounded-lg border border-border p-4">
      <div className="font-semibold text-text">
        <span data-exam-content>{option.name}</span>
        {option.chooseSubjects ? (
          <>
            {" · "}
            {t("chooseSubjects", { count: option.chooseSubjects, total: option.subjects.length })}
          </>
        ) : null}
      </div>
      {option.subjects.length > 0 && (
        <ul className="flex flex-wrap gap-1.5">
          {option.subjects.map((subject) => (
            <li key={subject.code} className="rounded-full bg-subtle px-2.5 py-0.5 text-caption text-text-secondary">
              {pickName(subject, locale)}
            </li>
          ))}
        </ul>
      )}
      {optionTypes.length > 0 && (
        <p className="text-field-label text-text-muted">
          {optionTypes
            .map(({ type, count }) =>
              t.has(`answerType.${type}`)
                ? t("answerTypeCount", { type: t(`answerType.${type}`), count })
                : t("answerTypeCount", { type, count }),
            )
            .join(", ")}
        </p>
      )}
      {option.countsByPick && option.countsByPick.length > 0 && (
        <p className="text-field-label text-text-muted">
          {t("countsByPick", { counts: option.countsByPick.join(" / ") })}
        </p>
      )}
    </div>
  );
}

function SupportLine() {
  const t = useTranslations("storefront.detail.policy");
  const contact = readSupportContact();
  if (!hasSupportContact(contact)) return <p className="text-text-secondary">{t("contactPending")}</p>;
  const channels = [contact.phone, contact.email].filter(Boolean).join(" · ");
  return <p className="text-text-secondary">{t("contactLine", { contact: channels || contact.hours })}</p>;
}

/** Trang chi tiết một bài thi đang bán (TM 11.3, UniNote 5.1.6). Khung Giá bán là phần chạy phía trình duyệt. */
export function ExamDetailView({ product }: { product: ProductDetail }) {
  const t = useTranslations("storefront.detail");
  const common = useTranslations("common");
  const storefront = useTranslations("storefront");
  const questions = rangeText(product.questionCount);
  const minutes = rangeText(product.durationMinutes);
  const fixedTypes = mergeAnswerTypes(product.parts.flatMap((part) => part.answerTypes));
  const hasEssay = allAnswerGroups(product).some(
    (group) => (group.childAnswerType ?? group.questionType) === ESSAY_TYPE,
  );
  const maxScore = product.scoring.maxScore ?? rangeText(product.maxPoints);
  const resultTimingKey = hasEssay ? "afterEssay" : "afterGrading";

  return (
    <div className="mx-auto flex w-full max-w-[1170px] flex-col gap-6 px-4 py-6 sm:px-6">
      <nav aria-label={t("breadcrumbLabel")}>
        <ol className="flex flex-wrap items-center gap-2 text-field-label text-text-muted">
          <li>
            <Link href="/" className="hover:underline">
              {t("breadcrumbHome")}
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li>
            <Link href="/#exams" className="hover:underline">
              {t("breadcrumbCatalog")}
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li aria-current="page" className="text-text" data-exam-content>
            {product.name}
          </li>
        </ol>
      </nav>

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-5">
          <section aria-labelledby="p-title" className="overflow-hidden rounded-xl border border-border bg-surface">
            {product.coverUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={product.coverUrl} alt="" className="aspect-[16/6] w-full object-cover" />
            ) : (
              <div className="flex aspect-[16/6] items-center justify-center gap-3 bg-subtle">
                <span className="flex size-14 items-center justify-center rounded-xl border border-border bg-surface text-h3 font-bold text-primary-text">
                  {common("logoMark")}
                </span>
                <span className="text-h2 font-bold text-text-secondary">{product.examKind.code}</span>
              </div>
            )}
            <div className="flex flex-col gap-3.5 p-6">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-full border border-primary-600 bg-primary-50 px-2.5 py-0.5 text-caption font-semibold text-primary-700">
                  {product.examKind.code}
                </span>
                <FormatChip format={product.format} scopeLabel={product.scopeLabel} />
                {product.buildMode === REVIEW_BUILD_MODE && (
                  <span className="rounded-full bg-info-bg px-2.5 py-0.5 text-caption font-semibold text-info">
                    {storefront("review")}
                  </span>
                )}
                <span className="text-caption text-text-muted">
                  {t("codeLabel")} <strong className="font-semibold text-text">{product.code}</strong>
                </span>
              </div>
              <h1 id="p-title" data-exam-content className="text-h1 font-bold text-text">
                {product.name}
              </h1>
              {product.summary && (
                <p data-exam-content className="text-title text-text-secondary">
                  {product.summary}
                </p>
              )}
              {(product.objectives || product.audience || product.description) && (
                <div className="grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-4 pt-1">
                  {product.description && (
                    <div className="col-span-full flex flex-col gap-1">
                      <p data-exam-content className="text-text-secondary">
                        {product.description}
                      </p>
                    </div>
                  )}
                  {product.objectives && (
                    <div className="flex flex-col gap-1">
                      <h2 className="text-label font-semibold text-text">{t("objectives")}</h2>
                      <p data-exam-content className="text-text-muted">
                        {product.objectives}
                      </p>
                    </div>
                  )}
                  {product.audience && (
                    <div className="flex flex-col gap-1">
                      <h2 className="text-label font-semibold text-text">{t("audience")}</h2>
                      <p data-exam-content className="text-text-muted">
                        {product.audience}
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          </section>

          {product.parts.length > 0 && (
            <section aria-labelledby="s-structure" className={cardClass}>
              <div className="flex flex-wrap items-center gap-3">
                <SectionHeading id="s-structure">{t("structureTitle")}</SectionHeading>
                {questions !== null && minutes !== null && (
                  <span className="rounded-full border border-border px-2.5 py-0.5 text-caption font-medium text-text-muted">
                    {t("structureTotal", { questions, minutes })}
                  </span>
                )}
              </div>
              {/* Bảng 6 cột cuộn ngang ở màn hẹp: vùng cuộn có tên và nhận focus để cuộn bằng bàn phím (DESIGN.md 5.13). */}
              <div
                role="region"
                aria-label={t("structureCaption")}
                tabIndex={0}
                className={cn("overflow-x-auto rounded-lg border border-border", focusRing)}
              >
                <table className="w-full border-collapse text-left tabular-nums">
                  <caption className="sr-only">{t("structureCaption")}</caption>
                  <thead>
                    <tr className="bg-table-head text-caption font-semibold text-text-muted">
                      <th scope="col" className="px-3.5 py-2.5">
                        {t("columns.order")}
                      </th>
                      <th scope="col" className="px-3.5 py-2.5">
                        {t("columns.part")}
                      </th>
                      <th scope="col" className="px-3.5 py-2.5 text-right">
                        {t("columns.questions")}
                      </th>
                      <th scope="col" className="px-3.5 py-2.5 text-right">
                        {t("columns.duration")}
                      </th>
                      <th scope="col" className="px-3.5 py-2.5 text-right">
                        {t("columns.maxPoints")}
                      </th>
                      <th scope="col" className="px-3.5 py-2.5">
                        {t("columns.nature")}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {product.parts.map((part) => (
                      <tr key={part.order} className="border-t border-border">
                        <td className="px-3.5 py-3">{part.order}</td>
                        <th scope="row" className="px-3.5 py-3 font-semibold" data-exam-content>
                          {part.name}
                        </th>
                        <td className="px-3.5 py-3 text-right">{rangeText(part.questionCount) ?? "—"}</td>
                        <td className="px-3.5 py-3 text-right">
                          {rangeText(part.durationMinutes) === null
                            ? "—"
                            : storefront("units.minutes", {
                                value: rangeText(part.durationMinutes) ?? "",
                                count: part.durationMinutes?.max ?? 0,
                              })}
                        </td>
                        <td className="px-3.5 py-3 text-right">{rangeText(part.maxPoints) ?? "—"}</td>
                        <td className="px-3.5 py-3">
                          <span
                            className={
                              part.required
                                ? "rounded-full bg-primary-50 px-2 py-0.5 text-caption font-medium text-primary-text"
                                : "rounded-full bg-warning-bg px-2 py-0.5 text-caption font-medium text-warning"
                            }
                          >
                            {part.required ? t("required") : t("optional")}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {product.choices.length > 0 && (
            <section aria-labelledby="s-options" className={cardClass}>
              <SectionHeading id="s-options">{t("choicesTitle")}</SectionHeading>
              {product.choices.map((group) => (
                <div key={group.partName} className="flex flex-col gap-3">
                  <h3 className="font-semibold text-text-secondary" data-exam-content>
                    {group.partName}
                  </h3>
                  <div className="grid grid-cols-[repeat(auto-fit,minmax(min(16rem,100%),1fr))] gap-3">
                    {group.options.map((option) => (
                      <ChoiceOption key={option.code} option={option} />
                    ))}
                  </div>
                </div>
              ))}
            </section>
          )}

          <section aria-labelledby="s-how" className={cardClass}>
            <SectionHeading id="s-how">{t("howTitle")}</SectionHeading>
            <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-4">
              {fixedTypes.length > 0 && (
                <div className="flex flex-col gap-1">
                  <dt className="text-field-label text-text-muted">{t("how.answerTypes")}</dt>
                  <dd>
                    {fixedTypes
                      .map(({ type, count }) =>
                        t.has(`answerType.${type}`)
                          ? t("answerTypeCount", { type: t(`answerType.${type}`), count })
                          : t("answerTypeCount", { type, count }),
                      )
                      .join(", ")}
                  </dd>
                </div>
              )}
              {maxScore !== null && maxScore !== undefined && (
                <div className="flex flex-col gap-1">
                  <dt className="text-field-label text-text-muted">{t("how.scoring")}</dt>
                  <dd>{t("how.maxScore", { score: String(maxScore) })}</dd>
                </div>
              )}
              {product.scoring.timing && (product.scoring.timing === "PER_PART" || product.scoring.timing === "SHARED") && (
                <div className="flex flex-col gap-1">
                  <dt className="text-field-label text-text-muted">{t("how.timing")}</dt>
                  <dd>{t(`how.timing${product.scoring.timing}`)}</dd>
                </div>
              )}
              <div className="flex flex-col gap-1">
                <dt className="text-field-label text-text-muted">{t("how.switching")}</dt>
                <dd>{t("how.switchingValue")}</dd>
              </div>
            </dl>
            {hasEssay && <p className="rounded-lg bg-page px-3.5 py-3 text-field-label text-text-muted">{t("how.essayNote")}</p>}
            {product.omittedEssayGroups.length > 0 && (
              <ul className="flex flex-col gap-1 text-field-label text-text-muted">
                {product.omittedEssayGroups.map((group) => (
                  <li key={group.name}>
                    {t("how.noEssay")} <span data-exam-content>{group.name}</span>
                    {group.points !== undefined && group.points !== null && <> · {t("how.points", { value: group.points })}</>}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-labelledby="s-rights" className={cardClass}>
            <SectionHeading id="s-rights">{t("rights.title")}</SectionHeading>
            <dl className="flex flex-col">
              <div className="flex flex-wrap gap-x-6 gap-y-2 border-b border-border py-3">
                <dt className="flex-[0_0_13.75rem] text-text-muted">{t("rights.attempts")}</dt>
                <dd className="flex-[1_1_17.5rem] font-semibold">{t("attemptsPerPurchase", { count: product.attemptsGranted })}</dd>
              </div>
              {product.resultView && (
                <div className="flex flex-wrap gap-x-6 gap-y-2 border-b border-border py-3">
                  <dt className="flex-[0_0_13.75rem] text-text-muted">{t("rights.resultView")}</dt>
                  <dd className="flex-[1_1_17.5rem]">
                    {t.has(`rights.resultViews.${product.resultView}`)
                      ? t(`rights.resultViews.${product.resultView}`)
                      : product.resultView}
                  </dd>
                </div>
              )}
              <div className="flex flex-wrap gap-x-6 gap-y-2 border-b border-border py-3">
                <dt className="flex-[0_0_13.75rem] text-text-muted">{t("rights.resultTiming")}</dt>
                <dd className="flex-[1_1_17.5rem]">{t(`rights.${resultTimingKey}`)}</dd>
              </div>
              <div className="flex flex-wrap gap-x-6 gap-y-2 border-b border-border py-3">
                <dt className="flex-[0_0_13.75rem] text-text-muted">{t("rights.ai")}</dt>
                <dd className="flex-[1_1_17.5rem]">
                  <YesNo value={product.benefits.aiExplanation} />
                </dd>
              </div>
              <div className="flex flex-wrap gap-x-6 gap-y-2 border-b border-border py-3">
                <dt className="flex-[0_0_13.75rem] text-text-muted">{t("rights.strengths")}</dt>
                <dd className="flex-[1_1_17.5rem]">
                  <YesNo value={product.benefits.strengthWeakness} />
                </dd>
              </div>
              <div className="flex flex-wrap gap-x-6 gap-y-2 py-3">
                <dt className="flex-[0_0_13.75rem] text-text-muted">{t("rights.practice")}</dt>
                <dd className="flex-[1_1_17.5rem]">
                  <YesNo value={product.benefits.practice} />
                </dd>
              </div>
            </dl>
            {product.benefits.note && (
              <p className="rounded-lg bg-page px-3.5 py-3 text-field-label text-text-muted">
                {t("rights.noteLabel")} <span data-exam-content>{product.benefits.note}</span>
              </p>
            )}
          </section>

          <section aria-labelledby="s-policy" className={cardClass}>
            <SectionHeading id="s-policy">{t("policy.title")}</SectionHeading>
            <p className="text-text-secondary">{t("policy.priceNote")}</p>
            <p className="text-text-secondary">{t("policy.refund", { policy: t("policy.refundPending") })}</p>
            <SupportLine />
          </section>
        </div>

        <BuyPanel product={product} />
      </div>
    </div>
  );
}
