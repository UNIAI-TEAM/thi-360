"use client";

import {
  Badge,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import { type FormEvent, useId, useRef, useState } from "react";
import type { CombinationOption } from "@/features/content/types";
import type { ExamPart } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { generateCombinations, type GeneratedCombination, optionQuestionCount } from "./exam-structure-draft";
import type { StructureNames } from "./exam-structure-issues";

export interface CombinationGeneratorDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  part: ExamPart;
  /** Môn có thể ghép (các môn xuất hiện trong tổ hợp của kỳ thi, theo thứ tự danh mục). */
  subjectPool: readonly string[];
  /** Số môn mỗi tổ hợp mặc định (HSA: 3). */
  defaultSize: number;
  catalog: readonly CombinationOption[];
  names: StructureNames;
  onGenerate: (combinations: GeneratedCombination[]) => void;
}

/**
 * Sinh tổ hợp "chọn k trong n môn" (HSA: 5 môn chọn 3 được 10 tổ hợp), bỏ bớt tổ hợp không mở cho học viên rồi gắn vào
 * phần tự chọn. Tổ hợp trùng danh mục dùng mã danh mục; tổ hợp mới nhận mã tạm, chờ nhập vào Cấu hình chung.
 */
export function CombinationGeneratorDialog(props: CombinationGeneratorDialogProps) {
  const t = useTranslations("exams.combinations.generator");
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent size="lg" closeLabel={t("cancel")} className="flex max-h-[90vh] flex-col overflow-hidden">
        <DialogHeader className="shrink-0">
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description", { count: optionQuestionCount(props.part) })}</DialogDescription>
        </DialogHeader>
        {props.open && <GeneratorForm {...props} />}
      </DialogContent>
    </Dialog>
  );
}

function GeneratorForm({
  onOpenChange,
  part,
  subjectPool,
  defaultSize,
  catalog,
  names,
  onGenerate,
}: CombinationGeneratorDialogProps) {
  const t = useTranslations("exams.combinations.generator");
  const formId = useId();
  const errorId = useId();
  const listRef = useRef<HTMLFieldSetElement>(null);
  const [subjects, setSubjects] = useState<string[]>(() => [...subjectPool]);
  const [size, setSize] = useState(defaultSize);
  // Tổ hợp bị bỏ chọn, theo môn (mã tạm đổi khi đổi môn hoặc số môn).
  const [excluded, setExcluded] = useState<Set<string>>(() => new Set());
  const [submitted, setSubmitted] = useState(false);

  const ordered = subjectPool.filter((code) => subjects.includes(code));
  const sizeInvalid = !Number.isInteger(size) || size < 1 || size > ordered.length;
  const generated = sizeInvalid ? [] : generateCombinations(ordered, size, catalog, part);
  const keyOf = (combination: GeneratedCombination) => combination.subjectCodes.slice().sort().join("+");
  const selected = generated.filter((combination) => !combination.attached && !excluded.has(keyOf(combination)));
  const noneSelected = submitted && selected.length === 0;

  const toggleSubject = (code: string, checked: boolean) =>
    setSubjects((current) => (checked ? [...current, code] : current.filter((item) => item !== code)));
  const toggleCombination = (combination: GeneratedCombination, checked: boolean) =>
    setExcluded((current) => {
      const next = new Set(current);
      if (checked) next.delete(keyOf(combination));
      else next.add(keyOf(combination));
      return next;
    });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    if (sizeInvalid || selected.length === 0) {
      listRef.current?.querySelector<HTMLElement>("button:not([disabled])")?.focus();
      return;
    }
    onGenerate(selected);
    onOpenChange(false);
  };

  return (
    <form id={formId} onSubmit={submit} noValidate className="flex min-h-0 flex-1 flex-col gap-4">
      <div className="-mx-1 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-1 scroll-shadows-y">
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-field-label font-medium text-text-secondary">{t("subjectsLegend")}</legend>
          <div className="flex flex-wrap gap-x-5 gap-y-2">
            {subjectPool.map((code) => {
              const id = `${formId}-subject-${code}`;
              return (
                <div key={code} className="flex items-center gap-2">
                  <Checkbox
                    id={id}
                    name="subjects"
                    value={code}
                    checked={subjects.includes(code)}
                    onCheckedChange={(checked) => toggleSubject(code, checked === true)}
                  />
                  <label htmlFor={id} className="cursor-pointer text-body text-text">
                    {names.subject(code)}
                  </label>
                </div>
              );
            })}
          </div>
        </fieldset>

        <FormField
          label={t("size")}
          required
          hint={t("sizeHint", { total: ordered.length })}
          error={sizeInvalid ? t("sizeInvalid", { total: ordered.length }) : undefined}
          className="max-w-xs"
        >
          {(controlProps) => <BlockNumberInput {...controlProps} name="size" value={size} onValueChange={setSize} />}
        </FormField>

        <fieldset
          ref={listRef}
          className="flex flex-col gap-2"
          aria-describedby={noneSelected ? errorId : undefined}
          aria-invalid={noneSelected || undefined}
        >
          <legend className="mb-1 text-field-label font-medium text-text-secondary">
            {t("listLegend", { total: generated.length, selected: selected.length })}
          </legend>
          {generated.length === 0 ? (
            <p className="text-body text-text-muted">{t("listEmpty")}</p>
          ) : (
            <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(16rem,100%),1fr))] gap-2">
              {generated.map((combination) => {
                const id = `${formId}-combo-${keyOf(combination)}`;
                const checked = !combination.attached && !excluded.has(keyOf(combination));
                return (
                  <li key={keyOf(combination)} className="flex items-start gap-2.5 rounded-md border border-border p-2.5">
                    <Checkbox
                      id={id}
                      checked={checked}
                      disabled={combination.attached}
                      aria-describedby={`${id}-meta`}
                      onCheckedChange={(value) => toggleCombination(combination, value === true)}
                      className="mt-0.5"
                    />
                    <div className="flex min-w-0 flex-col gap-1">
                      <label htmlFor={id} className="cursor-pointer text-body font-semibold text-text">
                        {combination.code}
                      </label>
                      <span className="text-caption text-text-secondary">
                        {combination.subjectCodes.map((code) => names.subject(code)).join(" – ")}
                      </span>
                      <span id={`${id}-meta`} className="flex flex-wrap gap-1.5">
                        {combination.attached ? (
                          <Badge>{t("attached")}</Badge>
                        ) : !combination.catalogued ? (
                          <Badge>{t("temporaryCode")}</Badge>
                        ) : null}
                      </span>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
          {noneSelected && !sizeInvalid && (
            <p id={errorId} role="alert" className="text-caption text-danger">
              {t("required")}
            </p>
          )}
        </fieldset>
        <p className="text-caption text-text-muted">{t("note")}</p>
      </div>
      <DialogFooter className="shrink-0">
        <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
          {t("cancel")}
        </Button>
        <Button type="submit">{t("submit", { count: selected.length })}</Button>
      </DialogFooter>
    </form>
  );
}
