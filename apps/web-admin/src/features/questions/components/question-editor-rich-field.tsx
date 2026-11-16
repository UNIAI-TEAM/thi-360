"use client";

import { RichTextEditor } from "@/components/rich-text";
import { Controller, useFormContext, type Path } from "react-hook-form";
import type { QuestionEditorValues } from "../editor-form";

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
  "aria-label": ariaLabel,
  "aria-labelledby": ariaLabelledby,
  "aria-describedby": ariaDescribedby,
  "aria-invalid": ariaInvalid,
  "aria-required": ariaRequired,
}: {
  name: Path<QuestionEditorValues>;
  id?: string;
  placeholder?: string;
  "aria-label"?: string;
  "aria-labelledby"?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
  "aria-required"?: boolean;
}) {
  const { control } = useFormContext<QuestionEditorValues>();
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        // `shouldFocus` của useFieldArray và việc dời focus tới ô lỗi khi gửi gọi `ref.focus()`: dẫn vào vùng soạn.
        <div ref={(node) => field.ref(node && { focus: () => focusEditable(node) })}>
          <RichTextEditor
            id={id}
            value={typeof field.value === "string" ? field.value : ""}
            onChange={field.onChange}
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
