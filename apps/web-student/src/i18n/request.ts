import { deepMergeMessages, LOCALE_COOKIE_NAME, parseLocaleCookie } from "@thi360/web-kit/preferences";
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
  const locale = parseLocaleCookie(await readLocaleCookie());
  return {
    locale,
    messages: locale === "en" ? deepMergeMessages(viMessages, enMessages) : viMessages,
    timeZone: "Asia/Ho_Chi_Minh",
  };
});
