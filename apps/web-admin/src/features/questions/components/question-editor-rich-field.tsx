"use client";

import { RichTextEditor, type FieldContext, type RichTextEditorHandle } from "@/components/rich-text";
import { useTranslations } from "next-intl";
import { useCallback } from "react";
import { Controller, useFormContext, type Path } from "react-hook-form";
import { questionAssetErrorMessageKey } from "../asset-upload";
import type { QuestionEditorValues } from "../editor-form";
import { notifyImageUploadError } from "../editor/upload-toasts";
import { useRichEditorRegistry } from "./question-editor-shared";

const EDITABLE = '[contenteditable="true"]';

/** TipTap dựng vùng soạn sau lần render đầu: chưa có thì chờ nó xuất hiện (tối đa 1 giây) rồi mới focus. */
function focusEditable(root: HTMLElement) {
  const ready = root.querySelector<HTMLElement>(EDITABLE);
  if (ready) {
    ready.focus();
    return;
  }
  const observer = new MutationObserver(() => {
    const target = root.querySelector<HTMLElement>(EDITABLE);
    if (!target) return;
    observer.disconnect();
    target.focus();
  });
  observer.observe(root, { childList: true, subtree: true });
  window.setTimeout(() => observer.disconnect(), 1000);
}

/**
 * Ô nhập HTML của biểu mẫu soạn câu hỏi: `RichTextEditor` gắn vào react-hook-form qua `Controller`, thay cho ô
 * chữ thuần. Nhận cùng thuộc tính điều khiển mà `FormField` rải (id, aria-*), nên nhãn, lỗi và gợi ý gắn như cũ.
 */
export function RichTextField({
  name,
  id,
  placeholder,
  fieldContext,
  blankNumberStart,
  "aria-label": ariaLabel,
  "aria-labelledby": ariaLabelledby,
  "aria-describedby": ariaDescribedby,
  "aria-invalid": ariaInvalid,
  "aria-required": ariaRequired,
}: {
  name: Path<QuestionEditorValues>;
  id?: string;
  placeholder?: string;
  /** Ngữ cảnh của ô (đề bài trắc nghiệm 1 đáp án, ngữ liệu câu chùm…): lọc mục Chèn (spec 4.1). */
  fieldContext?: FieldContext;
  /** Ngữ liệu câu chùm: số bắt đầu đánh chỗ trống (spec 3.4). */
  blankNumberStart?: number;
  "aria-label"?: string;
  "aria-labelledby"?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
  "aria-required"?: boolean;
}) {
  const { control } = useFormContext<QuestionEditorValues>();
  const registry = useRichEditorRegistry();
  const tAssetErrors = useTranslations("questions.assets.errors");
  // Ảnh dán/thả/chọn bị máy chủ từ chối (413, 422, 429...): trình soạn chỉ báo lỗi lên, nói cho người dùng ở đây.
  const onImageUploadError = useCallback(
    (error: unknown) => notifyImageUploadError(tAssetErrors(questionAssetErrorMessageKey(error, "IMAGE"))),
    [tAssetErrors],
  );
  const registerHandle = useCallback(
    (handle: RichTextEditorHandle | null) => {
      if (!registry) return;
      if (handle) registry.set(name, handle);
      else registry.delete(name);
    },
    [registry, name],
  );
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        // `shouldFocus` của useFieldArray và việc dời focus tới ô lỗi khi gửi gọi `ref.focus()`: dẫn vào vùng soạn.
        <div ref={(node) => field.ref(node && { focus: () => focusEditable(node) })}>
          <RichTextEditor
            handleRef={registerHandle}
            fieldContext={fieldContext}
            blankNumberStart={blankNumberStart}
            id={id}
            value={typeof field.value === "string" ? field.value : ""}
            onChange={field.onChange}
            onImageUploadError={onImageUploadError}
            placeholder={placeholder}
            invalid={ariaInvalid === true}
            aria-label={ariaLabel}
            aria-labelledby={ariaLabelledby}
            aria-describedby={ariaDescribedby}
            aria-required={ariaRequired ? "true" : undefined}
          />
        </div>
      )}
    />
  );
}
