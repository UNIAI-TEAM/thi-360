"use client";

import {
  Button,
  CardList,
  CardListItem,
  Checkbox,
  cn,
  ConfirmDialog,
  focusRing,
  IconButton,
  Input,
  Label,
  ListCard,
  StatusChip,
  useCardMode,
} from "@thi360/ui";
import { CircleMinus, Pencil, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { type ReactNode, useId, useState } from "react";
import { countOf, sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamPartOption } from "../types";
import { EditOverviewLink } from "./edit-overview-link";
import { BranchDialog } from "./exam-structure-branch-dialog";
import { ENGLISH_CODE, firstBlockId, optionQuestionCount } from "./exam-structure-draft";
import type { StructureNames } from "./exam-structure-issues";
import { LOCKED_FIELD } from "./locked-field";

export interface PartOptionsProps {
  part: ExamPart;
  /** Mã bài: đường dẫn "Sửa thông tin tổng quan" cạnh số câu từng môn chỉ đọc của các nhánh (UNI-2753). */
  examId: string;
  names: StructureNames;
  issues: readonly StructureIssue[];
  /** Hiện ô bật nhánh Tiếng Anh (HSA); kỳ thi có lựa chọn gắn sẵn trong mẫu (THPTQG) thì không. */
  showEnglish: boolean;
  /** Môn chọn được khi thêm nhánh nội dung. */
  branchSubjects: ReadonlyArray<{ code: string; name: string }>;
  onToggleEnglish: (enabled: boolean) => void;
  onAddBranch: (branch: { name: string; subjectCode: string }) => void;
  onConfirm: (optionId: string) => void;
  onRemove: (optionId: string) => void;
  onEditBlocks: (blockId: string) => void;
}

function optionIssueCount(issues: readonly StructureIssue[], optionId: string): number {
  return issues.filter((issue) =>
    issue.code === "SECTIONS_TOTAL" || issue.code === "PART_POINTS"
      ? issue.optionId === optionId
      : "location" in issue && issue.location.optionId === optionId,
  ).length;
}

/**
 * Lựa chọn của phần tự chọn không theo mã: nhánh Tiếng Anh và nhánh nội dung (THPTQG Tin học, học viên làm phần chung
 * cộng một nhánh), số câu từng môn theo thứ tự, tổng phải bằng số câu của phần. Phần theo mã (QDA, BCA) không còn thao
 * tác chọn mã hay gán môn cho mã: dùng `CodedPartPanel`. Khối của từng môn sửa ở cột Cấu trúc.
 */
export function PartOptions({
  part,
  examId,
  names,
  issues,
  showEnglish,
  branchSubjects,
  onToggleEnglish,
  onAddBranch,
  onConfirm,
  onRemove,
  onEditBlocks,
}: PartOptionsProps) {
  const t = useTranslations("exams.combinations");
  const headingId = useId();
  const englishId = useId();
  const englishHintId = useId();
  const [branchOpen, setBranchOpen] = useState(false);
  const [removing, setRemoving] = useState<ExamPartOption | null>(null);

  const english = part.options.find((option) => option.kind === "SUBJECT" && option.code === ENGLISH_CODE);
  const branches = part.options.filter((option) => option.kind === "BRANCH");
  const commonCount = sum(part.sections.map(countOf));
  const branchMode = branches.length > 0 || part.sections.length > 0;
  const label = (option: ExamPartOption) => (option.kind === "BRANCH" ? (option.name ?? option.code) : option.code);

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-4">
      <div className="flex flex-col gap-0.5">
        <h3 id={headingId} className="text-title font-semibold text-text">
          {t("title")}
        </h3>
        <p className="text-caption text-text-muted">
          {branchMode ? t("branchDescription", { common: commonCount, count: part.questionCount }) : t("description")}
        </p>
      </div>

      {showEnglish && (
        <div className="flex flex-wrap items-start justify-between gap-3 rounded-md border border-border p-3">
          <div className="flex items-start gap-3">
            <Checkbox
              id={englishId}
              checked={Boolean(english)}
              aria-describedby={englishHintId}
              onCheckedChange={(checked) => onToggleEnglish(checked === true)}
            />
            <div className="flex flex-col gap-0.5">
              <Label htmlFor={englishId} className="cursor-pointer text-body font-medium text-text">
                {names.subject(ENGLISH_CODE)}
              </Label>
              <p id={englishHintId} className="text-caption text-text-muted">
                {english
                  ? t("englishHint", {
                      count: optionQuestionCount(part),
                      blocks: sum(english.sections.map((section) => section.blocks.length)),
                    })
                  : t("englishOff")}
              </p>
            </div>
          </div>
          {english && (
            <div className="flex items-center gap-2">
              <OptionStatus option={english} issues={issues} onConfirm={onConfirm} label={names.subject(ENGLISH_CODE)} />
              <EditBlocksButton option={english} part={part} label={names.subject(ENGLISH_CODE)} onEditBlocks={onEditBlocks} />
            </div>
          )}
        </div>
      )}

      {branchMode && (
        <OptionGroup
          title={t("branchesTitle", { count: branches.length })}
          caption={t("branchesCaption")}
          empty={t("branchesEmpty")}
          options={branches}
          part={part}
          commonCount={commonCount}
          issues={issues}
          label={label}
          caption2={(option) => option.code}
          actions={
            <Button type="button" variant="secondary" size="sm" aria-haspopup="dialog" onClick={() => setBranchOpen(true)}>
              <Plus />
              <span>{t("addBranch")}</span>
            </Button>
          }
          onConfirm={onConfirm}
          onRemove={setRemoving}
          examId={examId}
          onEditBlocks={onEditBlocks}
          names={names}
        />
      )}

      <BranchDialog
        open={branchOpen}
        onOpenChange={setBranchOpen}
        subjects={branchSubjects}
        defaultSubjectCode={part.sections[0]?.subjectCode ?? branchSubjects[0]?.code ?? ""}
        questionCount={optionQuestionCount(part)}
        onAdd={onAddBranch}
      />
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) setRemoving(null);
        }}
        title={t("removeTitle", { code: removing ? label(removing) : "" })}
        description={t("removeDescription", {
          blocks: removing ? sum(removing.sections.map((section) => section.blocks.length)) : 0,
        })}
        confirmLabel={t("removeConfirm")}
        onConfirm={() => {
          if (removing) onRemove(removing.id);
          setRemoving(null);
        }}
      />
    </section>
  );
}

