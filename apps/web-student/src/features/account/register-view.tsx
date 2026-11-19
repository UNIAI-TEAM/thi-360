"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Checkbox,
  cn,
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  FormField,
  Input,
  LocaleSwitcher,
  PasswordField,
  ThemeToggle,
} from "@thi360/ui";
import { isPasswordStrong } from "@thi360/ui";
import { validateCallbackUrl } from "@thi360/web-kit/redirect";
import { AlertCircle, Check, Info, Lock, Mail, User } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { type ReactNode, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { registerAccount, type RegisterRequest, type RegisterResponse } from "@/features/account/api";
import { withCallbackUrl } from "@/features/account/account-links";
import { ApiStatusError } from "@/lib/api/client";
import { coarseTextHitArea, touchFieldText } from "@/lib/styles";

/** Bản điều khoản tạm gửi cùng đăng ký (TM 5.1, 15); phải khớp phía máy chủ khi bản chính thức có. */
export const TERMS_VERSION = "2026-11";

const USERNAME_PATTERN = /^[a-z0-9][a-z0-9._@-]{3,29}$/i;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Mã lỗi máy chủ đang hiện ở khối trên biểu mẫu (cũng là khóa dịch `account.register.errors.<mã>`). */
type RegisterAlertCode = "RATE_LIMITED" | "generic";

/** Khóa dịch của lỗi gắn ô; lưu khóa chứ không lưu câu đã dịch để đổi ngôn ngữ là câu đổi theo. */
type RegisterFieldErrorKey =
  | "usernameRequired"
  | "usernamePattern"
  | "usernameTaken"
  | "fullNameRequired"
  | "emailRequired"
  | "emailInvalid"
  | "emailTaken"
  | "passwordRequired"
  | "passwordWeak"
  | "passwordSameAsUsername"
  | "termsRequired";

type RegisterValues = {
  username: string;
  fullName: string;
  email: string;
  password: string;
  acceptTerms: boolean;
};

export function RegisterView({
  navigate = (url: string) => window.location.assign(url),
  registerApi = registerAccount,
  headerActions,
}: {
  navigate?: (url: string) => void;
  registerApi?: (body: RegisterRequest) => Promise<RegisterResponse>;
  headerActions?: ReactNode;
}) {
  const t = useTranslations("account.register");
  const common = useTranslations("common");
  const searchParams = useSearchParams();
  const rawCallbackUrl = searchParams.get("callbackUrl");
  const callbackUrl = validateCallbackUrl(rawCallbackUrl) ?? undefined;

  const [serverAlert, setServerAlert] = useState<RegisterAlertCode | null>(null);
  const fieldError = (message: string | undefined) =>
    message ? t(message as RegisterFieldErrorKey) : undefined;

  const schema = useMemo(
    () =>
      z.object({
        username: z
          .string()
          .trim()
          .min(1, "usernameRequired" satisfies RegisterFieldErrorKey)
          .refine(
            (value) => USERNAME_PATTERN.test(value),
            "usernamePattern" satisfies RegisterFieldErrorKey,
          ),
        fullName: z.string().trim().min(1, "fullNameRequired" satisfies RegisterFieldErrorKey),
        email: z
          .string()
          .trim()
          .min(1, "emailRequired" satisfies RegisterFieldErrorKey)
          .refine((value) => EMAIL_PATTERN.test(value), "emailInvalid" satisfies RegisterFieldErrorKey),
        password: z
          .string()
          .min(1, "passwordRequired" satisfies RegisterFieldErrorKey)
          .refine((value) => isPasswordStrong(value), "passwordWeak" satisfies RegisterFieldErrorKey),
        acceptTerms: z.boolean().refine((value) => value, "termsRequired" satisfies RegisterFieldErrorKey),
      }),
    [],
  );

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: "", fullName: "", email: "", password: "", acceptTerms: false },
  });

  function applyServerFieldError(error: ApiStatusError): boolean {
    if (error.status === 409 && error.code === "USERNAME_TAKEN") {
      setError("username", { message: "usernameTaken" satisfies RegisterFieldErrorKey }, { shouldFocus: true });
      return true;
    }
    if (error.status === 409 && error.code === "EMAIL_TAKEN") {
      setError("email", { message: "emailTaken" satisfies RegisterFieldErrorKey }, { shouldFocus: true });
      return true;
    }
    for (const field of error.fieldErrors) {
      if (field.field === "acceptTerms" && field.code === "TERMS_REQUIRED") {
        setError("acceptTerms", { message: "termsRequired" satisfies RegisterFieldErrorKey });
        return true;
      }
      if (field.field === "username" && field.code === "USERNAME_TAKEN") {
        setError("username", { message: "usernameTaken" satisfies RegisterFieldErrorKey }, { shouldFocus: true });
        return true;
      }
      if (field.field === "email") {
        const message =
          field.code === "EMAIL_TAKEN"
            ? "emailTaken"
            : field.code === "EMAIL_INVALID"
              ? "emailInvalid"
              : null;
        if (message) {
          setError("email", { message }, { shouldFocus: true });
          return true;
        }
      }
      if (field.field === "password" && (field.code === "PASSWORD_WEAK" || field.code === "PASSWORD_SAME_AS_USERNAME")) {
        setError(
          "password",
          {
            message:
              field.code === "PASSWORD_SAME_AS_USERNAME"
                ? ("passwordSameAsUsername" satisfies RegisterFieldErrorKey)
                : ("passwordWeak" satisfies RegisterFieldErrorKey),
          },
          { shouldFocus: true },
        );
        return true;
      }
    }
    return false;
  }

  async function onSubmit(values: RegisterValues) {
    setServerAlert(null);
    try {
      await registerApi({
        username: values.username.trim(),
        fullName: values.fullName.trim(),
        email: values.email.trim(),
        password: values.password,
        acceptTerms: true,
        termsVersion: TERMS_VERSION,
        ...(callbackUrl ? { callbackUrl } : {}),
      });
      // `callbackUrl` đã qua `validateCallbackUrl` ở máy khách (đường dẫn người dùng mở trang với); không dùng
      // `redirectTo` tuyệt đối của máy chủ vì nguồn gốc của nó có thể khác `NEXT_PUBLIC_THI360_WEB_URL` của bản dựng.
      navigate(withCallbackUrl("/verify-email", callbackUrl));
    } catch (error) {
      if (error instanceof ApiStatusError) {
        if (error.status === 429) {
          setServerAlert("RATE_LIMITED");
          return;
        }
        if (applyServerFieldError(error)) return;
      }
      setServerAlert("generic");
    }
  }

  const passwordLabels = {
    showPassword: t("password.show"),
    hidePassword: t("password.hide"),
    rulesLabel: t("password.rulesLabel"),
    ruleLength: t("password.ruleLength"),
    ruleUpper: t("password.ruleUpper"),
    ruleLower: t("password.ruleLower"),
    ruleDigit: t("password.ruleDigit"),
    ruleSpecial: t("password.ruleSpecial"),
  };

  const highlights = [t("intro.feature1"), t("intro.feature2"), t("intro.feature3")];

  return (
    <main className="flex min-h-screen flex-col items-center gap-4 bg-page p-4 sm:p-6 md:justify-center">
      <div className="flex w-full max-w-[920px] items-center gap-2">
        {/* Màn hẹp: logo ở hàng trên vì phần giới thiệu xếp dưới biểu mẫu. */}
        <Link href="/" className="flex items-center gap-2.5 md:hidden">
          <span
            aria-hidden
            className="flex size-10 items-center justify-center rounded-lg bg-primary-fill font-bold text-on-primary"
          >
            {common("logoMark")}
          </span>
          <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
        </Link>
        <div className="ml-auto flex items-center gap-2">
          {headerActions ?? (
            <>
              <ThemeToggle />
              <LocaleSwitcher />
            </>
          )}
        </div>
      </div>

      {/* Biểu mẫu trước trong DOM như trang đăng nhập; từ `md` phần giới thiệu nằm bên trái (DESIGN 5.12). */}
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

          {callbackUrl && (
            <div
              role="status"
              className="flex items-start gap-2.5 rounded-lg border border-info/30 bg-info-bg px-3.5 py-3 text-body text-info"
            >
              <Info aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{t("continueNote")}</span>
            </div>
          )}

          {serverAlert && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
            >
              <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{t(`errors.${serverAlert}`)}</span>
            </div>
          )}

          <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
            <FormField
              label={t("usernameLabel")}
              error={fieldError(errors.username?.message)}
              hint={t("usernameHint")}
              required
            >
              {(field) => (
                <Input
                  {...field}
                  {...register("username")}
                  size="lg"
                  type="text"
                  required
                  maxLength={30}
                  autoComplete="username"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  enterKeyHint="next"
                  className={touchFieldText}
                  leadingIcon={<User />}
                  placeholder={t("usernamePlaceholder")}
                />
              )}
            </FormField>

            <FormField label={t("fullNameLabel")} error={fieldError(errors.fullName?.message)} required>
              {(field) => (
                <Input
                  {...field}
                  {...register("fullName")}
                  size="lg"
                  type="text"
                  required
                  maxLength={200}
                  autoComplete="name"
                  enterKeyHint="next"
                  className={touchFieldText}
                  placeholder={t("fullNamePlaceholder")}
                />
              )}
            </FormField>

            <FormField
              label={t("emailLabel")}
              error={fieldError(errors.email?.message)}
              hint={t("emailHint")}
              required
            >
              {(field) => (
                <Input
                  {...field}
                  {...register("email")}
                  size="lg"
                  type="email"
                  required
                  autoComplete="email"
                  enterKeyHint="next"
                  className={touchFieldText}
                  leadingIcon={<Mail />}
                  placeholder={t("emailPlaceholder")}
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

            <div className="flex flex-col gap-1">
              <div className="flex items-start gap-2.5">
                <Controller
                  name="acceptTerms"
                  control={control}
                  render={({ field: controllerField }) => (
                    <Checkbox
                      id="register-terms"
                      checked={controllerField.value}
                      onCheckedChange={(checked) => controllerField.onChange(checked === true)}
                      onBlur={controllerField.onBlur}
                      ref={controllerField.ref}
                      aria-invalid={errors.acceptTerms ? true : undefined}
                      aria-describedby={errors.acceptTerms ? "register-terms-error" : undefined}
                      className="mt-0.5"
                    />
                  )}
                />
                <label htmlFor="register-terms" className="text-body text-text-secondary">
                  {t("termsLabel")}
                </label>
              </div>
              {errors.acceptTerms?.message && (
                <p id="register-terms-error" role="alert" className="text-caption text-danger">
                  {fieldError(errors.acceptTerms.message)}
                </p>
              )}
              <Dialog>
                <DialogTrigger
                  className={cn(
                    "self-start text-label font-medium text-primary-text underline underline-offset-2",
                    coarseTextHitArea,
                  )}
                >
                  {t("termsView")}
                </DialogTrigger>
                <DialogContent closeLabel={t("termsClose")}>
                  <DialogHeader>
                    <DialogTitle>{t("termsTitle")}</DialogTitle>
                    <DialogDescription>{t("termsDraft")}</DialogDescription>
                  </DialogHeader>
                  <div className="rounded-lg border border-border p-4 text-body text-text-secondary">
                    {t("termsBody")}
                  </div>
                  <DialogFooter>
                    <DialogClose asChild>
                      <Button type="button" variant="secondary">
                        {t("termsClose")}
                      </Button>
                    </DialogClose>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>

            <Button type="submit" size="xl" loading={isSubmitting} className="w-full">
              {t("submit")}
            </Button>
          </form>

          <p className="text-field-label text-text-muted">
            {t("haveAccount")}{" "}
            <Link
              href={withCallbackUrl("/login", callbackUrl)}
              className={cn("font-medium text-primary-text hover:underline", coarseTextHitArea)}
            >
              {t("loginLink")}
            </Link>
          </p>
        </section>

        <section className="flex min-w-0 flex-1 flex-col gap-6 bg-primary-50 p-6 sm:p-10 md:p-12">
          <Link href="/" className="hidden items-center gap-3 md:flex">
            <span
              aria-hidden
              className="flex size-11 items-center justify-center rounded-lg bg-primary-fill text-title font-bold text-on-primary"
            >
              {common("logoMark")}
            </span>
            <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
          </Link>

          <h2 className="text-h2 font-bold text-primary-900">{t("intro.headline")}</h2>
          <p className="text-body text-text-secondary">{t("intro.subtitle")}</p>

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
