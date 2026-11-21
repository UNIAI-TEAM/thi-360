"use client";

import { useLayoutEffect, useRef, useState, type RefObject } from "react";
import { useUiStrings } from "../../components/ui-strings";
import { cn } from "../../lib/cn";
import { focusRing } from "../../lib/styles";
import { DEFAULT_ION_SYMBOLS, insertTextAtCursor, type SymbolKey } from "./symbols";

export interface SymbolInputBarProps {
  targetRef: RefObject<HTMLInputElement | null>;
  value: string;
  onChange: (nextValue: string) => void;
  symbols?: readonly SymbolKey[];
  disabled?: boolean;
  className?: string;
}

export function SymbolInputBar({
  targetRef,
  value,
  onChange,
  symbols = DEFAULT_ION_SYMBOLS,
  disabled = false,
  className,
}: SymbolInputBarProps) {
  const strings = useUiStrings().question.symbolInput;
  const pendingCursorRef = useRef<number | null>(null);
  const [insertTick, setInsertTick] = useState(0);

  useLayoutEffect(() => {
    const nextCursor = pendingCursorRef.current;
    if (nextCursor == null) return;
    pendingCursorRef.current = null;
    const input = targetRef.current;
    if (!input) return;
    input.focus();
    const clamped = Math.min(nextCursor, input.value.length);
    input.setSelectionRange(clamped, clamped);
  }, [value, insertTick, targetRef]);

  function handleInsert(sym: SymbolKey) {
    if (disabled) return;
    const input = targetRef.current;
    const selectionStart = input?.selectionStart ?? null;
    const selectionEnd = input?.selectionEnd ?? null;
    const { nextValue, nextCursor } = insertTextAtCursor(
      value,
      selectionStart,
      selectionEnd,
      sym.insert,
    );
    pendingCursorRef.current = nextCursor;
    onChange(nextValue);
    setInsertTick((n) => n + 1);
  }

  return (
    <div
      role="group"
      aria-label={strings.groupLabel}
      className={cn("flex flex-wrap gap-1.5", className)}
    >
      {symbols.map((sym) => (
        <button
          key={sym.id}
          type="button"
          disabled={disabled}
          aria-label={strings.symbolNames[sym.id] ?? sym.label}
          data-symbol-insert={sym.insert}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => handleInsert(sym)}
          className={cn(
            "inline-flex min-h-11 min-w-11 items-center justify-center rounded-lg border border-border bg-surface px-2.5 text-title font-semibold text-text shadow-xs transition-colors duration-150 motion-reduce:transition-none",
            "hover:border-border-strong hover:bg-subtle active:bg-primary-50",
            "disabled:pointer-events-none disabled:opacity-50",
            focusRing,
          )}
        >
          {sym.label}
        </button>
      ))}
    </div>
  );
}
