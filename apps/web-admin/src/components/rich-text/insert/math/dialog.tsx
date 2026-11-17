"use client";

import { SESSION_QUERY_KEY } from "@/features/session/api";
import type { Session } from "@/features/session/types";
import { cn, focusRing, StatusChip, Tabs, TabsContent, TabsList, TabsTrigger, Textarea } from "@thi360/ui";
import { useQueryClient } from "@tanstack/react-query";
import type { Editor } from "@tiptap/core";
import { useTranslations } from "next-intl";
import { useId, useMemo, useRef, useState } from "react";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { loadRecent, pushRecent } from "../shared/recent-store";
import { DisplayToggle } from "./display-toggle";
import { KatexSpan } from "./katex-span";
import { checkLatex, type LatexCheck, type LatexErrorKind } from "./latex-check";
import { MathField, type MathFieldHandle } from "./math-field";
import { PALETTE_TABS, type PaletteTabId } from "./math-palette";
import { PaletteGrid } from "./palette-grid";

/** Dữ liệu `openForSelection` của mục `math` trả về — mở lại hộp với công thức đang chọn. */
export interface MathEditing {
  latex: string;
  display: boolean;
}

function asMathEditing(editing: unknown): MathEditing | null {
  if (typeof editing !== "object" || editing === null) return null;
  const { latex, display } = editing as Partial<MathEditing>;
  return typeof latex === "string" ? { latex, display: display === true } : null;
}

/** Số ký tự chữ tối đa lấy ở mỗi bên điểm chèn cho phần xem trước (spec 4.2). */
const PREVIEW_CONTEXT_CHARS = 80;

/** Chữ thuần ngay trước và sau điểm chèn hiện tại (hoặc quanh công thức đang sửa), gộp xuống dòng thành khoảng trắng. */
function insertionContext(editor: Editor): { before: string; after: string } {
  const { doc, selection } = editor.state;
  const before = doc.textBetween(0, selection.from, " ", " ").replace(/\s+/g, " ");
  const after = doc.textBetween(selection.to, doc.content.size, " ", " ").replace(/\s+/g, " ");
  return { before: before.slice(-PREVIEW_CONTEXT_CHARS), after: after.slice(0, PREVIEW_CONTEXT_CHARS) };
}

type FieldStatus = "loading" | "ready" | "failed";

/**
 * Hộp "Công thức toán" (spec 4.2, bản vẽ `SoanThaoCongThucToan`): ô soạn trực quan `mathlive` và ô mã LaTeX đồng bộ hai
 * chiều, tám tab mẫu, nhãn Hợp lệ/Lỗi cú pháp, xem trước trong câu đang soạn, dùng gần đây. Chèn tắt khi KaTeX báo lỗi
 * hoặc còn ô giữ chỗ. Lưu thành `span.math-inline` hoặc `span.math-block` qua `setMath`.
 */
