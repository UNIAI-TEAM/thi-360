"use client";

import { uploadQuestionAsset } from "@/features/questions/api";
import { cn, notify } from "@thi360/ui";
import type { Mark as PMMark, Node as PMNode } from "@tiptap/pm/model";
import { NodeSelection, Selection } from "@tiptap/pm/state";
import type { EditorView } from "@tiptap/pm/view";
import type { Editor } from "@tiptap/core";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import katex from "katex";
import {
  deferUntilMhchem,
  hasUnbalancedMath,
  markChargeSigns,
  sanitizeQuestionHtml,
  stripTrailingEmptyParagraph,
} from "@thi360/ui/rich-text";
import "katex/dist/katex.min.css";
import { useTranslations } from "next-intl";
import { useEffect, useImperativeHandle, useLayoutEffect, useMemo, useRef, useState, type AriaAttributes, type Ref } from "react";
import { createPortal } from "react-dom";
import { createRichTextExtensions } from "./extensions";
import { MathInputRule } from "./extensions/math-input-rule";
import { SlashMenu } from "./extensions/slash-menu";
import { INSERT_REGISTRY, registryEditorPlugins, slashMenuEntries, toolbarInsertEntries, type FieldContext, type InsertDialogModule, type InsertEntry } from "./insert/registry";
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
  /** Số bắt đầu đánh chỗ trống của ngữ liệu câu chùm (spec 3.4): chỗ trống n hiện `blankNumberStart + n − 1`; mặc định 1. Đổi số chỉ dựng lại hiển thị, không đổi HTML. */
  blankNumberStart?: number;
  /**
   * Gọi khi dán, trước mọi xử lý của ProseMirror (kể cả bước lọc HTML `transformPastedHTML`), với `clipboardData` gốc
   * chưa lọc (spec 4.10, ED-5 dùng để bắt nội dung Word). Trả `true` thì trình soạn chặn dán mặc định: không lọc, không chèn gì.
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
  if (deferUntilMhchem(latex, target, () => renderLatex(latex, displayMode, target))) return;
  try {
    katex.render(latex, target, { displayMode, throwOnError: true });
    markChargeSigns(latex, target);
  } catch {
    // LaTeX chưa hoàn chỉnh trong lúc gõ: hiện mã nguồn thay vì lỗi đỏ của KaTeX.
    target.textContent = displayMode ? `\\[${latex}\\]` : `\\(${latex}\\)`;
  }
}

const SANITIZE_CACHE_SIZE = 16;
const sanitizeCache = new Map<string, string>();

/**
 * `sanitizeQuestionHtml(...).html` nhớ theo (HTML, allowImages): một lần gõ phím đi qua `onUpdate`, effect đồng bộ và
 * `normalizedContent` với cùng chuỗi, nên chỉ lọc (parse DOM) một lần cho mỗi chuỗi thay vì 2–3 lần. Giữ ít mục, bỏ mục cũ nhất.
 */
function sanitizedHtml(html: string, allowImages: boolean): string {
  const key = `${allowImages ? 1 : 0}${html}`;
  const cached = sanitizeCache.get(key);
  if (cached !== undefined) return cached;
  const result = sanitizeQuestionHtml(html, { allowImages }).html;
  if (sanitizeCache.size >= SANITIZE_CACHE_SIZE) sanitizeCache.delete(sanitizeCache.keys().next().value as string);
  sanitizeCache.set(key, result);
  return result;
}

/**
 * Dạng chuẩn của nội dung để phát ra và để so với `value`: lọc theo danh sách cho phép rồi bỏ đúng một `<p></p>` rỗng ở
 * cuối (TrailingNode của TipTap thêm sau khối atom cuối, hoặc người dùng vừa nhấn Enter). Dùng chung cho `onChange` và
 * cho phần so sánh đồng bộ `value`, để hai bên luôn nhìn cùng một chuỗi: nếu không, bỏ đoạn rỗng ở một bên làm `value`
 * lệch HTML của trình soạn và `setContent` dựng lại nội dung giữa lúc người dùng gõ (mất con trỏ).
 */
