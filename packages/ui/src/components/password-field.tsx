"use client";

import { Check, Circle, Eye, EyeOff, RefreshCw } from "lucide-react";
import { type ComponentProps, type ReactNode, useId, useRef, useState } from "react";
import { cn } from "../lib/cn";
import { UI_STRINGS_BY_LOCALE, type UiLocale } from "../messages/ui-strings";
import { LiveStatus } from "./live-status";
import { useUiStrings } from "./ui-strings";
import { fieldBase, focusRing } from "../lib/styles";

const UPPER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
const LOWER_CHARS = "abcdefghijkmnpqrstuvwxyz";
const DIGIT_CHARS = "23456789";
const SPECIAL_CHARS = "!@#$%*?-_";
const ALL_CHARS = UPPER_CHARS + LOWER_CHARS + DIGIT_CHARS + SPECIAL_CHARS;

export interface PasswordRuleStatus {
  key: "length" | "upper" | "lower" | "digit" | "special";
  label: string;
  met: boolean;
}

export interface PasswordFieldLabels {
  /** Tên nút khi mật khẩu đang ẩn. Tên nút nêu hành động sẽ xảy ra, nên đổi theo trạng thái (không kèm `aria-pressed`). */
  showPassword: string;
  /** Tên nút khi mật khẩu đang hiện. */
  hidePassword: string;
  generate: string;
  /** Câu báo cho trình đọc màn hình sau khi tạo mật khẩu ngẫu nhiên mà ô vẫn ẩn. Không chứa mật khẩu. */
  generated: string;
  /** Như `generated`, khi mật khẩu vừa tạo đang hiện trong ô (`revealOnGenerate` hoặc người dùng đã bật hiện). */
  generatedShown: string;
  rulesLabel: string;
  /** Chữ đọc sau mỗi điều kiện đã đạt / chưa đạt; icon và màu chỉ dành cho người nhìn. */
  ruleMet: string;
  ruleUnmet: string;
  ruleLength: string;
  ruleUpper: string;
  ruleLower: string;
  ruleDigit: string;
  ruleSpecial: string;
}


export function evaluatePasswordRules(
  password: string,
  labelsOrLocale: Partial<PasswordFieldLabels> | UiLocale = "vi",
  labels: Partial<PasswordFieldLabels> = {},
): PasswordRuleStatus[] {
  const locale: UiLocale = typeof labelsOrLocale === "string" ? labelsOrLocale : "vi";
  const resolvedOverrides = typeof labelsOrLocale === "string" ? labels : labelsOrLocale;
  const base = UI_STRINGS_BY_LOCALE[locale]?.passwordField ?? UI_STRINGS_BY_LOCALE.vi.passwordField;
  const merged = { ...base, ...resolvedOverrides };
  return [
    { key: "length", label: merged.ruleLength, met: password.length >= 8 && password.length <= 64 },
    { key: "upper", label: merged.ruleUpper, met: /\p{Lu}/u.test(password) },
    { key: "lower", label: merged.ruleLower, met: /\p{Ll}/u.test(password) },
    { key: "digit", label: merged.ruleDigit, met: /\p{Nd}/u.test(password) },
    {
      key: "special",
      label: merged.ruleSpecial,
      met: /[^\p{Lu}\p{Ll}\p{Nd}\s]/u.test(password),
    },
  ];
}

export function isPasswordStrong(password: string): boolean {
  return evaluatePasswordRules(password).every((rule) => rule.met);
}

function randomIndex(max: number): number {
  const array = new Uint32Array(1);
  globalThis.crypto.getRandomValues(array);
  return (array[0] ?? 0) % max;
}

export function generateStrongPassword(length = 12): string {
  const targetLength = Math.max(8, Math.min(64, length));
  const chars = [
    UPPER_CHARS[randomIndex(UPPER_CHARS.length)]!,
    LOWER_CHARS[randomIndex(LOWER_CHARS.length)]!,
    DIGIT_CHARS[randomIndex(DIGIT_CHARS.length)]!,
    SPECIAL_CHARS[randomIndex(SPECIAL_CHARS.length)]!,
  ];
  while (chars.length < targetLength) {
    chars.push(ALL_CHARS[randomIndex(ALL_CHARS.length)]!);
  }
  for (let i = chars.length - 1; i > 0; i -= 1) {
    const j = randomIndex(i + 1);
    const tmp = chars[i]!;
    chars[i] = chars[j]!;
    chars[j] = tmp;
  }
  return chars.join("");
}

export type PasswordFieldProps = Omit<ComponentProps<"input">, "size" | "type"> & {
  size?: "md" | "lg";
  leadingIcon?: ReactNode;
  showRules?: boolean;
  /** Số cột của danh sách quy tắc; `2` cho bố cục cần tiết kiệm chiều cao (từ bề rộng `sm` trở lên). */
  rulesColumns?: 1 | 2;
  allowGenerate?: boolean;
  revealOnGenerate?: boolean;
  onValueChange?: (value: string) => void;
  labels?: Partial<PasswordFieldLabels>;
};