export default function MathInsertDialog({ editor, open, onOpenChange, editing }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.math");
  const initial = asMathEditing(editing);
  const [latex, setLatex] = useState(initial?.latex ?? "");
  const [display, setDisplay] = useState(initial?.display ?? false);
  const [tab, setTab] = useState<PaletteTabId>("common");
  const [fieldStatus, setFieldStatus] = useState<FieldStatus>("loading");
  const fieldRef = useRef<MathFieldHandle>(null);
  const latexId = useId();
  const errorId = useId();

  const queryClient = useQueryClient();
  // Phiên đã nạp ở khung ứng dụng; không có (test, trang rời) thì không có "Dùng gần đây".
  const userId = queryClient.getQueryData<Session>(SESSION_QUERY_KEY)?.id ?? null;
  const [recent, setRecent] = useState<string[]>(() => (userId ? loadRecent(userId, "math") : []));

  const context = useMemo(() => insertionContext(editor), [editor]);
  const check = useMemo(() => checkLatex(latex, display), [latex, display]);
  const fieldReady = fieldStatus === "ready";

  // Ô mã LaTeX là nguồn: ghi sang ô trực quan. Ô trực quan là nguồn: `onLatexChange` ghi sang ô mã (không ghi ngược).
  function onLatexTextChange(next: string) {
    setLatex(next);
    fieldRef.current?.setLatex(next);
  }

  function onRecentPick(recentLatex: string) {
    if (fieldReady) fieldRef.current?.insert(recentLatex);
    else onLatexTextChange(recentLatex);
  }

  function submit() {
    if (check.status !== "valid") return;
    const value = latex.trim();
    editor.chain().focus().setMath({ latex: value, display }).run();
    if (userId) {
      pushRecent(userId, "math", value);
      setRecent(loadRecent(userId, "math"));
    }
    onOpenChange(false);
  }

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      description={t("description")}
      submitLabel={initial ? t("update") : t("insert")}
      submitDisabled={check.status !== "valid"}
      onSubmit={submit}
    >
      <div className="flex flex-col gap-5 lg:flex-row">
        <div className="flex min-w-0 flex-col gap-3 lg:flex-[3]">
          <Tabs value={tab} onValueChange={(value) => setTab(value as PaletteTabId)}>
            <TabsList aria-label={t("tabsLabel")} className="flex-wrap overflow-visible">
              {PALETTE_TABS.map((paletteTab) => (
                <TabsTrigger key={paletteTab.id} value={paletteTab.id} className="h-[34px] px-3 text-caption">
                  {t(`tabs.${paletteTab.id}`)}
                </TabsTrigger>
              ))}
            </TabsList>
            {/* Vùng mẫu cao cố định (3 hàng ô 56px + đệm cho vòng focus, lộ một phần hàng 4 để báo còn cuộn): đổi tab
                không làm hộp co giãn và nội dung bên dưới không nhảy. Tab nhiều mẫu hơn thì cuộn trong vùng này. */}
            {PALETTE_TABS.map((paletteTab) => (
              <TabsContent key={paletteTab.id} value={paletteTab.id} className="-mx-1 mt-2 h-[228px] overflow-y-auto px-1 py-1">
                <PaletteGrid
                  items={paletteTab.items}
                  label={t(`tabs.${paletteTab.id}`)}
                  disabled={!fieldReady}
                  onPick={(pattern) => fieldRef.current?.insert(pattern)}
                />
              </TabsContent>
            ))}
          </Tabs>

          <div className="flex flex-col gap-1.5">
            <span className="text-field-label text-text">{t("visualLabel")}</span>
            <MathField
              ref={fieldRef}
              initialLatex={latex}
              ariaLabel={t("visualAriaLabel")}
              onLatexChange={setLatex}
              onStatusChange={setFieldStatus}
              className={cn(
                "flex min-h-[150px] items-center justify-center rounded-xl border border-border bg-surface p-4 transition-colors duration-150 motion-reduce:transition-none",
                "focus-within:border-primary-text focus-within:ring-3 focus-within:ring-primary-text/15",
                "[&_math-field]:w-full [&_math-field]:border-0 [&_math-field]:bg-transparent [&_math-field]:text-[28px] [&_math-field]:text-text [&_math-field]:outline-hidden",
                "[&_math-field::part(menu-toggle)]:hidden [&_math-field::part(virtual-keyboard-toggle)]:hidden",
              )}
            />
            <p className="text-caption text-text-muted">
              {fieldStatus === "failed" ? t("visualFailed") : fieldStatus === "loading" ? t("visualLoading") : t("visualHint")}
            </p>
          </div>

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between gap-2">
              <label htmlFor={latexId} className="text-field-label text-text">
                {t("latexLabel")}
              </label>
              <LatexStatus check={check.status} />
            </div>
            <Textarea
              id={latexId}
              rows={2}
              dir="ltr"
              spellCheck={false}
              value={latex}
              onChange={(event) => onLatexTextChange(event.target.value)}
              placeholder={t("latexPlaceholder")}
              aria-invalid={check.status === "error" || check.status === "placeholder"}
              aria-describedby={check.status === "error" || check.status === "placeholder" ? errorId : undefined}
              className="min-h-0 font-mono text-caption"
            />
            <div id={errorId} role="status" className="min-h-5 text-caption text-danger">
              <LatexErrorText check={check} />
            </div>
          </div>
        </div>

        <div className="flex min-w-0 flex-col gap-4 border-border lg:flex-[2] lg:border-l lg:pl-5">
          <div className="flex flex-col gap-2">
            <span className="text-field-label text-text">{t("previewLabel")}</span>
            <DisplayToggle
              display={display}
              onChange={setDisplay}
              label={t("displayLabel")}
              inlineLabel={t("inline")}
              blockLabel={t("block")}
            />
            <div className="rounded-xl bg-subtle p-3.5 text-body-lg text-text" data-testid="math-preview">
              <span>{context.before}</span>
              {latex.trim() ? (
                <KatexSpan latex={latex} displayMode={display} className={display ? "my-1 block text-center" : ""} />
              ) : (
                <span className="text-text-muted">{t("previewEmpty")}</span>
              )}
              <span>{context.after}</span>
            </div>
          </div>

          {recent.length > 0 && (
            <div className="flex flex-col gap-2">
              <span className="text-field-label text-text">{t("recentLabel")}</span>
              <div className="flex flex-wrap gap-2">
                {recent.map((recentLatex) => (
                  <button
                    key={recentLatex}
                    type="button"
                    aria-label={t("recentItem", { latex: recentLatex })}
                    onClick={() => onRecentPick(recentLatex)}
                    className={cn(
                      "flex h-11 max-w-full items-center overflow-hidden rounded-lg border border-border bg-surface px-3 text-text hover:border-border-strong hover:bg-subtle",
                      focusRing,
                    )}
                  >
                    <KatexSpan latex={recentLatex} className="pointer-events-none" />
                  </button>
                ))}
              </div>
            </div>
          )}

          <p className="text-caption text-text-muted">{t("note")}</p>
        </div>
      </div>
    </InsertDialogShell>
  );
}

/** Nhãn dưới ô LaTeX: Hợp lệ / Lỗi cú pháp / Còn ô giữ chỗ (trống thì để chỗ, không nhãn). */
function LatexStatus({ check }: { check: LatexCheck["status"] }) {
  const t = useTranslations("richText.insertDialog.math.status");
  if (check === "empty") return <span aria-hidden className="h-6" />;
  if (check === "valid") return <StatusChip tone="success">{t("valid")}</StatusChip>;
  if (check === "placeholder") return <StatusChip tone="warning">{t("placeholder")}</StatusChip>;
  return <StatusChip tone="danger">{t("error")}</StatusChip>;
}

function LatexErrorText({ check }: { check: LatexCheck }) {
  const t = useTranslations("richText.insertDialog.math.errors");
  if (check.status === "placeholder") return <>{t("placeholder")}</>;
  if (check.status !== "error") return null;
  return <>{errorMessage(check.error, t)}</>;
}

function errorMessage(error: LatexErrorKind, t: (key: string, values?: Record<string, string>) => string): string {
  if (error.kind === "missingClose") return t("missingClose");
  if (error.kind === "unsupportedCommand") return t("unsupportedCommand", { command: error.command });
  return t("generic");
}
