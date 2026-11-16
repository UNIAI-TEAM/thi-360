"use client";

import { Card, cn, focusRingInset, IconButton } from "@thi360/ui";
import { Plus, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, type ReactNode } from "react";
import { sectionGroups, sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamSection } from "../types";
import type { StructureSelection } from "./exam-structure-draft";
import { optionLabel, type StructureNames } from "./exam-structure-issues";

export interface ExamStructureOutlineProps {
  parts: readonly ExamPart[];
  selection: StructureSelection | null;
  issues: readonly StructureIssue[];
  names: StructureNames;
  onSelect: (selection: StructureSelection) => void;
  onAddBlock: (partId: string, optionId: string | null, section: ExamSection) => void;
}

/**
 * Cột Cấu trúc (bản vẽ `BaiThiCauTruc`): phần → (lựa chọn) → môn → khối. Bấm phần mở thông tin phần (phần tự chọn: nhánh
 * và tổ hợp), bấm khối mở khối. Mục đang mở có `aria-current`, nền nhạt và thanh dọc bên trái; mục có lỗi có biểu tượng
 * kèm chữ cho trình đọc màn hình.
 */
export function ExamStructureOutline({ parts, selection, issues, names, onSelect, onAddBlock }: ExamStructureOutlineProps) {
  const t = useTranslations("exams.structure.outline");
  const titleId = useId();
  const blockHasIssue = (blockId: string) => issues.some((issue) => issue.code === "BLOCK" && issue.blockId === blockId);
  const partHasIssue = (partId: string) =>
    issues.some((issue) => issue.code !== "BLOCK" && ("partId" in issue ? issue.partId : issue.location.partId) === partId);

  const renderSection = (part: ExamPart, optionId: string | null, section: ExamSection, showSubject: boolean) => {
    const subject = names.subject(section.subjectCode);
    return (
      <li key={section.id} className="flex flex-col gap-0.5">
        <div className="flex items-center justify-between gap-2 pl-3">
          {showSubject ? (
            <span className="min-w-0 text-caption font-semibold text-text-secondary">
              {t("section", { subject, count: section.questionCount })}
            </span>
          ) : (
            <span className="sr-only">{subject}</span>
          )}
          <IconButton
            icon={<Plus />}
            className="ml-auto size-8"
            aria-label={t("addBlock", { subject })}
            onClick={() => onAddBlock(part.id, optionId, section)}
          />
        </div>
        {section.blocks.length === 0 ? (
          <p className="pl-3 text-caption text-text-muted">{t("noBlocks")}</p>
        ) : (
          <ol className="flex flex-col gap-0.5">
            {section.blocks.map((block) => (
              <li key={block.id}>
                <OutlineButton
                  current={selection?.kind === "block" && selection.blockId === block.id}
                  onClick={() => onSelect({ kind: "block", blockId: block.id })}
                  meta={t("blockCount", { count: block.questionCount })}
                  warning={blockHasIssue(block.id) ? t("hasIssue") : null}
                  inset
                >
                  {block.name || t("untitledBlock")}
                  {block.questionType === "QUESTION_SET" && block.setCount && block.setSize
                    ? ` ${t("sets", { sets: block.setCount, size: block.setSize })}`
                    : ""}
                </OutlineButton>
              </li>
            ))}
          </ol>
        )}
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
        <ol className="scroll-shadows-y flex min-h-0 flex-col gap-3 overflow-y-auto p-2">
          {parts.map((part) => (
            <li key={part.id} className="flex flex-col gap-1">
              <OutlineButton
                current={selection?.kind === "part" && selection.partId === part.id}
                onClick={() => onSelect({ kind: "part", partId: part.id })}
                meta={t("partMeta", { count: part.questionCount, minutes: part.durationMinutes ?? 0 })}
                warning={partHasIssue(part.id) ? t("hasIssue") : null}
                strong
              >
                {part.name}
              </OutlineButton>
              {part.mode === "CHOICE" ? (
                <ol className="flex flex-col gap-2 pl-2">
                  {part.options.length === 0 && <li className="pl-3 text-caption text-text-muted">{t("noOptions")}</li>}
                  {part.options.map((option) => (
                    <li key={option.id} className="flex flex-col gap-0.5 border-l border-border pl-2">
                      <span className="pl-1 text-label font-medium text-text">{optionLabel(option, names)}</span>
                      <ol className="flex flex-col gap-1">
                        {option.sections.map((section) => renderSection(part, option.id, section, option.sections.length > 1))}
                      </ol>
                    </li>
                  ))}
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

function OutlineButton({
  current,
  onClick,
  meta,
  warning,
  strong = false,
  inset = false,
  children,
}: {
  current: boolean;
  onClick: () => void;
  meta: string;
  warning: string | null;
  strong?: boolean;
  inset?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-current={current ? "true" : undefined}
      onClick={onClick}
      className={cn(
        "relative flex w-full items-start justify-between gap-2 rounded-md px-3 py-2 text-left transition-colors duration-150 motion-reduce:transition-none",
        current
          ? "bg-primary-50 before:absolute before:inset-y-1 before:left-0 before:border-l-3 before:border-primary-text"
          : "hover:bg-subtle",
        inset && "pl-4",
        focusRingInset,
      )}
    >
      <span className={cn("min-w-0 wrap-break-word", strong ? "font-semibold text-text" : "text-text", current && "text-primary-text")}>
        {children}
      </span>
      <span className="flex shrink-0 items-center gap-1 text-caption whitespace-nowrap text-text-muted tabular-nums">
        {warning && (
          <>
            <TriangleAlert aria-hidden className="size-3.5 text-warning" />
            <span className="sr-only">{warning}</span>
          </>
        )}
        {meta}
      </span>
    </button>
  );
}
