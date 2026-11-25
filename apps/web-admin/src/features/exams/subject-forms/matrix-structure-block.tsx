"use client";

import {
  Badge,
  Button,
  cn,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  focusRing,
  IconButton,
  Input,
  Select,
  StatusChip,
  Switch,
} from "@thi360/ui";
import { ArrowDown, ArrowUp, ChevronDown, Info, Plus, RotateCcw, Shuffle, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useId, useMemo, useState, type ReactNode } from "react";
import type { QuestionType } from "@/features/content/types";
import { ratePercent } from "../allocation";
import { formatNumberText, parseNumberText, parsePercentText } from "../components/block-number-input";
import { LOCKED_FIELD } from "../components/locked-field";
import { isFlexibleSet } from "../matrix";
import type { QuestionBlock } from "../types";
import { subjectFormLimits } from "./limits";
import { LockReason } from "./lock-reason";
import type { MatrixBlockProps } from "./matrix-block-props";
import { MATRIX_FIELDS, MATRIX_ISSUE_CODES, matrixBlockingIssues } from "./matrix-blocking";
import {
  addMatrixBlock,
  changeMatrixBlockType,
  moveMatrixBlock,
  removeMatrixBlock,
  resetMatrixSectionBlocks,
  structureOf,
  toggleMatrixSetMode,
  toggleSectionMixBlocks,
  updateMatrixBlockCount,
  updateMatrixBlockName,
  updateMatrixSetDimensions,
} from "./matrix-form-model";
import { MatrixSetSource } from "./matrix-set-source";
import { percentsToCounts } from "./percent";

const MATRIX_FORMAT_ORDER: readonly QuestionType[] = [
  "SINGLE_CHOICE",
  "QUESTION_SET",
  "SHORT_ANSWER",
  "TRUE_FALSE",
  "MULTIPLE_CHOICE",
  "CLOZE",
  "MATCHING",
  "ESSAY",
];

interface MatrixInlineNumberProps {
  value: number;
  ariaLabel: string;
  field: string;
  blockId?: string;
  raw: MatrixBlockProps["raw"];
  error?: string;
  readOnly?: boolean;
  widthClass?: string;
  onValid: (parsed: number) => void;
}

