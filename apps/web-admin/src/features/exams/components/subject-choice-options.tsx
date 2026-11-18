"use client";

import { Badge, Button, cn, focusRing, StatusChip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId } from "react";
import type { ChoiceGroup, ExamPartOption, ExamSectionState } from "../types";
import { allocateSubjectCounts } from "../subject-choice";
import type { StructureNames } from "./exam-structure-issues";
import { SECTION_STATE_TONES } from "./exam-structure-list";

export interface SubjectChoiceOptionsProps {
  option: ExamPartOption;
  names: StructureNames;
  onSelectSection: (sectionId: string) => void;
  /** Trạng thái kiểm tra theo môn (H2, R-35); `null` hoặc bỏ trống: không hiện cột trạng thái. */
  sectionStates?: readonly ExamSectionState[] | null;
}

/** Ba ví dụ chia số câu của B10 (hai môn cùng nhóm, hoặc cả ba môn cùng nhóm tự nhiên). */
const EXAMPLES: ChoiceGroup[][] = [
  ["NATURAL", "NATURAL", "SOCIAL"],
  ["NATURAL", "SOCIAL", "SOCIAL"],
  ["NATURAL", "NATURAL", "NATURAL"],
];

/**
 * Phương án "chọn môn" của phần tự chọn (HSA Khoa học, spec D25): một phương án có 5 môn, mỗi môn soạn một lần; học viên
 * chọn 3 môn. Số câu từng môn theo quy tắc chia của B10, chỉ đọc ở đây (không còn 10 tổ hợp KH-n, không sinh tổ hợp).
 */
export function SubjectChoiceOptions({ option, names, sectionStates, onSelectSection }: SubjectChoiceOptionsProps) {
  const t = useTranslations("exams.subjectChoice");
  const listT = useTranslations("exams.structure.list");
  const headingId = useId();
  const picks = option.chooseSubjects ?? 0;
  const counts = option.countsByPick ?? [];
  const high = counts.length ? Math.max(...counts) : 0;
  const low = counts.length ? Math.min(...counts) : 0;
  const examples =
    picks === 3 && counts.length === 3
      ? EXAMPLES.map((groups) => ({ groups, cases: allocateSubjectCounts(groups, counts) })).filter((item) => item.cases.length > 0)
      : [];

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 rounded-md border border-border p-4">
      <div className="flex flex-col gap-0.5">
        <h3 id={headingId} className="text-title font-semibold text-text">
          {t("title", { name: option.name ?? names.subject(option.code) })}
        </h3>
        <p className="text-body font-medium text-text">{t("pick", { count: picks, total: option.sections.length })}</p>
        <p className="text-caption text-text-muted">{t("editOnce")}</p>
      </div>

      <div className="flex flex-col gap-1.5 rounded-md bg-subtle px-3 py-2.5">
        <p className="text-label font-semibold text-text">{t("ruleTitle")}</p>
        <p className="text-body text-text-secondary">{t("rule", { high, low })}</p>
        {examples.length > 0 && (
          <ul className="flex flex-col gap-0.5 text-caption text-text-muted">
            {examples.map(({ groups, cases }) => (
              <li key={groups.join()}>
                {t("example", {
                  groups: groups.map((group) => t(`choiceGroupShort.${group}`)).join(" + "),
                  cases: cases.map((values) => values.join(" + ")).join(` ${t("or")} `),
                })}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div role="region" tabIndex={0} aria-label={t("caption")} className={cn("overflow-x-auto rounded-md border border-border", focusRing)}>
        <table className="w-full border-separate border-spacing-0 text-body">
          <caption className="sr-only">{t("caption")}</caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("subject")}</th>
              <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("group")}</th>
              <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("groups")}</th>
              {sectionStates && <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("status")}</th>}
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold"><span className="sr-only">{t("actions")}</span></th>
            </tr>
          </thead>
          <tbody>
            {option.sections.map((section) => {
              const subject = names.subject(section.subjectCode);
              // Máy chủ đã trả trạng thái nhưng môn chưa có dòng nào: môn chưa thiết lập nhóm lấy câu thì "Chưa hoàn tất".
              const state = sectionStates
                ? (sectionStates.find((item) => item.sectionId === section.id)?.state ??
                  (section.blocks.length === 0 ? "INCOMPLETE" : null))
                : null;
              return (
                <tr key={section.id}>
                  <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">{subject}</th>
                  <td className="border-b border-border px-2 py-2">
                    {section.choiceGroup ? <Badge className="font-medium">{t(`choiceGroup.${section.choiceGroup}`)}</Badge> : "—"}
                  </td>
                  <td className="border-b border-border px-2 py-2 text-right tabular-nums">{section.blocks.length}</td>
                  {sectionStates && (
                    <td className="border-b border-border px-2 py-2">
                      {state ? <StatusChip tone={SECTION_STATE_TONES[state]}>{listT(`state.${state}`)}</StatusChip> : "—"}
                    </td>
                  )}
                  <td className="border-b border-border px-3 py-1 text-right">
                    <Button type="button" variant="secondary" size="sm" aria-label={t("editSubject", { subject })} onClick={() => onSelectSection(section.id)}>
                      {t("edit")}
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
