"use client";

import { IconButton } from "@thi360/ui";
import type { Editor } from "@tiptap/core";
import { useEditorState } from "@tiptap/react";
import {
  Bold,
  Italic,
  List,
  ListOrdered,
  Redo2,
  Strikethrough,
  Subscript as SubscriptIcon,
  Superscript as SuperscriptIcon,
  Underline,
  Undo2,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import type { InsertEntry } from "../insert/registry";

export interface RichTextToolbarProps {
  editor: Editor | null;
  disabled?: boolean;
  /** Mục của nhóm Chèn đã lọc theo `fieldContext` (và `allowImages`). */
  insertEntries: InsertEntry[];
  /** Id mục khớp node/mark đang chọn — nút đó hiện "đang bật" và bấm vào mở lại hộp thay vì chèn mới. */
  selectedEntryId: string | null;
  onOpenEntry: (entry: InsertEntry) => void;
}

type ToolbarButton = {
  key: string;
  icon: ReactNode;
  label: string;
  pressed?: boolean;
  disabled?: boolean;
  onClick: () => void;
};

type ToolbarItem = ToolbarButton | "separator";

function Separator() {
  return <span aria-hidden="true" className="mx-1 h-6 w-px bg-border" />;
}

/**
 * Thanh công cụ của trình soạn (spec 4.1, bản vẽ `SoanThaoTongQuan`): Hoàn tác/Làm lại, nhóm
 * "Văn bản" (định dạng + danh sách) rồi nhóm "Chèn" từ registry. Cả thanh là một điểm dừng Tab:
 * mũi tên trái/phải đi giữa các nút (roving tabindex), Home/End về đầu/cuối, Tab rời thanh.
 */
export function RichTextToolbar({ editor, disabled = false, insertEntries, selectedEntryId, onOpenEntry }: RichTextToolbarProps) {
  const t = useTranslations("richText");
  const toolbarRef = useRef<HTMLDivElement>(null);
  // Nút nhận Tab duy nhất của thanh; mũi tên không đổi nó — lần Tab sau vào lại nút này.
  const [tabStop, setTabStop] = useState(0);

  const state = useEditorState({
    editor,
    selector: (ctx) =>
      ctx.editor
        ? {
            bold: ctx.editor.isActive("bold"),
            italic: ctx.editor.isActive("italic"),
            underline: ctx.editor.isActive("underline"),
            strike: ctx.editor.isActive("strike"),
            subscript: ctx.editor.isActive("subscript"),
            superscript: ctx.editor.isActive("superscript"),
            bulletList: ctx.editor.isActive("bulletList"),
            orderedList: ctx.editor.isActive("orderedList"),
            canUndo: ctx.editor.can().undo(),
            canRedo: ctx.editor.can().redo(),
          }
        : null,
  });

  const chain = () => editor?.chain().focus();

  const textButtons: ToolbarButton[] = [
    {
      key: "bold",
      icon: <Bold />,
      label: t("toolbar.bold"),
      pressed: state?.bold,
      disabled: disabled || !editor?.can().toggleBold(),
      onClick: () => chain()?.toggleBold().run(),
    },
    {
      key: "italic",
      icon: <Italic />,
      label: t("toolbar.italic"),
      pressed: state?.italic,
      disabled: disabled || !editor?.can().toggleItalic(),
      onClick: () => chain()?.toggleItalic().run(),
    },
    {
      key: "underline",
      icon: <Underline />,
      label: t("toolbar.underline"),
      pressed: state?.underline,
      disabled: disabled || !editor?.can().toggleUnderline(),
      onClick: () => chain()?.toggleUnderline().run(),
    },
    {
      key: "strike",
      icon: <Strikethrough />,
      label: t("toolbar.strike"),
      pressed: state?.strike,
      disabled: disabled || !editor?.can().toggleStrike(),
      onClick: () => chain()?.toggleStrike().run(),
    },
    {
      key: "subscript",
      icon: <SubscriptIcon />,
      label: t("toolbar.subscript"),
      pressed: state?.subscript,
      disabled: disabled || !editor?.can().toggleSubscript(),
      onClick: () => chain()?.toggleSubscript().run(),
    },
    {
      key: "superscript",
      icon: <SuperscriptIcon />,
      label: t("toolbar.superscript"),
      pressed: state?.superscript,
      disabled: disabled || !editor?.can().toggleSuperscript(),
      onClick: () => chain()?.toggleSuperscript().run(),
    },
    {
      key: "bulletList",
      icon: <List />,
      label: t("toolbar.bulletList"),
      pressed: state?.bulletList,
      disabled: disabled || !editor?.can().toggleBulletList(),
      onClick: () => chain()?.toggleBulletList().run(),
    },
    {
      key: "orderedList",
      icon: <ListOrdered />,
      label: t("toolbar.orderedList"),
      pressed: state?.orderedList,
      disabled: disabled || !editor?.can().toggleOrderedList(),
      onClick: () => chain()?.toggleOrderedList().run(),
    },
  ];

  const historyButtons: ToolbarButton[] = [
    {
      key: "undo",
      icon: <Undo2 />,
      label: t("toolbar.undo"),
      disabled: disabled || !state?.canUndo,
      onClick: () => chain()?.undo().run(),
    },
    {
      key: "redo",
      icon: <Redo2 />,
      label: t("toolbar.redo"),
      disabled: disabled || !state?.canRedo,
      onClick: () => chain()?.redo().run(),
    },
  ];

  const insertButtons: ToolbarButton[] = insertEntries.map((entry) => {
    const Icon = entry.icon;
    return {
      key: entry.id,
      icon: <Icon />,
      label: t(entry.labelKey),
      pressed: entry.id === selectedEntryId,
      disabled: disabled || !editor,
      onClick: () => onOpenEntry(entry),
    };
  });

  const items: ToolbarItem[] = [
    ...historyButtons,
    "separator",
    ...textButtons,
    ...(insertButtons.length > 0 ? ["separator" as const, ...insertButtons] : []),
  ];
  const buttonCount = items.filter((item) => item !== "separator").length;
  const effectiveTabStop = Math.min(tabStop, Math.max(buttonCount - 1, 0));

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
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

  let buttonIndex = -1;
  const groups: { label?: string; start: number; end: number }[] = [
    { start: 0, end: historyButtons.length },
    { label: t("toolbar.groupText"), start: historyButtons.length + 1, end: historyButtons.length + 1 + textButtons.length },
  ];
  if (insertButtons.length > 0) {
    groups.push({ label: t("toolbar.groupInsert"), start: historyButtons.length + 1 + textButtons.length + 1, end: items.length });
  }

  return (
    <div
      ref={toolbarRef}
      className="flex flex-wrap items-center gap-0.5 border-b border-border px-1.5 py-1"
      role="toolbar"
      aria-label={t("toolbar.label")}
      onKeyDown={onKeyDown}
    >
      {groups.map((group, groupIndex) => (
        <div key={groupIndex} role="group" aria-label={group.label ?? t("toolbar.groupHistory")} className="flex items-center gap-0.5">
          {group.label && <span className="mx-1 text-caption font-medium text-text-muted">{group.label}</span>}
          {items.slice(group.start, group.end).map((item) => {
            if (item === "separator") return <Separator key={`sep-${groupIndex}`} />;
            buttonIndex += 1;
            const index = buttonIndex;
            return (
              <IconButton
                key={item.key}
                size="md"
                icon={item.icon}
                aria-label={item.label}
                aria-pressed={item.pressed ?? false}
                aria-disabled={item.disabled}
                tabIndex={index === effectiveTabStop ? 0 : -1}
                onFocus={() => setTabStop(index)}
                onClick={item.onClick}
              />
            );
          })}
          {groupIndex < groups.length - 1 && <Separator />}
        </div>
      ))}
    </div>
  );
}
