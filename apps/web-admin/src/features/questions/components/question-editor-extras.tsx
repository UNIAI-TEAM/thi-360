"use client";

import { Button, IconButton } from "@thi360/ui";
import { Paperclip, Trash2, Upload } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useRef, useState, type ChangeEvent } from "react";
import { useFormContext, useWatch } from "react-hook-form";
import type { QuestionEditorValues } from "../editor-form";
import { RichTextField } from "./question-editor-rich-field";
import {
  ATTACHMENT_ACCEPT,
  attachmentInfo,
  attachmentUploadRejection,
  checkAttachment,
  uploadQuestionAttachment,
} from "../editor/attachments";
import { dismissImageUploadToasts } from "../editor/upload-toasts";
import { EditorSection, FieldErrorText, useAnnounce, useEditorError } from "./question-editor-shared";

/** Lỗi sai thường gặp (PBI-03 mục 4.1): cách hiểu sai học viên hay mắc, làm dữ liệu tham khảo cho AI giải thích. */
export function CommonMistakesSection({ formId }: { formId: string }) {
  const t = useTranslations("questions.editor.commonMistakes");
  const tCommon = useTranslations("questions.editor");
  const error = useEditorError("commonMistakes");
  const titleId = `${formId}-mistakes-title`;
  return (
    <EditorSection title={t("title")} titleId={titleId} optionalLabel={tCommon("optional")}>
      <div className="flex flex-col gap-1.5">
        <RichTextField
          aria-labelledby={titleId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${formId}-mistakes-error` : `${formId}-mistakes-hint`}
          name="commonMistakes"
        />
        {!error && (
          <p id={`${formId}-mistakes-hint`} className="text-caption text-text-muted">
            {t("hint")}
          </p>
        )}
        <FieldErrorText id={`${formId}-mistakes-error`} message={error} />
      </div>
    </EditorSection>
  );
}

/**
 * Tệp đính kèm (PBI-03 mục 4.1): chọn tệp, tải lên (dữ liệu mẫu), xóa khỏi câu. Loại tệp và dung lượng bị chặn sớm;
 * máy chủ kiểm lại. Mỗi lần chọn nhận nhiều tệp, tệp sai bị bỏ qua kèm thông báo lý do.
 */
export function AttachmentsSection({ formId }: { formId: string }) {
  const t = useTranslations("questions.editor.attachments");
  const tCommon = useTranslations("questions.editor");
  const format = useFormatter();
  const announce = useAnnounce();
  const inputRef = useRef<HTMLInputElement>(null);
  const hintId = useId();
  const { control, getValues, setValue } = useFormContext<QuestionEditorValues>();
  const ids = useWatch({ control, name: "attachmentIds" }) ?? [];
  const [uploading, setUploading] = useState(false);
  const [problems, setProblems] = useState<string[]>([]);
  const titleId = `${formId}-attachments-title`;

  const labelOf = (id: string) => {
    const info = attachmentInfo(id);
    return info ? { name: info.name, size: format.number(Math.max(1, Math.round(info.size / 1024))) } : { name: id, size: null };
  };

  const pick = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = "";
    if (files.length === 0) return;
    const rejected: string[] = [];
    const accepted: string[] = [];
    setUploading(true);
    for (const file of files) {
      const rejection = checkAttachment(file);
      if (rejection) {
        rejected.push(t(`rejected.${rejection}`, { name: file.name }));
        continue;
      }
      try {
        accepted.push((await uploadQuestionAttachment(file)).id);
      } catch (error) {
        rejected.push(t(`rejected.${attachmentUploadRejection(error)}`, { name: file.name }));
      }
    }
    setUploading(false);
    // F-09: toast lỗi tải ảnh chèn cũ phải nhường chỗ cho lỗi trong dải mới; chỉ đóng toast của đường tải ảnh.
    if (rejected.length > 0) dismissImageUploadToasts();
    setProblems(rejected);
    if (accepted.length > 0) {
      setValue("attachmentIds", [...getValues("attachmentIds"), ...accepted], { shouldDirty: true });
      announce(t("added", { count: accepted.length }));
    }
  };

  return (
    <EditorSection title={t("title")} titleId={titleId} optionalLabel={tCommon("optional")}>
      <div className="flex flex-col gap-2">
        {ids.length === 0 ? (
          <p className="text-body text-text-muted">{t("none")}</p>
        ) : (
          <ul aria-labelledby={titleId} className="m-0 flex list-none flex-col gap-2 p-0">
            {ids.map((id) => {
              const label = labelOf(id);
              return (
                <li key={id} className="flex items-center gap-2 rounded-lg border border-border px-3 py-1.5">
                  <Paperclip aria-hidden className="size-4 shrink-0 text-text-muted" />
                  <span className="min-w-0 flex-1 truncate text-body text-text">{label.name}</span>
                  {label.size && <span className="shrink-0 text-caption text-text-muted">{t("size", { kb: label.size })}</span>}
                  <IconButton
                    tone="danger"
                    aria-label={t("remove", { name: label.name })}
                    icon={<Trash2 />}
                    onClick={() => {
                      setValue(
                        "attachmentIds",
                        getValues("attachmentIds").filter((item) => item !== id),
                        { shouldDirty: true },
                      );
                      announce(t("removed", { name: label.name }));
                    }}
                  />
                </li>
              );
            })}
          </ul>
        )}
        <input
          ref={inputRef}
          type="file"
          multiple
          hidden
          accept={ATTACHMENT_ACCEPT}
          aria-label={t("input")}
          onChange={(event) => void pick(event)}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="self-start"
          loading={uploading}
          aria-describedby={hintId}
          onClick={() => inputRef.current?.click()}
        >
          <Upload aria-hidden />
          {t("choose")}
        </Button>
        <p id={hintId} className="text-caption text-text-muted">
          {t("hint")}
        </p>
        {problems.map((problem) => (
          <p key={problem} role="alert" className="text-caption text-danger">
            {problem}
          </p>
        ))}
      </div>
    </EditorSection>
  );
}
