"use client";

import { Check, Languages } from "lucide-react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import type { UiLocale } from "../messages/ui-strings";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
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
    <DropdownMenu>
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
      <DropdownMenuContent align="end" className="min-w-40">
        {LOCALE_OPTIONS.map((itemLocale) => {
          const selected = currentLocale === itemLocale;
          return (
            <DropdownMenuItem
              key={itemLocale}
              aria-current={selected ? "true" : undefined}
              className={cn(selected && "bg-primary-50 font-semibold text-primary-text")}
              onSelect={() => handleSelect(itemLocale)}
            >
              <span>{strings.localeSwitcher.options[itemLocale]}</span>
              {selected && <Check aria-hidden className="ml-auto size-4" />}
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
