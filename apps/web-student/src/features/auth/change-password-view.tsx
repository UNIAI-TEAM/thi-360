"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  cn,
  EmptyState,
  FormField,
  isPasswordStrong,
  notify,
  PasswordField,
  Skeleton,
} from "@thi360/ui";
import { buildLoginUrl, validateCallbackUrl } from "@thi360/web-kit/redirect";
import { AlertCircle, Lock, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import {
  changeAccountPassword,
  clearSessionBestEffort,
  loadSession,
  logoutSession,
  refreshSession,
  SESSION_QUERY_KEY,
  type SessionLoader,
} from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { adminUrl, webUrl } from "@/lib/env";
import { coarseTextHitArea, touchFieldText } from "@/lib/styles";

/**
 * Màn hẹp: thẻ bám mép trên (cao hơn màn hình nên căn giữa chỉ làm thẻ nhảy khi nội dung tải xong); từ `md` căn giữa.
 * Khung chờ và biểu mẫu dùng chung hai lớp này để vị trí và đệm của thẻ không đổi giữa hai trạng thái.
 */
const PAGE_CLASS = "flex min-h-screen items-start justify-center bg-page p-4 sm:p-6 md:items-center";
const CARD_CLASS =
  "flex w-full max-w-[480px] flex-col gap-5 rounded-2xl border border-border bg-surface p-6 shadow-xl sm:p-10";

/** Lỗi của từng ô lưu bằng khóa dịch (`changePassword.<khóa>`), dịch lúc hiển thị: đổi ngôn ngữ là câu lỗi đổi theo. */
type ChangePasswordErrorKey =
  | "oldPasswordRequired"
  | "wrongOldPassword"
  | "newPasswordWeak"
  | "newPasswordSameAsUsername"
  | "confirmPasswordRequired"
  | "confirmPasswordMismatch";

type ChangePasswordValues = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

export function ChangePasswordView({
  loadCurrentSession = loadSession,
  navigate = (url: string) => window.location.assign(url),
}: {
  loadCurrentSession?: SessionLoader;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("changePassword");
  const common = useTranslations("common");
  const errT = useTranslations("errors");
  const searchParams = useSearchParams();
  const rawCallbackUrl = searchParams.get("callbackUrl");

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  // Chỉ có một lỗi chung (`genericError`): lưu cờ, câu chữ dịch lúc hiển thị.
  const [hasServerError, setHasServerError] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const fieldError = (message: string | undefined) =>
    message ? t(message as ChangePasswordErrorKey) : undefined;

  const username = sessionQuery.data?.username ?? "";

  const schema = useMemo(
    () =>
      z
        .object({
          oldPassword: z.string().min(1, "oldPasswordRequired" satisfies ChangePasswordErrorKey),
          newPassword: z
            .string()
            .refine((value) => isPasswordStrong(value), "newPasswordWeak" satisfies ChangePasswordErrorKey)
            .refine(
              (value) => !username || value.toLowerCase() !== username.trim().toLowerCase(),
              "newPasswordSameAsUsername" satisfies ChangePasswordErrorKey,
            ),
          confirmPassword: z.string().min(1, "confirmPasswordRequired" satisfies ChangePasswordErrorKey),
        })
        .refine((values) => values.newPassword === values.confirmPassword, {
          message: "confirmPasswordMismatch" satisfies ChangePasswordErrorKey,
          path: ["confirmPassword"],
        }),
    [username],
  );

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(schema),
    defaultValues: { oldPassword: "", newPassword: "", confirmPassword: "" },
  });

  function isAuthRouteContinuation(url: string, webBase: string): boolean {
    try {
      const parsed = new URL(url, webBase);
      const webOrigin = new URL(webBase).origin.toLowerCase();
      if (parsed.origin.toLowerCase() !== webOrigin) return false;
      const normalizedPath = parsed.pathname.replace(/\/+$/, "");
      return normalizedPath === "/change-password" || normalizedPath === "/login";
    } catch {
      return true;
    }
  }

  function resolveSuccessTarget(): string {
    const webBase = webUrl();
    const safeCallback = validateCallbackUrl(rawCallbackUrl, [webBase, adminUrl()]);
    if (safeCallback && !isAuthRouteContinuation(safeCallback, webBase)) {
      return safeCallback;
    }
    return sessionQuery.data?.homeUrl ?? "/home";
  }

  // Lỗi máy chủ gắn vào ô nào thì đưa focus về ô đó, để người dùng bàn phím và trình đọc màn hình tới ngay chỗ cần sửa.
  function applyPasswordFieldError(error: ApiStatusError): boolean {
    const setFieldError = (field: "oldPassword" | "newPassword", message: ChangePasswordErrorKey) =>
      setError(field, { message }, { shouldFocus: true });
    if (error.code === "WRONG_OLD_PASSWORD") {
      setFieldError("oldPassword", "wrongOldPassword");
      return true;
    }
    if (error.code === "PASSWORD_WEAK") {
      const fieldCode = error.fieldErrors[0]?.code;
      setFieldError(
        "newPassword",
        fieldCode === "PASSWORD_SAME_AS_USERNAME" ? "newPasswordSameAsUsername" : "newPasswordWeak",
      );
      return true;
    }
    return false;
  }

  async function onSubmit(values: ChangePasswordValues) {
    setHasServerError(false);
    const payload = {
      oldPassword: values.oldPassword,
      newPassword: values.newPassword,
    };
    try {
      await changeAccountPassword(payload);
      navigate(resolveSuccessTarget());
    } catch (error) {
      if (error instanceof ApiStatusError && error.status === 401) {
        const currentHref =
          typeof window !== "undefined" ? window.location.href : `${webUrl()}/change-password`;
        try {
          await refreshSession();
        } catch (refreshError) {
          if (!(refreshError instanceof ApiStatusError && refreshError.status === 401)) {
            await clearSessionBestEffort();
          }
          navigate(buildLoginUrl(webUrl(), currentHref));
          return;
        }
        try {
          await changeAccountPassword(payload);
          navigate(resolveSuccessTarget());
        } catch (retryError) {
          if (retryError instanceof ApiStatusError) {
            if (retryError.status === 401) {
              await clearSessionBestEffort();
              navigate(buildLoginUrl(webUrl(), currentHref));
              return;
            }
            if (applyPasswordFieldError(retryError)) return;
          }
          setHasServerError(true);
        }
        return;
      }
      if (error instanceof ApiStatusError && applyPasswordFieldError(error)) {
        return;
      }
      setHasServerError(true);
    }
  }

  async function switchAccount() {
    setLeaving(true);
    try {
      await logoutSession();
      navigate("/login");
    } catch {
      notify.error(errT("logoutFailed"));
    } finally {
      setLeaving(false);
    }
  }

  if (sessionQuery.isLoading) {
    // Khung chờ xếp đúng như biểu mẫu thật (logo, tiêu đề, ba ô, danh sách điều kiện, nút, ghi chú) để thẻ gần như
    // không đổi chiều cao khi phiên tải xong.
    const fieldSkeleton = (
      <div className="flex flex-col gap-1.5">
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-12 w-full" />
      </div>
    );
    return (
      <main className={PAGE_CLASS}>
        <div role="status" aria-label={common("loading")} className={CARD_CLASS}>
          <div className="flex items-center gap-3">
            <Skeleton className="size-10 rounded-lg" />
            <Skeleton className="h-7 w-20" />
          </div>
          <div className="flex flex-col gap-1.5">
            <Skeleton className="h-7.5 w-48" />
            <Skeleton className="h-10.5 w-full sm:h-5.25" />
          </div>
          <div className="flex flex-col gap-5">
            {fieldSkeleton}
            <div className="flex flex-col gap-2">
              {fieldSkeleton}
              <Skeleton className="h-27.75 w-56 max-w-full" />
            </div>
            {fieldSkeleton}
            <Skeleton className="h-13 w-full rounded-lg" />
          </div>
          <Skeleton className="h-15 w-full sm:h-10" />
        </div>
      </main>
    );
  }

  if (sessionQuery.isError || !sessionQuery.data) {
    return (
      <main className={PAGE_CLASS}>
        <div className="w-full max-w-[480px] rounded-2xl border border-border bg-surface p-8 shadow-xl">
          <EmptyState
            icon={<TriangleAlert />}
            title={t("errorLoadTitle")}
            description={t("errorLoad")}
            action={<Button onClick={() => void sessionQuery.refetch()}>{common("retry")}</Button>}
          />
        </div>
      </main>
    );
  }

  const session = sessionQuery.data;
  const ruleLabels = {
    showPassword: t("rules.showPassword"),
    hidePassword: t("rules.hidePassword"),
    generate: t("rules.generate"),
    rulesLabel: t("rules.rulesLabel"),
    ruleLength: t("rules.ruleLength"),
    ruleUpper: t("rules.ruleUpper"),
    ruleLower: t("rules.ruleLower"),
    ruleDigit: t("rules.ruleDigit"),
    ruleSpecial: t("rules.ruleSpecial"),
  };

  return (
    <main className={PAGE_CLASS}>
      <div className={CARD_CLASS}>
        <div className="flex items-center gap-3">
          <span
            aria-hidden
            className="flex size-10 items-center justify-center rounded-lg bg-primary-fill font-bold text-on-primary"
          >
            {common("logoMark")}
          </span>
          <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
          <p className="text-body text-text-muted">
            {t.rich("description", {
              username: session.username,
              strong: (chunks) => <strong className="font-semibold text-text">{chunks}</strong>,
            })}
          </p>
        </div>

        {session.realm === "MASTER" ? (
          <div role="status" className="rounded-lg border border-border bg-subtle p-4 text-body text-text-secondary">
            {t("masterRealmNotice")}
          </div>
        ) : (
          <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
            {hasServerError && (
              <div
                role="alert"
                className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
              >
                <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                <span>{t("genericError")}</span>
              </div>
            )}

            <FormField label={t("oldPasswordLabel")} error={fieldError(errors.oldPassword?.message)} required>
              {(field) => (
                <Controller
                  name="oldPassword"
                  control={control}
                  render={({ field: controllerField }) => (
                    <PasswordField
                      {...field}
                      ref={controllerField.ref}
                      name={controllerField.name}
                      value={controllerField.value}
                      onChange={controllerField.onChange}
                      onBlur={controllerField.onBlur}
                      size="lg"
                      required
                      autoComplete="current-password"
                      enterKeyHint="next"
                      className={touchFieldText}
                      leadingIcon={<Lock />}
                      labels={{
                        showPassword: ruleLabels.showPassword,
                        hidePassword: ruleLabels.hidePassword,
                      }}
                    />
                  )}
                />
              )}
            </FormField>

            <FormField label={t("newPasswordLabel")} error={fieldError(errors.newPassword?.message)} required>
              {(field) => (
                <Controller
                  name="newPassword"
                  control={control}
                  render={({ field: controllerField }) => (
                    <PasswordField
                      {...field}
                      ref={controllerField.ref}
                      name={controllerField.name}
                      value={controllerField.value}
                      onChange={controllerField.onChange}
                      onBlur={controllerField.onBlur}
                      size="lg"
                      required
                      showRules
                      labels={ruleLabels}
                      autoComplete="new-password"
                      enterKeyHint="next"
                      className={touchFieldText}
                      leadingIcon={<Lock />}
                    />
                  )}
                />
              )}
            </FormField>

            <FormField label={t("confirmPasswordLabel")} error={fieldError(errors.confirmPassword?.message)} required>
              {(field) => (
                <Controller
                  name="confirmPassword"
                  control={control}
                  render={({ field: controllerField }) => (
                    <PasswordField
                      {...field}
                      ref={controllerField.ref}
                      name={controllerField.name}
                      value={controllerField.value}
                      onChange={controllerField.onChange}
                      onBlur={controllerField.onBlur}
                      size="lg"
                      required
                      autoComplete="new-password"
                      enterKeyHint="done"
                      className={touchFieldText}
                      leadingIcon={<Lock />}
                      labels={{
                        showPassword: ruleLabels.showPassword,
                        hidePassword: ruleLabels.hidePassword,
                      }}
                    />
                  )}
                />
              )}
            </FormField>

            <Button type="submit" size="xl" loading={isSubmitting} className="w-full">
              {t("submit")}
            </Button>
          </form>
        )}

        <p className="text-field-label text-text-muted">
          {t("footerNote")}{" "}
          <button
            type="button"
            disabled={leaving}
            onClick={() => void switchAccount()}
            className={cn("font-medium text-primary-text hover:underline disabled:opacity-50", coarseTextHitArea)}
          >
            {t("otherAccount")}
          </button>
        </p>
      </div>
    </main>
  );
}
