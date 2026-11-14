"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  EmptyState,
  FormField,
  Input,
  notify,
  SectionTitle,
  Skeleton,
  StatusChip,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@thi360/ui";
import { initialsOf } from "@thi360/web-kit/client";
import { ArrowLeft, UserX } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { Can } from "@/features/session/can";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  defaultUsersApiDeps,
  type UpdateUserRequest,
  type UserDetail,
  type UsersApiDeps,
  USERS_QUERY_KEYS,
  UsersApiError,
} from "../api";
import { createUpdateUserSchema, type UpdateUserFormValues } from "../schema";
import {
  formatUserDateTime,
  getUserDisplayStatus,
  mapUserErrorCodeToKey,
  resolveUsersErrorMessage,
} from "../utils";
import { UserActionDialogs, useUserActionController } from "./user-action-dialogs";
import { UserClassesTab } from "@/features/classes/components/user-classes-tab";
import { UserHistoryTab } from "./user-history-tab";
import { UserRolesPermissionsPlaceholder } from "./user-roles-permissions-placeholder";

export interface UserDetailViewProps {
  userId: string;
  deps?: Partial<UsersApiDeps>;
}

export function UserDetailView({ userId, deps }: UserDetailViewProps) {
  const sessionState = useSession();
  const { can } = usePermissions();

  if (sessionState.status === "loading") {
    return <UserDetailSkeleton />;
  }

  if (sessionState.status === "ready" && !can("USER.VIEW")) {
    return <ForbiddenView />;
  }

  return <UserDetailContent userId={userId} deps={{ ...defaultUsersApiDeps, ...deps }} />;
}

function UserDetailSkeleton() {
  const common = useTranslations("common");
  return (
    <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-10 w-96 max-w-full" />
      <Skeleton className="h-72 w-full" />
    </div>
  );
}

