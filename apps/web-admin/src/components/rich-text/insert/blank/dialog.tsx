"use client";

import { Button, cn } from "@thi360/ui";
import { NodeSelection } from "@tiptap/pm/state";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { useFormContext, type UseFormReturn } from "react-hook-form";
import { isBlankNumberStart, type QuestionEditorValues } from "@/features/questions/editor-form";
import { BLANK_NODE } from "../../extensions/names";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { blankNumbersOfDoc, firstChildWithoutBlank } from "./blank-numbering";

/**
 * Hộp "Chỗ trống" của ngữ liệu câu chùm (spec 4.8, bản vẽ `SoanThaoTiengAnh`): chèn `span.blank` gắn với câu con đầu
 * tiên chưa có chỗ trống (hết câu con trống thì báo "Thêm câu con trước"). Mở lại trên chỗ trống đang chọn: đổi câu
 * con gắn với nó (chỉ câu con chưa có chỗ trống khác) hoặc xóa chỗ trống (không đổi câu con, spec 3.5). Số câu con và
 * "Đánh số từ" lấy từ biểu mẫu soạn câu (`FormProvider` của trang chứa trình soạn).
 */
export default function BlankInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const tEntry = useTranslations("richText.insert.blank");
  const t = useTranslations("richText.insert.blank.dialog");
  const tType = useTranslations("content.questionType");
  const groupId = useId();
  // Ngoài biểu mẫu soạn câu (không có `FormProvider`) `useFormContext` trả `null`: hộp chỉ báo "Thêm câu con trước".
  const form = useFormContext<QuestionEditorValues>() as UseFormReturn<QuestionEditorValues> | null;

  // Chụp một lần lúc mở: hộp là modal nên biểu mẫu không đổi khi hộp đang mở.
  const [snapshot] = useState(() => {
    const values = form?.getValues();
    const children = values?.children ?? [];
    const startText = values?.body.blankNumberStart ?? "1";
    const { selection } = editor.state;
    const attached =
      selection instanceof NodeSelection && selection.node.type.name === BLANK_NODE
        ? { pos: selection.from, blank: Number(selection.node.attrs.blank) }
        : null;
    const usedByOthers = blankNumbersOfDoc(editor.state.doc, attached?.pos);
    return {
      types: children.map((child) => child.type),
      start: isBlankNumberStart(startText) ? Number(startText.trim()) : 1,
      attached,
      usedByOthers,
    };
  });
  const { attached, usedByOthers, types, start } = snapshot;
  const free = types.flatMap((_, index) => (usedByOthers.includes(index + 1) ? [] : [index + 1]));
  const [choice, setChoice] = useState<number | null>(attached ? attached.blank : firstChildWithoutBlank(types.length, usedByOthers));
  const canApply = choice !== null;

  const childLabel = (number: number) => t("childOption", { number, type: tType(types[number - 1] ?? "SINGLE_CHOICE") });
  const shown = (number: number) => `(${start + number - 1}) ____`;

  function apply() {
    if (choice === null) return;
    if (attached) {
      editor
        .chain()
        .focus()
        .command(({ tr }) => {
          tr.setNodeMarkup(attached.pos, undefined, { blank: choice });
          return true;
        })
        .run();
    } else {
      editor.chain().focus().insertContent({ type: BLANK_NODE, attrs: { blank: choice } }).run();
    }
    onOpenChange(false);
  }

  function remove() {
    if (!attached) return;
    editor
      .chain()
      .focus()
      .command(({ tr }) => {
        tr.delete(attached.pos, attached.pos + 1);
        return true;
      })
      .run();
    onOpenChange(false);
  }

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={tEntry("label")}
      description={tEntry("description")}
      onSubmit={apply}
      submitLabel={attached ? t("change") : t("apply")}
      submitDisabled={!canApply}
    >
      <div className="flex flex-col gap-4 pb-2">
        {attached ? (
          <fieldset className="m-0 flex min-w-0 flex-col gap-2 border-0 p-0">
            <legend className="mb-1 text-field-label text-text">{t("childrenLegend")}</legend>
            {free.map((number) => (
              <label
                key={number}
                className={cn(
                  "flex min-h-10 cursor-pointer items-center gap-2 rounded-md border border-border bg-surface px-3 text-body text-text",
                  "has-checked:border-primary-600 has-checked:bg-primary-50",
                )}
              >
                <input
                  type="radio"
                  name={`${groupId}-child`}
                  checked={choice === number}
                  onChange={() => setChoice(number)}
                  className="size-4 accent-primary-fill"
                />
                <span className="min-w-0 flex-1">{childLabel(number)}</span>
                <span aria-hidden className="text-caption text-text-muted">
                  {shown(number)}
                </span>
              </label>
            ))}
          </fieldset>
        ) : choice !== null ? (
          <p className="text-body text-text">{t("newTarget", { child: childLabel(choice) })}</p>
        ) : (
          <p role="alert" className="text-body text-danger">
            {t("needChild")}
          </p>
        )}

        {choice !== null && (
          <div className="flex flex-col gap-1">
            <span className="text-field-label text-text">{t("preview")}</span>
            <div className="rich-text-body">
              <span className="blank">{shown(choice)}</span>
            </div>
          </div>
        )}

        {attached && (
          <div>
            <Button type="button" variant="danger" onClick={remove}>
              {t("remove")}
            </Button>
          </div>
        )}
      </div>
    </InsertDialogShell>
  );
}
