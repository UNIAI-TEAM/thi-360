"use client";

import { useQueryClient } from "@tanstack/react-query";
import { notify } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";
import type { UseFormSetError } from "react-hook-form";
import { getErrorStatus } from "@/lib/api/query-client";
import { apiErrorCode } from "@/features/content/api-error";
import type { ReloadPartRow } from "./components/reload-structure-dialog";
import type { OverviewRow } from "./create-exam-overview";
import { applyOverviewToParts } from "./create-exam-overview-structure";
import type { ExamInfoFormValues } from "./create-exam-schema";
import { updateExamInfo } from "./api";
import { ExamsApiError } from "./errors";
import { EXAMS_QUERY_KEYS } from "./keys";
import { useUpdateExamInfo, useUpdateExamStructure } from "./queries";
import type { ExamDetail, ExamStructure, UpdateExamInfoRequest } from "./types";

const FORM_FIELD_OF: Record<string, keyof ExamInfoFormValues> = {
  name: "name",
  code: "code",
  maxScore: "maxScore",
  durationMinutes: "sharedDuration",
  description: "description",
  instructions: "instructions",
};

export interface PendingInfoSave {
  request: UpdateExamInfoRequest;
  parts: ReloadPartRow[];
  modeChange?: boolean;
}

interface UseExamInfoSaveOptions {
  exam: ExamDetail | undefined;
  structure: ExamStructure | undefined;
  rows: readonly OverviewRow[];
  formDirty: boolean;
  setError: UseFormSetError<ExamInfoFormValues>;
  onHold?: (hold: boolean) => void;
  onSaved: (message: string, savedDetail?: ExamDetail) => void;
  onFailed: () => void;
  onStructureSaved?: (saved: ExamDetail) => void;
}

export function useExamInfoSave({
  exam,
  structure,
  rows,
  formDirty,
  setError,
  onHold,
  onSaved,
  onFailed,
  onStructureSaved,
}: UseExamInfoSaveOptions) {
  const t = useTranslations("exams.general");
  const tExams = useTranslations("exams");
  const queryClient = useQueryClient();
  const update = useUpdateExamInfo(exam?.id ?? "");
  const structureUpdate = useUpdateExamStructure(exam?.id ?? "");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const checkStatus = exam?.check.status;

  const done = (recheck = false, savedDetail?: ExamDetail) => {
    const message = t(recheck ? "overview.savedRecheck" : "saved");
    notify.success(message);
    onSaved(message, savedDetail);
  };

  const save = ({ request }: PendingInfoSave, structureSaved = false) => {
    if (!exam) return;
    setSubmitError(null);
    update.mutate(request, {
      onSuccess: async (savedDetail) => {
        let finalDetail = savedDetail;
        // `ExamService.changeScope` resets `yearsConfirmed` from the reloaded template; follow up if user already checked confirmation (`SF-1`).
        if (request.confirmReplace && request.yearsConfirmed === true && !savedDetail.yearsConfirmed) {
          try {
            finalDetail = await updateExamInfo(exam.id, {
              expectedRevision: savedDetail.revision,
              yearsConfirmed: true,
            });
            void queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.all });
          } catch {
            // Keep the saved scope change even if the follow-up confirmation PATCH fails.
          }
        }
        queryClient.setQueryData(EXAMS_QUERY_KEYS.detail(exam.id), finalDetail);
        onHold?.(false);
        done(structureSaved && checkStatus !== "UNCHECKED", finalDetail);
      },
      onError: (error) => {
        onHold?.(false);
        onFailed();
        const status = getErrorStatus(error);
        const code = apiErrorCode(error);
        if (status === 409 && code === "EXAM_SCOPE_LOCKED") {
          setSubmitError(t("scopeLocked"));
        } else if (status === 409 && code === "EXAM_CODE_TAKEN") {
          setError("code", { message: tExams("create.errors.codeTaken") }, { shouldFocus: true });
        } else if (status === 412) {
          setSubmitError(t("saveConflict"));
        } else if (status === 400 || status === 422) {
          const fields = (error instanceof ExamsApiError ? error.fieldErrors : []).flatMap((item) => {
            const field = FORM_FIELD_OF[item.field ?? ""];
            return field ? [{ field, code: item.code }] : [];
          });
          if (fields.length === 0) setSubmitError(t("saveInvalid"));
          fields.forEach(({ field, code: fieldCode }, index) =>
            setError(
              field,
              {
                message:
                  fieldCode === "EXAM_DURATION_REQUIRED"
                    ? tExams("create.validation.sharedDurationPositive")
                    : t("fieldInvalid"),
              },
              { shouldFocus: index === 0 },
            ),
          );
        } else {
          setSubmitError(t("saveError"));
        }
        if (structureSaved) setSubmitError(t("overview.infoNotSaved"));
      },
    });
  };

  const saveOverviewThenInfo = async (pending: PendingInfoSave) => {
    if (!exam || !structure) return;
    setSubmitError(null);
    onHold?.(true);
    let saved: ExamDetail;
    try {
      saved = await structureUpdate.mutateAsync({
        parts: applyOverviewToParts(structure.parts, rows),
        examQuotas: structure.examQuotas ?? [],
        expectedRevision: structure.revision,
      });
    } catch (error) {
      onHold?.(false);
      const status = getErrorStatus(error);
      setSubmitError(
        status === 412 ? t("saveConflict") : status === 422 ? tExams("structure.saveInvalid") : tExams("structure.saveError"),
      );
      return;
    }
    queryClient.setQueryData(EXAMS_QUERY_KEYS.detail(exam.id), saved);
    queryClient.setQueryData(EXAMS_QUERY_KEYS.structure(exam.id), {
      ...structure,
      revision: saved.revision,
      parts: saved.parts,
    });
    onStructureSaved?.(saved);
    if (!formDirty) {
      onHold?.(false);
      done(checkStatus !== "UNCHECKED", saved);
      return;
    }
    save({ ...pending, request: { ...pending.request, expectedRevision: saved.revision } }, true);
  };

  return { update, structureUpdate, submitError, setSubmitError, save, saveOverviewThenInfo };
}
