"use client";

import {
  Button,
  Card,
  EmptyState,
  PageHeader,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { hasPermission } from "@/features/session/permissions";
import { usePermissions, useSession } from "@/features/session/session-provider";
import type { RbacTabKey } from "../types";
import { BulkAssignTab, type BulkAssignTabProps } from "./bulk-assign-tab";
import { PermissionUsersTab, type PermissionUsersTabProps } from "./permission-users-tab";
import { PermissionsTab, type PermissionsTabProps } from "./permissions-tab";
import { RolesTab, type RolesTabProps } from "./roles-tab";

export const RBAC_TAB_ORDER: readonly RbacTabKey[] = [
  "vai-tro",
  "quyen",
  "nguoi-dung",
  "gan-hang-loat",
];

export function getAllowedRbacTabs(permissions: ReadonlySet<string>): RbacTabKey[] {
  const allowed: RbacTabKey[] = [];
  if (hasPermission(permissions, "ROLE.VIEW")) allowed.push("vai-tro");
  if (hasPermission(permissions, "PERMISSION.VIEW")) allowed.push("quyen");
  if (
    hasPermission(permissions, "PERMISSION.VIEW") &&
    hasPermission(permissions, "USER.VIEW")
  ) {
    allowed.push("nguoi-dung");
  }
  if (hasPermission(permissions, "USER.ASSIGN")) allowed.push("gan-hang-loat");
  return allowed;
}

export function resolveActiveRbacTab(
  requestedTab: string | null,
  allowedTabs: readonly RbacTabKey[],
): RbacTabKey | null {
  if (allowedTabs.length === 0) return null;
  if (
    requestedTab &&
    (allowedTabs as readonly string[]).includes(requestedTab)
  ) {
    return requestedTab as RbacTabKey;
  }
  return allowedTabs[0]!;
}

export interface RbacPageViewProps {
  rolesTabProps?: Omit<RolesTabProps, "createRequestNonce" | "onCreateRequestConsumed">;
  permissionsTabProps?: Omit<PermissionsTabProps, "onSelectPermissionUsers">;
  permissionUsersTabProps?: Omit<
    PermissionUsersTabProps,
    "selectedPermissionCode" | "onSelectPermission" | "onAssignMany" | "onViewRoles"
  >;
  bulkAssignTabProps?: Omit<BulkAssignTabProps, "initialPermissionCode">;
}

function replaceQueryUrl(nextUrl: string): void {
  if (typeof window !== "undefined") {
    window.history.replaceState(null, "", nextUrl);
  }
}

export function RbacPageView({
  rolesTabProps,
  permissionsTabProps,
  permissionUsersTabProps,
  bulkAssignTabProps,
}: RbacPageViewProps = {}) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const sessionState = useSession();
  const { permissions, can } = usePermissions();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [createRoleNonce, setCreateRoleNonce] = useState(0);
  const [consumedCreateNonce, setConsumedCreateNonce] = useState(0);
  const handleCreateRequestConsumed = useCallback((nonce: number) => {
    setConsumedCreateNonce((prev) => Math.max(prev, nonce));
  }, []);
  const allowedTabs = useMemo(
    () => getAllowedRbacTabs(permissions),
    [permissions],
  );

  const requestedTab = searchParams.get("tab");
  const requestedPermission = searchParams.get("permission");
  const activeTab = resolveActiveRbacTab(requestedTab, allowedTabs);

  useEffect(() => {
    if (sessionState.status !== "ready" || !activeTab) return;
    if (requestedTab === activeTab) return;

    const nextParams = new URLSearchParams(searchParams.toString());
    nextParams.set("tab", activeTab);
    if (activeTab !== "nguoi-dung" && activeTab !== "gan-hang-loat") {
      nextParams.delete("permission");
    }
    replaceQueryUrl(`${pathname}?${nextParams.toString()}`);
  }, [activeTab, pathname, requestedTab, searchParams, sessionState.status]);

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

  const currentTab = activeTab;
  const canCreateRole = currentTab === "vai-tro" && can("ROLE.MODIFY");

  function navigateToTab(nextTab: RbacTabKey, permissionCode?: string) {
    if (!(allowedTabs as readonly string[]).includes(nextTab)) return;
    const nextParams = new URLSearchParams(searchParams.toString());
    nextParams.set("tab", nextTab);
    if (permissionCode) {
      nextParams.set("permission", permissionCode);
    } else if (nextTab !== "nguoi-dung" && nextTab !== "gan-hang-loat") {
      nextParams.delete("permission");
    } else if (nextTab === "gan-hang-loat" && currentTab !== "nguoi-dung") {
      nextParams.delete("permission");
    }
    replaceQueryUrl(`${pathname}?${nextParams.toString()}`);
  }

  function handleTabChange(nextTab: string) {
    navigateToTab(nextTab as RbacTabKey);
  }
  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("title")}
        description={t(`tabsDescription.${currentTab}`)}
        actions={
          canCreateRole ? (
            <Button onClick={() => setCreateRoleNonce((prev) => prev + 1)}>
              {t("rolesTab.createButton")}
            </Button>
          ) : undefined
        }
      />

      <Tabs
        variant="content"
        value={currentTab}
        onValueChange={handleTabChange}
      >
        <TabsList aria-label={t("tabs.ariaLabel")} className="flex-wrap">
          {allowedTabs.map((tabKey) => (
            <TabsTrigger key={tabKey} value={tabKey}>
              {t(`tabs.${tabKey}`)}
            </TabsTrigger>
          ))}
        </TabsList>

        {allowedTabs.includes("vai-tro") && (
          <TabsContent value="vai-tro">
            <RolesTab
              createRequestNonce={createRoleNonce > consumedCreateNonce ? createRoleNonce : 0}
              onCreateRequestConsumed={handleCreateRequestConsumed}
              {...rolesTabProps}
            />
          </TabsContent>
        )}

        {allowedTabs.includes("quyen") && (
          <TabsContent value="quyen">
            <PermissionsTab
              {...permissionsTabProps}
              onSelectPermissionUsers={(code) => navigateToTab("nguoi-dung", code)}
            />
          </TabsContent>
        )}

        {allowedTabs.includes("nguoi-dung") && (
          <TabsContent value="nguoi-dung">
            <PermissionUsersTab
              {...permissionUsersTabProps}
              selectedPermissionCode={requestedPermission}
              onSelectPermission={(code) => navigateToTab("nguoi-dung", code)}
              onAssignMany={(code) => navigateToTab("gan-hang-loat", code)}
              onViewRoles={() => navigateToTab("vai-tro")}
            />
          </TabsContent>
        )}
        {allowedTabs.includes("gan-hang-loat") && (
          <TabsContent value="gan-hang-loat">
            <BulkAssignTab
              {...bulkAssignTabProps}
              initialPermissionCode={requestedPermission}
            />
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}
