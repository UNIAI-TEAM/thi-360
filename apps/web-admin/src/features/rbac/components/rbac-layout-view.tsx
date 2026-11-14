"use client";

import { Button, Card, EmptyState, NavTab, NavTabs, PageHeader, Skeleton } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import {
  getAllowedRbacTabs,
  rbacNavigationHref,
  rbacTabFromPathname,
  rbacTabHref,
  resolveActiveRbacTab,
} from "../rbac-tabs";
import type { RbacTabKey } from "../types";

export interface RbacLayoutContextValue {
  /** Mã quyền đang chọn trên URL (`?permission=CODE`). */
  selectedPermissionCode: string | null;
  /** Hộp thoại "Tạo vai trò": nút mở nằm ở tiêu đề trang (layout), hộp thoại nằm trong trang Vai trò. */
  createRoleOpen: boolean;
  setCreateRoleOpen: (open: boolean) => void;
  /** Chuyển sang tab khác (hoặc đổi quyền đang chọn ở tab hiện tại); bỏ qua tab người dùng không được xem. */
  navigateToTab: (nextTab: RbacTabKey, permissionCode?: string) => void;
}

const RbacLayoutContext = createContext<RbacLayoutContextValue | null>(null);

export function useRbacLayout(): RbacLayoutContextValue {
  const context = use(RbacLayoutContext);
  if (!context) throw new Error("useRbacLayout must be used within <RbacLayoutView>.");
  return context;
}

function replaceQueryUrl(nextUrl: string): void {
  if (typeof window !== "undefined") {
    window.history.replaceState(null, "", nextUrl);
  }
}

/**
 * Khung chung của bốn trang con Phân quyền: tiêu đề, thanh điều hướng dạng tab và vùng nội dung.
 * Đặt ở layout của đoạn `rbac` nên tiêu đề và thanh tab không bị dựng lại khi đổi trang con.
 * `/rbac` và trang con không được xem đều chuyển sang trang con đầu tiên được phép.
 */
export function RbacLayoutView({ children }: { children: ReactNode }) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const sessionState = useSession();
  const { permissions, can } = usePermissions();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const router = useRouter();

  const [createRoleOpen, setCreateRoleOpen] = useState(false);
  const allowedTabs = useMemo(
    () => getAllowedRbacTabs(permissions),
    [permissions],
  );

  const requestedTab = rbacTabFromPathname(pathname);
  const selectedPermissionCode = searchParams.get("permission");
  const activeTab = resolveActiveRbacTab(requestedTab, allowedTabs);
  const redirectHref =
    sessionState.status === "ready" && activeTab && requestedTab !== activeTab
      ? rbacTabHref(activeTab, selectedPermissionCode)
      : null;

  useEffect(() => {
    if (redirectHref) router.replace(redirectHref);
  }, [redirectHref, router]);

  const navigateToTab = useCallback(
    (nextTab: RbacTabKey, permissionCode?: string) => {
      if (!activeTab || !allowedTabs.includes(nextTab)) return;
      const href = rbacNavigationHref(
        nextTab,
        { tab: activeTab, permission: selectedPermissionCode },
        permissionCode,
      );
      if (nextTab === activeTab) {
        // Cùng trang, chỉ đổi `?permission=`: cập nhật URL tại chỗ, không tải lại trang con và không cuộn.
        replaceQueryUrl(href);
      } else {
        router.push(href);
      }
    },
    [activeTab, allowedTabs, router, selectedPermissionCode],
  );

  const contextValue = useMemo<RbacLayoutContextValue>(
    () => ({ selectedPermissionCode, createRoleOpen, setCreateRoleOpen, navigateToTab }),
    [selectedPermissionCode, createRoleOpen, navigateToTab],
  );

  if (sessionState.status === "loading") {
    return (
      <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-4">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-5 w-96 max-w-full" />
        <Skeleton className="h-10 w-80" />
        <Skeleton className="h-72" />
      </div>
    );
  }

  if (sessionState.status === "error") {
    return (
      <Card padding="md">
        <EmptyState
          icon={<TriangleAlert />}
          title={t("sessionErrorTitle")}
          description={t("sessionErrorDescription")}
          action={
            <Button variant="secondary" onClick={sessionState.retry}>
              {t("actions.retry")}
            </Button>
          }
        />
      </Card>
    );
  }

  if (allowedTabs.length === 0 || !activeTab) {
    return <ForbiddenView />;
  }

  const canCreateRole = activeTab === "roles" && can("ROLE.MODIFY");

  return (
    <RbacLayoutContext value={contextValue}>
      <div className="flex flex-col gap-4">
        <PageHeader
          title={t("title")}
          description={t(`tabsDescription.${activeTab}`)}
          actions={
            canCreateRole ? (
              <Button onClick={() => setCreateRoleOpen(true)}>
                {t("rolesTab.createButton")}
              </Button>
            ) : undefined
          }
        />

        <NavTabs aria-label={t("tabs.ariaLabel")}>
          {allowedTabs.map((tabKey) => (
            <NavTab key={tabKey} asChild current={tabKey === activeTab}>
              <Link
                href={rbacNavigationHref(tabKey, {
                  tab: activeTab,
                  permission: selectedPermissionCode,
                })}
              >
                {t(`tabs.${tabKey}`)}
              </Link>
            </NavTab>
          ))}
        </NavTabs>

        {requestedTab === activeTab ? (
          // `key` theo đường dẫn: đổi trang con thì vùng nội dung là phần tử mới nên hiệu ứng vào chạy lại;
          // đổi `?permission=` trong cùng trang thì không. Tiêu đề và thanh tab nằm ngoài nên đứng yên.
          <div key={pathname} className="animate-route-in motion-reduce:animate-none">
            {children}
          </div>
        ) : (
          // Đang chuyển sang trang con được phép: không dựng trang bị cấm để khỏi gọi API thiếu quyền.
          <div role="status" aria-label={commonT("loading")}>
            <Skeleton className="h-72" />
          </div>
        )}
      </div>
    </RbacLayoutContext>
  );
}
