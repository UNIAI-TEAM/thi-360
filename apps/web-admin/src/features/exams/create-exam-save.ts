"use client";

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
import { useUpdateExamInfo, useUpdateExamStructure } from "./queries";
import { ExamsApiError } from "./review-api";
import type { ExamDetail, ExamStructure, UpdateExamInfoRequest } from "./types";

/** Trường của máy chủ (`fieldErrors[].field`) tới ô nhập của biểu mẫu Thông tin chung. */
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
  /** Các phần sẽ được nạp lại theo mẫu mới nhất của phạm vi mới (hiện ở hộp xác nhận). */
  parts: ReloadPartRow[];
  /** `true` khi lần lưu này đổi cách dựng bài: hộp xác nhận cảnh báo dựng lại cấu trúc như đề mới (AC-1697-5). */
  modeChange?: boolean;
}

interface UseExamInfoSaveOptions {
  /** Bài đang sửa; `undefined` ở trang Tạo bài thi (chưa có bài): hook không ghi gì. */
  exam: ExamDetail | undefined;
  /** Cấu trúc đã lưu; chưa về thì chưa ghi được số liệu của bảng tổng quan. */
  structure: ExamStructure | undefined;
  /** Dòng bảng tổng quan đang hiện (đã áp ô người dùng sửa). */
  rows: readonly OverviewRow[];
  /** Biểu mẫu thông tin có thay đổi chưa lưu: không thì chỉ ghi cấu trúc. */
  formDirty: boolean;
  /** Bài đã công bố: phạm vi không đổi được nên mã trùng (409) không phải lỗi của ô mã. */
  lockScope: boolean;
  setError: UseFormSetError<ExamInfoFormValues>;
  /** Giữ (hoặc thả) khóa của biểu mẫu để lưu cấu trúc không làm dựng lại biểu mẫu giữa chừng (tab Thông tin chung). */
  onHold?: (hold: boolean) => void;
  /** Cả hai phần đã lưu xong; `message` là câu đã báo qua thông báo nổi (cho vùng đọc màn hình). */
  onSaved: (message: string) => void;
  /** Lưu thông tin lỗi: hộp xác nhận nạp lại (nếu có) đóng, lỗi hiện trên biểu mẫu. */
  onFailed: () => void;
  /** Số liệu bảng tổng quan đã ghi vào cấu trúc: các ô đã sửa không còn là "chưa lưu". */
  onStructureSaved?: () => void;
}

/**
 * Lưu Thông tin bài thi của một bài đã tạo, dùng chung cho tab Thông tin chung và bước 1 ở chế độ sửa.
 *
 * Số liệu bảng tổng quan đã sửa: ghi cấu trúc TRƯỚC (cấu trúc đã đọc, chỉ đổi các số đó, `expectedRevision` của cấu trúc).
 * Lỗi thì không gửi gì khác và giữ nguyên giá trị người dùng đã gõ; xong mới ghi thông tin chung (chỉ khi biểu mẫu có
 * thay đổi) bằng revision mới của bài. Đổi phạm vi thì máy chủ nạp mẫu mới nhất khớp (`confirmReplace`).
 */
export function useExamInfoSave({ exam, structure, rows, formDirty, lockScope, setError, onHold, onSaved, onFailed, onStructureSaved }: UseExamInfoSaveOptions) {
  const t = useTranslations("exams.general");
  const tExams = useTranslations("exams");
  const update = useUpdateExamInfo(exam?.id ?? "");
  const structureUpdate = useUpdateExamStructure(exam?.id ?? "");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const checkStatus = exam?.check.status;

  /** `recheck`: chỉ ghi cấu trúc mà kết quả kiểm tra trước đó đã cũ, nên báo cần kiểm tra lại (như bước Cấu trúc). */
  const done = (recheck = false) => {
    const message = t(recheck ? "overview.savedRecheck" : "saved");
    notify.success(message);
    onSaved(message);
  };

  /** `structureSaved`: số liệu của bảng tổng quan đã lưu trước đó, nên lỗi ở đây nói rõ phần nào đã lưu, phần nào chưa. */
  const save = ({ request }: PendingInfoSave, structureSaved = false) => {
    if (!exam) return;
    setSubmitError(null);
    update.mutate(request, {
      onSuccess: () => {
        onHold?.(false);
        done(structureSaved && checkStatus !== "UNCHECKED");
      },
      onError: (error) => {
        onFailed();
        const status = getErrorStatus(error);
        if (status === 409 && apiErrorCode(error) === "EXAM_SCOPE_LOCKED") {
          setSubmitError(t("scopeLocked"));
        } else if (status === 409 && !lockScope) {
          setError("code", { message: tExams("create.errors.codeTaken") }, { shouldFocus: true });
        } else if (status === 412) {
          setSubmitError(t("saveConflict"));
        } else if (status === 400 || status === 422) {
          const fields = (error instanceof ExamsApiError ? error.fieldErrors : []).flatMap((item) => {
            const field = FORM_FIELD_OF[item.field ?? ""];
            return field ? [{ field, code: item.code }] : [];
          });
          if (fields.length === 0) setSubmitError(t("saveInvalid"));
          fields.forEach(({ field, code }, index) =>
            setError(
              field,
              { message: code === "EXAM_DURATION_REQUIRED" ? tExams("create.validation.sharedDurationPositive") : t("fieldInvalid") },
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
      setSubmitError(status === 412 ? t("saveConflict") : status === 422 ? tExams("structure.saveInvalid") : tExams("structure.saveError"));
      return;
    }
    onStructureSaved?.();
    if (!formDirty) {
      onHold?.(false);
      done(checkStatus !== "UNCHECKED");
      return;
    }
    save({ ...pending, request: { ...pending.request, expectedRevision: saved.revision } }, true);
  };

  return { update, structureUpdate, submitError, setSubmitError, save, saveOverviewThenInfo };
}
