"use client";

import { cn, Input, Label } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { ratePercent } from "../allocation";
import { parseNumberText } from "../components/block-number-input";
import { percentToCount, wholeSetCount } from "./percent";
import type { SubjectFormEntry } from "./registry";

/** Chữ đã gõ mà chưa ghi được vào bản nháp (FTM R-15): biểu mẫu đưa vào kho nhập thô để chặn lưu và hỏi trước khi rời trang. */
export interface CountPercentRaw {
  text: string;
  code: "invalidNumber" | "setNotWhole";
  source?: "COUNT" | "PERCENT";
}

export interface CountPercentInputProps {
  /** Nhãn của hàng (ví dụ "Trắc nghiệm"); nhóm điều khiển lấy nhãn này. */
  label: string;
  /** Kiểu nhập % của cặp (`SubjectFormEntry.percentEntry`, R-9). `NONE`: một ô số câu, % chỉ đọc bên cạnh. */
  entry: SubjectFormEntry["percentEntry"];
  /** Mẫu số của hàng (số câu của mục chứa hàng). */
  total: number;
  /** Số câu đã lưu trong bản nháp. */
  count: number;
  /** Cỡ chùm cố định của dòng chùm: số câu quy ra phải chia hết (R-142). */
  setSize?: number | null;
  /** Tên ô để đưa focus tới (`data-form-field`). */
  fieldName: string;
  /** Giá trị hợp lệ: `percent` là tỷ lệ đã gõ, `null` khi gõ số câu. */
  onChange: (next: { count: number; percent: number | null }) => void;
  /** Báo chữ chưa ghi được (`null`: ô đã hợp lệ, xóa mục nhập thô). */
  onRawInput?: (raw: CountPercentRaw | null) => void;
  /** Lỗi từ nơi gọi (giới hạn R-12...), hiện tại ô. */
  error?: string;
  /** Chữ đã gõ còn giữ ở kho nhập thô khi ô mở lại (R-15): hiện thay cho số đã lưu. */
  rawText?: string;
  /** Ô chứa chữ nhập thô; mặc định COUNT để giữ hành vi của CR-2. */
  rawSource?: "COUNT" | "PERCENT";
}

type Problem = { code: CountPercentRaw["code"]; count: number } | null;

/**
 * Ô số câu / tỷ lệ của biểu mẫu theo môn (FTM R-9). Kỳ cho nhập %: hai ô cạnh nhau (Số câu, Tỷ lệ), nhập % thì hiện ngay số nguyên
 * quy ra và mẫu số trước khi lưu ("30% của 40 câu = 12 câu"); quy đổi dùng `percent.ts`. Giá trị sai (không phải số, tỷ lệ quy ra
 * không chia hết cỡ chùm) không vào bản nháp mà báo qua `onRawInput`.
 */
