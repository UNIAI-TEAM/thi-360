"use client";

import { uploadQuestionAsset } from "@/features/questions/api";
import { cn } from "@thi360/ui";
import type { Mark as PMMark, Node as PMNode } from "@tiptap/pm/model";
import { NodeSelection, Selection } from "@tiptap/pm/state";
import type { EditorView } from "@tiptap/pm/view";
import type { Editor } from "@tiptap/core";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import katex from "katex";
import "katex/dist/katex.min.css";
import { useTranslations } from "next-intl";
import { useEffect, useImperativeHandle, useLayoutEffect, useMemo, useRef, useState, type AriaAttributes, type Ref } from "react";
import { createRichTextExtensions } from "./extensions";
import { MathInputRule } from "./extensions/math-input-rule";
import { SlashMenu } from "./extensions/slash-menu";
import { stripTrailingEmptyParagraph } from "./html-contract";
import { INSERT_REGISTRY, registryEditorPlugins, toolbarInsertEntries, type FieldContext, type InsertDialogModule, type InsertEntry } from "./insert/registry";
import { hasUnbalancedMath, sanitizeQuestionHtml } from "./sanitize";
import { RichTextToolbar } from "./toolbar/toolbar";
import "./rich-text.css";

/** Tay cầm điều khiển trình soạn từ ngoài, qua `handleRef`. */
export interface RichTextEditorHandle {
  /** Chữ thuần trước và sau điểm chèn (con trỏ hiện tại; cuối tài liệu nếu chưa từng focus). */
  getInsertionContext: () => { before: string; after: string };
  /** Chèn chữ thuần tại con trỏ (thay vùng đang chọn); cuối tài liệu nếu chưa từng focus. `false` khi chưa chèn được. */
  insertText: (text: string) => boolean;
}

export interface RichTextEditorProps extends AriaAttributes {
  /** HTML nội dung (đã lọc theo spec 3.x khi lưu); component giữ đồng bộ hai chiều. */
  value: string;
  /** Gọi với HTML đã lọc ở mọi thay đổi — luôn an toàn đưa thẳng lên request lưu. */
  onChange: (html: string) => void;
  /** `false` ẩn nút ảnh và chặn dán/thả ảnh — cho trường chỉ chữ như hướng dẫn làm bài (spec 5.2). */
  allowImages?: boolean;
  /** Ngữ cảnh của trường đang soạn (spec 4.1): quyết định mục Chèn nào hiện trên thanh công cụ. */
  fieldContext?: FieldContext;
  /**
   * Gọi trước bước lọc HTML khi dán, với `clipboardData` gốc (spec 4.10, ED-5 dùng để bắt nội dung
   * Word). Trả `true` thì trình soạn bỏ qua dán mặc định.
   */
  onRichPaste?: (clipboardData: DataTransfer | null) => boolean;
  id?: string;
  disabled?: boolean;
  placeholder?: string;
  /** Đánh dấu lỗi (viền đỏ + `aria-invalid`), đi kèm `error` của `FormField`. */
  invalid?: boolean;
  className?: string;
  /** Gọi khi ảnh tải lên thất bại (mặc định: bỏ qua lặng lẽ, không chèn). */
  onImageUploadError?: (error: unknown) => void;
  /** Tay cầm để nơi dùng chèn chữ vào đúng vị trí con trỏ (vd. nút "Thêm ô trống"). */
  handleRef?: Ref<RichTextEditorHandle>;
}

function renderLatex(latex: string, displayMode: boolean, target: HTMLElement) {
  try {
    katex.render(latex, target, { displayMode, throwOnError: true });
  } catch {
    // LaTeX chưa hoàn chỉnh trong lúc gõ: hiện mã nguồn thay vì lỗi đỏ của KaTeX.
    target.textContent = displayMode ? `\\[${latex}\\]` : `\\(${latex}\\)`;
  }
}

/** Mảnh tệp ảnh của một sự kiện dán/thả. */
function imageFilesOf(transfer: DataTransfer | null): File[] {
  if (!transfer) return [];
  const fromItems = [...transfer.items]
    .filter((item) => item.kind === "file" && item.type.startsWith("image/"))
    .map((item) => item.getAsFile())
    .filter((file): file is File => file !== null);
  if (fromItems.length > 0) return fromItems;
  return [...transfer.files].filter((file) => file.type.startsWith("image/"));
}

/** Một node hoặc mark trong vùng chọn khớp `openForSelection` của mục registry nào. */
interface SelectionMatch {
  entry: InsertEntry;
  target: PMNode | PMMark;
  /** Dữ liệu khởi tạo cho hộp khi mở lại (`openForSelection` trả `true` thì `null`). */
  editing: unknown;
}

