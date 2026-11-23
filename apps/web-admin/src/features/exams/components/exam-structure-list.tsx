"use client";

import { Card, cn, focusRingInset, StatusChip } from "@thi360/ui";
import { ChevronRight, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, type ReactNode } from "react";
import { sectionGroups, sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamSection, ExamSectionState, StructureCombination } from "../types";
import { findBlock, type StructureSelection } from "./exam-structure-draft";
import { optionLabel, type StructureNames } from "./exam-structure-issues";
import { ExamName } from "./exam-review-shared";

interface ExamStructureListProps {
  parts: readonly ExamPart[];
  /** Mã bài thi của các phần theo mã trong bản làm việc (R-24). */
  combinations: readonly StructureCombination[];
  /** Trạng thái kiểm tra theo môn (H2); `null` khi BE-5 chưa triển khai — không hiện trạng thái. */
  sectionStates: readonly ExamSectionState[] | null;
  selection: StructureSelection | null;
  issues: readonly StructureIssue[];
  names: StructureNames;
  onSelect: (selection: StructureSelection) => void;
  /** Hiện mục "Tổng quan bài" (điểm theo môn, chỉ tiêu cấp bài) ở đầu danh sách. */
  showOverview?: boolean;
}

/** Màu huy hiệu trạng thái môn (H2, spec 7.3); chữ luôn đi kèm (DESIGN.md mục 8). */
export const SECTION_STATE_TONES = {
  CHECKED: "success",
  NEEDS_CHECK: "warning",
  INCOMPLETE: "danger",
} as const;

/**
 * Vùng danh sách (b) của bước Cấu trúc (bản vẽ `BaiThiBuoc2`, R-03, R-24): Phần → (phương án) → môn, kèm các mã bài
 * thi chỉ đọc của phần theo mã. Bấm phần mở thông tin phần, bấm môn mở chỉ tiêu cấp môn, bấm phương án chọn môn mở
 * tổng quan phương án. Mục đang mở có `aria-current`; điều hướng bằng phím mũi tên (R-04: danh sách dài có thể cuộn).
 */
