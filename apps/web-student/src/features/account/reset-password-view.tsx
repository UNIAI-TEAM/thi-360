"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, cn, FormField, isPasswordStrong, LocaleSwitcher, PasswordField, ThemeToggle } from "@thi360/ui";
import { AlertCircle, Link2Off, Lock } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { PASSWORD_RESET_DONE_PARAM, PASSWORD_RESET_DONE_VALUE } from "@/features/account/account-links";
import { resetPasswordByToken } from "@/features/account/api";
import { AuthPageStage } from "@/features/auth/auth-backdrop";
import { ApiStatusError } from "@/lib/api/client";
import { touchFieldText } from "@/lib/styles";

const CARD_CLASS =
  "flex w-full max-w-[480px] flex-col gap-5 rounded-2xl border border-border bg-surface p-6 shadow-xl sm:p-10";

type ResetFieldErrorKey = "passwordRequired" | "passwordWeak" | "confirmRequired" | "confirmMismatch";

type ResetValues = { newPassword: string; confirmPassword: string };

export function ResetPasswordView({
  navigate = (url: string) => window.location.assign(url),
  resetApi = resetPasswordByToken,
}: {
  navigate?: (url: string) => void;
  resetApi?: (token: string, newPassword: string) => Promise<void>;
}) {
  const t = useTranslations("account.resetPassword");
  const common = useTranslations("common");
  const searchParams = useSearchParams();
  const token = searchParams.get("token")?.trim() ?? "";

  // `RESET_TOKEN_INVALID`: liên kết hết hạn, đã dùng hoặc tài khoản bị khóa. Không có `token` thì cũng như vậy.
  const [tokenInvalid, setTokenInvalid] = useState(token === "");
  const [hasServerError, setHasServerError] = useState(false);

  const schema = useMemo(
    () =>
      z
        .object({
          newPassword: z
            .string()
            .min(1, "passwordRequired" satisfies ResetFieldErrorKey)
            .refine((value) => isPasswordStrong(value), "passwordWeak" satisfies ResetFieldErrorKey),
          confirmPassword: z.string().min(1, "confirmRequired" satisfies ResetFieldErrorKey),
        })
        .refine((values) => values.newPassword === values.confirmPassword, {
          message: "confirmMismatch" satisfies ResetFieldErrorKey,
          path: ["confirmPassword"],
        }),
    [],
  );

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetValues>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  const fieldError = (message: string | undefined) =>
    message ? t(message as ResetFieldErrorKey) : undefined;

  async function onSubmit(values: ResetValues) {
    setHasServerError(false);
    try {
      await resetApi(token, values.newPassword);
      // Không tự đăng nhập (TM 5.4); về `/login` không kèm `callbackUrl` vì liên kết trong thư chỉ có `token` (E16).
      navigate(`/login?${PASSWORD_RESET_DONE_PARAM}=${PASSWORD_RESET_DONE_VALUE}`);
    } catch (error) {
      if (error instanceof ApiStatusError && error.code === "RESET_TOKEN_INVALID") {
        setTokenInvalid(true);
        return;
      }
      if (error instanceof ApiStatusError && error.code === "PASSWORD_WEAK") {
        setError("newPassword", { message: "passwordWeak" satisfies ResetFieldErrorKey }, { shouldFocus: true });
        return;
      }
      setHasServerError(true);
    }
  }

  const header = (
    <div className="flex w-full max-w-[480px] items-center gap-2">
      <Link href="/" className="flex items-center gap-2.5">
        <span
          aria-hidden
          className="flex size-10 items-center justify-center rounded-lg bg-primary-fill font-bold text-on-primary"
        >
          {common("logoMark")}
        </span>
        <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
      </Link>
      <div className="ml-auto flex items-center gap-2">
        <ThemeToggle />
        <LocaleSwitcher />
      </div>
    </div>
  );

  if (tokenInvalid) {
    return (
      <AuthPageStage>
        {header}
        <section aria-labelledby="reset-invalid-title" className={CARD_CLASS}>
          <span aria-hidden className="grid size-12 place-items-center rounded-lg bg-warning-bg text-warning">
            <Link2Off className="size-6" />
          </span>
          <h1 id="reset-invalid-title" className="text-page-title font-bold text-text">
            {t("invalidTitle")}
          </h1>
          <p className="text-body text-text-secondary">{t("invalidMessage")}</p>
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link href="/forgot-password">{t("requestNew")}</Link>
            </Button>
            <Button asChild variant="secondary" size="lg">
              <Link href="/login">{t("backToLogin")}</Link>
            </Button>
          </div>
        </section>
      </AuthPageStage>
    );
  }

  const passwordLabels = {
    showPassword: t("toggles.newShow"),
    hidePassword: t("toggles.newHide"),
    rulesLabel: t("rules.rulesLabel"),
    ruleLength: t("rules.ruleLength"),
    ruleUpper: t("rules.ruleUpper"),
    ruleLower: t("rules.ruleLower"),
    ruleDigit: t("rules.ruleDigit"),
    ruleSpecial: t("rules.ruleSpecial"),
  };

  return (
    <AuthPageStage>
      {header}
      <div className={CARD_CLASS}>
        <div className="flex flex-col gap-1.5">
          <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
          <p className="text-body text-text-muted">{t("description")}</p>
        </div>

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
                    maxLength={64}
                    showRules
                    rulesColumns={2}
                    labels={passwordLabels}
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
                    maxLength={64}
                    autoComplete="new-password"
                    enterKeyHint="done"
                    className={touchFieldText}
                    leadingIcon={<Lock />}
                    labels={{ showPassword: t("toggles.confirmShow"), hidePassword: t("toggles.confirmHide") }}
                  />
                )}
              />
            )}
          </FormField>

          <Button type="submit" size="xl" loading={isSubmitting} className="w-full">
            {t("submit")}
          </Button>
        </form>

        <p className={cn("text-field-label text-text-muted")}>{t("footerNote")}</p>
      </div>
    </AuthPageStage>
  );
}
