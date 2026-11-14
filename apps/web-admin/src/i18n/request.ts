import {
  APP_TIME_ZONE,
  deepMergeMessages,
  INTL_FORMATS,
  LOCALE_COOKIE_NAME,
  parseLocaleCookie,
  resolveIntlLocale,
} from "@thi360/web-kit/preferences";
import { getRequestConfig } from "next-intl/server";
import { cookies } from "next/headers";
import viMessages from "../messages/vi.json";
import enMessages from "../messages/en.json";

async function readLocaleCookie(): Promise<string | undefined> {
  try {
    const store = await cookies();
    return store.get(LOCALE_COOKIE_NAME)?.value;
  } catch {
    return undefined;
  }
}

export default getRequestConfig(async () => {
  const uiLocale = parseLocaleCookie(await readLocaleCookie());
  return {
    locale: resolveIntlLocale(uiLocale),
    messages: uiLocale === "en" ? deepMergeMessages(viMessages, enMessages) : viMessages,
    timeZone: APP_TIME_ZONE,
    formats: INTL_FORMATS,
  };
});
