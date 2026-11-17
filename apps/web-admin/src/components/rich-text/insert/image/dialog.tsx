"use client";

import { Button, Checkbox, FormField, Input, Label } from "@thi360/ui";
import { questionAssetErrorMessageKey } from "@/features/questions/asset-upload";
import { uploadQuestionAsset } from "@/features/questions/api";
import { NodeSelection } from "@tiptap/pm/state";
import { ImageIcon, Trash2, Upload } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState } from "react";
import { IMAGE_FIGURE_NODE } from "../../extensions";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { upsertAtom } from "../shared/upsert-atom";
import { buildFigureHtml, parseFigure, type FigureState, type FigureWidth } from "./figure-html";

const EMPTY_FIGURE: FigureState = {
  asset: null,
  alt: "",
  caption: "",
  captionHtml: null,
  source: "",
  sourceHtml: null,
  width: "medium",
  zoom: true,
};

const ACCEPTED_TYPES = "image/png,image/jpeg,image/webp";
const WIDTHS: FigureWidth[] = ["small", "medium", "full"];

/** Hình đang chọn trong trình soạn (khối `imageFigure`) và vị trí của nó — `null` khi chèn hình mới. */
function selectedFigure(editor: InsertDialogProps["editor"]): { pos: number; state: FigureState } | null {
  const { selection } = editor.state;
  if (!(selection instanceof NodeSelection) || selection.node.type.name !== IMAGE_FIGURE_NODE) return null;
  return { pos: selection.from, state: parseFigure(selection.node.attrs) };
}

/**
 * Hộp "Hình" (spec 4.6, bản vẽ `SoanThaoHinh`): tải ảnh qua `POST /question-assets`, mô tả thay thế bắt buộc, chú thích,
 * nguồn, độ rộng, cho phép phóng to. Mở lại khi đang chọn một hình thì sửa hình đó thay vì chèn mới.
 */
