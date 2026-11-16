"use client";

import { Card, cn, IconButton } from "@thi360/ui";
import { ArrowDown, ArrowUp, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { createContext, useContext, type ReactNode } from "react";
import { get, useFormState, type FieldError } from "react-hook-form";
import type { QuestionEditorValues } from "../editor-form";

/** Đường dẫn tới phần thân của câu đơn hoặc của một câu con trong câu chùm. */
export type BodyPath = "body" | `children.${number}.body`;
/** Đường dẫn tới đề bài tương ứng (để chèn chỗ trống `___`). */
export type ContentPath = "content" | `children.${number}.content`;
/** Đường dẫn tới ô Điểm của câu đơn hoặc câu con (bảng điểm Đúng/Sai, tổng điểm tiêu chí Tự luận). */
export type PointsPath = "points" | `children.${number}.points`;

export function pointsPathOf(prefix: BodyPath): PointsPath {
  return prefix === "body" ? "points" : (prefix.replace(/\.body$/, ".points") as PointsPath);
}

/** Một `LiveStatus` cho cả biểu mẫu: các khối soạn báo kết quả đổi thứ tự, thêm, xóa qua đây. */
const AnnounceContext = createContext<(message: string) => void>(() => {});

export function EditorAnnounceProvider({ announce, children }: { announce: (message: string) => void; children: ReactNode }) {
  return <AnnounceContext.Provider value={announce}>{children}</AnnounceContext.Provider>;
}

export function useAnnounce() {
  return useContext(AnnounceContext);
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

/** Lên, xuống, xóa của một dòng; nút bị khóa (đầu/cuối danh sách) vẫn focus được và tooltip nêu lý do. */
export function RowActions({
  index,
  count,
  name,
  onMove,
  onRemove,
}: {
  index: number;
  count: number;
  /** Tên dòng để ghép vào nhãn, ví dụ "phương án A". */
  name: string;
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.rows");
  return (
    <div className="flex shrink-0 items-center">
      <IconButton
        aria-label={t("moveUp", { name })}
        icon={<ArrowUp />}
        disabled={index === 0}
        tooltip={index === 0 ? t("alreadyFirst") : undefined}
        onClick={() => onMove(index, index - 1)}
      />
      <IconButton
        aria-label={t("moveDown", { name })}
        icon={<ArrowDown />}
        disabled={index === count - 1}
        tooltip={index === count - 1 ? t("alreadyLast") : undefined}
        onClick={() => onMove(index, index + 1)}
      />
      <IconButton tone="danger" aria-label={t("remove", { name })} icon={<Trash2 />} onClick={onRemove} />
    </div>
  );
}