function normalizedContent(html: string, allowImages: boolean): string {
  return stripTrailingEmptyParagraph(sanitizedHtml(html, allowImages));
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
  blankNumberStart = 1,
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
  const [expanded, setExpanded] = useState(false);

  // Mở rộng ô nhập: khóa cuộn nền và hỗ trợ Esc để thu gọn (khi không mở hộp Chèn).
  useEffect(() => {
    if (!expanded || typeof document === "undefined") return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || event.defaultPrevented || dialogState) return;
      // Không thu gọn khung mở rộng khi menu `/` hoặc popover/menu Radix bên trong đang mở và cần Esc để đóng trước.
      if (
        document.querySelector(
          '[data-radix-popper-content-wrapper], [role="menu"][data-state="open"], [role="listbox"]',
        )
      ) {
        return;
      }
      event.preventDefault();
      setExpanded(false);
    };
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [expanded, dialogState]);

  // Ô soạn render lại theo mỗi phím gõ (qua `value`): chỉ quét công thức khi `value` đổi.
  const mathWarning = useMemo(() => typeof document !== "undefined" && hasUnbalancedMath(value ?? ""), [value]);

  // Refs cho các handler mà TipTap chụp lúc tạo editor (extensions chỉ dựng một lần).
  const onChangeRef = useRef(onChange);
  const allowImagesRef = useRef(allowImages);
  const uploadErrorRef = useRef(onImageUploadError);
  const onRichPasteRef = useRef(onRichPaste);
  // `handleDoubleClick` của TipTap chụp lúc tạo editor: đi qua ref để luôn gọi bản `openEntry` mới nhất.
  const openEntryRef = useRef<(entry: InsertEntry, editing: unknown) => void>(() => {});
  // Menu `/` được dựng một lần cùng editor: mục (theo fieldContext), cách mở hộp và hàm dịch đi qua ref để luôn mới nhất.
  const slashEntriesRef = useRef<InsertEntry[]>([]);
  const slashSelectRef = useRef<(entry: InsertEntry) => void>(() => {});
  const translateRef = useRef<(key: string) => string>((key) => key);
  // Chưa từng focus thì vùng soạn chưa có con trỏ do người dùng đặt: `insertText` chèn ở cuối thay vì đầu tài liệu.
  const hasFocusedRef = useRef(false);
  // HTML đã phát qua onChange: lọc lặp phát hiện (vòng đồng bộ, khởi tạo rỗng → "<p></p>").
  const lastEmittedRef = useRef<string | null>(null);
  // Lần bấm mở hộp mới nhất: các lần tải bất đồng bộ về muộn hơn không được ghi đè hộp của lần bấm sau.
  const loadTokenRef = useRef(0);
  // Rời trang/hủy trình soạn trước khi tải xong: bỏ kết quả về muộn, không mở hộp và không báo lỗi.
  useEffect(() => {
    return () => {
      loadTokenRef.current += 1;
    };
  }, []);
  useEffect(() => {
    onChangeRef.current = onChange;
    allowImagesRef.current = allowImages;
    uploadErrorRef.current = onImageUploadError;
    onRichPasteRef.current = onRichPaste;
    openEntryRef.current = openEntry;
    slashEntriesRef.current = slashMenuEntries(fieldContext).filter((entry) => entry.id !== "image" || allowImages);
    slashSelectRef.current = onToolbarEntry;
    translateRef.current = (key) => t(key as never);
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
    const token = ++loadTokenRef.current;
    entry.load().then(
      (module) => {
        // Bấm nhanh hai nút Chèn: chỉ lần bấm sau cùng được mở, kết quả tải cũ hơn bị bỏ.
        if (token !== loadTokenRef.current) return;
        setDialogModule(module);
        setDialogState({ entry, editing });
      },
      () => {
        if (token === loadTokenRef.current) notify.error(t("insertLoadError"));
      },
    );
  }

  const editor = useEditor(
    {
      immediatelyRender: false,
      extensions: [
        ...createRichTextExtensions({ allowImages, placeholder, renderLatex, blankNumberStart }),
        SlashMenu.configure({
          getEntries: () => slashEntriesRef.current,
          onSelect: (entry) => slashSelectRef.current(entry),
          translate: (key) => translateRef.current(key),
        }),
        MathInputRule,
        ...editorPlugins,
      ],
      content: typeof document === "undefined" ? "" : sanitizedHtml(value ?? "", allowImages),
      editable: !disabled,
      editorProps: {
        attributes,
        // Chạy ở tầng sự kiện DOM: `handlePaste` của ProseMirror đến sau `transformPastedHTML` (bước lọc), quá muộn cho hợp đồng này.
        handleDOMEvents: {
          paste: (_view: EditorView, event: ClipboardEvent) => {
            // ED-5 bắt nội dung Word trước bộ lọc (bộ lọc bỏ chú thích chứa OMML): true → bỏ dán mặc định.
            if (!onRichPasteRef.current?.(event.clipboardData ?? null)) return false;
            event.preventDefault();
            return true;
          },
        },
        handlePaste: (view: EditorView, event: ClipboardEvent) => {
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
        const html = normalizedContent(current.getHTML(), allowImagesRef.current);
        if (html === lastEmittedRef.current) return;
        lastEmittedRef.current = html;
        onChangeRef.current(html);
      },
    },
    // placeholder của extension chỉ đọc lúc tạo; allowImages đổi schema lọc dán — dựng lại editor.
    [allowImages, placeholder, blankNumberStart],
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

  // Đồng bộ giá trị điều khiển: chỉ setContent khi `value` ngoài lệch nội dung hiện có. `value` là chuỗi mà chính trình soạn đã
  // phát (đã bỏ một đoạn rỗng cuối) hoặc dữ liệu ngoài, nên khớp nếu bằng HTML thô của trình soạn hoặc bằng bản đã bỏ đoạn
  // rỗng cuối của nó; không bỏ thêm lần nữa ở phía `value`, nếu không hai đoạn rỗng do người dùng tạo bị coi là lệch.
  // `lastEmittedRef` luôn là giá trị `onChange` sẽ phát cho nội dung hiện có: focus lần đầu làm TrailingNode thêm đoạn rỗng
  // mà không đổi nội dung thì không được phát `onChange`.
  useEffect(() => {
    if (!editor) return;
    const fromValue = sanitizedHtml(value ?? "", allowImages);
    const incoming = fromValue === "<p></p>" ? "" : fromValue;
    const raw = sanitizedHtml(editor.getHTML(), allowImages);
    if (incoming !== (raw === "<p></p>" ? "" : raw) && incoming !== stripTrailingEmptyParagraph(raw)) {
      editor.commands.setContent(incoming, { emitUpdate: false });
    }
    lastEmittedRef.current = normalizedContent(editor.getHTML(), allowImages);
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

  // Nút Hình mở hộp `insert/image` như mọi mục; đang chọn một hình thì mở lại hộp để sửa hình đó.
  function onToolbarEntry(entry: InsertEntry) {
    const reopening = selection?.entry.id === entry.id;
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
  const expandedTitle =
    ariaProps["aria-label"] ??
    (expanded && ariaProps["aria-labelledby"] && typeof document !== "undefined"
      ? document.getElementById(ariaProps["aria-labelledby"])?.textContent?.trim()
      : undefined) ??
    t("toolbar.expand");

  const editorBox = (
    <div
      data-expanded={expanded ? "true" : undefined}
      role={expanded ? "dialog" : undefined}
      aria-modal={expanded ? true : undefined}
      aria-label={expanded ? expandedTitle : undefined}
      className={cn(
        "rich-text-editor min-w-0 max-w-full rounded-md border bg-surface transition-colors duration-150 motion-reduce:transition-none",
        "focus-within:border-primary-text focus-within:ring-3 focus-within:ring-primary-text/15",
        invalid && "border-danger focus-within:ring-danger/15",
        !invalid && "border-border hover:border-border-strong",
        disabled && "cursor-not-allowed bg-subtle opacity-60",
        expanded && "fixed inset-4 z-40 flex flex-col rounded-xl shadow-2xl sm:inset-8",
        className,
      )}
    >
      {expanded && (
        <div className="flex items-center justify-between gap-3 border-b border-border bg-subtle/60 px-4 py-2.5">
          <span className="truncate text-label font-semibold text-text">{expandedTitle}</span>
          <span className="shrink-0 text-caption text-text-muted">Esc</span>
        </div>
      )}

      <RichTextToolbar
        editor={editor}
        disabled={disabled}
        insertEntries={insertEntries}
        selectedEntryId={selection?.entry.id ?? null}
        onOpenEntry={onToolbarEntry}
        expanded={expanded}
        onToggleExpand={() => setExpanded((current) => !current)}
      />

      {/* Khung "đang chọn" của mục registry khớp node/mark hiện tại (spec 4.1). */}
      {editor && SelectionPanel && selection && (
        <SelectionPanel
          editor={editor}
          target={selection.target}
          openDialog={() => openEntry(selection.entry, selection.editing)}
        />
      )}

      <EditorContent
        editor={editor}
        className={cn(expanded && "flex min-h-0 flex-1 flex-col overflow-hidden")}
      />

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
              // Đóng hộp: lần tải đang chờ (nếu có) không được mở lại hộp sau đó.
              loadTokenRef.current += 1;
              setDialogState(null);
              setDialogModule(null);
            }
          }}
          editing={dialogState.editing}
        />
      )}
    </div>
  );

  if (expanded && typeof document !== "undefined") {
    return (
      <>
        <div aria-hidden="true" className="min-h-36 rounded-md border border-dashed border-border bg-subtle/50" />
        {createPortal(
          <>
            <div aria-hidden="true" className="fixed inset-0 z-40 bg-overlay" onClick={() => setExpanded(false)} />
            {editorBox}
          </>,
          document.body,
        )}
      </>
    );
  }

  return editorBox;
}
