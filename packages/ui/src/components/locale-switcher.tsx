"use client";

import { Check, Languages } from "lucide-react";
import { useRef } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import type { UiLocale } from "../messages/ui-strings";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItemIndicator,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "./dropdown-menu";
import { Tooltip } from "./tooltip";
import {
  LOCALE_PREFERENCE_COOKIE,
  useUiPreferences,
  writeClientPreferenceCookie,
} from "./ui-preferences";
import { useUiStrings } from "./ui-strings";

const LOCALE_OPTIONS: readonly UiLocale[] = ["vi", "en"];

export type LocaleSwitcherProps = {
  locale?: UiLocale;
  cookieDomain?: string;
  onLocaleChange?: (next: UiLocale) => void;
  className?: string;
};

export function LocaleSwitcher({
  locale: controlledLocale,
  cookieDomain,
  onLocaleChange,
  className,
}: LocaleSwitcherProps) {
  const prefs = useUiPreferences();
  const strings = useUiStrings();
  const currentLocale: UiLocale = controlledLocale ?? prefs?.locale ?? "vi";
  const label = strings.localeSwitcher.label;
  const needsInitialFocusRef = useRef(false);

  function handleSelect(next: UiLocale) {
    if (onLocaleChange) {
      onLocaleChange(next);
      return;
    }
    if (prefs) {
      prefs.setLocale(next);
      return;
    }
    writeClientPreferenceCookie(LOCALE_PREFERENCE_COOKIE, next, cookieDomain);
  }

  return (
    <DropdownMenu
      onOpenChange={(open) => {
        needsInitialFocusRef.current = open;
      }}
    >
      <Tooltip content={label}>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            aria-label={label}
            className={cn(
              "inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-border bg-surface text-text-secondary transition-colors duration-150 hover:border-border-strong hover:bg-subtle hover:text-text motion-reduce:transition-none",
              focusRing,
              className,
            )}
          >
            <Languages aria-hidden className="size-[18px]" />
          </button>
        </DropdownMenuTrigger>
      </Tooltip>
      <DropdownMenuContent
        align="end"
        loop
        className="min-w-40"
        onFocus={(event) => {
          if (!needsInitialFocusRef.current || event.target !== event.currentTarget) return;
          needsInitialFocusRef.current = false;
          const container = event.currentTarget;
          queueMicrotask(() => {
            if (!container.isConnected || !container.contains(document.activeElement)) return;
            const target =
              container.querySelector<HTMLElement>('[role="menuitemradio"][aria-checked="true"]') ??
              container.querySelector<HTMLElement>('[role="menuitemradio"]');
            target?.focus({ preventScroll: true });
          });
        }}
      >
        <DropdownMenuRadioGroup
          value={currentLocale}
          onValueChange={(value) => {
            if (value === "vi" || value === "en") {
              handleSelect(value);
            }
          }}
        >
          {LOCALE_OPTIONS.map((itemLocale) => (
            <DropdownMenuRadioItem key={itemLocale} value={itemLocale}>
              <span>{strings.localeSwitcher.options[itemLocale]}</span>
              <DropdownMenuItemIndicator className="ml-auto flex items-center">
                <Check aria-hidden className="size-4" />
              </DropdownMenuItemIndicator>
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
