"use client";

import type { RichTextEditorHandle } from "@/components/rich-text";
import { Card, cn, IconButton } from "@thi360/ui";
import { ArrowDown, ArrowUp, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { createContext, useContext, useState, type ReactNode } from "react";
import { get, useFormState, type FieldError } from "react-hook-form";
import type { QuestionEditorValues } from "../editor-form";
import type { ReadinessIssue } from "../readiness";

/** Đường dẫn tới phần thân của câu đơn hoặc của một câu con trong câu chùm. */
export type BodyPath = "body" | `children.${number}.body`;
/** Đường dẫn tới đề bài tương ứng (để chèn chỗ trống `___`). */
export type ContentPath = "content" | `children.${number}.content`;
/** Đường dẫn tới ô Điểm của câu đơn hoặc câu con (bảng điểm Đúng/Sai, tổng điểm tiêu chí Tự luận). */
export type PointsPath = "points" | `children.${number}.points`;

export function pointsPathOf(prefix: BodyPath): PointsPath {
  return prefix === "body" ? "points" : (prefix.replace(/\.body$/, ".points") as PointsPath);
}

/** Một `LiveStatus` cho cả biểu mẫu: các khối soạn báo kết quả đổi thứ tự, thêm, xóa qua đây. Mang luôn sổ tay cầm của các ô soạn HTML. */
const AnnounceContext = createContext<(message: string) => void>(() => {});

export function EditorAnnounceProvider({ announce, children }: { announce: (message: string) => void; children: ReactNode }) {
  return (
    <AnnounceContext.Provider value={announce}>
      <RichEditorRegistryProvider>{children}</RichEditorRegistryProvider>
    </AnnounceContext.Provider>
  );
}

export function useAnnounce() {
  return useContext(AnnounceContext);
}

/** Tay cầm các ô soạn HTML của biểu mẫu theo tên trường: nút "Thêm ô trống" chèn `___` đúng chỗ con trỏ trong ô Đề bài. */
const RichEditorRegistryContext = createContext<Map<string, RichTextEditorHandle> | null>(null);

function RichEditorRegistryProvider({ children }: { children: ReactNode }) {
  const [registry] = useState(() => new Map<string, RichTextEditorHandle>());
  return <RichEditorRegistryContext.Provider value={registry}>{children}</RichEditorRegistryContext.Provider>;
}

export function useRichEditorRegistry() {
  return useContext(RichEditorRegistryContext);
}

/**
 * Cảnh báo Sẵn sàng của máy chủ dành cho trình soạn (spec 2026-11-17 mục 3): trang sửa tải `GET /questions/{id}/
 * readiness` một lần khi mở và đưa danh sách xuống đây; khối soạn phương án lọc theo `position`/`child` để hiện dưới
 * phương án gây cảnh báo. Câu đang tạo chưa có cảnh báo (chưa từng lưu).
 */
const ReadinessWarningsContext = createContext<readonly ReadinessIssue[]>([]);

export function EditorWarningsProvider({
  warnings,
  children,
}: {
  warnings: readonly ReadinessIssue[];
  children: ReactNode;
}) {
  return <ReadinessWarningsContext.Provider value={warnings}>{children}</ReadinessWarningsContext.Provider>;
}

/** Cảnh báo của máy chủ theo `code`, `optionId` (id phương án đã khớp lúc mở) và `child` (câu con của chùm, tính từ 1). */
export function useEditorWarnings(code: string, optionId: string, child?: number): readonly ReadinessIssue[] {
  const warnings = useContext(ReadinessWarningsContext);
  return warnings.filter(
    (warning) => warning.code === code && warning.resolvedOptionId === optionId && warning.child === child,
  );
}

/** Lỗi đã dịch của một trường (kể cả lỗi cấp mảng mà resolver đặt ở `.root`). */
export function useEditorError(path: string): string | undefined {
  const t = useTranslations("questions.editor.errors");
  const { errors } = useFormState<QuestionEditorValues>({ name: path as never });
  const error = get(errors, path) as (FieldError & { root?: FieldError }) | undefined;
  const key = error?.message ?? error?.root?.message;
  return key ? t(key as never) : undefined;
}

/**
 * Dòng lỗi của một nhóm (phương án, ô trống, câu con) không gắn với một ô nhập: nhận focus khi gửi biểu mẫu lỗi
 * (`data-editor-error`) để người dùng bàn phím tới đúng chỗ.
 */
export function GroupError({ id, message }: { id: string; message: string | undefined }) {
  if (!message) return null;
  return (
    <p id={id} role="alert" tabIndex={-1} data-editor-error className="rounded-sm text-caption text-danger outline-hidden">
      {message}
    </p>
  );
}

export function FieldErrorText({ id, message }: { id: string; message: string | undefined }) {
  if (!message) return null;
  return (
    <p id={id} role="alert" className="text-caption text-danger">
      {message}
    </p>
  );
}

/** Thẻ của một khối trong biểu mẫu, tiêu đề `h2` (Phân loại, Đề bài, Phương án, Lời giải). */
export function EditorSection({
  title,
  titleId,
  required,
  optionalLabel,
  description,
  badge,
  children,
  className,
}: {
  title: string;
  titleId: string;
  required?: boolean;
  optionalLabel?: string;
  description?: string;
  badge?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card padding="none" className={cn("@container p-4 sm:p-5", className)}>
      <section aria-labelledby={titleId} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id={titleId} className="text-h3 font-semibold text-text">
              {title}
              {required && (
                <span aria-hidden className="ml-1 text-danger">
                  *
                </span>
              )}
              {optionalLabel && <span className="ml-1.5 text-body font-normal text-text-muted">{optionalLabel}</span>}
            </h2>
            {badge}
          </div>
          {description && <p className="text-body text-text-muted">{description}</p>}
        </div>
        {children}
      </section>
    </Card>
  );
}

export function optionLetter(index: number): string {
  return String.fromCharCode(65 + (index % 26));
}

export function statementLetter(index: number): string {
  return String.fromCharCode(97 + (index % 26));
}

/**
 * Lên, xuống, xóa của một dòng; nút bị khóa (đầu/cuối danh sách, hoặc `locked` do bên gọi) vẫn focus được và tooltip nêu
 * lý do.
 */
export function RowActions({
  index,
  count,
  name,
  locked,
  onMove,
  onRemove,
}: {
  index: number;
  count: number;
  /** Tên dòng để ghép vào nhãn, ví dụ "phương án A". */
  name: string;
  /** Lý do bên gọi khóa từng nút (ví dụ phương án đang lấy theo cụm gạch chân); không có thì nút theo vị trí. */
  locked?: { up?: string; down?: string; remove?: string };
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.rows");
  const upReason = index === 0 ? t("alreadyFirst") : locked?.up;
  const downReason = index === count - 1 ? t("alreadyLast") : locked?.down;
  return (
    <div className="flex shrink-0 items-center">
      <IconButton
        aria-label={t("moveUp", { name })}
        icon={<ArrowUp />}
        disabled={upReason !== undefined}
        tooltip={upReason}
        onClick={() => onMove(index, index - 1)}
      />
      <IconButton
        aria-label={t("moveDown", { name })}
        icon={<ArrowDown />}
        disabled={downReason !== undefined}
        tooltip={downReason}
        onClick={() => onMove(index, index + 1)}
      />
      <IconButton
        tone="danger"
        aria-label={t("remove", { name })}
        icon={<Trash2 />}
        disabled={locked?.remove !== undefined}
        tooltip={locked?.remove}
        onClick={onRemove}
      />
    </div>
  );
}