function MatrixInlineNumber({
  value,
  ariaLabel,
  field,
  blockId,
  raw,
  error,
  readOnly = false,
  widthClass = "w-20",
  onValid,
}: MatrixInlineNumberProps) {
  const id = useId();
  const storedRaw = raw.get(field);
  const rawInvalid = storedRaw?.code === "invalidNumber";
  const [text, setText] = useState(() => (rawInvalid ? storedRaw.text : formatNumberText(value)));
  const [lastValue, setLastValue] = useState(value);
  const [lastRawText, setLastRawText] = useState<string | null>(rawInvalid ? storedRaw.text : null);

  if (rawInvalid && storedRaw.text !== lastRawText) {
    setLastRawText(storedRaw.text);
    setText(storedRaw.text);
  } else if (!rawInvalid && lastRawText !== null) {
    setLastRawText(null);
    setText(formatNumberText(value));
  } else if (!rawInvalid && value !== lastValue) {
    setLastValue(value);
    if (parseNumberText(text, false) !== value) {
      setText(formatNumberText(value));
    }
  }

  const errorId = `${id}-error`;
  const hasError = Boolean(error);

  const handleChange = (nextText: string) => {
    setText(nextText);
    const parsed = parseNumberText(nextText, false);
    if (parsed === null) {
      raw.set(field, nextText, "invalidNumber");
      return;
    }
    raw.clear(field);
    setLastValue(parsed);
    onValid(parsed);
  };

  return (
    <div data-form-block={blockId} data-form-field={field} className="inline-flex flex-col items-end gap-1">
      <Input
        id={id}
        aria-label={ariaLabel}
        aria-invalid={hasError || undefined}
        aria-describedby={hasError ? errorId : undefined}
        readOnly={readOnly}
        aria-readonly={readOnly || undefined}
        inputMode="numeric"
        autoComplete="off"
        value={text}
        onChange={(event) => handleChange(event.target.value)}
        className={cn("h-9 px-2.5 text-right tabular-nums", widthClass, readOnly && LOCKED_FIELD)}
      />
      {hasError && (
        <p id={errorId} role="alert" className="max-w-44 text-right text-caption text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

interface MatrixTwoColumnCellsProps {
  count: number;
  total: number | null;
  countAria: string;
  percentAria: string;
  field: string;
  blockId: string;
  readOnly: boolean;
  raw: MatrixBlockProps["raw"];
  error?: string;
  percentTextValue: string;
  onEdit: (edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => void;
  onPercentText: (text: string) => void;
}

function MatrixTwoColumnCells({
  count,
  total,
  countAria,
  percentAria,
  field,
  blockId,
  readOnly,
  raw,
  error,
  percentTextValue,
  onEdit,
  onPercentText,
}: MatrixTwoColumnCellsProps) {
  const id = useId();
  const tCommon = useTranslations("exams.subjectForm.common.countPercent");
  const storedRaw = raw.get(field);
  const rawIsPercent = storedRaw?.code === "invalidPercent";
  const rawIsCount = storedRaw?.code === "invalidNumber";
  const [countText, setCountText] = useState(() => (rawIsCount ? storedRaw.text : formatNumberText(count)));
  const [lastCount, setLastCount] = useState(count);

  if (count !== lastCount) {
    setLastCount(count);
    if (!rawIsCount && parseNumberText(countText, false) !== count) {
      setCountText(formatNumberText(count));
    }
  }

  const shownPercent = total && total > 0 ? ratePercent(count, total) : null;
  const localError = rawIsCount || rawIsPercent ? tCommon("invalidNumber") : error;
  const errorId = `${id}-error`;
  const invalid = Boolean(localError);

  const handleCount = (nextText: string) => {
    setCountText(nextText);
    onPercentText("");
    if (nextText.trim() === "") {
      raw.clear(field);
      onEdit({ unit: "COUNT", value: 0 });
      return;
    }
    const parsed = parseNumberText(nextText, false);
    if (parsed === null) {
      raw.set(field, nextText, "invalidNumber");
      return;
    }
    raw.clear(field);
    onEdit({ unit: "COUNT", value: parsed });
  };

  const handlePercent = (nextText: string) => {
    onPercentText(nextText);
    if (nextText.trim() === "") {
      raw.clear(field);
      onEdit({ unit: "PERCENT", value: null });
      return;
    }
    const parsed = parsePercentText(nextText);
    if (parsed === null) {
      raw.set(field, nextText, "invalidPercent");
      return;
    }
    raw.clear(field);
    onEdit({ unit: "PERCENT", value: parsed });
  };

  return (
    <>
      <td className="px-3 py-2.5 text-right align-top">
        <div data-form-block={blockId} data-form-field={!rawIsPercent ? field : undefined} className="inline-flex flex-col items-end gap-1">
          <Input
            id={`${id}-count`}
            aria-label={countAria}
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? errorId : undefined}
            readOnly={readOnly}
            aria-readonly={readOnly || undefined}
            inputMode="numeric"
            autoComplete="off"
            value={countText}
            onChange={(event) => handleCount(event.target.value)}
            className={cn("h-9 w-20 px-2.5 text-right tabular-nums", readOnly && LOCKED_FIELD)}
          />
          {readOnly && <LockReason code="templateTotal" />}
          {invalid && (
            <p id={errorId} role="alert" className="max-w-44 text-right text-caption text-danger">
              {localError}
            </p>
          )}
        </div>
      </td>
      <td className="px-3 py-2.5 text-right align-top">
        <div data-form-field={rawIsPercent ? field : undefined} className="inline-flex justify-end">
          <Input
            id={`${id}-percent`}
            aria-label={percentAria}
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? errorId : undefined}
            readOnly={readOnly}
            aria-readonly={readOnly || undefined}
            inputMode="decimal"
            autoComplete="off"
            value={rawIsPercent ? storedRaw.text : percentTextValue}
            placeholder={shownPercent !== null ? formatNumberText(shownPercent) : undefined}
            onChange={(event) => handlePercent(event.target.value)}
            className={cn("h-9 w-20 px-2.5 text-right tabular-nums", readOnly && LOCKED_FIELD)}
          />
        </div>
      </td>
    </>
  );
}

export function MatrixStructureBlock({
  compact = false,
  renderRoleFooter,
  ...props
}: MatrixBlockProps & { compact?: boolean; renderRoleFooter?: (block: QuestionBlock) => ReactNode }) {
  const { section, entry, kindCode, total, reference, raw, onChange, templateLocked, defaultSnapshot = null } = props;
  const locale = useLocale();
  const t = useTranslations("exams.subjectForm.matrix.structure");
  const tErrors = useTranslations("exams.subjectForm.matrix.errors");
  const tCommon = useTranslations("exams.subjectForm.common.countPercent");
  const mixSwitchId = useId();

  const activeBlocks = useMemo(() => section.blocks.filter((block) => !block.omitted), [section.blocks]);
  const structure = structureOf(section);
  const mixed = section.mixBlocks === true;
  const twoColumns = entry.percentEntry === "TWO_COLUMNS";
  const limits = subjectFormLimits(kindCode, section.subjectCode);
  const issues = matrixBlockingIssues(section, kindCode);

  const [percentDraft, setPercentDraft] = useState<Record<string, number>>({});
  const [percentTexts, setPercentTexts] = useState<Record<string, string>>({});

  const formatLabel = (code: QuestionType): string =>
    locale === "vi"
      ? (reference.questionTypes.find((item) => item.code === code)?.name ?? t(`defaultRowName.${code}`))
      : t(`defaultRowName.${code}`);

  const autoNames = useMemo(() => {
    const names = new Set<string>();
    for (const code of MATRIX_FORMAT_ORDER) {
      names.add(t(`defaultRowName.${code}`));
      const refName = reference.questionTypes.find((item) => item.code === code)?.name;
      if (refName) names.add(refName);
    }
    return names;
  }, [reference.questionTypes, t]);

  const selectOptions = useMemo(
    () =>
      MATRIX_FORMAT_ORDER.map((code) => ({
        value: code,
        label: formatLabel(code),
        textValue: formatLabel(code),
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [locale, reference.questionTypes, t],
  );

  const errorOf = (field: string): string | undefined => {
    const stored = raw.get(field);
    const code = issues.find((issue) => issue.field === field)?.code ?? stored?.code;
    switch (code) {
      case MATRIX_ISSUE_CODES.setMin:
        return tErrors("setMin");
      case MATRIX_ISSUE_CODES.setSizeMin:
        return tErrors("setSizeMin");
      case MATRIX_ISSUE_CODES.setRange:
        return tErrors("setRange", { min: limits?.setCount.min ?? 1, max: limits?.setCount.max ?? 1 });
      case MATRIX_ISSUE_CODES.shortAnswerMin:
        return tErrors("shortAnswerMin", { min: limits?.shortAnswerMin ?? 1 });
      case MATRIX_ISSUE_CODES.structurePercentTotal:
        return tErrors("percentTotal", { total: stored?.text ?? "" });
      case "invalidNumber":
      case "invalidPercent":
        return tCommon("invalidNumber");
      default:
        return undefined;
    }
  };

  const percentEditableBlocks = useMemo(
    () => activeBlocks.filter((block) => block.questionType !== "QUESTION_SET" || isFlexibleSet(block)),
    [activeBlocks],
  );

  const handleTwoColumnEdit = (blockId: string, edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => {
    if (edit.unit === "COUNT") {
      setPercentDraft({});
      setPercentTexts({});
      raw.clear(MATRIX_FIELDS.structure);
      if (edit.value !== null) {
        onChange((current) => updateMatrixBlockCount(current, blockId, edit.value!));
      }
      return;
    }
    const next = { ...percentDraft };
    if (edit.value === null) delete next[blockId];
    else next[blockId] = edit.value;
    setPercentDraft(next);
    if (Object.keys(next).length === 0) {
      raw.clear(MATRIX_FIELDS.structure);
      return;
    }
    const complete = percentEditableBlocks.every((item) => next[item.id] !== undefined);
    const sumPct = percentEditableBlocks.reduce((acc, item) => acc + (next[item.id] ?? 0), 0);
    if (!complete || Math.abs(sumPct - 100) > 1e-6) {
      raw.set(MATRIX_FIELDS.structure, String(Math.round(sumPct * 100) / 100), MATRIX_ISSUE_CODES.structurePercentTotal);
      return;
    }
    const fixedSum = activeBlocks
      .filter((item) => item.questionType === "QUESTION_SET" && !isFlexibleSet(item))
      .reduce((acc, item) => acc + (item.questionCount ?? 0), 0);
    const pool = Math.max(0, (total ?? 0) - fixedSum);
    const counts = percentsToCounts(
      percentEditableBlocks.map((item) => next[item.id]!),
      pool,
    );
    raw.clear(MATRIX_FIELDS.structure);
    setPercentDraft({});
    setPercentTexts({});
    onChange((current) => {
      let updated = current;
      percentEditableBlocks.forEach((item, index) => {
        updated = updateMatrixBlockCount(updated, item.id, counts[index] ?? 0);
      });
      return updated;
    });
  };

  const clearRowRawInputs = (blockId?: string) => {
    if (blockId) {
      raw.clear(MATRIX_FIELDS.rowCount(blockId));
      raw.clear(MATRIX_FIELDS.rowSets(blockId));
      raw.clear(MATRIX_FIELDS.rowSetSize(blockId));
      return;
    }
    raw.clear(MATRIX_FIELDS.structure);
    raw.clearPrefix?.("COUNT:");
    raw.clearPrefix?.("SETS:");
    raw.clearPrefix?.("SET_SIZE:");
  };

  const handleResetDefault = () => {
    if (!defaultSnapshot) return;
    setPercentDraft({});
    setPercentTexts({});
    clearRowRawInputs();
    onChange((current) => resetMatrixSectionBlocks(current, defaultSnapshot));
  };

  const handleAddRow = (questionType: QuestionType) => {
    setPercentDraft({});
    setPercentTexts({});
    raw.clear(MATRIX_FIELDS.structure);
    onChange((current) => addMatrixBlock(current, questionType, t(`defaultRowName.${questionType}`)));
  };

  const handleRemoveRow = (blockId: string) => {
    setPercentDraft({});
    setPercentTexts({});
    clearRowRawInputs(blockId);
    raw.clear(MATRIX_FIELDS.structure);
    onChange((current) => removeMatrixBlock(current, blockId));
  };

  const handleChangeType = (block: QuestionBlock, nextType: QuestionType) => {
    clearRowRawInputs(block.id);
    const trimmed = block.name.trim();
    const shouldRename = trimmed === "" || autoNames.has(trimmed);
    onChange((current) =>
      changeMatrixBlockType(current, block.id, nextType, shouldRename ? { nextName: t(`defaultRowName.${nextType}`) } : {}),
    );
  };

  const ranges = useMemo(() => {
    const result: Array<{ from: number; to: number } | null> = [];
    let cursor = 1;
    for (const block of activeBlocks) {
      const count = block.questionCount ?? 0;
      if (count <= 0) {
        result.push(null);
      } else {
        result.push({ from: cursor, to: cursor + count - 1 });
        cursor += count;
      }
    }
    return result;
  }, [activeBlocks]);

  const rollupSummary = useMemo(() => {
    const byType = new Map<QuestionType, number>();
    for (const block of activeBlocks) {
      const count = block.questionCount ?? 0;
      if (count <= 0) continue;
      const type = block.questionType ?? "SINGLE_CHOICE";
      byType.set(type, (byType.get(type) ?? 0) + count);
    }
    if (byType.size === 0) return t("rollupEmpty");
    return [...byType.entries()].map(([type, count]) => t("rollupItem", { type: formatLabel(type), count })).join(" · ");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeBlocks, reference.questionTypes, t]);

  const allocated = structure.total;
  const targetTotal = total ?? allocated;
  const diff = allocated - targetTotal;
  const totalPct = targetTotal > 0 ? ratePercent(allocated, targetTotal) : 0;
  const structureError = errorOf(MATRIX_FIELDS.structure);
  const hasSetBlock = activeBlocks.some((block) => block.questionType === "QUESTION_SET");

  return (
    <section
      aria-label={t("title")}
      data-form-field={MATRIX_FIELDS.structure}
      data-structure-variant={compact ? "compact" : undefined}
      className="flex flex-col overflow-hidden rounded-lg border border-border bg-surface"
    >
      {/* Header Bước 1 + Công tắc Trộn lẫn giữa các loại câu + Mặc định của môn + Thêm dòng loại câu */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border bg-subtle/40 px-4 py-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <div className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-primary-subtle text-caption font-semibold text-primary"
            >
              1
            </span>
            <h3 className="text-title font-semibold text-text">{t("title")}</h3>
          </div>
          {!compact && <p className="text-caption text-text-muted">{t("hint")}</p>}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div
            title={t("mixSwitchHint")}
            className={cn(
              "inline-flex min-h-9 items-center gap-2 rounded-lg border px-2.5 py-1 transition-colors",
              mixed ? "border-primary/40 bg-primary-subtle text-primary" : "border-border bg-surface text-text-secondary",
            )}
          >
            <Switch
              id={mixSwitchId}
              checked={mixed}
              onCheckedChange={(checked) => onChange((current) => toggleSectionMixBlocks(current, checked))}
            />
            <label htmlFor={mixSwitchId} className="cursor-pointer text-caption font-semibold select-none">
              {t("mixSwitch")}
            </label>
          </div>

          {defaultSnapshot && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={templateLocked}
              title={t("resetDefaultHint")}
              onClick={handleResetDefault}
            >
              <RotateCcw />
              <span>{t("resetDefault")}</span>
            </Button>
          )}

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button type="button" variant="secondary" size="sm" disabled={templateLocked}>
                <Plus />
                <span>{t("addRow")}</span>
                <ChevronDown />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" aria-label={t("addMenuLabel")} className="w-80 max-w-[calc(100vw-2rem)]">
              {MATRIX_FORMAT_ORDER.map((code) => (
                <DropdownMenuItem
                  key={code}
                  onSelect={() => handleAddRow(code)}
                  className="h-auto min-h-11 flex-col items-start gap-0.5 py-2"
                >
                  <span className="flex w-full items-center justify-between gap-2 font-medium text-text">
                    <span>+ {formatLabel(code)}</span>
                    {code === "ESSAY" && <Badge>{t("manualBadge")}</Badge>}
                  </span>
                  <span className="text-caption font-normal text-text-muted">{t(`formatDesc.${code}`)}</span>
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Dòng Info giải thích rõ cơ chế đảo câu lẻ / câu chùm và cờ trộn chung toàn môn */}
      <div
        role="status"
        data-testid="matrix-shuffle-banner"
        data-mixed={mixed ? "true" : "false"}
        className={cn(
          "flex items-start gap-2.5 border-b px-4 py-2.5 text-caption",
          mixed ? "border-info/30 bg-info-bg text-text" : "border-border bg-surface text-text-secondary",
        )}
      >
        <span
          aria-hidden="true"
          className={cn(
            "mt-0.5 inline-flex size-5 shrink-0 items-center justify-center rounded-full",
            mixed ? "bg-info text-on-primary" : "bg-subtle text-text-secondary",
          )}
        >
          {mixed ? <Shuffle className="size-3" /> : <Info className="size-3.5" />}
        </span>
        <p className="min-w-0 leading-relaxed">
          <strong className={cn("font-semibold", mixed ? "text-info" : "text-text")}>{mixed ? t("shuffleBannerMixedLead") : t("shuffleBannerDefaultLead")}</strong>{" "}
          <span>{mixed ? t("shuffleBannerMixedBody") : t("shuffleBannerDefaultBody")}</span>
        </p>
      </div>

      {/* Bảng thành phần câu hỏi cộng thẳng từ trên xuống */}
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-body">
          <thead>
            <tr className="border-b border-border bg-subtle/30 text-left text-caption font-semibold text-text-secondary">
              <th scope="col" className="w-24 px-3 py-2.5 whitespace-nowrap">
                {t("colOrder")}
              </th>
              <th scope="col" className="min-w-44 px-3 py-2.5">
                {t("colName")}
              </th>
              <th scope="col" className="w-56 min-w-48 px-3 py-2.5">
                {t("colType")}
              </th>
              <th scope="col" className="min-w-60 px-3 py-2.5">
                {t("colConfig")}
              </th>
              <th scope="col" className="w-24 px-3 py-2.5 text-right whitespace-nowrap">
                {t("colCount")}
              </th>
              <th scope="col" className="w-20 px-3 py-2.5 text-right whitespace-nowrap">
                {t("colPercent")}
              </th>
              <th scope="col" className="w-28 px-3 py-2.5 text-right whitespace-nowrap">
                {t("colActions")}
              </th>
            </tr>
          </thead>

          <tbody className="divide-y divide-border">
            {activeBlocks.map((block, index) => {
              const effectiveType: QuestionType = block.questionType ?? "SINGLE_CHOICE";
              const isSet = effectiveType === "QUESTION_SET";
              const flexible = isSet && isFlexibleSet(block);
              const isFixedSet = isSet && !flexible;
              const blockCount = block.questionCount ?? 0;
              const rawName = block.name.trim();
              const baseLabel = locale === "vi" && rawName ? rawName : formatLabel(effectiveType);
              const sameLabelBlocks = activeBlocks.filter((item) => {
                const itemType: QuestionType = item.questionType ?? "SINGLE_CHOICE";
                const itemName = item.name.trim();
                return (locale === "vi" && itemName ? itemName : formatLabel(itemType)) === baseLabel;
              });
              const displayName = sameLabelBlocks.length > 1 ? `${baseLabel} (${sameLabelBlocks.indexOf(block) + 1})` : baseLabel;
              const range = ranges[index] ?? null;
              const sharePct = total && total > 0 ? ratePercent(blockCount, total) : null;
              const singleRowLocked = templateLocked && activeBlocks.length === 1;
              const countField = MATRIX_FIELDS.rowCount(block.id);
              const setsField = MATRIX_FIELDS.rowSets(block.id);
              const setSizeField = MATRIX_FIELDS.rowSetSize(block.id);

              return (
                <tr key={block.id} data-matrix-row={block.id} className="hover:bg-subtle/20">
                  {/* Cột 1: Vị trí câu */}
                  <td className="px-3 py-2.5 align-top whitespace-nowrap">
                    {mixed ? (
                      <span className="inline-flex h-6 items-center rounded-md bg-info-bg px-2 text-caption font-semibold text-info">
                        {t("mixBadge")}
                      </span>
                    ) : range === null ? (
                      <span className="inline-flex h-6 min-w-12 items-center justify-center rounded-md bg-subtle px-2 text-caption font-medium text-text-muted tabular-nums">
                        {t("posEmpty")}
                      </span>
                    ) : (
                      <span className="inline-flex h-6 min-w-12 items-center justify-center rounded-md bg-subtle px-2 text-caption font-semibold text-text-secondary tabular-nums">
                        {range.from === range.to ? t("posSingle", { num: range.from }) : t("posRange", { from: range.from, to: range.to })}
                      </span>
                    )}
                  </td>

                  {/* Cột 2: Tên nhóm trên đề */}
                  <td data-exam-content className="px-3 py-2.5 align-top">
                    <Input
                      value={block.name}
                      aria-label={t("rowNameAria", { index: index + 1 })}
                      onChange={(event) => onChange((current) => updateMatrixBlockName(current, block.id, event.target.value))}
                      className="h-9 min-w-36 font-medium"
                    />
                  </td>

                  {/* Cột 3: Loại câu hỏi */}
                  <td className="px-3 py-2.5 align-top">
                    <Select
                      value={effectiveType}
                      options={selectOptions}
                      disabled={block.answerTypeLocked || templateLocked}
                      aria-label={t("rowTypeAria", { name: displayName })}
                      onValueChange={(value) => handleChangeType(block, value as QuestionType)}
                      className="min-h-9 py-1"
                    />
                  </td>

                  {/* Cột 4: Quy cách lấy câu */}
                  <td className="px-3 py-2.5 align-top">
                    {isSet ? (
                      <div className="flex flex-col gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <div
                            role="radiogroup"
                            aria-label={t("setModeAria", { name: displayName })}
                            className="inline-flex rounded-lg border border-border bg-subtle p-0.5"
                          >
                            <button
                              type="button"
                              role="radio"
                              aria-checked={!flexible}
                              disabled={templateLocked}
                              onClick={() => {
                                clearRowRawInputs(block.id);
                                onChange((current) => toggleMatrixSetMode(current, block.id, "FIXED"));
                              }}
                              className={cn(
                                "rounded-md px-2.5 py-1 text-caption font-medium transition-colors disabled:opacity-50",
                                focusRing,
                                !flexible ? "bg-surface font-semibold text-text shadow-xs" : "text-text-muted hover:text-text",
                              )}
                            >
                              {t("setModeFixed")}
                            </button>
                            <button
                              type="button"
                              role="radio"
                              aria-checked={flexible}
                              disabled={templateLocked}
                              onClick={() => {
                                clearRowRawInputs(block.id);
                                onChange((current) => toggleMatrixSetMode(current, block.id, "FLEXIBLE"));
                              }}
                              className={cn(
                                "rounded-md px-2.5 py-1 text-caption font-medium transition-colors disabled:opacity-50",
                                focusRing,
                                flexible ? "bg-surface font-semibold text-text shadow-xs" : "text-text-muted hover:text-text",
                              )}
                            >
                              {t("setModeFlexible")}
                            </button>
                          </div>
                          <span className="text-caption text-text-muted">· {t("setKeepOrderNote")}</span>
                        </div>

                        {!flexible ? (
                          <div className="flex flex-wrap items-start gap-1.5">
                            <MatrixInlineNumber
                              value={block.setCount ?? 1}
                              ariaLabel={t("rowSetsAria", { name: displayName })}
                              field={setsField}
                              blockId={block.id}
                              raw={raw}
                              error={errorOf(setsField)}
                              readOnly={templateLocked}
                              widthClass="w-16"
                              onValid={(setCount) => onChange((current) => updateMatrixSetDimensions(current, block.id, { setCount }))}
                            />
                            <span className="pt-2 text-caption text-text-secondary">{t("setTimesUnit")}</span>
                            <MatrixInlineNumber
                              value={block.setSize ?? 3}
                              ariaLabel={t("rowSetSizeAria", { name: displayName })}
                              field={setSizeField}
                              raw={raw}
                              error={errorOf(setSizeField)}
                              readOnly={templateLocked}
                              widthClass="w-16"
                              onValid={(setSize) => onChange((current) => updateMatrixSetDimensions(current, block.id, { setSize }))}
                            />
                            <span className="pt-2 text-caption text-text-secondary">{t("setPerUnit")}</span>
                          </div>
                        ) : (
                          <p className="text-caption text-text-muted">{t("flexibleRuleHint")}</p>
                        )}
                        {renderRoleFooter?.(block)}
                      </div>
                    ) : (
                      <div className="flex flex-col gap-1 pt-1">
                        <div className="flex flex-wrap items-center gap-2 text-caption text-text-muted">
                          <span>{t(`formatDesc.${effectiveType}`)}</span>
                          {effectiveType === "ESSAY" && <Badge>{t("manualBadge")}</Badge>}
                          {effectiveType === "SHORT_ANSWER" && limits && limits.shortAnswerMin > 0 && (
                            <span className="font-medium text-text-secondary">({t("fillMin", { min: limits.shortAnswerMin })})</span>
                          )}
                        </div>
                        {renderRoleFooter?.(block)}
                      </div>
                    )}
                  </td>

                  {/* Cột 5 & 6: Số câu và Tỷ lệ */}
                  {isFixedSet ? (
                    <>
                      <td className="px-3 py-2.5 text-right align-top">
                        <Input
                          value={String(blockCount)}
                          readOnly
                          aria-readonly="true"
                          aria-label={t("rowCountAria", { name: displayName })}
                          className={cn("ml-auto h-9 w-20 px-2.5 text-right tabular-nums", LOCKED_FIELD)}
                        />
                      </td>
                      <td className="px-3 py-2.5 pt-4 text-right align-top text-caption font-medium text-text-secondary tabular-nums">
                        {sharePct !== null ? tCommon("share", { percent: sharePct }) : "—"}
                      </td>
                    </>
                  ) : twoColumns ? (
                    <MatrixTwoColumnCells
                      count={blockCount}
                      total={total}
                      countAria={t("rowCountAria", { name: displayName })}
                      percentAria={t("rowPercentAria", { name: displayName })}
                      field={countField}
                      blockId={block.id}
                      readOnly={singleRowLocked}
                      raw={raw}
                      error={errorOf(countField)}
                      percentTextValue={percentTexts[block.id] ?? ""}
                      onPercentText={(nextText) => setPercentTexts((prev) => ({ ...prev, [block.id]: nextText }))}
                      onEdit={(edit) => handleTwoColumnEdit(block.id, edit)}
                    />
                  ) : (
                    <>
                      <td className="px-3 py-2.5 text-right align-top">
                        <div className="inline-flex flex-col items-end gap-1">
                          <MatrixInlineNumber
                            value={blockCount}
                            ariaLabel={t("rowCountAria", { name: displayName })}
                            field={countField}
                            blockId={block.id}
                            raw={raw}
                            error={errorOf(countField)}
                            readOnly={singleRowLocked}
                            onValid={(nextCount) => onChange((current) => updateMatrixBlockCount(current, block.id, nextCount))}
                          />
                          {singleRowLocked && <LockReason code="templateTotal" />}
                        </div>
                      </td>
                      <td className="px-3 py-2.5 pt-4 text-right align-top text-caption font-medium text-text-secondary tabular-nums">
                        {sharePct !== null ? tCommon("share", { percent: sharePct }) : "—"}
                      </td>
                    </>
                  )}

                  {/* Cột 7: Thao tác ↑ ↓ ✕ */}
                  <td className="px-3 py-2.5 text-right align-top whitespace-nowrap">
                    <div className="inline-flex items-center justify-end gap-0.5">
                      <IconButton
                        icon={<ArrowUp />}
                        aria-label={t("moveUpAria", { name: displayName })}
                        tooltip={mixed ? t("moveDisabledMixed") : t("moveUpAria", { name: displayName })}
                        disabled={mixed || index === 0}
                        className="size-8"
                        onClick={() => onChange((current) => moveMatrixBlock(current, block.id, -1))}
                      />
                      <IconButton
                        icon={<ArrowDown />}
                        aria-label={t("moveDownAria", { name: displayName })}
                        tooltip={mixed ? t("moveDisabledMixed") : t("moveDownAria", { name: displayName })}
                        disabled={mixed || index === activeBlocks.length - 1}
                        className="size-8"
                        onClick={() => onChange((current) => moveMatrixBlock(current, block.id, 1))}
                      />
                      <IconButton
                        tone="danger"
                        icon={<X />}
                        aria-label={t("deleteRowAria", { name: displayName })}
                        tooltip={activeBlocks.length <= 1 ? t("deleteDisabledLast") : t("deleteRowAria", { name: displayName })}
                        disabled={activeBlocks.length <= 1 || templateLocked}
                        className="size-8"
                        onClick={() => handleRemoveRow(block.id)}
                      />
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>

          <tfoot>
            <tr className="border-t-2 border-border bg-subtle/40 font-semibold text-text">
              <th scope="row" colSpan={4} className="px-3 py-2.5 text-left">
                <div className="flex flex-wrap items-center gap-2.5">
                  <span className="text-label font-semibold text-text">{t("footerRows", { count: activeBlocks.length })}</span>
                  {total !== null &&
                    (diff === 0 ? (
                      <StatusChip tone="success">{t("gapMatch", { total })}</StatusChip>
                    ) : diff < 0 ? (
                      <StatusChip tone="warning">{t("gapUnder", { count: Math.abs(diff) })}</StatusChip>
                    ) : (
                      <StatusChip tone="danger">{t("gapOver", { count: diff })}</StatusChip>
                    ))}
                </div>
              </th>
              <td className="px-3 py-2.5 text-right font-semibold tabular-nums whitespace-nowrap">
                {total !== null ? `${allocated} / ${total}` : String(allocated)}
              </td>
              <td className="px-3 py-2.5 text-right text-caption font-semibold tabular-nums whitespace-nowrap">
                {total !== null && total > 0 && totalPct !== null ? tCommon("share", { percent: totalPct }) : "—"}
              </td>
              <td className="px-3 py-2.5" />
            </tr>
          </tfoot>
        </table>
      </div>

      {/* Lỗi tổng thể của bảng cấu trúc (ví dụ tổng % chưa đủ 100% ở V-ACT hoặc thiếu dòng bắt buộc) */}
      {structureError && (
        <p role="alert" className="border-t border-border bg-danger-bg/40 px-4 py-2 text-caption font-medium text-danger">
          {structureError}
        </p>
      )}

      {/* Thanh tóm tắt cộng gộp theo định dạng câu */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border bg-subtle/20 px-4 py-2 text-caption text-text-secondary">
        <span>
          <strong className="font-semibold text-text">{t("rollupPrefix")}</strong> {rollupSummary}
        </span>
        <span className="text-text-muted">{mixed ? t("stripHintMixed") : t("stripHintDefault")}</span>
      </div>

      {/* Điều kiện nguồn chùm (QDA, BCA, V-ACT) */}
      {entry.setSource && hasSetBlock && (
        <div className="border-t border-border p-4">
          <MatrixSetSource {...props} />
        </div>
      )}
    </section>
  );
}