function UserDetailContent({ userId, deps }: { userId: string; deps: UsersApiDeps }) {
  const t = useTranslations("users");
  const common = useTranslations("common");
  const { can } = usePermissions();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState("info");

  const detailQuery = useQuery({
    queryKey: USERS_QUERY_KEYS.detail(userId),
    queryFn: () => deps.getUser(userId),
  });

  function refreshUser() {
    void queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEYS.detail(userId) });
    void queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEYS.lists() });
    void queryClient.invalidateQueries({ queryKey: ["users", "audit", userId] });
  }

  const actionController = useUserActionController({
    deps,
    onMutated: refreshUser,
  });

  if (detailQuery.isLoading) {
    return <UserDetailSkeleton />;
  }

  if (detailQuery.isError) {
    const isNotFound = getErrorStatus(detailQuery.error) === 404;
    return (
      <div className="flex flex-col gap-4">
        <div>
          <Button asChild variant="link" size="sm">
            <Link href="/tai-khoan">
              <ArrowLeft />
              <span>{t("detail.backToList")}</span>
            </Link>
          </Button>
        </div>
        <Card>
          <EmptyState
            icon={<UserX />}
            title={isNotFound ? t("detail.notFoundTitle") : t("detail.errorTitle")}
            description={
              isNotFound ? t("detail.notFoundDescription") : t("detail.errorDescription")
            }
            action={
              isNotFound ? (
                <Button asChild variant="secondary">
                  <Link href="/tai-khoan">{t("detail.backToList")}</Link>
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => void detailQuery.refetch()}>
                  {common("retry")}
                </Button>
              )
            }
          />
        </Card>
      </div>
    );
  }

  const user = detailQuery.data;
  if (!user) return null;

  const displayStatus = getUserDisplayStatus(user);
  const isDeleted = Boolean(user.deletedAt);
  const lastLoginDisplay = user.lastLoginAt
    ? formatUserDateTime(user.lastLoginAt)
    : t("detail.neverLoggedIn");

  return (
    <div className="flex flex-col gap-5">
      <div>
        <Button asChild variant="link" size="sm">
          <Link href="/tai-khoan">
            <ArrowLeft />
            <span>{t("detail.backToList")}</span>
          </Link>
        </Button>
      </div>

      <Card className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3.5">
          <div
            aria-hidden
            className="grid size-13 shrink-0 place-items-center rounded-full bg-primary-50 text-title font-bold text-primary-text"
          >
            {initialsOf(user.fullName, user.username)}
          </div>
          <div className="flex flex-col gap-1">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="text-page-title font-bold text-text">{user.fullName}</h1>
              <StatusChip tone={displayStatus.tone}>
                {t(`statuses.${displayStatus.status}`)}
              </StatusChip>
            </div>
            <p className="text-body text-text-secondary tabular-nums">
              {t("detail.subtitle", {
                username: user.username,
                code: user.code,
                source: t(`sources.${user.source}`),
                lastLogin: lastLoginDisplay,
              })}
            </p>
          </div>
        </div>

        {!isDeleted && (
          <Can permission="USER.MODIFY">
            <div className="flex flex-wrap items-center gap-2">
              <Button
                type="button"
                variant="secondary"
                disabled={actionController.checkingAction}
                onClick={() =>
                  actionController.openResetPassword({ id: user.id, username: user.username })
                }
              >
                {t("actions.resetPassword")}
              </Button>
              {user.status === "ACTIVE" ? (
                <Button
                  type="button"
                  variant="secondary"
                  loading={actionController.checkingAction}
                  onClick={() =>
                    void actionController.openDeactivate({ id: user.id, username: user.username })
                  }
                >
                  {t("actions.deactivate")}
                </Button>
              ) : (
                <Button
                  type="button"
                  variant="secondary"
                  disabled={actionController.checkingAction}
                  onClick={() =>
                    actionController.openActivate({ id: user.id, username: user.username })
                  }
                >
                  {t("actions.activate")}
                </Button>
              )}
              <Button
                type="button"
                variant="danger"
                loading={actionController.checkingAction}
                onClick={() =>
                  void actionController.openDelete({ id: user.id, username: user.username })
                }
              >
                {t("actions.delete")}
              </Button>
            </div>
          </Can>
        )}
      </Card>

      {isDeleted && (
        <div
          role="status"
          className="rounded-lg border border-border bg-neutral-bg px-4 py-3 text-body text-neutral"
        >
          {t("detail.deletedBanner", {
            time: formatUserDateTime(user.deletedAt),
            reason: user.deleteReason ?? "—",
          })}
        </div>
      )}

      {!isDeleted && user.status === "INACTIVE" && (
        <div
          role="status"
          className="rounded-lg border border-danger bg-danger-bg px-4 py-3 text-body text-danger"
        >
          {t("detail.inactiveBanner", {
            time: formatUserDateTime(user.inactivatedAt),
            reason: user.inactiveReason ?? "—",
          })}
        </div>
      )}

      <Tabs variant="content" value={activeTab} onValueChange={setActiveTab}>
        <TabsList aria-label={t("detail.tabsAriaLabel")}>
          <TabsTrigger value="info">{t("detail.tabs.info")}</TabsTrigger>
          <TabsTrigger value="classes">{t("detail.tabs.classes")}</TabsTrigger>
          <TabsTrigger value="roles">{t("detail.tabs.roles")}</TabsTrigger>
          {can("AUDIT.VIEW") && (
            <TabsTrigger value="history">{t("detail.tabs.history")}</TabsTrigger>
          )}
        </TabsList>

        <TabsContent value="info">
          <UserInfoTab
            key={`${user.id}-${user.version}`}
            user={user}
            canEdit={can("USER.MODIFY") && !isDeleted}
            updateUser={deps.updateUser}
            onUpdated={(updated) => {
              queryClient.setQueryData(USERS_QUERY_KEYS.detail(userId), updated);
              refreshUser();
            }}
            onReload={() => void detailQuery.refetch()}
          />
        </TabsContent>

        <TabsContent value="classes">
          <UserClassesTab userId={user.id} getUserClasses={deps.getUserClasses} />
        </TabsContent>

        <TabsContent value="roles">
          <UserRolesPermissionsPlaceholder userId={user.id} roles={user.roles} />
        </TabsContent>

        {can("AUDIT.VIEW") && (
          <TabsContent value="history">
            <UserHistoryTab
              userId={user.id}
              listUserAuditEvents={deps.listUserAuditEvents}
            />
          </TabsContent>
        )}
      </Tabs>

      <UserActionDialogs controller={actionController} />
    </div>
  );
}

