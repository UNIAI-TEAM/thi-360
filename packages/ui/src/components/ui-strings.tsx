"use client";

import { createContext, useContext, type ReactNode } from "react";
import { UI_STRINGS_BY_LOCALE, type UiLocale, type UiStrings } from "../messages/ui-strings";

const UiStringsContext = createContext<UiStrings>(UI_STRINGS_BY_LOCALE.vi);

export type UiStringsProviderProps = {
  locale?: UiLocale;
  strings?: Partial<UiStrings>;
  children: ReactNode;
};

export function UiStringsProvider({ locale = "vi", strings, children }: UiStringsProviderProps) {
  const base = UI_STRINGS_BY_LOCALE[locale] ?? UI_STRINGS_BY_LOCALE.vi;
  const value: UiStrings = strings ? { ...base, ...strings } : base;
  return <UiStringsContext.Provider value={value}>{children}</UiStringsContext.Provider>;
}

export function useUiStrings(): UiStrings {
  return useContext(UiStringsContext);
}
