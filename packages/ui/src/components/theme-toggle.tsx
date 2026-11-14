"use client";

import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import type { ThemeMode } from "../theme/palettes";
import { Tooltip } from "./tooltip";
import {
  THEME_PREFERENCE_COOKIE,
  useUiPreferences,
  writeClientPreferenceCookie,
} from "./ui-preferences";
import { useUiStrings } from "./ui-strings";

export type ThemeToggleProps = {
  theme?: ThemeMode;
  defaultTheme?: ThemeMode;
  cookieDomain?: string;
  onThemeChange?: (next: ThemeMode) => void;
  className?: string;
};

function readDomTheme(fallback: ThemeMode): ThemeMode {
  if (typeof document === "undefined") return fallback;
  const domTheme = document.documentElement.dataset.theme;
  return domTheme === "dark" || domTheme === "light" ? domTheme : fallback;
}

export function ThemeToggle({
  theme: controlledTheme,
  defaultTheme = "light",
  cookieDomain,
  onThemeChange,
  className,
}: ThemeToggleProps) {
  const prefs = useUiPreferences();
  const strings = useUiStrings();
  const [uncontrolledTheme, setUncontrolledTheme] = useState<ThemeMode>(() =>
    readDomTheme(defaultTheme),
  );

  useEffect(() => {
    if (controlledTheme !== undefined || prefs !== null || typeof document === "undefined") return;
    const root = document.documentElement;
    const sync = () => {
      const domTheme = root.dataset.theme;
      if (domTheme === "light" || domTheme === "dark") {
        setUncontrolledTheme(domTheme);
      }
    };
    sync();
    const observer = new MutationObserver(sync);
    observer.observe(root, { attributes: true, attributeFilter: ["data-theme"] });
    return () => observer.disconnect();
  }, [controlledTheme, prefs]);

  const currentTheme: ThemeMode = controlledTheme ?? prefs?.theme ?? uncontrolledTheme;
  const label =
    currentTheme === "dark" ? strings.themeToggle.switchToLight : strings.themeToggle.switchToDark;

  function handleToggle() {
    const next: ThemeMode = currentTheme === "dark" ? "light" : "dark";
    if (onThemeChange) {
      if (typeof document !== "undefined") {
        document.documentElement.dataset.theme = next;
      }
      onThemeChange(next);
      return;
    }
    if (prefs) {
      prefs.setTheme(next);
      return;
    }
    setUncontrolledTheme(next);
    if (typeof document !== "undefined") {
      document.documentElement.dataset.theme = next;
    }
    writeClientPreferenceCookie(THEME_PREFERENCE_COOKIE, next, cookieDomain);
  }

  return (
    <Tooltip content={label}>
      <button
        type="button"
        aria-label={label}
        aria-pressed={currentTheme === "dark"}
        onClick={handleToggle}
        className={cn(
          "inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-border bg-surface text-text-secondary transition-colors duration-150 hover:border-border-strong hover:bg-subtle hover:text-text motion-reduce:transition-none",
          focusRing,
          className,
        )}
      >
        {currentTheme === "dark" ? (
          <Moon aria-hidden className="size-[18px]" />
        ) : (
          <Sun aria-hidden className="size-[18px]" />
        )}
      </button>
    </Tooltip>
  );
}
