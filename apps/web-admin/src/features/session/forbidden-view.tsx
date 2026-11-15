"use client";

import { Button, notify } from "@thi360/ui";
import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { StatusPage, type StatusPageProps } from "@/components/status-page";
import { useInsideShellMain } from "@/features/shell/shell-main";
import { webUrl } from "@/lib/env";
import { logout } from "./api";
import { useSession } from "./session-provider";

/*
 * Giả định về kit (UNI-2581, nhánh kit làm song song): `StatusPage` có thêm prop boolean `embedded` để KHÔNG dựng
 * `<main>` và chiều cao toàn màn hình riêng khi nằm trong khung trang. Trước khi nhánh kit merge, prop này chưa có
 * và bị bỏ qua. Nếu kit thêm prop mới mà không có tên `embedded`, kiểu dưới đây thành `never` và typecheck báo lỗi
 * ngay tại `statusPageLandmarkProps`, để người tích hợp sửa tên prop ở đó.
 */
type KnownStatusPageProp = "icon" | "eyebrow" | "title" | "description" | "actions" | "meta";
type AddedStatusPageProp = Exclude<keyof StatusPageProps, KnownStatusPageProp>;
type EmbeddedPropContract = [AddedStatusPageProp] extends [never]
  ? true
  : "embedded" extends AddedStatusPageProp
    ? true
    : never;

function statusPageLandmarkProps(embedded: boolean): object {
  const contractHolds: EmbeddedPropContract = true;
  return contractHolds && embedded ? { embedded: true } : {};
}

/**
 * Trang 403 (`Forbidden.dc.html`): về trang chủ theo `homeUrl`, hoặc đăng xuất để vào bằng tài khoản khác.
 * Dùng ở hai nơi: trang riêng `/forbidden` (tự dựng `<main>`) và bên trong khung trang khi một màn thiếu quyền
 * (khung đã có `<main>`, nên không dựng thêm và tự đổi tiêu đề thẻ trình duyệt).
 */
export function ForbiddenView() {
  const t = useTranslations("forbidden");
  const common = useTranslations("common");
  const errors = useTranslations("errors");
  const metadata = useTranslations("metadata");
  const state = useSession();
  const [leaving, setLeaving] = useState(false);
  const insideShell = useInsideShellMain();
  const documentTitle = metadata("titleTemplate", { title: t("title") });

  // Trong khung trang, tiêu đề thẻ trình duyệt vẫn là của màn bị chặn: đổi sang tiêu đề 403 khi đang hiện,
  // trả lại khi rời đi (chỉ trả khi chưa có trang mới đặt tiêu đề khác).
  useEffect(() => {
    if (!insideShell) return;
    const previousTitle = document.title;
    document.title = documentTitle;
    return () => {
      if (document.title === documentTitle) {
        document.title = previousTitle;
      }
    };
  }, [insideShell, documentTitle]);

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
      {...statusPageLandmarkProps(insideShell)}
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