export function ExamStructureList({
  parts,
  combinations,
  sectionStates,
  selection,
  issues,
  names,
  onSelect,
  showOverview = false,
}: ExamStructureListProps) {
  const t = useTranslations("exams.structure.list");
  const titleId = useId();
  const listRef = useRef<HTMLOListElement | null>(null);

  // Khối đang mở thì môn chứa khối là mục đang mở của danh sách (nhóm lấy câu soạn trong vùng (c), không có dòng riêng).
  const blockSectionId = selection?.kind === "block" ? (findBlock(parts, selection.blockId)?.section.id ?? null) : null;

  const partHasIssue = (partId: string) =>
    issues.some((issue) => issue.code !== "BLOCK" && ("partId" in issue ? issue.partId : issue.location.partId) === partId);
  const sectionHasIssue = (sectionId: string) =>
    issues.some((issue) => (issue.code === "BLOCKS_TOTAL" || issue.code === "BLOCK") && issue.location.sectionId === sectionId);

  // Mục đang mở luôn nhìn thấy được trong danh sách cuộn (R-04: bấm lỗi từ vùng (d) nhảy tới đúng mục).
  useEffect(() => {
    listRef.current
      ?.querySelector("[aria-current='true']")
      ?.scrollIntoView({ block: "nearest" });
  }, [selection]);

  // Điều hướng bằng phím trong vùng danh sách: mũi tên lên/xuống qua các mục, Home/End tới đầu/cuối.
  const onKeyDown = (event: React.KeyboardEvent<HTMLOListElement>) => {
    if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;
    const items = Array.from(listRef.current?.querySelectorAll<HTMLElement>("[data-list-item]") ?? []);
    if (items.length === 0) return;
    event.preventDefault();
    const active = document.activeElement as HTMLElement | null;
    const index = items.findIndex((item) => item === active || item.contains(active));
    const next =
      event.key === "Home"
        ? items[0]
        : event.key === "End"
          ? items[items.length - 1]
          : items[Math.min(items.length - 1, Math.max(0, index + (event.key === "ArrowDown" ? 1 : -1)))];
    next?.focus();
  };

  const renderSection = (part: ExamPart, optionId: string | null, section: ExamSection, showSubject: boolean) => {
    const subject = names.subject(section.subjectCode);
    const state = sectionStates?.find((item) => item.sectionId === section.id)?.state ?? null;
    return (
      <li key={section.id} className="flex flex-col gap-0.5">
        <ListItemButton
          current={(selection?.kind === "section" && selection.sectionId === section.id) || blockSectionId === section.id}
          onClick={() => onSelect({ kind: "section", sectionId: section.id })}
          meta={
            section.questionCount === null
              ? t("sectionCountVaries")
              : t("blockCount", { count: section.questionCount })
          }
          warning={sectionHasIssue(section.id) ? t("hasIssue") : null}
        >
          <span className="text-caption font-semibold">
            {showSubject || section.choiceGroup ? subject : t("sectionOf", { subject })}
          </span>
          {state && (
            <StatusChip tone={SECTION_STATE_TONES[state]} className="ml-1.5">
              {t(`state.${state}`)}
            </StatusChip>
          )}
        </ListItemButton>
      </li>
    );
  };

  const renderCodedPart = (part: ExamPart) => {
    const pool = part.subjectPool ?? [];
    const codes = combinations.filter((item) => item.partId === part.id || item.partId === null);
    return (
      <div className="flex flex-col gap-2 pl-2">
        <ol className="flex flex-col gap-1">
          {pool.map((section) => renderSection(part, null, section, pool.length > 1))}
        </ol>
        {codes.length > 0 && (
          <div className="flex flex-col gap-1 border-l border-border pl-2">
            <span className="pl-1 text-label font-medium text-text-muted">{t("codesReadonly")}</span>
            <ul className="flex flex-col gap-1">
              {codes.map((item) => (
                <li
                  key={item.code}
                  className="flex flex-wrap items-center gap-x-1.5 rounded-md px-2 py-1 text-caption text-text"
                >
                  <span className="font-semibold">{item.examCode ?? item.code}</span>
                  <ChevronRight aria-hidden className="size-3 text-text-muted" />
                  <span>{item.subjectCodes.map(names.subject).join(" – ")}</span>
                  {item.missingSubjects.length > 0 && (
                    <span className="inline-flex items-center gap-1 font-normal text-danger">
                      <TriangleAlert aria-hidden className="size-3.5" />
                      {t("missingSubjects", { subjects: item.missingSubjects.map(names.subject).join(", ") })}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    );
  };

  const renderOption = (part: ExamPart, option: ExamPart["options"][number]) => {
    const choice = option.kind === "SUBJECT_CHOICE";
    const header = (
      <span className="flex flex-wrap items-center gap-x-1.5 pl-1 text-label font-medium text-text">
        {option.kind === "BRANCH" || choice ? <ExamName>{optionLabel(option, names)}</ExamName> : optionLabel(option, names)}
        {choice && (
          <span className="text-caption font-normal text-text-muted">
            {t("pickSubjects", { count: option.chooseSubjects ?? 0 })}
          </span>
        )}
        {!choice && option.expertConfirmed === false && (
          <span className="inline-flex items-center gap-1 text-caption font-normal">
            <TriangleAlert aria-hidden className="size-3.5 text-warning" />
            {t("expertPending")}
          </span>
        )}
      </span>
    );
    return (
      <li key={option.id} className="flex flex-col gap-0.5 border-l border-border pl-2">
        {choice ? (
          <ListItemButton
            current={selection?.kind === "option" && selection.optionId === option.id}
            onClick={() => onSelect({ kind: "option", partId: part.id, optionId: option.id })}
            subtle
          >
            {header}
          </ListItemButton>
        ) : (
          header
        )}
        <ol className="flex flex-col gap-1">
          {option.sections.map((section) => renderSection(part, option.id, section, option.sections.length > 1))}
        </ol>
      </li>
    );
  };

  return (
    <Card padding="none" className="flex flex-col @5xl:sticky @5xl:top-4 @5xl:max-h-[calc(100dvh-6rem)]">
      <nav aria-labelledby={titleId} className="flex min-h-0 flex-col">
        <div className="flex items-baseline justify-between gap-2 border-b border-border px-4 py-3">
          <h2 id={titleId} className="text-title font-semibold text-text">
            {t("title")}
          </h2>
          <span className="text-caption text-text-muted">
            {t("summary", { parts: parts.length, count: sum(parts.map((part) => part.questionCount)) })}
          </span>
        </div>
        <ol
          ref={listRef}
          onKeyDown={onKeyDown}
          className="scroll-shadows-y flex min-h-0 flex-col gap-3 overflow-y-auto p-2"
        >
          {showOverview && (
            <li className="flex flex-col gap-1">
              <ListItemButton current={selection?.kind === "exam"} onClick={() => onSelect({ kind: "exam" })} strong>
                {t("overview")}
              </ListItemButton>
            </li>
          )}
          {parts.map((part) => (
            <li key={part.id} className="flex flex-col gap-1">
              <ListItemButton
                current={selection?.kind === "part" && selection.partId === part.id}
                onClick={() => onSelect({ kind: "part", partId: part.id })}
                meta={t("partMeta", {
                  count: part.questionCount,
                  minutes: part.durationMinutes ?? 0,
                  hasMinutes: part.durationMinutes === null ? "no" : "yes",
                })}
                warning={partHasIssue(part.id) ? t("hasIssue") : null}
                strong
              >
                <ExamName>{part.name}</ExamName>
              </ListItemButton>
              {part.codeSource ? (
                renderCodedPart(part)
              ) : part.mode === "CHOICE" ? (
                <ol className="flex flex-col gap-2 pl-2">
                  {part.sections.length > 0 && (
                    <li className="flex flex-col gap-0.5 border-l border-border pl-2">
                      <span className="pl-1 text-label font-medium text-text">{t("common")}</span>
                      <ol className="flex flex-col gap-1">
                        {part.sections.map((section) => renderSection(part, null, section, true))}
                      </ol>
                    </li>
                  )}
                  {part.options.length === 0 && <li className="pl-3 text-caption text-text-muted">{t("noOptions")}</li>}
                  {part.options.map((option) => renderOption(part, option))}
                </ol>
              ) : (
                <ol className="flex flex-col gap-1 pl-2">
                  {sectionGroups(part)[0]!.sections.map((section) =>
                    renderSection(part, null, section, part.sections.length > 1),
                  )}
                </ol>
              )}
            </li>
          ))}
        </ol>
      </nav>
    </Card>
  );
}

function ListItemButton({
  current,
  onClick,
  meta,
  warning,
  strong = false,
  subtle = false,
  children,
}: {
  current: boolean;
  onClick: () => void;
  meta?: string;
  warning?: string | null;
  strong?: boolean;
  /** Mục là tiêu đề một nhóm con (phương án chọn môn): nền nhạt hơn mục thường. */
  subtle?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      data-list-item
      aria-current={current || undefined}
      onClick={onClick}
      className={cn(
        "flex w-full flex-wrap items-center gap-x-1.5 gap-y-0.5 rounded-md px-2 py-1.5 text-left text-body text-text",
        "hover:bg-subtle",
        focusRingInset,
        current ? "bg-primary-100 text-primary shadow-[inset_2px_0_0_var(--accent)]" : subtle && "bg-subtle/60",
      )}
    >
      <span className={cn("min-w-0 flex-1 break-words", strong ? "font-semibold" : "font-medium")}>{children}</span>
      {meta && <span className="shrink-0 text-caption text-text-muted">{meta}</span>}
      {warning && (
        <span className="inline-flex shrink-0 items-center gap-1 text-caption">
          <TriangleAlert aria-hidden className="size-3.5 text-warning" />
          <span className="sr-only">{warning}</span>
        </span>
      )}
    </button>
  );
}