export function PasswordField({
  className,
  size = "md",
  leadingIcon,
  showRules = false,
  rulesColumns = 1,
  allowGenerate = false,
  revealOnGenerate = false,
  value,
  defaultValue,
  onChange,
  onValueChange,
  labels,
  ref,
  "aria-describedby": ariaDescribedBy,
  ...props
}: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const generationCount = useRef(0);
  const [uncontrolledValue, setUncontrolledValue] = useState(
    typeof defaultValue === "string" ? defaultValue : "",
  );
  const inputRef = useRef<HTMLInputElement | null>(null);
  const rulesId = useId();
  const { passwordField: defaultLabels } = useUiStrings();
  const resolvedValue = typeof value === "string" ? value : uncontrolledValue;
  const mergedLabels = { ...defaultLabels, ...labels };
  const rules = showRules ? evaluatePasswordRules(resolvedValue, mergedLabels) : null;
  const describedBy = [ariaDescribedBy, rules && rulesId].filter(Boolean).join(" ") || undefined;

  function setRefs(node: HTMLInputElement | null) {
    inputRef.current = node;
    if (typeof ref === "function") {
      ref(node);
    } else if (ref) {
      ref.current = node;
    }
  }
  function handleChange(event: React.ChangeEvent<HTMLInputElement>) {
    if (typeof value !== "string") {
      setUncontrolledValue(event.target.value);
    }
    // Người dùng tự gõ lại thì câu báo "đã tạo mật khẩu" không còn đúng.
    setAnnouncement("");
    onChange?.(event);
    onValueChange?.(event.target.value);
  }

  /**
   * Báo qua vùng `role="status"`: focus ở lại nút Tạo ngẫu nhiên nên người dùng trình đọc màn hình không biết ô
   * đã có giá trị, cũng không biết mật khẩu đang hiện trên màn hình. Bấm hai lần liền nhau cho cùng một câu;
   * vùng thông báo chỉ đọc lại khi nội dung đổi, nên lần chẵn thêm một dấu cách không ngắt.
   */
  function announceGenerated(shown: boolean) {
    generationCount.current += 1;
    const message = shown ? mergedLabels.generatedShown : mergedLabels.generated;
    setAnnouncement(generationCount.current % 2 === 0 ? `${message} ` : message);
  }

  function handleGenerate() {
    const next = generateStrongPassword(12);
    if (revealOnGenerate) {
      setVisible(true);
    }
    const input = inputRef.current;
    if (input) {
      const valueSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
      valueSetter?.call(input, next);
      input.dispatchEvent(new Event("input", { bubbles: true }));
      announceGenerated(visible || revealOnGenerate);
      return;
    }
    if (typeof value !== "string") {
      setUncontrolledValue(next);
    }
    onValueChange?.(next);
    announceGenerated(visible || revealOnGenerate);
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          {leadingIcon && (
            <span
              aria-hidden
              className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-text-placeholder [&_svg]:size-4"
            >
              {leadingIcon}
            </span>
          )}
          <input
            ref={setRefs}
            type={visible ? "text" : "password"}
            value={value}
            defaultValue={value === undefined ? defaultValue : undefined}
            onChange={handleChange}
            aria-describedby={describedBy}
            className={cn(
              fieldBase,
              size === "lg" ? "h-12" : "h-10",
              "pr-11 pl-3",
              leadingIcon && "pl-10",
              className,
            )}
            {...props}
          />
          <button
            type="button"
            // Một cách báo trạng thái duy nhất: tên nút đổi theo hành động. Thêm `aria-pressed` thì trạng thái bị
            // nói hai lần và ngược nghĩa ("Ẩn mật khẩu, đang nhấn").
            aria-label={visible ? mergedLabels.hidePassword : mergedLabels.showPassword}
            onClick={() => setVisible((prev) => !prev)}
            className={cn(
              "absolute inset-y-0 right-1 my-auto flex size-9 items-center justify-center rounded-sm text-text-muted transition-colors duration-150 hover:text-text motion-reduce:transition-none",
              focusRing,
            )}
          >
            {visible ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
          </button>
        </div>
        {allowGenerate && (
          <button
            type="button"
            onClick={handleGenerate}
            className={cn(
              "inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-border bg-surface px-3 text-label font-medium text-text-secondary transition-colors duration-150 hover:border-border-strong hover:bg-subtle motion-reduce:transition-none",
              size === "lg" ? "h-12" : "h-10",
              focusRing,
            )}
          >
            <RefreshCw aria-hidden className="size-4" />
            <span>{mergedLabels.generate}</span>
          </button>
        )}
      </div>

      {allowGenerate && <LiveStatus message={announcement} />}

      {rules && (
        <ul
          id={rulesId}
          aria-label={mergedLabels.rulesLabel}
          className={cn("grid gap-1 text-caption", rulesColumns === 2 && "gap-x-4 sm:grid-cols-2")}
        >
          {rules.map((rule) => (
            <li
              key={rule.key}
              data-met={rule.met ? "true" : "false"}
              className={cn(
                "flex items-center gap-2 transition-colors duration-150 motion-reduce:transition-none",
                rule.met ? "font-medium text-success" : "text-text-muted",
              )}
            >
              {rule.met ? (
                <Check aria-hidden className="size-3.5 shrink-0" />
              ) : (
                <Circle aria-hidden className="size-3.5 shrink-0" />
              )}
              <span>{rule.label}</span>
              <span className="sr-only">: {rule.met ? mergedLabels.ruleMet : mergedLabels.ruleUnmet}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