function UserInfoTab({
  user,
  canEdit,
  updateUser,
  onUpdated,
  onReload,
}: {
  user: UserDetail;
  canEdit: boolean;
  updateUser: UsersApiDeps["updateUser"];
  onUpdated: (updated: UserDetail) => void;
  onReload: () => void;
}) {
  const t = useTranslations("users");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [staleConflict, setStaleConflict] = useState(false);

  const schema = useMemo(
    () =>
      createUpdateUserSchema({
        fullNameRequired: t("validation.fullNameRequired"),
        fullNameMax: t("validation.fullNameMax"),
        emailInvalid: t("validation.emailInvalid"),
        emailMax: t("validation.emailMax"),
        phoneMax: t("validation.phoneMax"),
      }),
    [t],
  );

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isDirty },
  } = useForm<UpdateUserFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: user.fullName,
      email: user.email ?? "",
      phone: user.phone ?? "",
    },
  });

  const mutation = useMutation({
    mutationFn: (payload: UpdateUserRequest) => updateUser(user.id, payload),
    onSuccess: (updated) => {
      setSubmitError(null);
      setStaleConflict(false);
      notify.success(t("detail.saveSuccess"));
      onUpdated(updated);
    },
    onError: (error) => {
      if (error instanceof UsersApiError) {
        if (error.code === "VERSION_CONFLICT" || error.status === 412) {
          setStaleConflict(true);
          setSubmitError(t("errorCodes.VERSION_CONFLICT"));
          return;
        }
        if (error.code === "EMAIL_TAKEN" || error.code === "EMAIL_INVALID") {
          const key = mapUserErrorCodeToKey(error.code);
          if (key) {
            setError("email", { message: t(`errorCodes.${key}`) });
            return;
          }
        }
        for (const fe of error.fieldErrors) {
          const key = mapUserErrorCodeToKey(fe.code);
          const msg = key ? t(`errorCodes.${key}`) : t("errorCodes.VALIDATION_FAILED");
          if (fe.field === "fullName" || fe.field === "email" || fe.field === "phone") {
            setError(fe.field, { message: msg });
            return;
          }
        }
      }
      setSubmitError(
        resolveUsersErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  function handleFormSubmit(values: UpdateUserFormValues) {
    setSubmitError(null);
    setStaleConflict(false);
    mutation.mutate({
      fullName: values.fullName.trim(),
      email: values.email.trim(),
      phone: values.phone.trim(),
      version: user.version,
    });
  }

  const displayStatus = getUserDisplayStatus(user);

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <Card className="flex flex-col gap-4">
        <SectionTitle title={t("detail.infoMetaTitle")} />
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <dt className="text-caption text-text-muted">{t("detail.codeLabel")}</dt>
            <dd className="text-body font-semibold text-text tabular-nums">{user.code}</dd>
          </div>
          <div>
            <dt className="text-caption text-text-muted">{t("detail.usernameLabel")}</dt>
            <dd className="text-body font-semibold text-text">{user.username}</dd>
          </div>
          <div>
            <dt className="text-caption text-text-muted">{t("detail.sourceLabel")}</dt>
            <dd className="text-body text-text">{t(`sources.${user.source}`)}</dd>
          </div>
          <div>
            <dt className="text-caption text-text-muted">{t("detail.statusLabel")}</dt>
            <dd className="mt-0.5">
              <StatusChip tone={displayStatus.tone}>
                {t(`statuses.${displayStatus.status}`)}
              </StatusChip>
            </dd>
          </div>
          <div>
            <dt className="text-caption text-text-muted">{t("detail.lastLoginLabel")}</dt>
            <dd className="text-body text-text tabular-nums">
              {user.lastLoginAt ? formatUserDateTime(user.lastLoginAt) : t("detail.neverLoggedIn")}
            </dd>
          </div>
          <div>
            <dt className="text-caption text-text-muted">{t("detail.mustChangePasswordLabel")}</dt>
            <dd className="text-body text-text">
              {user.mustChangePassword ? t("detail.yes") : t("detail.no")}
            </dd>
          </div>
          <div>
            <dt className="text-caption text-text-muted">{t("detail.createdAtLabel")}</dt>
            <dd className="text-body text-text tabular-nums">
              {formatUserDateTime(user.createdAt)}
            </dd>
          </div>
          <div>
            <dt className="text-caption text-text-muted">{t("detail.updatedAtLabel")}</dt>
            <dd className="text-body text-text tabular-nums">
              {formatUserDateTime(user.updatedAt)}
            </dd>
          </div>
        </dl>
      </Card>

      <Card className="flex flex-col gap-4">
        <SectionTitle title={t("detail.infoEditTitle")} />

        {submitError && (
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            <span>{submitError}</span>
            {staleConflict && (
              <Button type="button" variant="secondary" size="sm" onClick={onReload}>
                {t("detail.reload")}
              </Button>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit(handleFormSubmit)} className="flex flex-col gap-4" noValidate>
          <FormField label={t("detail.fullNameLabel")} required error={errors.fullName?.message}>
            {(controlProps) => (
              <Input
                {...controlProps}
                {...register("fullName")}
                disabled={!canEdit || mutation.isPending}
              />
            )}
          </FormField>

          <FormField label={t("detail.emailLabel")} error={errors.email?.message}>
            {(controlProps) => (
              <Input
                {...controlProps}
                type="email"
                {...register("email")}
                disabled={!canEdit || mutation.isPending}
              />
            )}
          </FormField>

          <FormField label={t("detail.phoneLabel")} error={errors.phone?.message}>
            {(controlProps) => (
              <Input
                {...controlProps}
                type="tel"
                {...register("phone")}
                disabled={!canEdit || mutation.isPending}
              />
            )}
          </FormField>

          {canEdit && (
            <div className="flex justify-end">
              <Button type="submit" loading={mutation.isPending} disabled={!isDirty}>
                {t("detail.save")}
              </Button>
            </div>
          )}
        </form>
      </Card>
    </div>
  );
}
