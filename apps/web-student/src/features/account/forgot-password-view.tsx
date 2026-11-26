"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { BrandWordmark, Button, cn, FormField, Input, LiveStatus, LocaleSwitcher, ThemeToggle } from "@thi360/ui";
import { validateCallbackUrl } from "@thi360/web-kit/redirect";
import { AlertCircle, Mail } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { withCallbackUrl } from "@/features/account/account-links";
import { requestPasswordReset } from "@/features/account/api";
import { cooldownAnnouncement, formatClock, useCooldown } from "@/features/account/cooldown";
import { AuthPageStage } from "@/features/auth/auth-backdrop";
import { ApiStatusError } from "@/lib/api/client";
import { coarseTextHitArea, touchFieldText } from "@/lib/styles";

const CARD_CLASS =
  "flex w-full max-w-[480px] flex-col gap-5 rounded-2xl border border-border bg-surface p-6 shadow-xl sm:p-10";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Máy chủ giới hạn mỗi tài khoản 1 lần mỗi 60 giây (E16): nút gửi lại chờ đúng chừng đó. */
const RESEND_SECONDS = 60;

type ForgotFieldErrorKey = "emailRequired" | "emailInvalid";
type ForgotAlertCode = "RATE_LIMITED" | "generic";

type ForgotValues = { email: string };

export function ForgotPasswordView({
  requestApi = requestPasswordReset,
  now = () => Date.now(),
}: {
  requestApi?: (email: string) => Promise<void>;
  now?: () => number;
}) {
  const t = useTranslations("account.forgotPassword");
  const common = useTranslations("common");
  const searchParams = useSearchParams();
  const callbackUrl = validateCallbackUrl(searchParams.get("callbackUrl")) ?? undefined;
  const loginHref = withCallbackUrl("/login", callbackUrl);

  // Sau khi gửi, mọi email đều cho cùng một nội dung (AC-06.04): không lộ email có tài khoản hay không.
  const [sent, setSent] = useState(false);
  const [alert, setAlert] = useState<ForgotAlertCode | null>(null);
  const cooldown = useCooldown(now);
  const secondsLeft = cooldown.secondsLeft;

  const schema = useMemo(
    () =>
      z.object({
        email: z
          .string()
          .trim()
          .min(1, "emailRequired" satisfies ForgotFieldErrorKey)
          .refine((value) => EMAIL_PATTERN.test(value), "emailInvalid" satisfies ForgotFieldErrorKey),
      }),
    [],
  );

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<ForgotValues>({
    // Kiểm tra khi rời ô (DESIGN 5.2): email sai định dạng báo lỗi tại ô, không gọi API.
    mode: "onTouched",
    resolver: zodResolver(schema),
    defaultValues: { email: "" },
  });

  const fieldError = (message: string | undefined) =>
    message ? t(message as ForgotFieldErrorKey) : undefined;

  async function send(email: string) {
    setAlert(null);
    try {
      await requestApi(email.trim());
      setSent(true);
      cooldown.start(RESEND_SECONDS);
    } catch (error) {
      setAlert(error instanceof ApiStatusError && error.status === 429 ? "RATE_LIMITED" : "generic");
    }
  }

  async function onSubmit(values: ForgotValues) {
    await send(values.email);
  }

  const header = (
    <div className="flex w-full max-w-[480px] items-center gap-2">
      <Link href="/" className="flex items-center">
        <BrandWordmark alt={common("appName")} className="h-9" />
      </Link>
      <div className="ml-auto flex items-center gap-2">
        <ThemeToggle />
        <LocaleSwitcher />
      </div>
    </div>
  );

  if (sent) {
    return (
      <AuthPageStage>
        {header}
        <section aria-labelledby="forgot-sent-title" className={CARD_CLASS}>
          <span aria-hidden className="grid size-12 place-items-center rounded-lg bg-success-bg text-success">
            <Mail className="size-6" />
          </span>
          <h1 id="forgot-sent-title" className="text-page-title font-bold text-text">
            {t("sentTitle")}
          </h1>
          <p role="status" className="text-body text-text-secondary">
            {t("sentMessage")}
          </p>
          <ul className="flex list-disc flex-col gap-1 pl-5 text-body text-text-muted">
            <li>{t("sentNote1")}</li>
            <li>{t("sentNote2")}</li>
            <li>{t("sentNote3")}</li>
          </ul>
          {alert && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
            >
              <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{t(`errors.${alert}`)}</span>
            </div>
          )}
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link href={loginHref}>{t("backToLogin")}</Link>
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="lg"
              disabled={secondsLeft > 0}
              onClick={() => void send(getValues("email"))}
              className="tabular-nums"
            >
              {t("resend")}
            </Button>
          </div>
          {/* Thời gian chờ là chữ thường, không nằm trong nhãn nút bị khóa (nút khóa làm nhạt chữ, kém 4.5:1). */}
          {secondsLeft > 0 && <p className="text-body text-text-muted tabular-nums">{t("resendIn", { time: formatClock(secondsLeft) })}</p>}
          <LiveStatus
            message={cooldownAnnouncement(cooldown, {
              start: (time) => t("cooldownStart", { time }),
              end: t("cooldownEnd"),
            })}
          />
        </section>
      </AuthPageStage>
    );
  }

  return (
    <AuthPageStage>
      {header}
      <div className={CARD_CLASS}>
        <div className="flex flex-col gap-1.5">
          <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
          <p className="text-body text-text-muted">{t("description")}</p>
        </div>

        {alert && (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
          >
            <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
            <span>{t(`errors.${alert}`)}</span>
          </div>
        )}

        <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
          <FormField label={t("emailLabel")} error={fieldError(errors.email?.message)} required>
            {(field) => (
              <Input
                {...field}
                {...register("email")}
                size="lg"
                type="email"
                required
                autoComplete="email"
                enterKeyHint="done"
                className={touchFieldText}
                leadingIcon={<Mail />}
                placeholder={t("emailPlaceholder")}
              />
            )}
          </FormField>

          <Button type="submit" size="xl" loading={isSubmitting} className="w-full">
            {t("submit")}
          </Button>
        </form>

        <div className="flex flex-wrap justify-between gap-2">
          <Link
            href={loginHref}
            className={cn("text-label font-medium text-primary-text hover:underline", coarseTextHitArea)}
          >
            ← {t("backToLogin")}
          </Link>
          <Link
            href={withCallbackUrl("/register", callbackUrl)}
            className={cn("text-label font-medium text-primary-text hover:underline", coarseTextHitArea)}
          >
            {t("createAccount")}
          </Link>
        </div>
      </div>
    </AuthPageStage>
  );
}
