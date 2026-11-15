"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
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

  const [serverError, setServerError] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);

  const username = sessionQuery.data?.username ?? "";

  const schema = useMemo(
    () =>
      z
        .object({
          oldPassword: z.string().min(1, t("oldPasswordRequired")),
          newPassword: z
            .string()
            .refine((value) => isPasswordStrong(value), t("newPasswordWeak"))
            .refine(
              (value) => !username || value.toLowerCase() !== username.trim().toLowerCase(),
              t("newPasswordSameAsUsername"),
            ),
          confirmPassword: z.string().min(1, t("confirmPasswordRequired")),
        })
        .refine((values) => values.newPassword === values.confirmPassword, {
          message: t("confirmPasswordMismatch"),
          path: ["confirmPassword"],
        }),
    [t, username],
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

  function applyPasswordFieldError(error: ApiStatusError): boolean {
    if (error.code === "WRONG_OLD_PASSWORD") {
      setError("oldPassword", { message: t("wrongOldPassword") });
      return true;
    }
    if (error.code === "PASSWORD_WEAK") {
      const fieldCode = error.fieldErrors[0]?.code;
      if (fieldCode === "PASSWORD_SAME_AS_USERNAME") {
        setError("newPassword", { message: t("newPasswordSameAsUsername") });
      } else {
        setError("newPassword", { message: t("newPasswordWeak") });
      }
      return true;
    }
    return false;
  }

  async function onSubmit(values: ChangePasswordValues) {
    setServerError(null);
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
          setServerError(t("genericError"));
        }
        return;
      }
      if (error instanceof ApiStatusError && applyPasswordFieldError(error)) {
        return;
      }
      setServerError(t("genericError"));
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
    return (
      <main className="flex min-h-screen items-center justify-center bg-page p-6">
        <div
          role="status"
          aria-label={common("loading")}
          className="flex w-full max-w-[480px] flex-col gap-4 rounded-2xl border border-border bg-surface p-10 shadow-xl"
        >
          <Skeleton className="h-10 w-40" />
          <Skeleton className="h-7 w-56" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      </main>
    );
  }

  if (sessionQuery.isError || !sessionQuery.data) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-page p-6">
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
    <main className="flex min-h-screen items-center justify-center bg-page p-6">
      <div className="flex w-full max-w-[480px] flex-col gap-5 rounded-2xl border border-border bg-surface p-10 shadow-xl">
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
            {serverError && (
              <div
                role="alert"
                className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
              >
                <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                <span>{serverError}</span>
              </div>
            )}

            <FormField label={t("oldPasswordLabel")} error={errors.oldPassword?.message} required>
              {(field) => (
                <Controller
                  name="oldPassword"
                  control={control}
                  render={({ field: controllerField }) => (
                    <PasswordField
                      {...field}
                      name={controllerField.name}
                      value={controllerField.value}
                      onChange={controllerField.onChange}
                      onBlur={controllerField.onBlur}
                      size="lg"
                      autoComplete="current-password"
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

            <FormField label={t("newPasswordLabel")} error={errors.newPassword?.message} required>
              {(field) => (
                <Controller
                  name="newPassword"
                  control={control}
                  render={({ field: controllerField }) => (
                    <PasswordField
                      {...field}
                      name={controllerField.name}
                      value={controllerField.value}
                      onChange={controllerField.onChange}
                      onBlur={controllerField.onBlur}
                      size="lg"
                      showRules
                      labels={ruleLabels}
                      autoComplete="new-password"
                      leadingIcon={<Lock />}
                    />
                  )}
                />
              )}
            </FormField>

            <FormField label={t("confirmPasswordLabel")} error={errors.confirmPassword?.message} required>
              {(field) => (
                <Controller
                  name="confirmPassword"
                  control={control}
                  render={({ field: controllerField }) => (
                    <PasswordField
                      {...field}
                      name={controllerField.name}
                      value={controllerField.value}
                      onChange={controllerField.onChange}
                      onBlur={controllerField.onBlur}
                      size="lg"
                      autoComplete="new-password"
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
            className="font-medium text-primary-text hover:underline disabled:opacity-50"
          >
            {t("otherAccount")}
          </button>
        </p>
      </div>
    </main>
  );
}
