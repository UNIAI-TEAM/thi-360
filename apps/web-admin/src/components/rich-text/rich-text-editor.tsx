"use client";

import { uploadQuestionAsset } from "@/features/questions/api";
import { Button, cn, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, IconButton, Input } from "@thi360/ui";
import { NodeSelection } from "@tiptap/pm/state";
import type { EditorView } from "@tiptap/pm/view";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Image } from "@tiptap/extension-image";
import { Subscript } from "@tiptap/extension-subscript";
import { Superscript } from "@tiptap/extension-superscript";
import { TableKit } from "@tiptap/extension-table";
import { Placeholder } from "@tiptap/extensions";
import katex from "katex";
import "katex/dist/katex.min.css";
import {
  Bold,
  ImagePlus,
  Italic,
  List,
  ListOrdered,
  Sigma,
  Strikethrough,
  Subscript as SubscriptIcon,
  Superscript as SuperscriptIcon,
  Table,
  Underline,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useLayoutEffect, useRef, useState, type AriaAttributes } from "react";
import { HtmlSanitize, MathBlock, MathCommands, MathInline } from "./extensions";
import { hasUnbalancedMath, sanitizeQuestionHtml } from "./sanitize";
import "./rich-text.css";

export interface RichTextEditorProps extends AriaAttributes {
  /** HTML nội dung (đã lọc theo spec 4.2 khi lưu); component giữ đồng bộ hai chiều. */
  value: string;
  /** Gọi với HTML đã lọc ở mọi thay đổi — luôn an toàn đưa thẳng lên request lưu. */
  onChange: (html: string) => void;
  /** `false` ẩn nút ảnh và chặn dán/thả ảnh — cho trường chỉ chữ như hướng dẫn làm bài (spec 5.2). */
  allowImages?: boolean;
  id?: string;
  disabled?: boolean;
  placeholder?: string;
  /** Đánh dấu lỗi (viền đỏ + `aria-invalid`), đi kèm `error` của `FormField`. */
  invalid?: boolean;
  className?: string;
  /** Gọi khi ảnh tải lên thất bại (mặc định: bỏ qua lặng lẽ, không chèn). */
  onImageUploadError?: (error: unknown) => void;
}


const MATH_NODE_NAMES = new Set(["mathInline", "mathBlock"]);

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

/**
 * Trình soạn nội dung HTML của câu hỏi/bài thi (TipTap + KaTeX, spec 4.2/9, UNI-2617): định dạng
 * chữ, danh sách, bảng, công thức `\( … \)`/`\[ … \]` dưới dạng chip KaTeX, ảnh tải lên qua
 * `uploadQuestionAsset`. HTML dán vào và HTML trả về qua `onChange` đều đã qua bộ lọc mirror
 * `QuestionHtmlSanitizer` của backend — thẻ/thuộc tính ngoài danh sách bị loại.
 */
