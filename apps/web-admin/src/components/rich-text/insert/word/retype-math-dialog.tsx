"use client";

import { Editor } from "@tiptap/core";
import { useEffect, useState, type ComponentType } from "react";
import { createRichTextExtensions } from "../../extensions";
import { INSERT_REGISTRY, type InsertDialogProps } from "../registry";

/** LaTeX của công thức đầu tiên trong HTML của một editor tạm; `null` khi chưa chèn gì. */
function firstLatex(html: string): string | null {
  const span = new DOMParser().parseFromString(html, "text/html").querySelector("span.math-inline, span.math-block");
  if (!span) return null;
  const text = (span.textContent ?? "").trim();
  const latex = /^\\[([]([\s\S]*)\\[)\]]$/.exec(text)?.[1] ?? text;
  return latex.trim() === "" ? null : latex.trim();
}

export interface RetypeMathDialogProps {
  /** Gọi một lần khi hộp công thức đóng: LaTeX đã chèn, hoặc `null` nếu người soạn hủy. */
  onDone: (latex: string | null) => void;
}

/**
 * "Gõ lại công thức" (spec 4.10): mở hộp công thức toán của registry cho một editor tạm không gắn vào trang, rồi đọc
 * công thức mà hộp chèn vào đó. Cách này không phụ thuộc cách hộp công thức chèn (`setMath`, `insertContent`…) và
 * không đụng vào ô đang soạn: công thức gõ lại chỉ vào đúng chỗ giữ của nó khi người soạn bấm "Đưa vào biểu mẫu".
 */
export function RetypeMathDialog({ onDone }: RetypeMathDialogProps) {
  const [state, setState] = useState<{ Dialog: ComponentType<InsertDialogProps>; editor: Editor } | null>(null);

  useEffect(() => {
    let cancelled = false;
    let scratch: Editor | null = null;
    const entry = INSERT_REGISTRY.find((candidate) => candidate.id === "math");
    void entry?.load().then((module) => {
      if (cancelled) return;
      scratch = new Editor({ extensions: createRichTextExtensions({}), content: "" });
      setState({ Dialog: module.default, editor: scratch });
    });
    return () => {
      cancelled = true;
      scratch?.destroy();
    };
  }, []);

  if (!state) return null;
  const MathDialog = state.Dialog;
  return (
    <MathDialog
      editor={state.editor}
      open
      editing={null}
      onOpenChange={(open) => {
        if (!open) onDone(firstLatex(state.editor.getHTML()));
      }}
    />
  );
}
