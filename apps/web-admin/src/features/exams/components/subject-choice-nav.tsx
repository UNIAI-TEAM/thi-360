"use client";

import { Button } from "@thi360/ui";
import { ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ExamPartOption } from "../types";
import type { StructureNames } from "./exam-structure-issues";
import { optionLabel } from "./exam-structure-issues";

export interface SubjectChoiceNavProps {
  option: ExamPartOption;
  sectionId: string;
  names: StructureNames;
  onOpenOption: () => void;
  onOpenSection: (sectionId: string) => void;
}

/**
 * Thanh chuyển môn ở đầu màn một môn của phương án chọn môn (HSA Khoa học, R-35, bản vẽ `KhoaHocHSA2B`): vị trí của môn
 * trong 5 môn, về tổng quan, sang môn tiếp theo. Còn thay đổi chưa lưu thì việc chuyển đi qua hộp thoại ba nút.
 */
export function SubjectChoiceNav({ option, sectionId, names, onOpenOption, onOpenSection }: SubjectChoiceNavProps) {
  const t = useTranslations("exams.structure.science");
  const index = option.sections.findIndex((section) => section.id === sectionId);
  if (index < 0) return null;
  const next = option.sections[index + 1];
  const name = optionLabel(option, names);

  return (
    <nav
      aria-label={t("navLabel", { name })}
      data-testid="subject-choice-nav"
      className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border px-3 py-2.5"
    >
      <span className="text-caption text-text-muted">
        {t("position", { name, index: index + 1, total: option.sections.length })}
      </span>
      <span className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onOpenOption}>
          {t("overview", { name })}
        </Button>
        {next && (
          <Button type="button" variant="secondary" size="sm" onClick={() => onOpenSection(next.id)}>
            <span>{t("next", { subject: names.subject(next.subjectCode) })}</span>
            <ArrowRight aria-hidden />
          </Button>
        )}
      </span>
    </nav>
  );
}
