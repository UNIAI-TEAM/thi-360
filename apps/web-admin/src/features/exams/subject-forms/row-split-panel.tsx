"use client";

import { Button, Input, Label, Select } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { isSetBlock } from "../matrix";
import type { QuestionBlock } from "../types";
import type { RawInputStore } from "./raw-input-store";
import { SPLIT_FIELD } from "./row-fields";
import { evenSplit, splitChoices, splitIssues, type SplitIssue } from "./row-split";

export interface RowSplitPanelProps {
  block: QuestionBlock;
  /** Số đơn vị của dòng cha (câu, hoặc chùm với dòng chùm). */
  units: number;
  sectionId: string;
  rawInputs: RawInputStore;
  onApply: (parts: number[]) => void;
  onCancel: () => void;
}

const parse = (text: string): number => (/^\d+$/.test(text.trim()) ? Number(text.trim()) : Number.NaN);

/**
 * Khối "Tách dòng" của màn sửa dòng (FTM R-7, AC 51): chọn số dòng (2 tới số đơn vị của dòng cha, nên 4 chùm không thành 5 dòng), chia đều
 * sẵn, sửa được số của từng dòng con. Cách chia sai (dòng con không phải số nguyên dương, tổng khác dòng cha) hiện lỗi tại dòng và ghi
 * vào kho nhập thô để chặn mọi đường lưu cho tới khi sửa hoặc hủy (R-15); nút Tách chỉ bật khi cách chia hợp lệ.
 */
export function RowSplitPanel({ block, units, sectionId, rawInputs, onApply, onCancel }: RowSplitPanelProps) {
  const t = useTranslations("exams.subjectForm.rows.split");
  const countId = useId();
  const stored = rawInputs.get(sectionId, block.id, SPLIT_FIELD);
  const [texts, setTexts] = useState(() => stored ? stored.text.split(" + ") : evenSplit(units, 2).map(String));
  const parts = texts.length;
  const set = isSetBlock(block);
  const issues = splitIssues(units, texts.map(parse));
  const unit = set ? "set" : "single";

  const publish = (next: readonly string[]) => {
    const found = splitIssues(units, next.map(parse));
    if (found.length === 0) rawInputs.clear(sectionId, block.id, SPLIT_FIELD);
    else rawInputs.set({ sectionId, blockId: block.id, field: SPLIT_FIELD, text: next.join(" + "), code: found.some((issue) => issue.code === "NOT_POSITIVE") ? "splitNotPositive" : "splitTotal" });
  };
  const choose = (value: string) => {
    const next = Number(value);
    const even = evenSplit(units, next).map(String);
    setTexts(even);
    publish(even);
  };
  const edit = (index: number, text: string) => {
    const next = texts.map((item, position) => (position === index ? text : item));
    setTexts(next);
    publish(next);
  };
  const apply = () => {
    rawInputs.clear(sectionId, block.id, SPLIT_FIELD);
    onApply(texts.map(parse));
  };
  const totalIssue = issues.find((issue): issue is Extract<SplitIssue, { code: "TOTAL" }> => issue.code === "TOTAL");

  return (
    <section aria-label={t("title", { name: block.name })} data-form-field={SPLIT_FIELD} data-row-split className="flex flex-col gap-3 rounded-md border border-border bg-subtle/40 p-4">
      <div className="flex flex-col gap-0.5">
        <h4 className="text-label font-semibold text-text">{t("title", { name: block.name })}</h4>
        <p className="text-caption text-text-muted">{t("hint", { units, unit })}</p>
      </div>
      <div className="flex max-w-xs flex-col gap-1.5">
        <Label htmlFor={countId}>{t("partsLabel")}</Label>
        <Select
          id={countId}
          aria-label={t("partsLabel")}
          value={String(parts)}
          onValueChange={choose}
          options={splitChoices(units).map((value) => ({ value: String(value), label: t("partsOption", { count: value }) }))}
        />
      </div>
      <ul className="flex flex-col gap-2">
        {texts.map((text, index) => {
          const issue = issues.find((item) => item.code === "NOT_POSITIVE" && item.index === index);
          const value = parse(text);
          return (
            <li key={index} className="flex flex-col gap-1" data-split-child={index + 1}>
              <span className="flex flex-wrap items-center gap-2">
                <Input
                  aria-label={t("childAria", { index: index + 1, unit })}
                  aria-invalid={issue ? true : undefined}
                  inputMode="numeric"
                  autoComplete="off"
                  value={text}
                  onChange={(event) => edit(index, event.target.value)}
                  className="w-24 text-right tabular-nums"
                />
                <span className="text-caption text-text-muted">
                  {set ? t("childSets", { size: block.setSize ?? 0, count: Number.isNaN(value) ? 0 : value * (block.setSize ?? 0) }) : t("childSingle")}
                </span>
              </span>
              {issue && (
                <p role="alert" className="text-caption text-danger">
                  {t("errors.notPositive", { index: index + 1, unit })}
                </p>
              )}
            </li>
          );
        })}
      </ul>
      {totalIssue && (
        <p role="alert" className="text-caption text-danger">
          {t("errors.total", { total: totalIssue.total, units: totalIssue.units, unit })}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" disabled={issues.length > 0} onClick={apply}>
          {t("apply")}
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => {
            rawInputs.clear(sectionId, block.id, SPLIT_FIELD);
            onCancel();
          }}
        >
          {t("cancel")}
        </Button>
      </div>
    </section>
  );
}
