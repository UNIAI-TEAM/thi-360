"use client";

import { buttonVariants, cn, IconButton } from "@thi360/ui";
import { ImageIcon, ImageUp, X } from "lucide-react";
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
 *
 * Nút chọn tệp theo kiểu nút Phụ của kit (như màn Cấp tài khoản hàng loạt): ô chọn tệp thật ẩn về hình ảnh nhưng vẫn
 * nhận Tab và Enter/Space, nhãn của nó vẽ thành nút, nên không còn chữ tiếng Anh của trình duyệt ("Choose file", "No
 * file chosen"). Ô nhập đứng trước nhãn để lớp `peer` vẽ vòng focus lên nút khi ô nhập nhận focus bằng bàn phím.
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
  const labelId = useId();
  const buttonTextId = useId();
  const fileNameId = useId();
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
      <p id={labelId} className="text-field-label font-medium text-text-secondary">
        {t("label")}
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <div
          aria-hidden
          className="grid aspect-video w-28 shrink-0 place-items-center overflow-hidden rounded-md border border-border bg-subtle text-text-muted"
        >
          {value?.url ? (
            // Ảnh xem trước tại chỗ từ đường dẫn tạm (trang trí, tên tệp đọc ở dòng bên cạnh).
            <span className="size-full bg-cover bg-center" style={{ backgroundImage: `url("${value.url}")` }} />
          ) : (
            <ImageIcon className="size-6" />
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <div className="relative flex flex-wrap items-center gap-2">
            <input
              ref={inputRef}
              id={inputId}
              type="file"
              accept={COVER_TYPES.join(",")}
              aria-labelledby={`${labelId} ${buttonTextId}`}
              aria-describedby={[value ? fileNameId : null, hintId, error ? errorId : null].filter(Boolean).join(" ")}
              aria-invalid={error ? true : undefined}
              onChange={(event) => pick(event.target.files?.[0])}
              className="peer sr-only"
            />
            <label
              htmlFor={inputId}
              className={cn(
                buttonVariants({ variant: "secondary" }),
                "cursor-pointer",
                "peer-focus-visible:outline-3 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-text",
              )}
            >
              <ImageUp aria-hidden />
              <span id={buttonTextId}>{value ? t("change") : t("choose")}</span>
            </label>
            {!value && <span className="text-caption text-text-muted">{t("empty")}</span>}
          </div>
          {value && (
            <p className="flex min-w-0 items-center gap-2 text-caption text-text-secondary">
              <span id={fileNameId} className="min-w-0 wrap-anywhere">
                {value.name}
                <span className="ml-2 text-text-muted tabular-nums">
                  {t("size", { size: Math.max(1, Math.round(value.size / 1024)) })}
                </span>
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
