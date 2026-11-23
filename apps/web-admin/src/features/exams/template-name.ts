"use client";

import { useTranslations } from "next-intl";
import { useCallback } from "react";
import { useContentReference } from "@/features/content/queries";
import type { ExamTemplate } from "./types";

export function useTemplateLabel(): (template: ExamTemplate) => string {
  const t = useTranslations("exams.templateName");
  const tKind = useTranslations("exams.kind");
  const tPart = useTranslations("exams.create.partName");
  const subjects = useContentReference().data?.subjects;
  return useCallback(
    (template) => {
      const kind = tKind.has(template.kind) ? tKind(template.kind) : template.kind;
      const subject = subjects?.find((item) => item.code === template.subjectCode)?.name ?? template.subjectCode ?? "";
      const part = template.partCode && tPart.has(template.partCode) ? tPart(template.partCode) : (template.partCode ?? "");
      const base =
        template.scope === "PART"
          ? t("part", { kind, scope: part })
          : template.scope === "SUBJECT"
            ? t("subject", { kind, subject })
            : t("full", { kind });
      return template.structureYear == null ? base : t("withYear", { name: base, year: String(template.structureYear) });
    },
    [t, tKind, tPart, subjects],
  );
}
