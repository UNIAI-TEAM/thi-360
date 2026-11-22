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
  LiveStatus,
  LocaleSwitcher,
  PasswordField,
  ThemeToggle,
} from "@thi360/ui";
import { isPasswordStrong } from "@thi360/ui";
import { validateCallbackUrl } from "@thi360/web-kit/redirect";
import { AlertCircle, Check, Clock, Info, Lock, Mail, User } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { type ReactNode, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import {
  EmailCodeStatusError,
  type RegisterRequest,
  type RegisterResponse,
  type RegisterStartResponse,
  resendRegistrationCode,
  startRegistration,
  verifyRegistration,
} from "@/features/account/api";
import { withCallbackUrl } from "@/features/account/account-links";
import { cooldownAnnouncement, formatClock, LONG_COOLDOWN_SECONDS, useCooldown } from "@/features/account/cooldown";
import { ApiStatusError } from "@/lib/api/client";
import { coarseTextHitArea, touchFieldText } from "@/lib/styles";

/** Ban dieu khoan tam gui cung dang ky (TM 5.1, 15); phai khop phia may chu khi ban chinh thuc co. */
export const TERMS_VERSION = "2026-11";

const USERNAME_PATTERN = /^[a-z0-9][a-z0-9._@-]{3,29}$/i;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const DEFAULT_RESEND_SECONDS = 60;

type RegisterAlertCode = "RATE_LIMITED" | "generic";
type OtpAlertCode = "codeExpired" | "rateLimited" | "resendFailed" | "generic";
type OtpFieldErrorKey = "codeRequired" | "codeInvalid";

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

type OtpValues = {
  code: string;
};

export function RegisterView({
  navigate = (url: string) => window.location.assign(url),
  startRegisterApi = startRegistration,
  resendRegisterApi = resendRegistrationCode,
  verifyRegisterApi = verifyRegistration,
  now = () => Date.now(),
  headerActions,
}: {
  navigate?: (url: string) => void;
  startRegisterApi?: (body: RegisterRequest) => Promise<RegisterStartResponse>;
  resendRegisterApi?: (registrationId: string) => Promise<RegisterStartResponse>;
  verifyRegisterApi?: (registrationId: string, code: string) => Promise<RegisterResponse>;
  now?: () => number;
  headerActions?: ReactNode;
}) {
  const t = useTranslations("account.register");
  const common = useTranslations("common");
  const format = useFormatter();
  const searchParams = useSearchParams();
  const rawCallbackUrl = searchParams.get("callbackUrl");
  const callbackUrl = validateCallbackUrl(rawCallbackUrl) ?? undefined;
  const continueUrl = callbackUrl ?? "/home";

  const [step, setStep] = useState<"details" | "otp">("details");
  const [pendingSession, setPendingSession] = useState<{ registrationId: string; email: string } | null>(null);
  const [serverAlert, setServerAlert] = useState<RegisterAlertCode | null>(null);
  const [otpAlert, setOtpAlert] = useState<OtpAlertCode | null>(null);
  const [dailyLimitUntil, setDailyLimitUntil] = useState<number | null>(null);
  const [codeSentNote, setCodeSentNote] = useState(false);
  const [otpRemainingAttempts, setOtpRemainingAttempts] = useState<number | null>(null);

  const cooldown = useCooldown(now);
  const resendSecondsLeft = cooldown.secondsLeft;

  const fieldError = (message: string | undefined) =>
    message ? t(message as RegisterFieldErrorKey) : undefined;

  const otpFieldError = (message: string | undefined) =>
    message ? t(`otp.${message as OtpFieldErrorKey}`, { count: otpRemainingAttempts ?? 0 }) : undefined;

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

  const otpSchema = useMemo(
    () =>
      z.object({
        code: z.string().regex(/^\d{6}$/, "codeRequired" satisfies OtpFieldErrorKey),
      }),
    [],
  );

  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: "", fullName: "", email: "", password: "", acceptTerms: false },
  });

  const {
    register: registerOtp,
    handleSubmit: handleSubmitOtp,
    setError: setOtpError,
    clearErrors: clearOtpErrors,
    reset: resetOtpForm,
    formState: { errors: otpErrors, isSubmitting: isVerifying },
  } = useForm<OtpValues>({
    resolver: zodResolver(otpSchema),
    defaultValues: { code: "" },
  });

  function applyFieldErrorAndFocus(
    field: keyof RegisterValues,
    message: RegisterFieldErrorKey,
    deferFocus: boolean,
  ) {
    if (deferFocus) {
      setError(field, { message });
      setTimeout(() => setFocus(field), 0);
    } else {
      setError(field, { message }, { shouldFocus: true });
    }
  }

  function applyServerFieldError(error: ApiStatusError, deferFocus = false): boolean {
    if (error.status === 409 && error.code === "USERNAME_TAKEN") {
      applyFieldErrorAndFocus("username", "usernameTaken", deferFocus);
      return true;
    }
    if (error.status === 409 && error.code === "EMAIL_TAKEN") {
      applyFieldErrorAndFocus("email", "emailTaken", deferFocus);
      return true;
    }
    for (const field of error.fieldErrors) {
      if (field.field === "acceptTerms" && field.code === "TERMS_REQUIRED") {
        setError("acceptTerms", { message: "termsRequired" satisfies RegisterFieldErrorKey });
        return true;
      }
      if (field.field === "username" && field.code === "USERNAME_TAKEN") {
        applyFieldErrorAndFocus("username", "usernameTaken", deferFocus);
        return true;
      }
      if (field.field === "email") {
        const message =
          field.code === "EMAIL_TAKEN"
            ? ("emailTaken" satisfies RegisterFieldErrorKey)
            : field.code === "EMAIL_INVALID"
              ? ("emailInvalid" satisfies RegisterFieldErrorKey)
              : null;
        if (message) {
          applyFieldErrorAndFocus("email", message, deferFocus);
          return true;
        }
      }
      if (field.field === "password" && (field.code === "PASSWORD_WEAK" || field.code === "PASSWORD_SAME_AS_USERNAME")) {
        applyFieldErrorAndFocus(
          "password",
          field.code === "PASSWORD_SAME_AS_USERNAME" ? "passwordSameAsUsername" : "passwordWeak",
          deferFocus,
        );
        return true;
      }
    }
    return false;
  }

  async function onSubmit(values: RegisterValues) {
    setServerAlert(null);
    try {
      const response = await startRegisterApi({
        ...(pendingSession ? { registrationId: pendingSession.registrationId } : {}),
        username: values.username.trim(),
        fullName: values.fullName.trim(),
        email: values.email.trim(),
        password: values.password,
        acceptTerms: true,
        termsVersion: TERMS_VERSION,
        ...(callbackUrl ? { callbackUrl } : {}),
      });
      setPendingSession({ registrationId: response.registrationId, email: response.email });
      setOtpAlert(null);
      setOtpRemainingAttempts(null);
      setDailyLimitUntil(null);
      setCodeSentNote(false);
      resetOtpForm({ code: "" });
      cooldown.start(response.resendAfterSeconds ?? DEFAULT_RESEND_SECONDS);
      setStep("otp");
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

  async function onSubmitOtp(values: OtpValues) {
    if (!pendingSession) return;
    setOtpAlert(null);
    setOtpRemainingAttempts(null);
    try {
      await verifyRegisterApi(pendingSession.registrationId, values.code);
      navigate(continueUrl);
    } catch (error) {
      if (error instanceof EmailCodeStatusError && error.code === "EMAIL_CODE_INVALID") {
        setOtpRemainingAttempts(error.remainingAttempts ?? null);
        setOtpError("code", { message: "codeInvalid" satisfies OtpFieldErrorKey }, { shouldFocus: true });
        return;
      }
      if (error instanceof ApiStatusError) {
        if (error.code === "EMAIL_CODE_EXPIRED") {
          setOtpAlert("codeExpired");
          return;
        }
        if (error.status === 429) {
          setOtpAlert("rateLimited");
          return;
        }
        if (error.status === 409 || error.status === 400) {
          setStep("details");
          if (applyServerFieldError(error, true)) return;
        }
      }
      setOtpAlert("generic");
    }
  }

  async function onResendOtp() {
    if (!pendingSession) return;
    setOtpAlert(null);
    setCodeSentNote(false);
    setDailyLimitUntil(null);
    try {
      const res = await resendRegisterApi(pendingSession.registrationId);
      setCodeSentNote(true);
      cooldown.start(res.resendAfterSeconds ?? DEFAULT_RESEND_SECONDS);
      clearOtpErrors("code");
      setOtpRemainingAttempts(null);
    } catch (error) {
      if (error instanceof EmailCodeStatusError && error.status === 429) {
        const retryAfter = error.retryAfter ?? DEFAULT_RESEND_SECONDS;
        if (retryAfter >= LONG_COOLDOWN_SECONDS) {
          setDailyLimitUntil(now() + retryAfter * 1000);
        }
        cooldown.start(retryAfter);
        return;
      }
      if (error instanceof ApiStatusError && error.code === "EMAIL_CODE_EXPIRED") {
        setOtpAlert("codeExpired");
        return;
      }
      setOtpAlert("resendFailed");
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

      <div className="flex w-full max-w-[920px] flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-xl md:flex-row-reverse">
        <section className="flex min-w-0 flex-1 flex-col gap-5 p-6 sm:p-10 md:p-12">
          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
            <h1 className="text-page-title font-bold text-text">{step === "otp" ? t("otp.title") : t("title")}</h1>
            <Link
              href="/"
              className={cn(
                "text-label font-medium text-primary-text transition-colors duration-150 hover:underline",
                coarseTextHitArea,
              )}
            >
              {"\u2190"} {t("backHome")}
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

          {step === "details" ? (
            <>
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
            </>
          ) : (
            <>
              <p className="text-body text-text-muted">
                {t.rich("otp.sentTo", {
                  email: pendingSession?.email ?? "",
                  strong: (chunks) => <strong className="font-semibold text-text">{chunks}</strong>,
                })}
              </p>

              {otpAlert && (
                <div
                  role="alert"
                  className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
                >
                  <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <span>{t(`otp.alerts.${otpAlert}`)}</span>
                </div>
              )}

              {dailyLimitUntil && (
                <div
                  role="alert"
                  className="flex items-start gap-2.5 rounded-lg border border-warning/30 bg-warning-bg px-3.5 py-3 text-body text-warning"
                >
                  <Clock aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <span>
                    {t("otp.dailyLimit", {
                      time: format.dateTime(new Date(dailyLimitUntil), {
                        day: "numeric",
                        month: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                      }),
                    })}
                  </span>
                </div>
              )}

              <form noValidate onSubmit={handleSubmitOtp(onSubmitOtp)} className="flex flex-col gap-5">
                <FormField label={t("otp.codeLabel")} error={otpFieldError(otpErrors.code?.message)} required>
                  {(field) => (
                    <Input
                      {...field}
                      {...registerOtp("code")}
                      type="text"
                      required
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      enterKeyHint="done"
                      maxLength={6}
                      className={cn(
                        "h-14 text-center text-h3 font-semibold tracking-[0.4em] tabular-nums",
                        touchFieldText,
                      )}
                      placeholder={t("otp.codePlaceholder")}
                    />
                  )}
                </FormField>

                <Button type="submit" size="xl" loading={isVerifying} className="w-full">
                  {t("otp.submit")}
                </Button>
              </form>

              <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-page px-3.5 py-3">
                <span className="text-body text-text-muted">{t("otp.notReceived")}</span>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={resendSecondsLeft > 0}
                  onClick={() => void onResendOtp()}
                  className="tabular-nums"
                >
                  {t("otp.resend")}
                </Button>
              </div>

              {resendSecondsLeft > 0 && !dailyLimitUntil && (
                <p className="text-body text-text-muted tabular-nums">
                  {t("otp.resendIn", { time: formatClock(resendSecondsLeft) })}
                </p>
              )}

              <LiveStatus
                message={cooldownAnnouncement(cooldown, {
                  start: (time) => t("otp.cooldownStart", { time }),
                  end: t("otp.cooldownEnd"),
                })}
              />

              {codeSentNote && (
                <p role="status" className="text-caption text-success">
                  {t("otp.codeResent")}
                </p>
              )}

              <Button
                type="button"
                variant="secondary"
                size="lg"
                className="w-full"
                onClick={() => {
                  setOtpAlert(null);
                  setServerAlert(null);
                  setStep("details");
                }}
              >
                {t("otp.backToEdit")}
              </Button>

              <p className="text-field-label text-text-muted">{t("otp.footnote")}</p>
            </>
          )}
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
