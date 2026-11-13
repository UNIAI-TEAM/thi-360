"use client";

import { Button, notify } from "@thi360/ui";
import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { StatusPage } from "@/components/status-page";
import { webUrl } from "@/lib/env";
import { logout } from "./api";
import { useSession } from "./session-provider";

/** Trang 403 (`Forbidden.dc.html`): về trang chủ theo `homeUrl`, hoặc đăng xuất để vào bằng tài khoản khác. */
export function ForbiddenView() {
  const t = useTranslations("forbidden");
  const common = useTranslations("common");
  const errors = useTranslations("errors");
  const state = useSession();
  const [leaving, setLeaving] = useState(false);

  async function signInAsAnother() {
    setLeaving(true);
    try {
      await logout();
    } catch {
      notify.error(errors("logoutFailed"));
    } finally {
      setLeaving(false);
    }
  }

  const description =
    state.status === "ready"
      ? t.rich("description", {
          username: state.session.username,
          strong: (chunks) => <strong className="font-semibold text-text">{chunks}</strong>,
        })
      : t("descriptionAnonymous");

  const homeHref = state.status === "ready" ? state.session.homeUrl : webUrl();

  return (
    <StatusPage
      icon={Lock}
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={description}
      actions={
        <>
          <Button asChild size="lg">
            <a href={homeHref}>{common("backHome")}</a>
          </Button>
          <Button variant="secondary" size="lg" loading={leaving} onClick={signInAsAnother}>
            {t("otherAccount")}
          </Button>
        </>
      }
    />
  );
}