/** Node/mark khớp đầu tiên trong `INSERT_REGISTRY` cho vùng chọn hiện tại; `null` khi không có. */
function matchSelection(editor: Editor | null): SelectionMatch | null {
  if (!editor) return null;
  const { selection } = editor.state;
  if (selection instanceof NodeSelection) {
    const node = selection.node;
    for (const entry of INSERT_REGISTRY) {
      const opened = entry.openForSelection(node);
      if (opened !== null && opened !== undefined) {
        return { entry, target: node, editing: opened === true ? null : opened };
      }
    }
    return null;
  }
  const marks: PMMark[] = selection.empty
    ? [...selection.$from.marks()]
    : (() => {
        const found: PMMark[] = [];
        editor.state.doc.nodesBetween(selection.from, selection.to, (node) => {
          for (const mark of node.marks) found.push(mark);
        });
        return found;
      })();
  for (const mark of marks) {
    for (const entry of INSERT_REGISTRY) {
      const opened = entry.openForSelection(mark);
      if (opened !== null && opened !== undefined) {
        return { entry, target: mark, editing: opened === true ? null : opened };
      }
    }
  }
  return null;
}

/**
 * Trình soạn nội dung HTML của câu hỏi/bài thi (TipTap + KaTeX, spec UNI-2638): định dạng chữ,
 * danh sách, các khối chèn qua registry (công thức, hóa học, ký hiệu, bảng, hình, đoạn trích,
 * gạch chân đánh nhãn, chỗ trống, sơ đồ lai) và ảnh. HTML dán vào và HTML trả về qua `onChange`
 * đều đã qua bộ lọc mirror `QuestionHtmlSanitizer` của backend.
 */
