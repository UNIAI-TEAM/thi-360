"use client";

import { Badge, Checkbox } from "@thi360/ui";
import { Info } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId } from "react";
import { allocateSubjectCounts } from "../subject-choice";
import type { ExamPartOption } from "../types";
import { useLookup } from "./exam-review-shared";

export interface ExamPreviewSubjectsProps {
  option: ExamPartOption;
  /** Mã môn đang chọn, theo thứ tự cấu trúc. */
  value: readonly string[];
  onChange: (codes: string[]) => void;
}

export function ExamPreviewSubjects({ option, value, onChange }: ExamPreviewSubjectsProps) {
  const t = useTranslations("exams.preview.subjects");
  const lookup = useLookup();
  const hintId = useId();
  const choose = option.chooseSubjects ?? 0;
  const full = value.length >= choose;
  const picked = option.sections.filter((section) => value.includes(section.subjectCode));
  const allocations = allocateSubjectCounts(
    picked.map((section) => section.choiceGroup),
    option.countsByPick ?? [],
  );

  function toggle(code: string, checked: boolean) {
    onChange(
      option.sections
        .map((section) => section.subjectCode)
        .filter((subjectCode) => (subjectCode === code ? checked : value.includes(subjectCode))),
    );
  }

  let hint: string | null = null;
  if (!full) {
    hint = t("remaining", { picked: value.length, choose });
  } else if (allocations.length === 1) {
    const counts = allocations[0]!;
    hint = t("split", {
      list: picked.map((section, index) => `${lookup.subject(section.subjectCode)} ${counts[index]}`).join(" · "),
    });
  } else if (allocations.length > 1) {
    hint = t("splitSeeded", { choose, low: Math.min(...(option.countsByPick ?? [])) });
  }

  return (
    <fieldset aria-describedby={hintId} className="m-0 flex min-w-0 flex-col gap-2 border-0 p-0">
      <legend className="mb-2 p-0 text-label font-semibold text-text">{t("legend", { count: choose })}</legend>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(13rem,100%),1fr))] gap-2">
        {option.sections.map((section) => {
          const checked = value.includes(section.subjectCode);
          return (
            <label
              key={section.id}
              className="flex min-h-10 items-center gap-2.5 rounded-lg border border-border bg-surface px-3 py-2 text-body text-text has-[:disabled]:cursor-not-allowed has-[:disabled]:text-text-muted"
            >
              <Checkbox
                checked={checked}
                disabled={!checked && full}
                onCheckedChange={(next) => toggle(section.subjectCode, next === true)}
              />
              <span className="min-w-0 flex-1">{lookup.subject(section.subjectCode)}</span>
              {section.choiceGroup && <Badge>{t(`group.${section.choiceGroup}`)}</Badge>}
            </label>
          );
        })}
      </div>
      <div id={hintId} className="flex flex-col gap-1 text-body text-text-muted">
        {hint && (
          <p className="flex items-start gap-2">
            <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
            {hint}
          </p>
        )}
        {full && <p className="ps-6 text-caption">{t("full", { choose })}</p>}
      </div>
    </fieldset>
  );
}
