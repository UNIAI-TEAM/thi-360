"use client";

import { IconButton } from "@thi360/ui";
import { ImageIcon, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState } from "react";
import { COVER_IMAGE_MAX_BYTES } from "../create-exam-schema";

const COVER_TYPES = ["image/png", "image/jpeg", "image/webp"];

export interface CoverImage {
  name: string;
  size: number;
  /** Đường dẫn tạm (`URL.createObjectURL`) để xem trước; không có trong môi trường không hỗ trợ. */
  url: string | null;
}

/**
 * Ảnh giới thiệu (UniNote PBI-04 mục 4, không bắt buộc). Dữ liệu mẫu: chỉ xem trước tại chỗ bằng đường dẫn tạm, không
 * tải lên; tên tệp không gửi kèm khi tạo bài.
 */
export function CreateExamCover({
  value,
  onChange,
  onAnnounce,
}: {
  value: CoverImage | null;
  onChange: (value: CoverImage | null) => void;
  onAnnounce: (message: string) => void;
}) {
  const t = useTranslations("exams.create.cover");
  const inputId = useId();
  const hintId = useId();
  const errorId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  // Thu hồi đường dẫn tạm khi đổi ảnh hoặc rời trang.
  const url = value?.url ?? null;
  useEffect(() => {
    if (!url) return;
    return () => URL.revokeObjectURL(url);
  }, [url]);

  const pick = (file: File | undefined) => {
    if (!file) return;
    if (!COVER_TYPES.includes(file.type)) {
      setError(t("invalidType"));
      return;
    }
    if (file.size > COVER_IMAGE_MAX_BYTES) {
      setError(t("tooLarge"));
      return;
    }
    setError(null);
    const preview = typeof URL.createObjectURL === "function" ? URL.createObjectURL(file) : null;
    onChange({ name: file.name, size: file.size, url: preview });
    onAnnounce(t("selected", { name: file.name }));
  };

  const clear = () => {
    onChange(null);
    setError(null);
    if (inputRef.current) inputRef.current.value = "";
    inputRef.current?.focus();
    onAnnounce(t("removed"));
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-field-label font-medium text-text-secondary">
        {t("label")}
      </label>
      <div className="flex flex-wrap items-center gap-3">
        <div
          aria-hidden
          className="grid size-20 shrink-0 place-items-center overflow-hidden rounded-md border border-border bg-subtle text-text-muted"
        >
          {value?.url ? (
            // Ảnh xem trước tại chỗ từ đường dẫn tạm (trang trí, tên tệp đọc ở dòng bên cạnh).
            <span className="size-full bg-cover bg-center" style={{ backgroundImage: `url("${value.url}")` }} />
          ) : (
            <ImageIcon className="size-6" />
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <input
            ref={inputRef}
            id={inputId}
            type="file"
            accept={COVER_TYPES.join(",")}
            aria-describedby={[hintId, error ? errorId : null].filter(Boolean).join(" ")}
            aria-invalid={error ? true : undefined}
            onChange={(event) => pick(event.target.files?.[0])}
            className="block w-full max-w-sm text-body text-text-secondary file:mr-3 file:min-h-10 file:cursor-pointer file:rounded-md file:border file:border-border-strong file:bg-surface file:px-3.5 file:font-medium file:text-text hover:file:bg-subtle"
          />
          {value && (
            <p className="flex min-w-0 items-center gap-2 text-caption text-text-secondary">
              <span className="truncate">{value.name}</span>
              <span className="shrink-0 text-text-muted tabular-nums">
                {t("size", { size: Math.max(1, Math.round(value.size / 1024)) })}
              </span>
              <IconButton aria-label={t("removeAria", { name: value.name })} icon={<X />} onClick={clear} />
            </p>
          )}
        </div>
      </div>
      <p id={hintId} className="text-caption text-text-muted">
        {t("hint")}
      </p>
      {error && (
        <p id={errorId} role="alert" className="text-caption text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
