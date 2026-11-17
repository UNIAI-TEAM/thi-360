"use client";

import {
  Badge,
  Button,
  CardList,
  CardListItem,
  Checkbox,
  cn,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  focusRing,
  IconButton,
  Label,
  ListCard,
  StatusChip,
  useCardMode,
} from "@thi360/ui";
import { CircleMinus, Pencil, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { type ReactNode, useId, useState } from "react";
import type { CombinationOption } from "@/features/content/types";
import { countOf, sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamPartOption } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { BranchDialog } from "./exam-structure-branch-dialog";
import { ENGLISH_CODE, firstBlockId, optionQuestionCount } from "./exam-structure-draft";
import type { StructureNames } from "./exam-structure-issues";

export interface CombinationOptionsProps {
  part: ExamPart;
  combinations: readonly CombinationOption[];
  names: StructureNames;
  issues: readonly StructureIssue[];
  /** Hiện ô bật nhánh Tiếng Anh (HSA, QDA); kỳ thi có lựa chọn gắn sẵn trong mẫu (BCA, THPTQG) thì không. */
  showEnglish: boolean;
  /** Môn chọn được khi thêm nhánh nội dung. */
  branchSubjects: ReadonlyArray<{ code: string; name: string }>;
  onToggleEnglish: (enabled: boolean) => void;
  onAttach: (combination: CombinationOption) => void;
  onAddBranch: (branch: { name: string; subjectCode: string }) => void;
  onConfirm: (optionId: string) => void;
  onRemove: (optionId: string) => void;
  onSectionCount: (optionId: string, sectionId: string, questionCount: number) => void;
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
 * Phần tự chọn (bản vẽ `BaiThiToHop`, UniNote PBI-04 mục 5.3): nhánh Tiếng Anh, nhánh nội dung (THPTQG Tin học, học
 * viên làm phần chung cộng một nhánh) và các tổ hợp gắn vào phần, số câu từng môn theo thứ tự (mặc định chia 17–17–16),
 * tổng phải bằng số câu của phần. Số câu chia tự động chờ chuyên gia xác nhận trước khi công bố. Gắn, sinh, gỡ lựa chọn
 * ở đây; khối của từng môn sửa ở cột Cấu trúc.
 */
export function CombinationOptions({
  part,
  combinations,
  names,
  issues,
  showEnglish,
  branchSubjects,
  onToggleEnglish,
  onAttach,
  onAddBranch,
  onConfirm,
  onRemove,
  onSectionCount,
  onEditBlocks,
}: CombinationOptionsProps) {
  const t = useTranslations("exams.combinations");
  const headingId = useId();
  const englishId = useId();
  const englishHintId = useId();
  const [attachOpen, setAttachOpen] = useState(false);
  const [branchOpen, setBranchOpen] = useState(false);
  const [removing, setRemoving] = useState<ExamPartOption | null>(null);

  const english = part.options.find((option) => option.kind === "SUBJECT" && option.code === ENGLISH_CODE);
  const attached = part.options.filter((option) => option.kind === "COMBINATION");
  const branches = part.options.filter((option) => option.kind === "BRANCH");
  const available = combinations.filter((combination) => !attached.some((option) => option.code === combination.code));
  const commonCount = sum(part.sections.map(countOf));
  const branchMode = branches.length > 0 || part.sections.length > 0;
  // Phần có phương án chọn môn (HSA Khoa học) không dùng tổ hợp KH-n: không hiện nhóm tổ hợp và quy tắc của tổ hợp.
  const hasSubjectChoice = part.options.some((option) => option.kind === "SUBJECT_CHOICE");
  const showCombinations = !hasSubjectChoice && (combinations.length > 0 || attached.length > 0);

  const label = (option: ExamPartOption) =>
    option.kind === "BRANCH" ? (option.name ?? option.code) : option.code;

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
          onSectionCount={onSectionCount}
          onEditBlocks={onEditBlocks}
          names={names}
        />
      )}

      {showCombinations && (
        <OptionGroup
          title={t("combinationsTitle", { count: attached.length })}
          caption={t("tableCaption")}
          empty={t("empty")}
          options={attached}
          part={part}
          commonCount={commonCount}
          issues={issues}
          label={label}
          caption2={(option) => option.name ?? names.combination(option.code)}
          temporary={(option) => Boolean(option.name)}
          actions={
            <Button type="button" variant="secondary" size="sm" aria-haspopup="dialog" onClick={() => setAttachOpen(true)}>
              <Plus />
              <span>{t("attach")}</span>
            </Button>
          }
          onConfirm={onConfirm}
          onRemove={setRemoving}
          onSectionCount={onSectionCount}
          onEditBlocks={onEditBlocks}
          names={names}
        />
      )}

      {!hasSubjectChoice && (
        <ul className="ml-5 list-disc space-y-1 text-caption text-text-muted">
          <li>{t("rules.total")}</li>
          <li>{t("rules.fixed")}</li>
          <li>{t("rules.expert")}</li>
          <li>{t("rules.newCodes")}</li>
        </ul>
      )}

      <CombinationAttachDialog
        open={attachOpen}
        onOpenChange={setAttachOpen}
        available={available}
        names={names}
        questionCount={optionQuestionCount(part)}
        onAttach={(combination) => {
          onAttach(combination);
          setAttachOpen(false);
        }}
      />
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

/** Bảng (thẻ khi khung hẹp) các lựa chọn cùng loại: tổ hợp hoặc nhánh nội dung, số câu từng môn, trạng thái, thao tác. */
function OptionGroup({
  title,
  caption,
  empty,
  options,
  part,
  commonCount,
  issues,
  names,
  label,
  caption2,
  temporary = () => false,
  actions,
  onConfirm,
  onRemove,
  onSectionCount,
  onEditBlocks,
}: {
  title: string;
  caption: string;
  empty: string;
  options: readonly ExamPartOption[];
  part: ExamPart;
  commonCount: number;
  issues: readonly StructureIssue[];
  names: StructureNames;
  label: (option: ExamPartOption) => string;
  caption2: (option: ExamPartOption) => string;
  temporary?: (option: ExamPartOption) => boolean;
  actions: ReactNode;
  onConfirm: (optionId: string) => void;
  onRemove: (option: ExamPartOption) => void;
  onSectionCount: (optionId: string, sectionId: string, questionCount: number) => void;
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
            <BlockNumberInput
              className="h-8 w-14 px-2"
              value={section.questionCount ?? 0}
              onValueChange={(value) => onSectionCount(option.id, section.id, value)}
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
      <span className="flex flex-wrap items-center gap-1.5 font-semibold text-text">
        {label(option)}
        {temporary(option) && <Badge>{t("temporaryCode")}</Badge>}
      </span>
      <span className="block min-w-36 text-caption font-normal text-text-muted">{caption2(option)}</span>
    </>
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="text-label font-semibold text-text">{title}</h4>
        <div className="flex flex-wrap gap-2">{actions}</div>
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
          <div role="region" tabIndex={0} aria-label={caption} className={cn("overflow-x-auto rounded-md border border-border", focusRing)}>
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

/** "Gắn tổ hợp" (chưa có bản vẽ): chọn một mã tổ hợp chưa gắn từ danh mục Tổ hợp của Cấu hình chung. */
export function CombinationAttachDialog({
  open,
  onOpenChange,
  available,
  names,
  questionCount,
  onAttach,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  available: readonly CombinationOption[];
  names: StructureNames;
  questionCount: number;
  onAttach: (combination: CombinationOption) => void;
}) {
  const t = useTranslations("exams.combinations.attachDialog");
  const [selected, setSelected] = useState<string>("");
  const [error, setError] = useState(false);
  const legendId = useId();
  const errorId = useId();
  const choice = available.find((combination) => combination.code === selected);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setSelected("");
          setError(false);
        }
        onOpenChange(next);
      }}
    >
      <DialogContent size="md" closeLabel={t("cancel")}>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description", { count: questionCount })}</DialogDescription>
        </DialogHeader>
        {available.length === 0 ? (
          <p className="text-body text-text-muted">{t("empty")}</p>
        ) : (
          <fieldset
            className="flex flex-col gap-2"
            aria-describedby={error ? errorId : undefined}
            aria-invalid={error || undefined}
          >
            <legend id={legendId} className="mb-1 text-field-label font-medium text-text-secondary">
              {t("legend")}
            </legend>
            {available.map((combination) => (
              <label
                key={combination.code}
                className={cn(
                  "flex cursor-pointer items-start gap-3 rounded-md border p-3",
                  selected === combination.code ? "border-primary-text bg-primary-50" : "border-border",
                )}
              >
                <input
                  type="radio"
                  name="combination"
                  value={combination.code}
                  checked={selected === combination.code}
                  onChange={() => {
                    setSelected(combination.code);
                    setError(false);
                  }}
                  className={cn("mt-0.5 size-4 shrink-0 accent-primary-fill", focusRing)}
                />
                <span className="flex flex-col gap-0.5">
                  <span className="font-semibold text-text">{combination.code}</span>
                  <span className="text-caption text-text-muted">
                    {combination.subjectCodes.map((code) => names.subject(code)).join(" – ")}
                  </span>
                </span>
              </label>
            ))}
            {error && (
              <p id={errorId} role="alert" className="text-caption text-danger">
                {t("required")}
              </p>
            )}
          </fieldset>
        )}
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            {t("cancel")}
          </Button>
          {available.length > 0 && (
            <Button
              type="button"
              onClick={() => {
                if (!choice) {
                  setError(true);
                  return;
                }
                setSelected("");
                onAttach(choice);
              }}
            >
              {t("submit")}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
