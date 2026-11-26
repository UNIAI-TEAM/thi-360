"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useCallback } from "react";
import type { BlockIssue, MatrixDimension, StructureIssue } from "../matrix";
import type { ExamPart, ExamPartOption } from "../types";
import type { StructureSelection } from "./exam-structure-draft";

export interface StructureNames {
  subject: (code: string) => string;
  combination: (code: string) => string;
}

export function optionLabel(
  option: { kind: ExamPartOption["kind"]; code: string; name?: string | null },
  names: StructureNames,
): string {
  if (option.kind === "BRANCH") return option.name ?? option.code;
  if (option.kind === "SUBJECT") return names.subject(option.code);
  // Phương án chọn môn (HSA Khoa học): tên của phương án, không phải mã tổ hợp.
  if (option.kind === "SUBJECT_CHOICE") return option.name ?? option.code;
  // Tổ hợp: tên theo danh mục (đúng ngôn ngữ đang chọn) trước; tổ hợp sinh từ "chọn k trong n" chưa có trong danh mục dùng tên lưu của phương án.
  const catalogName = names.combination(option.code);
  return `${option.code} · ${catalogName !== option.code ? catalogName : (option.name ?? catalogName)}`;
}

export function useStructureIssueText(parts: readonly ExamPart[], names: StructureNames) {
  const t = useTranslations("exams.structure.issues");
  const format = useFormatter();

  const blockDetail = useCallback(
    (issue: BlockIssue): string => {
      const dimension = (value: MatrixDimension) => t(`dimension.${value}`);
      switch (issue.code) {
        case "COUNT_NOT_POSITIVE":
          return t("block.countNotPositive");
        case "SET_SIZE_MISMATCH":
          return t("block.setSizeMismatch", { expected: issue.expected, actual: issue.actual });
        case "INVALID_VALUE":
          return t("block.invalidValue");
        case "PERCENT_TOTAL":
          return t("block.percentTotal", {
            dimension: dimension(issue.dimension),
            value: format.number(issue.actual, { maximumFractionDigits: 2 }),
          });
        case "ALLOCATION_TOTAL":
          return t("block.allocationTotal", {
            dimension: dimension(issue.dimension),
            actual: issue.actual,
            expected: issue.expected,
          });
      }
    },
    [t, format],
  );

  return useCallback(
    (issue: StructureIssue): string => {
      const partId = "partId" in issue ? issue.partId : issue.location.partId;
      const part = parts.find((item) => item.id === partId);
      const partName = part?.name ?? "";
      const optionId = "optionId" in issue ? issue.optionId : "location" in issue ? issue.location.optionId : null;
      const option = part?.options.find((item) => item.id === optionId);
      const optionText = option ? optionLabel(option, names) : null;
      const prefix = [partName, optionText].filter(Boolean).join(" · ");
      if (issue.code === "PART_COUNT_NOT_POSITIVE") return t("partCount", { part: prefix });
      if (issue.code === "PART_DURATION_NOT_POSITIVE") return t("partDuration", { part: prefix });
      if (issue.code === "NO_OPTIONS") return t("noOptions", { part: prefix });
      if (issue.code === "PART_PAPER_COUNT") {
        return t("partPaperCount", { part: prefix, onPaper: issue.onPaper, required: issue.required });
      }
      if (issue.code === "PART_CHOOSE_INVALID") {
        return t("partChoose", { part: prefix, choose: issue.choose, options: issue.options });
      }
      if (issue.code === "SECTIONS_TOTAL") {
        const diff = issue.actual - issue.expected;
        if (diff > 0) return t("sectionsTotalOver", { part: prefix, diff, actual: issue.actual, expected: issue.expected });
        if (diff < 0) return t("sectionsTotalUnder", { part: prefix, diff: -diff, actual: issue.actual, expected: issue.expected });
        return t("sectionsTotal", { part: prefix, actual: issue.actual, expected: issue.expected });
      }
      if (issue.code === "PART_POINTS") {
        const number = (value: number) => format.number(value, { maximumFractionDigits: 2 });
        return t("partPoints", { part: prefix, actual: number(issue.actual), expected: number(issue.expected) });
      }
      const sections = option ? option.sections : [...(part?.sections ?? []), ...(part?.subjectPool ?? [])];
      const section = sections.find((item) => item.id === issue.location.sectionId);
      // Môn có mã môn: chỉ dùng tên danh mục (theo ngôn ngữ đang chọn); ghép thêm tên phần của mẫu cho ra "Toán · Mathematics" (V-05).
      const sectionText = section?.subjectCode ? names.subject(section.subjectCode) : prefix;
      if (issue.code === "BLOCKS_TOTAL") {
        const diff = issue.actual - issue.expected;
        if (diff > 0) return t("blocksTotalOver", { section: sectionText, diff, actual: issue.actual, expected: issue.expected });
        if (diff < 0) return t("blocksTotalUnder", { section: sectionText, diff: -diff, actual: issue.actual, expected: issue.expected });
        return t("blocksTotal", { section: sectionText, actual: issue.actual, expected: issue.expected });
      }
      if (issue.code === "SUBJECT_COUNT_EXCEEDS_ALLOCATION") {
        return t("subjectCountExceeds", { section: sectionText, declared: issue.declared, allocated: issue.allocated });
      }
      if (issue.code === "SUBJECT_TRIM_IMPOSSIBLE") {
        return t("subjectTrimImpossible", { section: sectionText, allocated: issue.allocated });
      }
      const block = section?.blocks.find((item) => item.id === issue.blockId);
      return t("block.prefix", { section: sectionText, block: block?.name ?? "", detail: blockDetail(issue.issue) });
    },
    [parts, names, t, format, blockDetail],
  );
}

/** Nơi sửa một lỗi: khối, môn hoặc phần chứa lỗi. */
export function issueTarget(issue: StructureIssue): StructureSelection {
  if (issue.code === "BLOCK") return { kind: "block", blockId: issue.blockId };
  if ("location" in issue) return { kind: "section", sectionId: issue.location.sectionId };
  return { kind: "part", partId: issue.partId };
}
