import { useId, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { Label } from "./label";

export type FormFieldControlProps = {
  id: string;
  "aria-invalid"?: true;
  "aria-required"?: true;
  "aria-describedby"?: string;
};

export type FormFieldProps = {
  label: string;
  /** Thông báo lỗi; có giá trị thì ô nhập được đánh dấu `aria-invalid`. */
  error?: string;
  hint?: string;
  required?: boolean;
  className?: string;
  /** Rải `props` vào điều khiển (Input, Textarea, Select…) để nhãn, lỗi và gợi ý gắn đúng với nó. */
  children: (props: FormFieldControlProps) => ReactNode;
};

/** Nhãn phía trên, điều khiển ở giữa, dòng lỗi ngay dưới (DESIGN.md mục 5.2). */
export function FormField({ label, error, hint, required, className, children }: FormFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error && errorId, hint && hintId].filter(Boolean).join(" ");

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <Label htmlFor={id} required={required}>
        {label}
      </Label>
      {children({
        id,
        "aria-invalid": error ? true : undefined,
        "aria-required": required ? true : undefined,
        "aria-describedby": describedBy || undefined,
      })}
      {hint && (
        <p id={hintId} className="text-caption text-text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-caption text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
