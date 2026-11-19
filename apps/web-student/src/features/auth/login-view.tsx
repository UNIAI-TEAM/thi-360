"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, cn, FormField, Input, LocaleSwitcher, PasswordField, ThemeToggle } from "@thi360/ui";
import { AlertCircle, Check, CheckCircle2, Lock, User } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { type ReactNode, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import {
  PASSWORD_RESET_DONE_PARAM,
  PASSWORD_RESET_DONE_VALUE,
  withCallbackUrl,
} from "@/features/account/account-links";
import { loginWithPassword } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { coarseTextHitArea, touchFieldText } from "@/lib/styles";

type LoginFormValues = {
  identifier: string;
  password: string;
};

const KNOWN_ERROR_CODES = ["INVALID_CREDENTIALS", "LOGIN_THROTTLED", "ACCOUNT_INACTIVE", "RATE_LIMITED"] as const;
/** Mã lỗi máy chủ đang hiện, cũng là khóa dịch `login.errors.<mã>`. Lưu mã chứ không lưu câu đã dịch, để đổi ngôn ngữ là câu đổi theo. */
type LoginErrorCode = (typeof KNOWN_ERROR_CODES)[number] | "generic";

function toLoginErrorCode(error: unknown): LoginErrorCode {
  if (error instanceof ApiStatusError) {
    const known = KNOWN_ERROR_CODES.find((code) => code === error.code);
    if (known) return known;
  }
  return "generic";
}

/** Lỗi của từng ô cũng lưu bằng khóa dịch (`login.<khóa>`) và dịch lúc hiển thị. */
type LoginFieldErrorKey = "identifierRequired" | "passwordRequired";

const loginSchema = z.object({
  identifier: z.string().trim().min(1, "identifierRequired" satisfies LoginFieldErrorKey),
  password: z.string().min(1, "passwordRequired" satisfies LoginFieldErrorKey),
});

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
  // Vừa đặt lại mật khẩu xong (về từ `/reset-password`): báo thành công, không tự đăng nhập (TM 5.4).
  const passwordResetDone = searchParams.get(PASSWORD_RESET_DONE_PARAM) === PASSWORD_RESET_DONE_VALUE;

  const [serverErrorCode, setServerErrorCode] = useState<LoginErrorCode | null>(() =>
    reason === "inactive" ? "ACCOUNT_INACTIVE" : null,
  );
  const fieldError = (message: string | undefined) =>
    message ? t(message as LoginFieldErrorKey) : undefined;

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: "", password: "" },
  });

  async function onSubmit(values: LoginFormValues) {
    setServerErrorCode(null);
    try {
      const result = await loginWithPassword({
        identifier: values.identifier.trim(),
        password: values.password,
        ...(callbackUrl ? { callbackUrl } : {}),
      });
      navigate(result.redirectTo);
    } catch (error) {
      setServerErrorCode(toLoginErrorCode(error));
    }
  }

  const highlights = [t("feature1"), t("feature2"), t("feature3")];

  return (
    <main className="flex min-h-screen flex-col items-center gap-4 bg-page p-4 sm:p-6 md:justify-center">
      <div className="flex w-full max-w-[920px] items-center gap-2">
        {/* Màn hẹp: khối giới thiệu nằm dưới biểu mẫu, nên logo hiện ở hàng trên cùng. */}
        <div className="flex items-center gap-2.5 md:hidden">
          <span
            aria-hidden
            className="flex size-10 items-center justify-center rounded-lg bg-primary-fill font-bold text-on-primary"
          >
            {common("logoMark")}
          </span>
          <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
        </div>
        <div className="ml-auto flex items-center gap-2">
          {headerActions ?? (
            <>
              <ThemeToggle />
              <LocaleSwitcher />
            </>
          )}
        </div>
      </div>
      {/*
        Biểu mẫu đứng trước trong DOM: màn hẹp xếp một cột thì ô nhập và nút Đăng nhập nằm ngay đầu trang, phần giới
        thiệu ở dưới. Từ `md` hai khối đứng cạnh nhau và đảo chiều để phần giới thiệu ở bên trái (DESIGN 5.12); khối
        giới thiệu không có điều khiển nào nên thứ tự Tab không đổi. `min-w-0` để không khối nào rộng hơn thẻ ở 320px.
      */}
      <div className="flex w-full max-w-[920px] flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-xl md:flex-row-reverse">
        <section className="flex min-w-0 flex-1 flex-col gap-5 p-6 sm:p-10 md:p-12">
          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
            <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
            <Link
              href="/"
              className={cn(
                "text-label font-medium text-primary-text transition-colors duration-150 hover:underline",
                coarseTextHitArea,
              )}
            >
              ← {t("backHome")}
            </Link>
          </div>

          {passwordResetDone && (
            <div
              role="status"
              className="flex items-start gap-2.5 rounded-lg border border-success/30 bg-success-bg px-3.5 py-3 text-body text-success"
            >
              <CheckCircle2 aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{t("resetDone")}</span>
            </div>
          )}
          {serverErrorCode && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
            >
              <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{t(`errors.${serverErrorCode}`)}</span>
            </div>
          )}

          <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
            <FormField label={t("identifierLabel")} error={fieldError(errors.identifier?.message)} required>
              {(field) => (
                <Input
                  {...field}
                  {...register("identifier")}
                  size="lg"
                  type="text"
                  required
                  autoComplete="username"
                  // Tên đăng nhập là mã định danh, không phải văn bản: tắt tự viết hoa, tự sửa và kiểm chính tả.
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  enterKeyHint="next"
                  className={touchFieldText}
                  leadingIcon={<User />}
                  placeholder={t("identifierPlaceholder")}
                />
              )}
            </FormField>

            <FormField label={t("passwordLabel")} error={fieldError(errors.password?.message)} required>
              {(field) => (
                <Controller
                  name="password"
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
                      enterKeyHint="done"
                      className={touchFieldText}
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

          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 text-field-label">
            <Link
              href={withCallbackUrl("/forgot-password", callbackUrl)}
              className={cn("font-medium text-primary-text hover:underline", coarseTextHitArea)}
            >
              {t("forgotLink")}
            </Link>
            <p className="text-text-muted">
              {t("noAccount")}{" "}
              <Link
                href={withCallbackUrl("/register", callbackUrl)}
                className={cn("font-medium text-primary-text hover:underline", coarseTextHitArea)}
              >
                {t("registerLink")}
              </Link>
            </p>
          </div>
        </section>

        <section className="flex min-w-0 flex-1 flex-col gap-6 bg-primary-50 p-6 sm:p-10 md:p-12">
          <div className="hidden items-center gap-3 md:flex">
            <span
              aria-hidden
              className="flex size-11 items-center justify-center rounded-lg bg-primary-fill text-title font-bold text-on-primary"
            >
              {common("logoMark")}
            </span>
            <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
          </div>

          <h2 className="text-h2 font-bold text-primary-900">{t("headline")}</h2>
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
      </div>
    </main>
  );
}
