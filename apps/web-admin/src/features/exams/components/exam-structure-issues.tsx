"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useCallback } from "react";
import type { BlockIssue, StructureIssue } from "../matrix";
import type { ExamPart, ExamPartOption } from "../types";
import type { StructureSelection } from "./exam-structure-draft";

export interface StructureNames {
  subject: (code: string) => string;
  combination: (code: string) => string;
}

/**
 * Tên một lựa chọn của phần tự chọn: "Tiếng Anh", "KH-1 · Vật lí – Hóa học – Sinh học", hoặc tên nhánh ("Khoa học máy
 * tính").
 */
export function optionLabel(
  option: { kind: ExamPartOption["kind"]; code: string; name?: string | null },
  names: StructureNames,
): string {
  if (option.kind === "BRANCH") return option.name ?? option.code;
  return option.kind === "SUBJECT" ? names.subject(option.code) : `${option.code} · ${names.combination(option.code)}`;
}

/** Chữ của một lỗi cấu trúc, kèm vị trí (phần · lựa chọn · môn · khối), theo mẫu thông báo của UniNote mục 9.4. */
export function useStructureIssueText(parts: readonly ExamPart[], names: StructureNames) {
  const t = useTranslations("exams.structure.issues");
  const format = useFormatter();

  const blockDetail = useCallback(
    (issue: BlockIssue): string => {
      const dimension = (value: "COMBINED" | "GRADE" | "DIFFICULTY") => t(`dimension.${value}`);
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
      if (issue.code === "SECTIONS_TOTAL") {
        return t("sectionsTotal", { part: prefix, actual: issue.actual, expected: issue.expected });
      }
      const sections = option ? option.sections : (part?.sections ?? []);
      const section = sections.find((item) => item.id === issue.location.sectionId);
      const sectionText = [prefix, section ? names.subject(section.subjectCode) : null].filter(Boolean).join(" · ");
      if (issue.code === "BLOCKS_TOTAL") {
        return t("blocksTotal", { section: sectionText, actual: issue.actual, expected: issue.expected });
      }
      const block = section?.blocks.find((item) => item.id === issue.blockId);
      return t("block.prefix", { section: sectionText, block: block?.name ?? "", detail: blockDetail(issue.issue) });
    },
    [parts, names, t, blockDetail],
  );
}

/** Nơi sửa một lỗi: khối có lỗi, hoặc phần chứa lỗi. */
export function issueTarget(issue: StructureIssue): StructureSelection {
  if (issue.code === "BLOCK") return { kind: "block", blockId: issue.blockId };
  return { kind: "part", partId: "partId" in issue ? issue.partId : issue.location.partId };
}
