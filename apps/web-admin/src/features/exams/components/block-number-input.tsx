"use client";

import { cn, Input, type InputProps } from "@thi360/ui";
import { useState } from "react";

const INTEGER = /^\d+$/;
const DECIMAL = /^\d+([.,]\d{1,2})?$/;

/** Đọc chữ đã gõ thành số: ô trống là 0; sai định dạng trả `null`. */
export function parseNumberText(text: string, allowDecimal: boolean): number | null {
  const trimmed = text.trim();
  if (trimmed === "") return 0;
  if (!(allowDecimal ? DECIMAL : INTEGER).test(trimmed)) return null;
  return Number(trimmed.replace(",", "."));
}

/** Đọc chữ tỷ lệ % trong đoạn 0–100; ngoài khoảng hoặc sai định dạng trả `null`. */
export function parsePercentText(text: string): number | null {
  const parsed = parseNumberText(text, true);
  return parsed !== null && parsed >= 0 && parsed <= 100 ? parsed : null;
}

/** Chuỗi hiển thị của số theo dấu phẩy thập phân; `null` (ô để trống) là chuỗi rỗng. */
export function formatNumberText(value: number | null): string {
  return value === null ? "" : String(value).replace(".", ",");
}

type BlockNumberInputProps = Omit<InputProps, "value" | "onChange" | "type" | "inputMode"> & {
  value: number;
  onValueChange: (value: number) => void;
  /** Cho nhập số thập phân (tỷ lệ %), tối đa hai chữ số sau dấu phẩy. */
  allowDecimal?: boolean;
  /** Báo chữ gõ sai định dạng (để hiện lỗi ở ô hoặc bảng). */
  onInvalidChange?: (invalid: boolean) => void;
};

/**
 * Ô nhập số nguyên không âm (hoặc thập phân cho %): `type="text"` + `inputMode` (không dùng `type="number"`, theo
 * hướng dẫn biểu mẫu), giữ nguyên chữ người dùng gõ, chỉ báo giá trị lên khi đọc được. Chữ sai thì `aria-invalid`.
 */
export function BlockNumberInput({
  value,
  onValueChange,
  allowDecimal = false,
  onInvalidChange,
  className,
  ...props
}: BlockNumberInputProps) {
  const [text, setText] = useState(() => formatNumberText(value));
  const [lastValue, setLastValue] = useState(value);
  const parsed = parseNumberText(text, allowDecimal);
  // Giá trị đổi từ bên ngoài (quy đổi, hoàn tác): hiện giá trị mới thay cho chữ cũ.
  if (value !== lastValue) {
    setLastValue(value);
    if (parsed !== value) setText(formatNumberText(value));
  }
  const invalid = parsed === null;

  return (
    <Input
      {...props}
      type="text"
      inputMode={allowDecimal ? "decimal" : "numeric"}
      autoComplete="off"
      value={text}
      aria-invalid={invalid || props["aria-invalid"] ? true : undefined}
      className={cn("text-right tabular-nums", className)}
      onChange={(event) => {
        const next = event.target.value;
        setText(next);
        const nextValue = parseNumberText(next, allowDecimal);
        onInvalidChange?.(nextValue === null);
        if (nextValue !== null && nextValue !== value) {
          setLastValue(nextValue);
          onValueChange(nextValue);
        }
      }}
    />
  );
}
