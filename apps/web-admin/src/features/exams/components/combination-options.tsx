"use client";

import {
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
import { useId, useState } from "react";
import type { CombinationOption } from "@/features/content/types";
import { sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamPartOption } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { ENGLISH_CODE, firstBlockId } from "./exam-structure-draft";
import type { StructureNames } from "./exam-structure-issues";

export interface CombinationOptionsProps {
  part: ExamPart;
  combinations: readonly CombinationOption[];
  names: StructureNames;
  issues: readonly StructureIssue[];
  onToggleEnglish: (enabled: boolean) => void;
  onAttach: (combination: CombinationOption) => void;
  onRemove: (optionId: string) => void;
  onSectionCount: (optionId: string, sectionId: string, questionCount: number) => void;
  onEditBlocks: (blockId: string) => void;
}

function optionIssueCount(issues: readonly StructureIssue[], optionId: string): number {
  return issues.filter((issue) =>
    issue.code === "SECTIONS_TOTAL" ? issue.optionId === optionId : "location" in issue && issue.location.optionId === optionId,
  ).length;
}

/**
 * Phần tự chọn (bản vẽ `BaiThiToHop`, UniNote PBI-04 mục 5.3): nhánh Tiếng Anh và các tổ hợp Khoa học gắn vào phần,
 * số câu từng môn theo thứ tự (mặc định chia 17–17–16), tổng phải bằng số câu của phần. Gắn/gỡ tổ hợp ở đây; khối của
 * từng môn sửa ở cột Cấu trúc.
 */
export function CombinationOptions({
  part,
  combinations,
  names,
  issues,
  onToggleEnglish,
  onAttach,
  onRemove,
  onSectionCount,
  onEditBlocks,
}: CombinationOptionsProps) {
  const t = useTranslations("exams.combinations");
  const headingId = useId();
  const englishId = useId();
  const englishHintId = useId();
  const [attachOpen, setAttachOpen] = useState(false);
  const [removing, setRemoving] = useState<ExamPartOption | null>(null);
  const [cardsRef, cardMode] = useCardMode<HTMLDivElement>(true, 640);

  const english = part.options.find((option) => option.kind === "SUBJECT" && option.code === ENGLISH_CODE);
  const attached = part.options.filter((option) => option.kind === "COMBINATION");
  const available = combinations.filter((combination) => !attached.some((option) => option.code === combination.code));

  const statusChip = (option: ExamPartOption) => {
    const count = optionIssueCount(issues, option.id);
    return count === 0 ? (
      <StatusChip tone="success">{t("statusOk")}</StatusChip>
    ) : (
      <StatusChip tone="danger">{t("statusIssues", { count })}</StatusChip>
    );
  };

  const editButton = (option: ExamPartOption) => {
    const blockId = firstBlockId({ ...part, mode: "FIXED", sections: option.sections });
    return (
      <IconButton
        icon={<Pencil />}
        aria-label={t("editBlocksAria", { code: option.code })}
        tooltip={blockId ? t("editBlocks") : t("noBlocks")}
        disabled={!blockId}
        onClick={() => blockId && onEditBlocks(blockId)}
      />
    );
  };

  const subjectInputs = (option: ExamPartOption) => (
    <div className="flex flex-wrap gap-2">
      {option.sections.map((section, index) => {
        const subject = names.subject(section.subjectCode);
        return (
          <label key={section.id} className="flex items-center gap-2 rounded-md border border-border py-1 pr-1 pl-2.5 text-label">
            <span className="whitespace-nowrap text-text">{t("subjectPosition", { position: index + 1, subject })}</span>
            <BlockNumberInput
              className="h-8 w-14 px-2"
              value={section.questionCount}
              onValueChange={(value) => onSectionCount(option.id, section.id, value)}
              aria-label={t("subjectCountAria", { code: option.code, subject })}
            />
          </label>
        );
      })}
    </div>
  );

  const totalOf = (option: ExamPartOption) => sum(option.sections.map((section) => section.questionCount));
  const totalText = (option: ExamPartOption) => t("totalValue", { total: totalOf(option), count: part.questionCount });

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-4">
      <div className="flex flex-col gap-0.5">
        <h3 id={headingId} className="text-title font-semibold text-text">
          {t("title")}
        </h3>
        <p className="text-caption text-text-muted">{t("description")}</p>
      </div>

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
                    count: part.questionCount,
                    blocks: sum(english.sections.map((section) => section.blocks.length)),
                  })
                : t("englishOff")}
            </p>
          </div>
        </div>
        {english && (
          <div className="flex items-center gap-2">
            {statusChip(english)}
            {editButton(english)}
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="text-label font-semibold text-text">{t("combinationsTitle", { count: attached.length })}</h4>
        <Button type="button" variant="secondary" size="sm" aria-haspopup="dialog" onClick={() => setAttachOpen(true)}>
          <Plus />
          <span>{t("attach")}</span>
        </Button>
      </div>

      <div ref={cardsRef}>
        {attached.length === 0 ? (
          <p className="rounded-md border border-dashed border-border px-3 py-4 text-center text-body text-text-muted">
            {t("empty")}
          </p>
        ) : cardMode ? (
          <CardList aria-label={t("tableCaption")}>
            {attached.map((option) => (
              <CardListItem key={option.id}>
                <ListCard
                  title={option.code}
                  caption={names.combination(option.code)}
                  status={statusChip(option)}
                  actions={
                    <>
                      {editButton(option)}
                      <IconButton
                        icon={<CircleMinus />}
                        tone="danger"
                        aria-label={t("removeAria", { code: option.code })}
                        onClick={() => setRemoving(option)}
                      />
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
          <div
            role="region"
            tabIndex={0}
            aria-label={t("tableCaption")}
            className={cn("overflow-x-auto rounded-md border border-border", focusRing)}
          >
            <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
              <caption className="sr-only">{t("tableCaption")}</caption>
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
                {attached.map((option) => (
                  <tr key={option.id}>
                    <th scope="row" className="border-b border-border px-3 py-2 text-left align-top font-normal">
                      <span className="block font-semibold text-text">{option.code}</span>
                      <span className="block min-w-36 text-caption text-text-muted">{names.combination(option.code)}</span>
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
                    <td className="border-b border-border px-2 py-2 align-top">{statusChip(option)}</td>
                    <td className="border-b border-border px-3 py-1 text-right align-top whitespace-nowrap">
                      {editButton(option)}
                      <IconButton
                        icon={<CircleMinus />}
                        tone="danger"
                        aria-label={t("removeAria", { code: option.code })}
                        onClick={() => setRemoving(option)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <ul className="ml-5 list-disc space-y-1 text-caption text-text-muted">
        <li>{t("rules.total")}</li>
        <li>{t("rules.fixed")}</li>
        <li>{t("rules.newCodes")}</li>
        <li>{t("rules.sample")}</li>
      </ul>

      <CombinationAttachDialog
        open={attachOpen}
        onOpenChange={setAttachOpen}
        available={available}
        names={names}
        questionCount={part.questionCount}
        onAttach={(combination) => {
          onAttach(combination);
          setAttachOpen(false);
        }}
      />
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) setRemoving(null);
        }}
        title={t("removeTitle", { code: removing?.code ?? "" })}
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
