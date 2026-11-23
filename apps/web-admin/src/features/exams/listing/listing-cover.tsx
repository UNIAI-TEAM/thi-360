"use client";

import { buttonVariants, cn, Button } from "@thi360/ui";
import { CircleAlert, ImageOff, LoaderCircle, Upload } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { COVER_IMAGE_MAX_BYTES } from "../create-exam-schema";
import { COVER_ACCEPT, coverErrorKeyOf, coverFileProblem, type CoverErrorKey } from "./cover";
import { useUploadExamCover } from "./queries";

export interface CoverValue {
  assetId: string;
  url: string;
}

interface CoverFailure {
  key: CoverErrorKey;
  fileName: string;
}

/** Trạng thái và thao tác của ô ảnh bìa (`useCoverField`). */
export interface CoverField {
  cover: CoverValue | null;
  failure: CoverFailure | null;
  uploading: boolean;
  pick: (file: File) => void;
  remove: () => void;
}

export function useCoverField(examId: string, initial: CoverValue | null): CoverField {
  const [cover, setCover] = useState<CoverValue | null>(initial);
  const [failure, setFailure] = useState<CoverFailure | null>(null);
  const upload = useUploadExamCover(examId);

  return {
    cover,
    failure,
    uploading: upload.isPending,
    pick(file: File) {
      setFailure(null);
      const problem = coverFileProblem(file);
      if (problem) {
        setFailure({ key: problem, fileName: file.name });
        return;
      }
      upload.mutate(file, {
        onSuccess: (uploaded) => setCover({ assetId: uploaded.assetId, url: uploaded.url }),
        onError: (error) => setFailure({ key: coverErrorKeyOf(error), fileName: file.name }),
      });
    },
    remove() {
      setFailure(null);
      setCover(null);
    },
  };
}

/** Ảnh mặc định của thẻ khi bài chưa có ảnh bìa: mã kỳ thi trên nền màu chủ đạo. */
export function CoverFallback({ label, className }: { label: string; className?: string }) {
  return (
    <div
      aria-hidden
      className={cn(
        "flex aspect-video flex-col justify-end overflow-hidden bg-primary-fill p-3 text-on-primary",
        className,
      )}
    >
      <span className="text-title font-bold wrap-anywhere">{label}</span>
    </div>
  );
}

export interface ListingCoverProps {
  value: CoverField;
  fallbackLabel: string;
  readOnly: boolean;
}

export function ListingCover({ value, fallbackLabel, readOnly }: ListingCoverProps) {
  const t = useTranslations("exams.listing.cover");
  const inputId = useId();
  const labelId = useId();
  const hintId = useId();
  const { cover, failure, uploading } = value;

  return (
    <div className="flex flex-col gap-1.5">
      <span id={labelId} className="text-field-label font-medium text-text-secondary">
        {t("label")}
      </span>
      <div className="flex flex-wrap items-center gap-4">
        {cover ? (
          // eslint-disable-next-line @next/next/no-img-element -- ảnh bản nháp lấy qua API quản trị cùng gốc, không qua trình tối ưu ảnh
          <img src={cover.url} alt={t("alt")} className="aspect-video w-48 rounded-lg border border-border object-cover" />
        ) : (
          <CoverFallback label={fallbackLabel} className="w-48 rounded-lg" />
        )}
        <div className="flex min-w-0 flex-1 basis-64 flex-col gap-2">
          <p className="flex items-center gap-2 font-semibold text-text">
            {uploading ? (
              <>
                <LoaderCircle aria-hidden className="size-4 animate-spin motion-reduce:animate-none" />
                {t("uploading")}
              </>
            ) : cover ? (
              t("current")
            ) : (
              <>
                <ImageOff aria-hidden className="size-4 text-text-muted" />
                {t("none")}
              </>
            )}
          </p>
          {!readOnly && (
            <div className="flex flex-wrap gap-2">
              <div className="relative">
                <input
                  id={inputId}
                  type="file"
                  accept={COVER_ACCEPT}
                  aria-labelledby={`${labelId} ${inputId}-text`}
                  aria-describedby={hintId}
                  disabled={uploading}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    event.target.value = "";
                    if (file) value.pick(file);
                  }}
                  className="peer sr-only"
                />
                <label
                  htmlFor={inputId}
                  className={cn(
                    buttonVariants({ variant: "outline" }),
                    "cursor-pointer peer-disabled:pointer-events-none peer-disabled:opacity-50",
                    "peer-focus-visible:outline-3 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-text",
                  )}
                >
                  <Upload aria-hidden />
                  <span id={`${inputId}-text`}>{cover ? t("replace") : t("choose")}</span>
                </label>
              </div>
              {cover && (
                <Button type="button" variant="secondary" disabled={uploading} onClick={value.remove}>
                  {t("remove")}
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
      <p id={hintId} className="text-caption text-text-muted">
        {t("hint", { max: COVER_IMAGE_MAX_BYTES / (1024 * 1024) })}
      </p>
      {failure && (
        <p role="alert" className="flex items-start gap-1.5 text-caption text-danger">
          <CircleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
          <span>
            {t(`errors.${failure.key}`, { name: failure.fileName, max: COVER_IMAGE_MAX_BYTES / (1024 * 1024) })}
            {cover ? ` ${t("keep")}` : ""}
          </span>
        </p>
      )}
    </div>
  );
}