/** Kết quả cấu hình của một lựa chọn và cờ "Chờ chuyên gia xác nhận" kèm nút xác nhận. */
function OptionStatus({
  option,
  issues,
  label,
  onConfirm,
}: {
  option: ExamPartOption;
  issues: readonly StructureIssue[];
  label: string;
  onConfirm: (optionId: string) => void;
}) {
  const t = useTranslations("exams.combinations");
  const count = optionIssueCount(issues, option.id);
  return (
    <div className="flex flex-col items-start gap-1.5">
      {count === 0 ? (
        <StatusChip tone="success">{t("statusOk")}</StatusChip>
      ) : (
        <StatusChip tone="danger">{t("statusIssues", { count })}</StatusChip>
      )}
      {option.expertConfirmed === false && (
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusChip tone="warning">{t("expertPending")}</StatusChip>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            aria-label={t("expertConfirmAria", { code: label })}
            onClick={() => onConfirm(option.id)}
          >
            {t("expertConfirm")}
          </Button>
        </div>
      )}
      {option.expertConfirmed === true && <StatusChip tone="info">{t("expertConfirmed")}</StatusChip>}
    </div>
  );
}

function EditBlocksButton({
  option,
  part,
  label,
  onEditBlocks,
}: {
  option: ExamPartOption;
  part: ExamPart;
  label: string;
  onEditBlocks: (blockId: string) => void;
}) {
  const t = useTranslations("exams.combinations");
  const blockId = firstBlockId({ ...part, mode: "FIXED", sections: option.sections });
  return (
    <IconButton
      icon={<Pencil />}
      aria-label={t("editBlocksAria", { code: label })}
      tooltip={blockId ? t("editBlocks") : t("noBlocks")}
      disabled={!blockId}
      onClick={() => blockId && onEditBlocks(blockId)}
    />
  );
}

