"use client";

import { useTranslations } from "next-intl";
import { useCallback } from "react";
import { useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import {
  buildErrorReportTable,
  errorReportFileName,
  type IssueText,
} from "../error-report";
import { saveBlob, toCsvBlob } from "../files";
import type { ImportErrorLine, ImportRowIssue } from "../types";

/** Câu lỗi và cách sửa của một lỗi, dịch lúc hiển thị (lỗi lưu dưới dạng mã, DESIGN.md mục 5.13). */
export function useIssueText(reference: ContentReference | undefined): (issue: ImportRowIssue) => IssueText {
  const t = useTranslations("questionImport");
  const lookup = useReferenceLookup(reference);
  return useCallback(
    (issue: ImportRowIssue) => {
      const field = issue.field ? t(`fields.${issue.field}`) : "";
      const options =
        issue.code === "GRADE_UNKNOWN"
          ? (reference?.grades ?? []).map((option) => option.name).join(", ")
          : issue.code === "DIFFICULTY_UNKNOWN"
            ? (reference?.difficulties ?? []).map((option) => option.name).join(", ")
            : "";
      const values = {
        field,
        column: issue.column ?? field,
        value: issue.value ?? "—",
        row: issue.relatedRow ?? 0,
        subject: issue.subjectCode ? lookup.subject(issue.subjectCode) : "",
        options,
        min: issue.min ?? 0,
        max: issue.max ?? 0,
        type: issue.questionType ?? "other",
      };
      return {
        message: t(`issues.${issue.code}.message`, values),
        fix: t(`issues.${issue.code}.fix`, values),
      };
    },
    [t, lookup, reference],
  );
}

/** Tải file lỗi: CSV sinh trong trình duyệt từ danh sách lỗi (backend sẽ trả tệp có sẵn). */
export function useErrorReportDownload(reference: ContentReference | undefined) {
  const t = useTranslations("questionImport.errorReport");
  const describe = useIssueText(reference);
  return useCallback(
    (lines: readonly ImportErrorLine[], sourceFileName: string) => {
      const table = buildErrorReportTable(
        lines,
        {
          colRow: t("colRow"),
          colColumn: t("colColumn"),
          colSourceCode: t("colSourceCode"),
          colError: t("colError"),
          colFix: t("colFix"),
        },
        describe,
      );
      saveBlob(toCsvBlob(table), errorReportFileName(sourceFileName));
    },
    [t, describe],
  );
}
