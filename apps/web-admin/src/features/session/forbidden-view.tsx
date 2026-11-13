"use client";

import { Lock } from "lucide-react";
import { Button } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState } from "react";
import { StatusPage } from "@/components/status-page";
import { logout } from "./api";
import { useSession } from "./session-provider";

/** Trang 403 (`Forbidden.dc.html`): về trang chủ, hoặc đăng xuất để vào bằng tài khoản khác. */
export function ForbiddenView() {
  const t = useTranslations("forbidden");
  const common = useTranslations("common");
  const state = useSession();
  const [leaving, setLeaving] = useState(false);

  async function signInAsAnother() {
    setLeaving(true);
    try {
      await logout();
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

  return (
    <StatusPage
      icon={Lock}
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={description}
      actions={
        <>
          <Button asChild size="lg">
            <Link href="/">{common("backHome")}</Link>
          </Button>
          <Button variant="secondary" size="lg" loading={leaving} onClick={signInAsAnother}>
            {t("otherAccount")}
          </Button>
        </>
      }
    />
  );
}