export function RichTextEditor({
  value,
  onChange,
  allowImages = true,
  id,
  disabled = false,
  placeholder,
  invalid = false,
  className,
  onImageUploadError,
  ...ariaProps
}: RichTextEditorProps) {
  const t = useTranslations("richText");
  const [formulaOpen, setFormulaOpen] = useState(false);
  const [formulaLatex, setFormulaLatex] = useState("");
  const [formulaDisplay, setFormulaDisplay] = useState(false);
  const [imageUploading, setImageUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const mathWarning = typeof document !== "undefined" && hasUnbalancedMath(value ?? "");

  // Refs cho các handler mà TipTap chụp lúc tạo editor (extensions chỉ dựng một lần).
  const onChangeRef = useRef(onChange);
  const allowImagesRef = useRef(allowImages);
  const uploadErrorRef = useRef(onImageUploadError);
  // HTML đã phát qua onChange: lọc lặp phát hiện (vòng đồng bộ, khởi tạo rỗng → "<p></p>").
  const lastEmittedRef = useRef<string | null>(null);
  useEffect(() => {
    onChangeRef.current = onChange;
    allowImagesRef.current = allowImages;
    uploadErrorRef.current = onImageUploadError;
  });

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

  const editor = useEditor(
    {
      immediatelyRender: false,
      extensions: [
        StarterKit.configure({
          // Schema chỉ gồm các thẻ của allowlist spec 4.2.
          blockquote: false,
          code: false,
          codeBlock: false,
          heading: false,
          horizontalRule: false,
          link: false,
        }),
        Placeholder.configure({ placeholder: () => placeholder ?? "" }),
        Subscript,
        Superscript,
        TableKit.configure({ table: { resizable: false } }),
        Image.extend({
          addAttributes() {
            return {
              ...this.parent?.(),
              dataAssetId: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute("data-asset-id"),
                renderHTML: (attributes: Record<string, unknown>) =>
                  attributes.dataAssetId ? { "data-asset-id": attributes.dataAssetId as string } : {},
              },
            };
          },
        }).configure({ allowBase64: false }),
        MathInline.configure({ renderLatex }),
        MathBlock.configure({ renderLatex }),
        MathCommands,
        HtmlSanitize.configure({ allowImages }),
      ],
      content: typeof document === "undefined" ? "" : sanitizeQuestionHtml(value ?? "", { allowImages }).html,
      editable: !disabled,
      editorProps: {
        attributes,
        handlePaste: (view: EditorView, event: ClipboardEvent) => {
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
      },
      onUpdate: ({ editor: current }) => {
        let html = sanitizeQuestionHtml(current.getHTML(), {
          allowImages: allowImagesRef.current,
        }).html;
        // Tài liệu rỗng của TipTap tuần tự hóa thành "<p></p>": đưa về "" cho hợp đồng nội dung.
        if (html === "<p></p>") html = "";
        if (html === lastEmittedRef.current) return;
        lastEmittedRef.current = html;
        onChangeRef.current(html);
      },
    },
    // placeholder của extension chỉ đọc lúc tạo; allowImages đổi schema lọc dán — dựng lại editor.
    [allowImages, placeholder],
  );

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
        .insertContent({
          type: "image",
          attrs: { src: asset.url, alt: asset.fileName, dataAssetId: asset.id },
        })
        .run();
    } catch (error) {
      onImageUploadError?.(error);
    } finally {
      setImageUploading(false);
    }
  }

  function openFormulaDialog() {
    if (!editor) return;
    const { selection } = editor.state;
    if (selection instanceof NodeSelection && MATH_NODE_NAMES.has(selection.node.type.name)) {
      setFormulaLatex(typeof selection.node.attrs.latex === "string" ? selection.node.attrs.latex : "");
      setFormulaDisplay(selection.node.type.name === "mathBlock");
    } else {
      setFormulaLatex("");
      setFormulaDisplay(false);
    }
    setFormulaOpen(true);
  }

  function applyFormula() {
    if (!editor) return;
    editor.chain().focus().setMath({ latex: formulaLatex, display: formulaDisplay }).run();
    setFormulaOpen(false);
  }

  const toolbar = useEditorState({
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
          }
        : null,
  });

  const chain = () => editor?.chain().focus();

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
      <div className="flex flex-wrap items-center gap-0.5 border-b border-border px-1.5 py-1" role="toolbar" aria-label={t("toolbar.label")}>
        <IconButton
          size="md"
          icon={<Bold />}
          aria-label={t("toolbar.bold")}
          aria-pressed={toolbar?.bold ?? false}
          aria-disabled={disabled || !editor?.can().toggleBold()}
          onClick={() => chain()?.toggleBold().run()}
        />
        <IconButton
          size="md"
          icon={<Italic />}
          aria-label={t("toolbar.italic")}
          aria-pressed={toolbar?.italic ?? false}
          aria-disabled={disabled || !editor?.can().toggleItalic()}
          onClick={() => chain()?.toggleItalic().run()}
        />
        <IconButton
          size="md"
          icon={<Underline />}
          aria-label={t("toolbar.underline")}
          aria-pressed={toolbar?.underline ?? false}
          aria-disabled={disabled || !editor?.can().toggleUnderline()}
          onClick={() => chain()?.toggleUnderline().run()}
        />
        <IconButton
          size="md"
          icon={<Strikethrough />}
          aria-label={t("toolbar.strike")}
          aria-pressed={toolbar?.strike ?? false}
          aria-disabled={disabled || !editor?.can().toggleStrike()}
          onClick={() => chain()?.toggleStrike().run()}
        />
        <IconButton
          size="md"
          icon={<SubscriptIcon />}
          aria-label={t("toolbar.subscript")}
          aria-pressed={toolbar?.subscript ?? false}
          aria-disabled={disabled || !editor?.can().toggleSubscript()}
          onClick={() => chain()?.toggleSubscript().run()}
        />
        <IconButton
          size="md"
          icon={<SuperscriptIcon />}
          aria-label={t("toolbar.superscript")}
          aria-pressed={toolbar?.superscript ?? false}
          aria-disabled={disabled || !editor?.can().toggleSuperscript()}
          onClick={() => chain()?.toggleSuperscript().run()}
        />
        <span aria-hidden="true" className="mx-1 h-6 w-px bg-border" />
        <IconButton
          size="md"
          icon={<List />}
          aria-label={t("toolbar.bulletList")}
          aria-pressed={toolbar?.bulletList ?? false}
          aria-disabled={disabled || !editor?.can().toggleBulletList()}
          onClick={() => chain()?.toggleBulletList().run()}
        />
        <IconButton
          size="md"
          icon={<ListOrdered />}
          aria-label={t("toolbar.orderedList")}
          aria-pressed={toolbar?.orderedList ?? false}
          aria-disabled={disabled || !editor?.can().toggleOrderedList()}
          onClick={() => chain()?.toggleOrderedList().run()}
        />
        <IconButton
          size="md"
          icon={<Table />}
          aria-label={t("toolbar.table")}
          aria-disabled={disabled || !editor?.can().insertTable()}
          onClick={() => chain()?.insertTable({ rows: 2, cols: 2, withHeaderRow: true }).run()}
        />
        <span aria-hidden="true" className="mx-1 h-6 w-px bg-border" />
        <IconButton
          size="md"
          icon={<Sigma />}
          aria-label={t("toolbar.formula")}
          aria-disabled={disabled}
          onClick={openFormulaDialog}
        />
        {allowImages && (
          <>
            <IconButton
              size="md"
              icon={<ImagePlus />}
              aria-label={imageUploading ? t("toolbar.imageUploading") : t("toolbar.image")}
              aria-disabled={disabled || imageUploading}
              onClick={() => fileInputRef.current?.click()}
            />
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
          </>
        )}
      </div>

      <EditorContent editor={editor} />

      {mathWarning && (
        <p role="alert" className="border-t border-border px-3 py-1.5 text-caption text-warning">
          {t("mathUnbalanced")}
        </p>
      )}

      <Dialog open={formulaOpen} onOpenChange={setFormulaOpen}>
        <DialogContent closeLabel={t("formula.close")}>
          <DialogHeader>
            <DialogTitle>{t("formula.title")}</DialogTitle>
            <DialogDescription>{t("formula.description")}</DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              // Dialog dựng trong portal: submit React vẫn nổi bọt lên biểu mẫu của trang chứa trình soạn.
              event.stopPropagation();
              applyFormula();
            }}
          >
            <label className="mb-4 block">
              <span className="mb-1.5 block text-field-label text-text">{t("formula.latexLabel")}</span>
              <Input
                value={formulaLatex}
                onChange={(event) => setFormulaLatex(event.target.value)}
                placeholder={t("formula.latexPlaceholder")}
                autoFocus
                dir="ltr"
              />
            </label>
            <fieldset className="mb-2 flex gap-4">
              <legend className="sr-only">{t("formula.displayLabel")}</legend>
              <label className="flex items-center gap-2 text-body text-text">
                <input
                  type="radio"
                  name="formula-display"
                  checked={!formulaDisplay}
                  onChange={() => setFormulaDisplay(false)}
                />
                {t("formula.inline")}
              </label>
              <label className="flex items-center gap-2 text-body text-text">
                <input
                  type="radio"
                  name="formula-display"
                  checked={formulaDisplay}
                  onChange={() => setFormulaDisplay(true)}
                />
                {t("formula.block")}
              </label>
            </fieldset>
            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => setFormulaOpen(false)}>
                {t("formula.cancel")}
              </Button>
              <Button type="submit" disabled={!formulaLatex.trim()}>
                {t("formula.insert")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