export function RichTextEditor({
  value,
  onChange,
  allowImages = true,
  fieldContext = "default",
  onRichPaste,
  id,
  disabled = false,
  placeholder,
  invalid = false,
  className,
  onImageUploadError,
  handleRef,
  ...ariaProps
}: RichTextEditorProps) {
  const t = useTranslations("richText");
  const [dialogState, setDialogState] = useState<{ entry: InsertEntry; editing: unknown } | null>(null);
  const [dialogModule, setDialogModule] = useState<InsertDialogModule | null>(null);
  const [imageUploading, setImageUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const mathWarning = typeof document !== "undefined" && hasUnbalancedMath(value ?? "");

  // Refs cho các handler mà TipTap chụp lúc tạo editor (extensions chỉ dựng một lần).
  const onChangeRef = useRef(onChange);
  const allowImagesRef = useRef(allowImages);
  const uploadErrorRef = useRef(onImageUploadError);
  const onRichPasteRef = useRef(onRichPaste);
  // `handleDoubleClick` của TipTap chụp lúc tạo editor: đi qua ref để luôn gọi bản `openEntry` mới nhất.
  const openEntryRef = useRef<(entry: InsertEntry, editing: unknown) => void>(() => {});
  // Chưa từng focus thì vùng soạn chưa có con trỏ do người dùng đặt: `insertText` chèn ở cuối thay vì đầu tài liệu.
  const hasFocusedRef = useRef(false);
  // HTML đã phát qua onChange: lọc lặp phát hiện (vòng đồng bộ, khởi tạo rỗng → "<p></p>").
  const lastEmittedRef = useRef<string | null>(null);
  useEffect(() => {
    onChangeRef.current = onChange;
    allowImagesRef.current = allowImages;
    uploadErrorRef.current = onImageUploadError;
    onRichPasteRef.current = onRichPaste;
    openEntryRef.current = openEntry;
  });

  const insertEntries = useMemo(
    () => toolbarInsertEntries(fieldContext).filter((entry) => entry.id !== "image" || allowImages),
    [fieldContext, allowImages],
  );
  // `editorPlugins` của các mục registry (ví dụ decoration của ED-5) — một bản cho editor này.
  const editorPlugins = useMemo(() => registryEditorPlugins(), []);

  const attributes: Record<string, string> = Object.fromEntries(
    [
      ["class", "rich-text-editor-content"],
      ["id", id],
      ...Object.entries(ariaProps),
      ["aria-invalid", invalid || ariaProps["aria-invalid"] ? "true" : undefined],
    ]
      .filter((entry) => entry[1] !== undefined)
      .map(([key, val]) => [key, String(val)]),
  );

  function openEntry(entry: InsertEntry, editing: unknown) {
    void entry.load().then((module) => {
      setDialogModule(module);
      setDialogState({ entry, editing });
    });
  }

  const editor = useEditor(
    {
      immediatelyRender: false,
      extensions: [
        ...createRichTextExtensions({ allowImages, placeholder, renderLatex }),
        SlashMenu,
        MathInputRule,
        ...editorPlugins,
      ],
      content: typeof document === "undefined" ? "" : sanitizeQuestionHtml(value ?? "", { allowImages }).html,
      editable: !disabled,
      editorProps: {
        attributes,
        handlePaste: (view: EditorView, event: ClipboardEvent) => {
          // ED-5 bắt nội dung Word trước bộ lọc (bộ lọc bỏ chú thích chứa OMML): trả true → bỏ qua dán mặc định.
          if (onRichPasteRef.current?.(event.clipboardData ?? null)) return true;
          // Word/Docs đưa kèm ảnh chụp của chính đoạn văn: có `text/html` thì để TipTap dán HTML, chỉ dán tệp ảnh thuần mới tải lên.
          if (event.clipboardData?.getData("text/html")) return false;
          const files = imageFilesOf(event.clipboardData);
          if (files.length === 0) return false;
          if (!allowImagesRef.current) return true;
          void insertImageFiles(view, files);
          return true;
        },
        handleDrop: (view: EditorView, event: DragEvent) => {
          const files = imageFilesOf(event.dataTransfer);
          if (files.length === 0) return false;
          event.preventDefault();
          if (!allowImagesRef.current) return true;
          void insertImageFiles(view, files);
          return true;
        },
        // Nhấp đúp một node có mục registry thì mở lại hộp của mục đó (spec 4.1).
        handleDoubleClick: (view: EditorView, pos: number) => {
          const node = view.state.doc.nodeAt(pos);
          if (!node) return false;
          for (const entry of INSERT_REGISTRY) {
            const opened = entry.openForSelection(node);
            if (opened !== null && opened !== undefined) {
              openEntryRef.current(entry, opened === true ? null : opened);
              return true;
            }
          }
          return false;
        },
      },
      onFocus: () => {
        hasFocusedRef.current = true;
      },
      onUpdate: ({ editor: current }) => {
        let html = sanitizeQuestionHtml(current.getHTML(), {
          allowImages: allowImagesRef.current,
        }).html;
        // TipTap (TrailingNode) thêm một `<p></p>` sau khối atom cuối: bỏ trước khi phát để không lưu đoạn thừa.
        html = html === "<p></p>" ? "" : stripTrailingEmptyParagraph(html);
        if (html === lastEmittedRef.current) return;
        lastEmittedRef.current = html;
        onChangeRef.current(html);
      },
    },
    // placeholder của extension chỉ đọc lúc tạo; allowImages đổi schema lọc dán — dựng lại editor.
    [allowImages, placeholder],
  );

  // Node/mark đang chọn khớp mục registry nào — cho khung "đang chọn" và `aria-pressed` của nút Chèn.
  // Selector trả bản nhẹ (id + vị trí + khóa attrs); bản đầy đủ so ở `selection` dưới.
  const selectionDescriptor = useEditorState({
    editor,
    selector: (ctx) => {
      if (!ctx.editor) return null;
      const match = matchSelection(ctx.editor);
      if (!match) return null;
      const { selection } = ctx.editor.state;
      return {
        id: match.entry.id,
        pos: selection.from,
        key: JSON.stringify(match.target.attrs ?? {}),
      };
    },
    equalityFn: (a, b) => (a && b ? a.id === b.id && a.pos === b.pos && a.key === b.key : a === b),
  });

  const selection = useMemo<SelectionMatch | null>(() => {
    if (!editor || !selectionDescriptor) return null;
    return matchSelection(editor);
  }, [editor, selectionDescriptor]);

  // Đồng bộ giá trị điều khiển: chỉ setContent khi `value` ngoài lệch HTML hiện có.
  useEffect(() => {
    if (!editor) return;
    const normalize = (html: string) => (html === "<p></p>" ? "" : html);
    const incoming = normalize(sanitizeQuestionHtml(value ?? "", { allowImages }).html);
    const current = normalize(sanitizeQuestionHtml(editor.getHTML(), { allowImages }).html);
    if (incoming !== current) {
      editor.commands.setContent(incoming, { emitUpdate: false });
      lastEmittedRef.current = incoming;
    }
  }, [editor, value, allowImages]);

  useEffect(() => {
    editor?.setEditable(!disabled, false);
  }, [editor, disabled]);

  // `useEditor` chỉ dựng editorProps lúc tạo (deps là allowImages/placeholder): id, aria-* và trạng thái lỗi đổi sau đó
  // (lỗi xác thực, nhãn của dòng đổi chỗ) phải đẩy vào DOM của vùng soạn tại đây. Layout effect để thuộc tính có
  // trước frame kế (biểu mẫu tìm `[aria-invalid]` để focus ngay sau khi gửi lỗi). Khóa chuỗi để effect không chạy
  // lại mỗi lần render.
  const attributesKey = JSON.stringify(attributes);
  useLayoutEffect(() => {
    if (!editor || editor.isDestroyed) return;
    editor.setOptions({ editorProps: { ...editor.options.editorProps, attributes: JSON.parse(attributesKey) } });
  }, [editor, attributesKey]);

  // Ảnh thả/dán: tải lên rồi chèn `img` kèm `data-asset-id` (backend viết lại `src` từ id này).
  async function insertImageFiles(view: EditorView, files: File[]) {
    for (const file of files) {
      try {
        const asset = await uploadQuestionAsset(file, "IMAGE");
        const imageType = view.state.schema.nodes.image;
        if (!imageType) return;
        const node = imageType.create({
          src: asset.url,
          alt: asset.fileName,
          dataAssetId: asset.id,
        });
        view.dispatch(view.state.tr.replaceSelectionWith(node));
      } catch (error) {
        uploadErrorRef.current?.(error);
      }
    }
  }

  async function insertImageFile(file: File) {
    if (!editor) return;
    setImageUploading(true);
    try {
      const asset = await uploadQuestionAsset(file, "IMAGE");
      editor
        .chain()
        .focus()
        .insertContent({ type: "image", attrs: { src: asset.url, alt: asset.fileName, dataAssetId: asset.id } })
        .run();
    } catch (error) {
      onImageUploadError?.(error);
    } finally {
      setImageUploading(false);
    }
  }

  // Tạm cho tới khi ED-3 (UNI-2643) có hộp Hình: nút Hình chọn tệp và tải lên thẳng như trước; đang chọn một hình
  // của registry thì mở lại hộp của mục như mọi mục khác.
  function onToolbarEntry(entry: InsertEntry) {
    const reopening = selection?.entry.id === entry.id;
    if (entry.id === "image" && !reopening) {
      if (!imageUploading) fileInputRef.current?.click();
      return;
    }
    openEntry(entry, reopening ? selection.editing : null);
  }

  useImperativeHandle(
    handleRef,
    () => ({
      getInsertionContext() {
        if (!editor || editor.isDestroyed) return { before: "", after: "" };
        const { doc, selection } = editor.state;
        const { from, to } = hasFocusedRef.current ? selection : Selection.atEnd(doc);
        return { before: doc.textBetween(0, from, "\n"), after: doc.textBetween(to, doc.content.size, "\n") };
      },
      insertText(text: string) {
        if (!editor || editor.isDestroyed || !editor.isEditable) return false;
        return editor
          .chain()
          .focus(hasFocusedRef.current ? null : "end")
          .command(({ tr }) => {
            tr.insertText(text);
            return true;
          })
          .run();
      },
    }),
    [editor],
  );

  const DialogComponent = dialogModule?.default;
  const SelectionPanel = selection?.entry.selectionPanel;

  return (
    <div
      className={cn(
        "rich-text-editor rounded-md border bg-surface transition-colors duration-150 motion-reduce:transition-none",
        "focus-within:border-primary-text focus-within:ring-3 focus-within:ring-primary-text/15",
        invalid && "border-danger focus-within:ring-danger/15",
        !invalid && "border-border hover:border-border-strong",
        disabled && "cursor-not-allowed bg-subtle opacity-60",
        className,
      )}
    >
      <RichTextToolbar
        editor={editor}
        disabled={disabled}
        insertEntries={insertEntries}
        selectedEntryId={selection?.entry.id ?? null}
        onOpenEntry={onToolbarEntry}
      />
      {allowImages && (
        <input
          ref={fileInputRef}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = "";
            if (file) void insertImageFile(file);
          }}
        />
      )}

      {/* Khung "đang chọn" của mục registry khớp node/mark hiện tại (spec 4.1). */}
      {editor && SelectionPanel && selection && (
        <SelectionPanel
          editor={editor}
          target={selection.target}
          openDialog={() => openEntry(selection.entry, selection.editing)}
        />
      )}

      <EditorContent editor={editor} />

      {mathWarning && (
        <p role="alert" className="border-t border-border px-3 py-1.5 text-caption text-warning">
          {t("mathUnbalanced")}
        </p>
      )}

      {editor && dialogState && DialogComponent && (
        <DialogComponent
          editor={editor}
          open={true}
          onOpenChange={(open: boolean) => {
            if (!open) {
              setDialogState(null);
              setDialogModule(null);
            }
          }}
          editing={dialogState.editing}
        />
      )}
    </div>
  );
}
