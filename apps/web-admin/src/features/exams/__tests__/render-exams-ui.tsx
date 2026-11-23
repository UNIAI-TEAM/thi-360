import { QueryClient } from "@tanstack/react-query";
import type { AbstractIntlMessages } from "next-intl";
import type { ReactElement } from "react";
import { SESSION_QUERY_KEY } from "@/features/session/api";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";

export const DEFAULT_EXAM_PERMISSIONS = ["EXAM.VIEW", "EXAM.MODIFY", "EXAM.PUBLISH"] as const;

export interface RenderExamsUiOptions {
  client?: QueryClient;
  locale?: string;
  messages?: AbstractIntlMessages;
  permissions?: readonly string[];
}

export function renderExamsUi(
  ui: ReactElement,
  {
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
    locale,
    messages,
    permissions = DEFAULT_EXAM_PERMISSIONS,
  }: RenderExamsUiOptions = {},
) {
  const session: Session = {
    ...TEST_ADMIN_SESSION,
    permissions: [...TEST_ADMIN_SESSION.permissions, ...permissions],
  };
  client.setQueryData(SESSION_QUERY_KEY, session);
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>{ui}</SessionProvider>,
    { client, locale, messages },
  );
}