/** Bảng (thẻ khi khung hẹp) các nhánh nội dung: số câu từng môn, trạng thái, thao tác. */
function OptionGroup({
  title,
  caption,
  empty,
  options,
  part,
  examId,
  commonCount,
  issues,
  names,
  label,
  caption2,
  actions,
  onConfirm,
  onRemove,
  onEditBlocks,
}: {
  title: string;
  caption: string;
  empty: string;
  options: readonly ExamPartOption[];
  part: ExamPart;
  examId: string;
  commonCount: number;
  issues: readonly StructureIssue[];
  names: StructureNames;
  label: (option: ExamPartOption) => string;
  caption2: (option: ExamPartOption) => string;
  actions: ReactNode;
  onConfirm: (optionId: string) => void;
  onRemove: (option: ExamPartOption) => void;
  onEditBlocks: (blockId: string) => void;
}) {
  const t = useTranslations("exams.combinations");
  const [cardsRef, cardMode] = useCardMode<HTMLDivElement>(true, 640);

  const subjectInputs = (option: ExamPartOption) => (
    <div className="flex flex-wrap gap-2">
      {option.sections.map((section, index) => {
        const subject = names.subject(section.subjectCode);
        return (
          <label key={section.id} className="flex items-center gap-2 rounded-md border border-border py-1 pr-1 pl-2.5 text-label">
            <span className="whitespace-nowrap text-text">{t("subjectPosition", { position: index + 1, subject })}</span>
            <Input
              className={cn("h-8 w-14 px-2 tabular-nums", LOCKED_FIELD)}
              value={String(section.questionCount ?? 0)}
              readOnly
              aria-readonly="true"
              aria-label={t("subjectCountAria", { code: label(option), subject })}
            />
          </label>
        );
      })}
    </div>
  );

  const totalOf = (option: ExamPartOption) => commonCount + sum(option.sections.map(countOf));
  const totalText = (option: ExamPartOption) =>
    commonCount > 0
      ? t("totalWithCommon", { common: commonCount, total: totalOf(option), count: part.questionCount })
      : t("totalValue", { total: totalOf(option), count: part.questionCount });
  const removeButton = (option: ExamPartOption) => (
    <IconButton
      icon={<CircleMinus />}
      tone="danger"
      aria-label={t("removeAria", { code: label(option) })}
      onClick={() => onRemove(option)}
    />
  );
  const status = (option: ExamPartOption) => (
    <OptionStatus option={option} issues={issues} label={label(option)} onConfirm={onConfirm} />
  );
  const titleCell = (option: ExamPartOption) => (
    <>
      <span className="flex flex-wrap items-center gap-1.5 font-semibold text-text">{label(option)}</span>
      <span className="block min-w-36 text-caption font-normal text-text-muted">{caption2(option)}</span>
    </>
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="text-label font-semibold text-text">{title}</h4>
        <div className="flex flex-wrap items-center gap-2">
          <EditOverviewLink examId={examId} />
          {actions}
        </div>
      </div>
      <div ref={cardsRef}>
        {options.length === 0 ? (
          <p className="rounded-md border border-dashed border-border px-3 py-4 text-center text-body text-text-muted">{empty}</p>
        ) : cardMode ? (
          <CardList aria-label={caption}>
            {options.map((option) => (
              <CardListItem key={option.id}>
                <ListCard
                  title={label(option)}
                  caption={caption2(option)}
                  status={status(option)}
                  actions={
                    <>
                      <EditBlocksButton option={option} part={part} label={label(option)} onEditBlocks={onEditBlocks} />
                      {removeButton(option)}
                    </>
                  }
                  meta={[{ label: t("total"), value: totalText(option) }]}
                >
                  <div className="relative z-1">{subjectInputs(option)}</div>
                </ListCard>
              </CardListItem>
            ))}
          </CardList>
        ) : (
          <div role="region" tabIndex={0} aria-label={caption} className={cn("relative overflow-x-auto rounded-md border border-border", focusRing)}>
            <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
              <caption className="sr-only">{caption}</caption>
              <thead>
                <tr className="text-left text-caption text-text-muted">
                  <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">
                    {t("combination")}
                  </th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">
                    {t("subjects")}
                  </th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">
                    {t("total")}
                  </th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">
                    {t("status")}
                  </th>
                  <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">
                    {t("actions")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {options.map((option) => (
                  <tr key={option.id}>
                    <th scope="row" className="border-b border-border px-3 py-2 text-left align-top font-normal">
                      {titleCell(option)}
                    </th>
                    <td className="border-b border-border px-2 py-2 align-top">{subjectInputs(option)}</td>
                    <td
                      className={cn(
                        "border-b border-border px-2 py-2 align-top font-semibold whitespace-nowrap",
                        totalOf(option) !== part.questionCount && "text-danger",
                      )}
                    >
                      <output>{totalText(option)}</output>
                    </td>
                    <td className="border-b border-border px-2 py-2 align-top">{status(option)}</td>
                    <td className="border-b border-border px-3 py-1 text-right align-top whitespace-nowrap">
                      <EditBlocksButton option={option} part={part} label={label(option)} onEditBlocks={onEditBlocks} />
                      {removeButton(option)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