export default function ImageInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.image");
  const errors = useTranslations("questions.assets.errors");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [editingFigure] = useState(() => selectedFigure(editor));
  const [figure, setFigure] = useState<FigureState>(() => editingFigure?.state ?? EMPTY_FIGURE);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputId = useId();
  // Lỗi mô tả thay thế chỉ hiện sau khi rời ô (DESIGN 5.2), mất ngay khi có chữ.
  const [altTouched, setAltTouched] = useState(false);
  const altMissing = altTouched && !figure.alt.trim();
  // Hộp đóng khi đang tải thì kết quả về muộn không được ghi vào trạng thái đã bỏ.
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  function update(patch: Partial<FigureState>) {
    setFigure((current) => ({ ...current, ...patch }));
  }

  async function upload(file: File) {
    setUploading(true);
    setUploadError(null);
    try {
      const asset = await uploadQuestionAsset(file, "IMAGE");
      if (mountedRef.current) update({ asset: { id: asset.id, url: asset.url } });
    } catch (error) {
      if (mountedRef.current) setUploadError(errors(questionAssetErrorMessageKey(error, "IMAGE")));
    } finally {
      if (mountedRef.current) setUploading(false);
    }
  }

  function submit() {
    if (!figure.asset || !figure.alt.trim() || uploading) return;
    const attrs = { width: figure.width, zoom: String(figure.zoom), html: buildFigureHtml(figure) };
    // Còn đúng hình đang sửa ở vị trí cũ thì ghi đè nó; không thì (nội dung đã đổi dưới chân hộp) chèn mới.
    const current = editingFigure ? editor.state.doc.nodeAt(editingFigure.pos) : null;
    const existing =
      editingFigure && current?.type.name === IMAGE_FIGURE_NODE ? { pos: editingFigure.pos, node: current } : null;
    upsertAtom(editor, IMAGE_FIGURE_NODE, attrs, existing);
    onOpenChange(false);
  }

  const previewWidth = figure.width === "small" ? "w-2/5" : figure.width === "medium" ? "w-3/4" : "w-full";

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      description={t("description")}
      onSubmit={submit}
      submitLabel={editingFigure ? t("submitEdit") : t("submit")}
      submitDisabled={!figure.asset || !figure.alt.trim() || uploading}
    >
      <div className="grid gap-6 md:grid-cols-2">
        <section aria-labelledby={`${fileInputId}-preview`} className="flex flex-col gap-3">
          <span id={`${fileInputId}-preview`} className="text-field-label font-medium text-text-secondary">
            {t("previewLabel")}
          </span>
          <figure className="m-0 flex min-h-40 flex-col items-center justify-center gap-2 rounded-md border border-border bg-subtle p-3">
            {figure.asset ? (
              <>
                {/* eslint-disable-next-line @next/next/no-img-element -- ảnh tải lên qua API quản trị, không qua tối ưu ảnh của Next */}
                <img src={figure.asset.url} alt={figure.alt} className={`${previewWidth} max-w-full rounded-sm`} />
                {figure.caption.trim() && (
                  <figcaption className="text-caption text-text-muted">{figure.caption.trim()}</figcaption>
                )}
              </>
            ) : (
              <div role="img" aria-label={t("noImage")} className="flex flex-col items-center gap-2 text-text-muted">
                <ImageIcon aria-hidden className="size-6" />
                <span className="text-body">{t("noImage")}</span>
              </div>
            )}
          </figure>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="secondary" size="sm" disabled={uploading} onClick={() => fileInputRef.current?.click()}>
              <Upload aria-hidden />
              {uploading ? t("uploading") : figure.asset ? t("replaceImage") : t("chooseImage")}
            </Button>
            {figure.asset && (
              <Button type="button" variant="secondary" size="sm" disabled={uploading} onClick={() => update({ asset: null })}>
                <Trash2 aria-hidden />
                {t("removeImage")}
              </Button>
            )}
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPTED_TYPES}
            aria-label={t("fileInputLabel")}
            className="sr-only"
            tabIndex={-1}
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (file) void upload(file);
            }}
          />
          {uploadError && (
            <p role="alert" className="text-caption text-danger">
              {uploadError}
            </p>
          )}
          <p className="text-caption text-text-muted">{t("supported")}</p>
        </section>

        <div className="flex flex-col gap-4">
          <FormField label={t("altLabel")} hint={t("altHint")} required error={altMissing ? t("altError") : undefined}>
            {(props) => (
              <Input
                {...props}
                value={figure.alt}
                onChange={(event) => update({ alt: event.target.value })}
                onBlur={() => setAltTouched(true)}
                autoComplete="off"
              />
            )}
          </FormField>
          <FormField label={t("captionLabel")}>
            {(props) => (
              <Input {...props} value={figure.caption} onChange={(event) => update({ caption: event.target.value })} autoComplete="off" />
            )}
          </FormField>
          <FormField label={t("sourceLabel")}>
            {(props) => (
              <Input {...props} value={figure.source} onChange={(event) => update({ source: event.target.value })} autoComplete="off" />
            )}
          </FormField>
          <fieldset className="flex flex-col gap-1.5">
            <legend className="mb-1.5 text-field-label font-medium text-text-secondary">{t("widthLabel")}</legend>
            <div className="flex h-12 w-full items-center gap-1 rounded-lg bg-subtle p-1">
              {WIDTHS.map((width) => (
                <label
                  key={width}
                  className="flex h-full flex-1 cursor-pointer items-center justify-center rounded-md text-body font-medium text-text-muted has-checked:bg-surface has-checked:text-primary-text has-checked:shadow-sm has-focus-visible:outline-3 has-focus-visible:-outline-offset-3 has-focus-visible:outline-primary-text"
                >
                  <input
                    type="radio"
                    name={`${fileInputId}-width`}
                    value={width}
                    checked={figure.width === width}
                    onChange={() => update({ width })}
                    className="sr-only"
                  />
                  {t(`width.${width}`)}
                </label>
              ))}
            </div>
          </fieldset>
          <div className="flex items-center gap-3">
            <Checkbox id={`${fileInputId}-zoom`} checked={figure.zoom} onCheckedChange={(checked) => update({ zoom: checked === true })} />
            <Label htmlFor={`${fileInputId}-zoom`} className="cursor-pointer text-body text-text">
              {t("zoomLabel")}
            </Label>
          </div>
        </div>
      </div>
    </InsertDialogShell>
  );
}
