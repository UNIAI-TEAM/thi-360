"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, FormField, Input, PasswordField } from "@thi360/ui";
import { AlertCircle, Check, Lock, User } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { type ReactNode, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { loginWithPassword } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";

type LoginFormValues = {
  identifier: string;
  password: string;
};

const KNOWN_ERROR_CODES = new Set([
  "INVALID_CREDENTIALS",
  "LOGIN_THROTTLED",
  "ACCOUNT_INACTIVE",
  "RATE_LIMITED",
]);

export function LoginView({
  navigate = (url: string) => window.location.assign(url),
  headerActions,
}: {
  navigate?: (url: string) => void;
  headerActions?: ReactNode;
}) {
  const t = useTranslations("login");
  const common = useTranslations("common");
  const searchParams = useSearchParams();
  const callbackUrl = searchParams.get("callbackUrl") ?? undefined;
  const reason = searchParams.get("reason");

  const [serverError, setServerError] = useState<string | null>(() =>
    reason === "inactive" ? t("errors.ACCOUNT_INACTIVE") : null,
  );

  const schema = useMemo(
    () =>
      z.object({
        identifier: z.string().trim().min(1, t("identifierRequired")),
        password: z.string().min(1, t("passwordRequired")),
      }),
    [t],
  );

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { identifier: "", password: "" },
  });

  async function onSubmit(values: LoginFormValues) {
    setServerError(null);
    try {
      const result = await loginWithPassword({
        identifier: values.identifier.trim(),
        password: values.password,
        ...(callbackUrl ? { callbackUrl } : {}),
      });
      navigate(result.redirectTo);
    } catch (error) {
      if (error instanceof ApiStatusError && KNOWN_ERROR_CODES.has(error.code)) {
        setServerError(
          t(`errors.${error.code as "INVALID_CREDENTIALS" | "LOGIN_THROTTLED" | "ACCOUNT_INACTIVE" | "RATE_LIMITED"}`),
        );
        return;
      }
      setServerError(t("errors.generic"));
    }
  }

  const highlights = [t("feature1"), t("feature2"), t("feature3")];

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-page p-6">
      <div className="flex w-full max-w-[920px] items-center justify-end gap-2 empty:hidden">
        {headerActions}
      </div>
      <div className="flex w-full max-w-[920px] flex-wrap overflow-hidden rounded-2xl border border-border bg-surface shadow-xl">
        <section className="flex min-w-[300px] flex-1 basis-[360px] flex-col gap-6 bg-primary-50 p-10 md:p-12">
          <div className="flex items-center gap-3">
            <span
              aria-hidden
              className="flex size-11 items-center justify-center rounded-lg bg-primary-fill text-title font-bold text-on-primary"
            >
              {common("logoMark")}
            </span>
            <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
          </div>

          <h1 className="text-h2 font-bold text-primary-900">{t("headline")}</h1>
          <p className="text-body text-text-secondary">{t("subtitle")}</p>

          <ul className="flex flex-col gap-3.5">
            {highlights.map((item) => (
              <li key={item} className="flex items-center gap-3 text-body text-text">
                <span
                  aria-hidden
                  className="flex size-8 shrink-0 items-center justify-center rounded-md bg-surface text-primary-text"
                >
                  <Check className="size-4" />
                </span>
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="flex min-w-[300px] flex-1 basis-[360px] flex-col gap-5 p-10 md:p-12">
          <div className="flex items-center justify-between gap-4">
            <h2 className="text-page-title font-bold text-text">{t("title")}</h2>
            <Link
              href="/"
              className="text-label font-medium text-primary-text transition-colors duration-150 hover:underline"
            >
              ← {t("backHome")}
            </Link>
          </div>

          {serverError && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
            >
              <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{serverError}</span>
            </div>
          )}

          <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
            <FormField label={t("identifierLabel")} error={errors.identifier?.message} required>
              {(field) => (
                <Input
                  {...field}
                  {...register("identifier")}
                  size="lg"
                  type="text"
                  autoComplete="username"
                  leadingIcon={<User />}
                  placeholder={t("identifierPlaceholder")}
                />
              )}
            </FormField>

            <FormField label={t("passwordLabel")} error={errors.password?.message} required>
              {(field) => (
                <Controller
                  name="password"
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
                      labels={{ showPassword: t("showPassword"), hidePassword: t("hidePassword") }}
                      placeholder={t("passwordPlaceholder")}
                    />
                  )}
                />
              )}
            </FormField>

            <Button type="submit" size="xl" loading={isSubmitting} className="w-full">
              {t("submit")}
            </Button>
          </form>

          <p className="text-field-label text-text-muted">{t("forgotNote")}</p>
        </section>
      </div>
    </main>
  );
}
