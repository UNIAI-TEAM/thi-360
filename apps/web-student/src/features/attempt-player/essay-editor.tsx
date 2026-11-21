"use client";

import { cn, focusRingInset } from "@thi360/ui";
import type { EssayEditorProps } from "@thi360/ui/question";
import { sanitizeQuestionHtml, stripTrailingEmptyParagraph } from "@thi360/ui/rich-text";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Bold, Italic, List, ListOrdered, Redo2, Undo2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useLayoutEffect, useRef } from "react";

function normalizeEssayHtml(rawHtml: string): string {
  const sanitized = sanitizeQuestionHtml(rawHtml, { allowImages: false }).html;
  const trimmed = stripTrailingEmptyParagraph(sanitized);
  return trimmed === "<p></p>" ? "" : trimmed;
}

export function EssayEditor({
  id,
  value,
  onChange,
  placeholder,
  "aria-label": ariaLabel,
  "aria-describedby": ariaDescribedBy,
  readOnly = false,
  minHeightClass,
}: EssayEditorProps) {
  const t = useTranslations("attempt.editor");
  const lastEmittedHtmlRef = useRef<string>(normalizeEssayHtml(value));
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const editor = useEditor({
    immediatelyRender: false,
    editable: !readOnly,
    extensions: [
      StarterKit.configure({
        blockquote: false,
        code: false,
        codeBlock: false,
        gapcursor: false,
        heading: false,
        horizontalRule: false,
        link: false,
        strike: false,
        trailingNode: false,
        underline: false,
      }),
    ],
    content: normalizeEssayHtml(value),
    editorProps: {
      attributes: {
        id,
        role: "textbox",
        "aria-multiline": "true",
        "aria-label": ariaLabel,
        ...(ariaDescribedBy ? { "aria-describedby": ariaDescribedBy } : {}),
        ...(readOnly ? { "aria-readonly": "true" } : {}),
        class: cn(
          "rich-text w-full rounded-b-lg px-3.5 py-2.5 text-body text-text",
          minHeightClass,
        ),
      },
    },
    onUpdate({ editor: nextEditor }) {
      const normalized = normalizeEssayHtml(nextEditor.getHTML());
      if (normalized !== lastEmittedHtmlRef.current) {
        lastEmittedHtmlRef.current = normalized;
        onChangeRef.current(normalized);
      }
    },
  });

  const toolbarState = useEditorState({
    editor,
    selector: (ctx) => {
      const ed = ctx.editor;
      if (!ed) {
        return {
          bold: false,
          italic: false,
          bulletList: false,
          orderedList: false,
          canUndo: false,
          canRedo: false,
          isEmpty: true,
        };
      }
      return {
        bold: ed.isActive("bold"),
        italic: ed.isActive("italic"),
        bulletList: ed.isActive("bulletList"),
        orderedList: ed.isActive("orderedList"),
        canUndo: ed.can().undo(),
        canRedo: ed.can().redo(),
        isEmpty: ed.isEmpty,
      };
    },
  });

  useEffect(() => {
    if (!editor || editor.isDestroyed) return;
    const normalizedExternal = normalizeEssayHtml(value);
    const normalizedCurrent = normalizeEssayHtml(editor.getHTML());
    if (normalizedExternal !== normalizedCurrent) {
      editor.commands.setContent(normalizedExternal, { emitUpdate: false });
    }
    lastEmittedHtmlRef.current = normalizeEssayHtml(editor.getHTML());
  }, [editor, value]);

  useEffect(() => {
    if (!editor || editor.isDestroyed) return;
    editor.setEditable(!readOnly, false);
  }, [editor, readOnly]);

  const attrKey = JSON.stringify({
    id,
    ariaLabel,
    ariaDescribedBy: ariaDescribedBy ?? "",
    readOnly,
    minHeightClass,
  });

  useLayoutEffect(() => {
    if (!editor || editor.isDestroyed) return;
    editor.setOptions({
      editorProps: {
        ...editor.options.editorProps,
        attributes: {
          id,
          role: "textbox",
          "aria-multiline": "true",
          "aria-label": ariaLabel,
          ...(ariaDescribedBy ? { "aria-describedby": ariaDescribedBy } : {}),
          ...(readOnly ? { "aria-readonly": "true" } : {}),
          class: cn(
            "rich-text w-full rounded-b-lg px-3.5 py-2.5 text-body text-text",
            minHeightClass,
          ),
        },
      },
    });
  }, [editor, attrKey, id, ariaLabel, ariaDescribedBy, readOnly, minHeightClass]);

  const showPlaceholder = toolbarState?.isEmpty ?? value.trim().length === 0;

  return (
    <div
      className={cn(
        "rounded-lg border border-border-strong bg-surface transition-colors",
        "focus-within:border-primary-600 focus-within:ring-2 focus-within:ring-primary-200",
        readOnly && "bg-subtle",
      )}
    >
      <div
        role="toolbar"
        aria-label={t("toolbarLabel")}
        className="flex flex-wrap items-center gap-1 border-b border-border px-2 py-1.5"
      >
        <button
          type="button"
          aria-label={t("bold")}
          aria-pressed={toolbarState?.bold ?? false}
          disabled={readOnly || !editor?.can().toggleBold()}
          onClick={() => editor?.chain().focus().toggleBold().run()}
          className={cn(
            "inline-flex size-8 items-center justify-center rounded-md text-text-secondary transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
            "hover:bg-subtle disabled:cursor-not-allowed disabled:opacity-45",
            toolbarState?.bold && "bg-primary-100 text-primary-800",
            focusRingInset,
          )}
        >
          <Bold className="size-4" aria-hidden="true" />
        </button>

        <button
          type="button"
          aria-label={t("italic")}
          aria-pressed={toolbarState?.italic ?? false}
          disabled={readOnly || !editor?.can().toggleItalic()}
          onClick={() => editor?.chain().focus().toggleItalic().run()}
          className={cn(
            "inline-flex size-8 items-center justify-center rounded-md text-text-secondary transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
            "hover:bg-subtle disabled:cursor-not-allowed disabled:opacity-45",
            toolbarState?.italic && "bg-primary-100 text-primary-800",
            focusRingInset,
          )}
        >
          <Italic className="size-4" aria-hidden="true" />
        </button>

        <button
          type="button"
          aria-label={t("bulletList")}
          aria-pressed={toolbarState?.bulletList ?? false}
          disabled={readOnly || !editor?.can().toggleBulletList()}
          onClick={() => editor?.chain().focus().toggleBulletList().run()}
          className={cn(
            "inline-flex size-8 items-center justify-center rounded-md text-text-secondary transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
            "hover:bg-subtle disabled:cursor-not-allowed disabled:opacity-45",
            toolbarState?.bulletList && "bg-primary-100 text-primary-800",
            focusRingInset,
          )}
        >
          <List className="size-4" aria-hidden="true" />
        </button>

        <button
          type="button"
          aria-label={t("orderedList")}
          aria-pressed={toolbarState?.orderedList ?? false}
          disabled={readOnly || !editor?.can().toggleOrderedList()}
          onClick={() => editor?.chain().focus().toggleOrderedList().run()}
          className={cn(
            "inline-flex size-8 items-center justify-center rounded-md text-text-secondary transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
            "hover:bg-subtle disabled:cursor-not-allowed disabled:opacity-45",
            toolbarState?.orderedList && "bg-primary-100 text-primary-800",
            focusRingInset,
          )}
        >
          <ListOrdered className="size-4" aria-hidden="true" />
        </button>

        <span aria-hidden="true" className="mx-1 h-5 w-px bg-border" />

        <button
          type="button"
          aria-label={t("undo")}
          disabled={readOnly || !(toolbarState?.canUndo ?? false)}
          onClick={() => editor?.chain().focus().undo().run()}
          className={cn(
            "inline-flex size-8 items-center justify-center rounded-md text-text-secondary transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
            "hover:bg-subtle disabled:cursor-not-allowed disabled:opacity-45",
            focusRingInset,
          )}
        >
          <Undo2 className="size-4" aria-hidden="true" />
        </button>

        <button
          type="button"
          aria-label={t("redo")}
          disabled={readOnly || !(toolbarState?.canRedo ?? false)}
          onClick={() => editor?.chain().focus().redo().run()}
          className={cn(
            "inline-flex size-8 items-center justify-center rounded-md text-text-secondary transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
            "hover:bg-subtle disabled:cursor-not-allowed disabled:opacity-45",
            focusRingInset,
          )}
        >
          <Redo2 className="size-4" aria-hidden="true" />
        </button>
      </div>

      <div className="relative">
        {showPlaceholder ? (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute top-2.5 left-3.5 right-3.5 text-body text-text-placeholder"
          >
            {placeholder}
          </span>
        ) : null}
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
