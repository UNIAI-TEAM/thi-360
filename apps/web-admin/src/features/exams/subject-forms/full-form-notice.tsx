"use client";

import { Info } from "lucide-react";
import { useTranslations } from "next-intl";
import type { FitReason } from "./fit";

export function FullFormNotice({ reason }: { reason?: FitReason }) {
  const t = useTranslations("exams.subjectForm.common");
  return (
    <p data-testid="full-form-notice" data-reason={reason} className="flex items-start gap-2 text-body text-text-secondary">
      <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
      <span>{t("fullFormNotice")}</span>
    </p>
  );
}
