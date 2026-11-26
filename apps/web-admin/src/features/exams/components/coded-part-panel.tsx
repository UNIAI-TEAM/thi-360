"use client";

import { Button, cn, focusRing, StatusChip } from "@thi360/ui";
import { ChevronRight, Plus, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useId } from "react";
import { codePositions, codesUsing, type HowPart, sectionHow, sectionStateOf, type SectionStateName, worstState } from "../coded-part";
import { DISPLAY_NUMBER_FORMAT, roundForDisplay } from "../display-number";
import { computeAllocation, type MatrixAxes, sum } from "../matrix";
import type { ExamPart, ExamSection, ExamSectionState, StructureCombination } from "../types";
import type { StructureNames } from "./exam-structure-issues";
import { SECTION_STATE_TONES } from "./exam-structure-list";

export interface CodedPartPanelProps {
  part: ExamPart;
  names: StructureNames;
  axes: MatrixAxes;
  /** Mã của kỳ thi thuộc phần này (đọc từ Global List, chỉ xem). */
  combinations: readonly StructureCombination[];
  sectionStates?: readonly ExamSectionState[] | null;
  /** Mở môn để soạn ma trận ("Thiết lập"). */
  onSelectSection: (sectionId: string) => void;
  onAddSubject: (subjectCode: string) => void;
}

