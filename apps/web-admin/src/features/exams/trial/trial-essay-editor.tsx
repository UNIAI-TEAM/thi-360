"use client";

import { IconButton, cn } from "@thi360/ui";
import type { EssayEditorProps } from "@thi360/ui/question";
import { sanitizeQuestionHtml, stripTrailingEmptyParagraph } from "@thi360/ui/rich-text";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Bold, Italic, List, ListOrdered, Redo2, Undo2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";

function normalizeEssayHtml(rawHtml: string): string {
  const sanitized = sanitizeQuestionHtml(rawHtml, { allowImages: false }).html;
  const trimmed = stripTrailingEmptyParagraph(sanitized);
  return trimmed === "<p></p>" ? "" : trimmed;
}

export function TrialEssayEditor({
  id,
  value,
  onChange,
  placeholder,
  "aria-label": ariaLabel,
  "aria-describedby": ariaDescribedBy,
  readOnly = false,
  minHeightClass,
}: EssayEditorProps) {
  const t = useTranslations("exams.trial.editor");
  const lastEmittedHtmlRef = useRef<string>(normalizeEssayHtml(value));
  const onChangeRef = useRef(onChange);
  const toolbarRef = useRef<HTMLDivElement>(null);
  const [tabStop, setTabStop] = useState(0);

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
          "rich-text w-full rounded-b-lg px-3.5 py-2.5 text-body text-text outline-hidden",
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
            "rich-text w-full rounded-b-lg px-3.5 py-2.5 text-body text-text outline-hidden",
            minHeightClass,
          ),
        },
      },
    });
  }, [editor, attrKey, id, ariaLabel, ariaDescribedBy, readOnly, minHeightClass]);

  const showPlaceholder = toolbarState?.isEmpty ?? value.trim().length === 0;

  function onToolbarKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const buttons = Array.from(toolbarRef.current?.querySelectorAll("button") ?? []);
    if (buttons.length === 0) return;
    const current = buttons.indexOf(event.target as HTMLButtonElement);
    let next = -1;
    switch (event.key) {
      case "ArrowRight":
        next = current < 0 ? 0 : (current + 1) % buttons.length;
        break;
      case "ArrowLeft":
        next = current < 0 ? buttons.length - 1 : (current - 1 + buttons.length) % buttons.length;
        break;
      case "Home":
        next = 0;
        break;
      case "End":
        next = buttons.length - 1;
        break;
      default:
        return;
    }
    event.preventDefault();
    buttons[next]?.focus();
  }

  type ToolbarButton = {
    key: string;
    label: string;
    icon: ReactNode;
    pressed?: boolean;
    disabled: boolean;
    onClick: () => void;
  };

  const formatButtons: ToolbarButton[] = [
    {
      key: "bold",
      label: t("bold"),
      icon: <Bold className="size-4" aria-hidden="true" />,
      pressed: toolbarState?.bold ?? false,
      disabled: readOnly || !editor?.can().toggleBold(),
      onClick: () => editor?.chain().focus().toggleBold().run(),
    },
    {
      key: "italic",
      label: t("italic"),
      icon: <Italic className="size-4" aria-hidden="true" />,
      pressed: toolbarState?.italic ?? false,
      disabled: readOnly || !editor?.can().toggleItalic(),
      onClick: () => editor?.chain().focus().toggleItalic().run(),
    },
    {
      key: "bulletList",
      label: t("bulletList"),
      icon: <List className="size-4" aria-hidden="true" />,
      pressed: toolbarState?.bulletList ?? false,
      disabled: readOnly || !editor?.can().toggleBulletList(),
      onClick: () => editor?.chain().focus().toggleBulletList().run(),
    },
    {
      key: "orderedList",
      label: t("orderedList"),
      icon: <ListOrdered className="size-4" aria-hidden="true" />,
      pressed: toolbarState?.orderedList ?? false,
      disabled: readOnly || !editor?.can().toggleOrderedList(),
      onClick: () => editor?.chain().focus().toggleOrderedList().run(),
    },
  ];

  const historyButtons: ToolbarButton[] = [
    {
      key: "undo",
      label: t("undo"),
      icon: <Undo2 className="size-4" aria-hidden="true" />,
      disabled: readOnly || !(toolbarState?.canUndo ?? false),
      onClick: () => editor?.chain().focus().undo().run(),
    },
    {
      key: "redo",
      label: t("redo"),
      icon: <Redo2 className="size-4" aria-hidden="true" />,
      disabled: readOnly || !(toolbarState?.canRedo ?? false),
      onClick: () => editor?.chain().focus().redo().run(),
    },
  ];

  const renderToolbarButton = (btn: ToolbarButton, index: number) => (
    <IconButton
      key={btn.key}
      type="button"
      size="md"
      icon={btn.icon}
      tooltip={btn.label}
      aria-label={btn.label}
      aria-pressed={btn.pressed}
      aria-disabled={btn.disabled || undefined}
      tabIndex={index === tabStop ? 0 : -1}
      onFocus={() => setTabStop(index)}
      onMouseDown={(event) => {
        if (btn.disabled) {
          event.preventDefault();
        }
      }}
      onClick={(event) => {
        if (btn.disabled) {
          event.preventDefault();
          return;
        }
        btn.onClick();
      }}
      className={cn(
        "text-text-secondary transition-colors motion-reduce:transition-none pointer-coarse:min-h-11 pointer-coarse:min-w-11",
        "hover:bg-subtle aria-disabled:cursor-not-allowed aria-disabled:opacity-45 aria-disabled:hover:bg-transparent",
        btn.pressed &&
          "relative bg-primary-100 text-primary-800 hover:bg-primary-100 after:pointer-events-none after:absolute after:inset-0 after:rounded-lg after:border-2 after:border-primary-600",
      )}
    />
  );

  return (
    <div
      className={cn(
        "rounded-lg border border-border-strong bg-surface transition-colors motion-reduce:transition-none",
        "focus-within:border-primary-text focus-within:ring-3 focus-within:ring-primary-text/15",
        readOnly && "bg-subtle",
      )}
    >
      <div
        ref={toolbarRef}
        role="toolbar"
        aria-label={t("toolbarLabel")}
        onKeyDown={onToolbarKeyDown}
        className="flex flex-wrap items-center gap-1 border-b border-border px-2 py-1.5"
      >
        {formatButtons.map((btn, idx) => renderToolbarButton(btn, idx))}
        <span aria-hidden="true" className="mx-1 h-5 w-px bg-border" />
        {historyButtons.map((btn, idx) => renderToolbarButton(btn, formatButtons.length + idx))}
      </div>

      <div className="relative">
        {showPlaceholder ? (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute top-2.5 right-3.5 left-3.5 text-body text-text-placeholder"
          >
            {placeholder}
          </span>
        ) : null}
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