export function CountPercentInput({ label, entry, total, count, setSize = null, fieldName, onChange, onRawInput, error, rawText, rawSource }: CountPercentInputProps) {
  const t = useTranslations("exams.subjectForm.common.countPercent");
  const id = useId();
  const [countText, setCountText] = useState(() => rawSource === "PERCENT" ? String(count) : rawText ?? String(count));
  const [percentText, setPercentText] = useState(() => rawSource === "PERCENT" ? rawText ?? "" : "");
  const [lastCount, setLastCount] = useState(count);
  const [lastSetSize, setLastSetSize] = useState(setSize);
  const [problem, setProblem] = useState<Problem>(() => {
    if (rawText === undefined) return null;
    const percent = rawSource === "PERCENT";
    const parsed = parseNumberText(rawText, percent);
    if (parsed === null || (percent && (parsed < 0 || parsed > 100))) return { code: "invalidNumber", count: 0 };
    if (percent && setSize !== null) {
      const converted = percentToCount(parsed, total);
      if (!wholeSetCount(converted, setSize).ok) return { code: "setNotWhole", count: converted };
    }
    return null;
  });

  // Số câu đổi từ bên ngoài (đổi tổng, hoàn tác): hiện số mới thay cho chữ cũ.
  if (count !== lastCount) {
    setLastCount(count);
    if (parseNumberText(countText, false) !== count) {
      setCountText(String(count));
      setPercentText("");
      setProblem(null);
    }
  }
  if (setSize !== lastSetSize) {
    setLastSetSize(setSize);
    if (rawText === undefined && problem?.code === "setNotWhole") {
      setPercentText("");
      setProblem(null);
    }
  }

  const typedPercent = percentText === "" ? null : parseNumberText(percentText, true);
  const preview = problem === null && typedPercent !== null ? t("preview", { percent: typedPercent, total, count: percentToCount(typedPercent, total) }) : null;
  const message = problem
    ? problem.code === "setNotWhole"
      ? t("setNotWhole", { count: problem.count, size: setSize ?? 0 })
      : t("invalidNumber")
    : (error ?? null);
  const errorId = `${id}-error`;
  const previewId = `${id}-preview`;
  const describedBy = [message && errorId, preview && previewId].filter(Boolean).join(" ") || undefined;

  const reject = (text: string, code: CountPercentRaw["code"], source: "COUNT" | "PERCENT", rejected = 0) => {
    setProblem({ code, count: rejected });
    onRawInput?.(rawSource === undefined ? { text, code } : { text, code, source });
  };
  const accept = (next: { count: number; percent: number | null }) => {
    setProblem(null);
    onRawInput?.(null);
    onChange(next);
  };

  const changeCount = (text: string) => {
    setCountText(text);
    setPercentText("");
    const parsed = parseNumberText(text, false);
    if (parsed === null) reject(text, "invalidNumber", "COUNT");
    else accept({ count: parsed, percent: null });
  };
  const changePercent = (text: string) => {
    setPercentText(text);
    // Xóa ô tỷ lệ: không gọi onChange (giữ số câu), nhưng vẫn phải gỡ lỗi nhập thô đang bám vào ô này (R1-7).
    if (text.trim() === "") {
      setProblem(null);
      onRawInput?.(null);
      return;
    }
    const parsed = parseNumberText(text, true);
    // R1-9: tỷ lệ ngoài 0–100 báo sai ngay, không quy đổi.
    if (parsed === null || parsed < 0 || parsed > 100) return reject(text, "invalidNumber", "PERCENT");
    const converted = percentToCount(parsed, total);
    if (setSize !== null && !wholeSetCount(converted, setSize).ok) return reject(text, "setNotWhole", "PERCENT", converted);
    setCountText(String(converted));
    accept({ count: converted, percent: parsed });
  };

  const share = ratePercent(count, total);
  return (
    <div role="group" aria-label={label} data-form-field={fieldName} className="flex min-w-0 flex-col gap-1">
      {entry === "NONE" ? (
        <span className="flex items-center gap-2">
          <Input
            aria-label={label}
            aria-invalid={message ? true : undefined}
            aria-describedby={describedBy}
            inputMode="numeric"
            autoComplete="off"
            value={countText}
            onChange={(event) => changeCount(event.target.value)}
            className="w-24 text-right tabular-nums"
          />
          {share !== null && <span className="text-caption text-text-muted tabular-nums">{t("share", { percent: share })}</span>}
        </span>
      ) : (
        <span className="flex flex-wrap items-end gap-3">
          <span className="flex flex-col gap-1">
            <Label htmlFor={`${id}-count`}>{t("count")}</Label>
            <Input
              id={`${id}-count`}
              aria-invalid={message ? true : undefined}
              aria-describedby={describedBy}
              inputMode="numeric"
              autoComplete="off"
              value={countText}
              onChange={(event) => changeCount(event.target.value)}
              className="w-24 text-right tabular-nums"
            />
          </span>
          <span className="flex flex-col gap-1">
            <Label htmlFor={`${id}-percent`}>{t("percent")}</Label>
            <Input
              id={`${id}-percent`}
              aria-invalid={message ? true : undefined}
              aria-describedby={describedBy}
              inputMode="decimal"
              autoComplete="off"
              value={percentText}
              onChange={(event) => changePercent(event.target.value)}
              className="w-24 text-right tabular-nums"
            />
          </span>
        </span>
      )}
      {preview && (
        <p id={previewId} className="text-caption text-text-muted">
          {preview}
        </p>
      )}
      {message && (
        <p id={errorId} role="alert" className={cn("text-caption text-danger")}>
          {message}
        </p>
      )}
    </div>
  );
}