export function CodedPartPanel({ part, names, axes, combinations, sectionStates, onSelectSection, onAddSubject }: CodedPartPanelProps) {
  const t = useTranslations("exams.codedPart");
  const format = useFormatter();
  const listT = useTranslations("exams.structure.list");
  const tContent = useTranslations("content");
  const subjectsId = useId();
  const codesId = useId();
  const pool = part.subjectPool ?? [];
  const codes = combinations.filter((item) => item.partId === part.id || item.partId === null);
  const stateOf = (section: ExamSection) => sectionStateOf(section, sectionStates);
  const chip = (state: SectionStateName | null) =>
    state ? <StatusChip tone={SECTION_STATE_TONES[state]}>{listT(`state.${state}`)}</StatusChip> : <span aria-hidden>—</span>;

  const howText = (part: HowPart) =>
    part.kind === "set"
      ? t("howSet", { sets: roundForDisplay(part.sets), size: roundForDisplay(part.size) })
      : t("howSingle", { count: roundForDisplay(part.count), type: part.type ? tContent(`questionType.${part.type}`) : t("howAny") });
  const matrixText = (section: ExamSection) => {
    if (!section.matrix) return null;
    const allocation = computeAllocation({ questionCount: section.questionCount ?? 0, matrix: section.matrix }, axes);
    if (sum(allocation.byGrade) === 0 && sum(allocation.byDifficulty) === 0) return null;
    return t("matrix", { grades: allocation.byGrade.join("/"), difficulties: allocation.byDifficulty.join("/") });
  };
  // Môn máy chủ báo thiếu nhưng đã thêm vào danh sách môn của bản nháp thì không còn thiếu (R1-22), không đợi lưu rồi tải lại.
  const missingOf = (item: StructureCombination) => item.missingSubjects.filter((subjectCode) => !pool.some((section) => section.subjectCode === subjectCode));
  const missing = Array.from(new Set(codes.flatMap(missingOf)));

  return (
    <div className="flex flex-col gap-5">
      <section aria-labelledby={subjectsId} className="flex flex-col gap-2">
        <div className="flex flex-col gap-0.5">
          <h3 id={subjectsId} className="text-title font-semibold text-text">
            {t("subjectsTitle")}
          </h3>
          <p className="text-caption text-text-muted">{t("subjectsHint")}</p>
        </div>
        {pool.length === 0 ? (
          <p className="text-body text-text-muted">{t("empty")}</p>
        ) : (
          <div role="region" tabIndex={0} aria-label={t("subjectsCaption", { part: part.name })} className={cn("relative overflow-x-auto rounded-md border border-border", focusRing)}>
            <table className="w-full min-w-168 border-separate border-spacing-0 text-body">
              <caption className="sr-only">{t("subjectsCaption", { part: part.name })}</caption>
              <thead>
                <tr className="text-left text-caption text-text-muted">
                  <th scope="col" className="min-w-28 border-b border-border bg-subtle px-3 py-2 font-semibold">{t("subject")}</th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold whitespace-nowrap">{t("count")}</th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("how")}</th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("usedIn")}</th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("status")}</th>
                  <th scope="col" className="sticky right-0 z-1 border-b border-l border-border bg-subtle px-3 py-2 text-right font-semibold"><span className="sr-only">{t("actions")}</span></th>
                </tr>
              </thead>
              <tbody>
                {pool.map((section) => {
                  const subject = names.subject(section.subjectCode);
                  const how = sectionHow(section);
                  const matrix = matrixText(section);
                  return (
                    <tr key={section.id}>
                      <th scope="row" className="min-w-28 border-b border-border px-3 py-2 text-left align-top font-medium text-text">{subject}</th>
                      <td className="border-b border-border px-2 py-2 text-right align-top tabular-nums text-text">{section.questionCount === null ? "—" : format.number(section.questionCount, DISPLAY_NUMBER_FORMAT)}</td>
                      <td className="border-b border-border px-2 py-2 align-top text-text">
                        {how.length === 0 ? <span className="text-text-muted">{t("noBlocks")}</span> : how.map(howText).join(", ")}
                        {matrix && <span className="block text-caption text-text-muted">{matrix}</span>}
                      </td>
                      <td className="border-b border-border px-2 py-2 align-top text-text-secondary">{codesUsing(section.subjectCode, combinations).join(", ") || "—"}</td>
                      <td className="border-b border-border px-2 py-2 align-top">{chip(stateOf(section))}</td>
                      <td className="sticky right-0 z-1 border-b border-l border-border bg-surface px-3 py-1 text-right align-top">
                        <Button type="button" variant="secondary" size="sm" aria-label={t("setupAria", { subject })} onClick={() => onSelectSection(section.id)}>
                          {t("setup")}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {missing.length > 0 && (
        <ul className="flex flex-col gap-2 rounded-md border border-danger/40 bg-danger-bg px-3 py-2">
          {codes
            .filter((item) => missingOf(item).length > 0)
            .flatMap((item) =>
              missingOf(item).map((subjectCode) => (
                <li key={`${item.code}-${subjectCode}`} className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body text-danger">
                  <TriangleAlert aria-hidden className="size-4 shrink-0" />
                  <span>{t("missingSubject", { code: item.code, subject: names.subject(subjectCode) })}</span>
                  <Button type="button" variant="secondary" size="sm" onClick={() => onAddSubject(subjectCode)}>
                    <Plus />
                    <span>{t("addSubject", { subject: names.subject(subjectCode) })}</span>
                  </Button>
                </li>
              )),
            )}
        </ul>
      )}

      <section aria-labelledby={codesId} className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 id={codesId} className="text-title font-semibold text-text">
            {t("codesTitle")}
          </h3>
          <Button asChild variant="link" size="sm">
            <Link href="/catalog">{t("editCodes")}</Link>
          </Button>
        </div>
        {codes.length === 0 ? (
          <p className="text-body text-text-muted">{t("codesEmpty")}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {codes.map((item) => {
              const poolSections = item.subjectCodes.map((subjectCode) => pool.find((section) => section.subjectCode === subjectCode));
              const state = missingOf(item).length > 0 ? "INCOMPLETE" : worstState(poolSections.map((section) => (section ? stateOf(section) : "INCOMPLETE")));
              const positions = codePositions(item, pool);
              return (
                <li key={item.code} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-border px-3 py-2">
                  <strong className="font-semibold text-text">{item.code}</strong>
                  <span className="inline-flex flex-wrap items-center gap-1 text-body text-text-secondary">
                    {item.subjectCodes.map((subjectCode, index) => (
                      <span key={subjectCode} className="inline-flex items-center gap-1">
                        {index > 0 && <ChevronRight aria-hidden className="size-3 text-text-muted" />}
                        {names.subject(subjectCode)}
                      </span>
                    ))}
                  </span>
                  {positions.length > 0 && (
                    <span className="text-caption text-text-muted">
                      {positions.map((position) => t("position", { subject: names.subject(position.subjectCode), from: roundForDisplay(position.from), to: roundForDisplay(position.to) })).join(", ")}
                    </span>
                  )}
                  <span className="ml-auto">{chip(state)}</span>
                </li>
              );
            })}
          </ul>
        )}
        <p className="text-caption text-text-muted">{t("codesHint")}</p>
      </section>
    </div>
  );
}
