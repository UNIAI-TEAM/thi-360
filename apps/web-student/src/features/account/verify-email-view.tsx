"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  cn,
  EmptyState,
  FormField,
  Input,
  LiveStatus,
  LocaleSwitcher,
  Skeleton,
  ThemeToggle,
} from "@thi360/ui";
import { validateCallbackUrl } from "@thi360/web-kit/redirect";
import { AlertCircle, CheckCircle2, Clock, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import {
  EmailCodeStatusError,
  loadProfile,
  PROFILE_QUERY_KEY,
  type ProfileResponse,
  sendVerificationCode,
  verifyEmailCode,
} from "@/features/account/api";
import { cooldownAnnouncement, formatClock, LONG_COOLDOWN_SECONDS, useCooldown } from "@/features/account/cooldown";
import { AuthPageStage } from "@/features/auth/auth-backdrop";
import { ApiStatusError } from "@/lib/api/client";
import { coarseTextHitArea, touchFieldText } from "@/lib/styles";

const CARD_CLASS =
  "flex w-full max-w-[480px] flex-col gap-5 rounded-2xl border border-border bg-surface p-6 shadow-xl sm:p-10";

/** Lỗi của ô mã và cảnh báo trên màn lưu bằng khóa dịch (`account.verifyEmail.<khóa>`), dịch lúc hiển thị. */
type VerifyFieldErrorKey = "codeRequired" | "codeInvalid";
type VerifyAlertCode = "codeExpired" | "resendFailed" | "generic";

type VerifyValues = { code: string };

/** Đếm ngược (giây) giữa hai lần gửi mã, theo chính sách E15/E17 của máy chủ khi không có `retryAfter`. */
const DEFAULT_RESEND_SECONDS = 60;

export function VerifyEmailView({
  navigate = (url: string) => window.location.assign(url),
  loadProfileApi = loadProfile,
  sendCodeApi = sendVerificationCode,
  verifyCodeApi = verifyEmailCode,
  now = () => Date.now(),
}: {
  navigate?: (url: string) => void;
  loadProfileApi?: () => Promise<ProfileResponse>;
  sendCodeApi?: () => Promise<void>;
  verifyCodeApi?: (code: string) => Promise<void>;
  now?: () => number;
}) {
  const t = useTranslations("account.verifyEmail");
  const common = useTranslations("common");
  const format = useFormatter();
  const searchParams = useSearchParams();
  const continueUrl = validateCallbackUrl(searchParams.get("callbackUrl")) ?? "/home";

  const profileQuery = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: loadProfileApi,
    staleTime: 0,
    retry: false,
  });

  // Đích sau xác minh thành công và nút "Để sau": callbackUrl hợp lệ, không thì về /home.
  const [verified, setVerified] = useState(false);
  const [alert, setAlert] = useState<VerifyAlertCode | null>(null);
  // Vượt giới hạn ngày: khóa gửi lại tới thời điểm máy chủ trả trong `retryAfter`.
  const [dailyLimitUntil, setDailyLimitUntil] = useState<number | null>(null);
  const [codeSentNote, setCodeSentNote] = useState(false);
  const [remainingAttempts, setRemainingAttempts] = useState<number | null>(null);
  const cooldown = useCooldown(now);
  const resendSecondsLeft = cooldown.secondsLeft;

  const schema = useMemo(
    () =>
      z.object({
        code: z.string().regex(/^\d{6}$/, "codeRequired" satisfies VerifyFieldErrorKey),
      }),
    [],
  );

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<VerifyValues>({
    resolver: zodResolver(schema),
    defaultValues: { code: "" },
  });

  const fieldError = (message: string | undefined) =>
    message ? t(message as VerifyFieldErrorKey, { count: remainingAttempts ?? 0 }) : undefined;

  function applyVerifyError(error: unknown) {
    if (error instanceof EmailCodeStatusError && error.code === "EMAIL_CODE_INVALID") {
      setRemainingAttempts(error.remainingAttempts ?? null);
      setError("code", { message: "codeInvalid" satisfies VerifyFieldErrorKey }, { shouldFocus: true });
      return;
    }
    if (error instanceof ApiStatusError && error.code === "EMAIL_CODE_EXPIRED") {
      setAlert("codeExpired");
      return;
    }
    setAlert("generic");
  }

  async function onSubmit(values: VerifyValues) {
    setAlert(null);
    setRemainingAttempts(null);
    try {
      await verifyCodeApi(values.code);
      setVerified(true);
      navigate(continueUrl);
    } catch (error) {
      applyVerifyError(error);
    }
  }

  async function onResend() {
    setAlert(null);
    setCodeSentNote(false);
    setDailyLimitUntil(null);
    try {
      await sendCodeApi();
      setCodeSentNote(true);
      cooldown.start(DEFAULT_RESEND_SECONDS);
      clearErrors("code");
      setRemainingAttempts(null);
    } catch (error) {
      if (error instanceof EmailCodeStatusError && error.status === 429) {
        const retryAfter = error.retryAfter ?? DEFAULT_RESEND_SECONDS;
        // 60 giây chờ → đếm ngược trên nút; giới hạn ngày (giờ trở lên) → cảnh báo kèm giờ thử lại.
        if (retryAfter >= LONG_COOLDOWN_SECONDS) {
          setDailyLimitUntil(now() + retryAfter * 1000);
        }
        cooldown.start(retryAfter);
        return;
      }
      setAlert("resendFailed");
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

  if (profileQuery.isLoading) {
    return (
      <AuthPageStage>
        {header}
        <div role="status" aria-label={common("loading")} className={CARD_CLASS}>
          <div className="flex flex-col gap-1.5">
            <Skeleton className="h-7.5 w-40" />
            <Skeleton className="h-10.5 w-full sm:h-5.25" />
          </div>
          <div className="flex flex-col gap-1.5">
            <Skeleton className="h-5 w-28" />
            <Skeleton className="h-14 w-full" />
          </div>
          <Skeleton className="h-13 w-full rounded-lg" />
          <Skeleton className="h-11 w-full rounded-lg" />
          <Skeleton className="h-12 w-full rounded-lg" />
        </div>
      </AuthPageStage>
    );
  }

  if (profileQuery.isError || !profileQuery.data) {
    return (
      <AuthPageStage>
        {header}
        <div className={CARD_CLASS}>
          <EmptyState
            headingLevel={1}
            icon={<TriangleAlert />}
            title={t("errorLoadTitle")}
            description={t("errorLoad")}
            action={<Button onClick={() => void profileQuery.refetch()}>{common("retry")}</Button>}
          />
        </div>
      </AuthPageStage>
    );
  }

  const profile = profileQuery.data;

  // Tài khoản đã xác minh (mở lại trang): trạng thái đã xác minh + lối đi tiếp (quyết định tạm của plan).
  if (profile.emailVerified || verified) {
    return (
      <AuthPageStage>
        {header}
        <div className={CARD_CLASS}>
          <div className="flex flex-col gap-1.5">
            <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
          </div>
          <div
            role="status"
            className="flex items-start gap-2.5 rounded-lg border border-success/30 bg-success-bg px-3.5 py-3 text-body text-success"
          >
            <CheckCircle2 aria-hidden className="mt-0.5 size-4.5 shrink-0" />
            <span>{verified ? t("verifiedContinue") : t("alreadyVerified")}</span>
          </div>
          <Button asChild size="lg" className="w-full">
            <Link href={continueUrl}>{t("continue")}</Link>
          </Button>
        </div>
      </AuthPageStage>
    );
  }

  return (
    <AuthPageStage>
      {header}
      <div className={CARD_CLASS}>
        <div className="flex flex-col gap-1.5">
          <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
          <p className="text-body text-text-muted">
            {t.rich("sentTo", {
              email: profile.email ?? "",
              strong: (chunks) => <strong className="font-semibold text-text">{chunks}</strong>,
            })}
          </p>
        </div>

        {alert && (
          <div
            role="alert"
            className={cn(
              "flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger",
            )}
          >
            <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
            <span>{t(`alerts.${alert}`)}</span>
          </div>
        )}

        {dailyLimitUntil && (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg border border-warning/30 bg-warning-bg px-3.5 py-3 text-body text-warning"
          >
            <Clock aria-hidden className="mt-0.5 size-4.5 shrink-0" />
            <span>
              {t("dailyLimit", {
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

        <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
          <FormField label={t("codeLabel")} error={fieldError(errors.code?.message)} required>
            {(field) => (
              <Input
                {...field}
                {...register("code")}
                type="text"
                required
                inputMode="numeric"
                autoComplete="one-time-code"
                enterKeyHint="done"
                maxLength={6}
                className={cn("h-14 text-center text-h3 font-semibold tracking-[0.4em] tabular-nums", touchFieldText)}
                placeholder={t("codePlaceholder")}
              />
            )}
          </FormField>

          <Button type="submit" size="xl" loading={isSubmitting} className="w-full">
            {t("submit")}
          </Button>
        </form>

        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-page px-3.5 py-3">
          <span className="text-body text-text-muted">{t("notReceived")}</span>
          <Button
            type="button"
            variant="secondary"
            disabled={resendSecondsLeft > 0}
            onClick={() => void onResend()}
            className="tabular-nums"
          >
            {t("resend")}
          </Button>
        </div>
        {/* Thời gian chờ là chữ thường bên ngoài nút khóa: chữ trong nút khóa bị nhạt, dưới 4.5:1. */}
        {resendSecondsLeft > 0 && !dailyLimitUntil && (
          <p className="text-body text-text-muted tabular-nums">{t("resendIn", { time: formatClock(resendSecondsLeft) })}</p>
        )}
        <LiveStatus
          message={cooldownAnnouncement(cooldown, {
            start: (time) => t("cooldownStart", { time }),
            end: t("cooldownEnd"),
          })}
        />
        {codeSentNote && (
          <p role="status" className="text-caption text-success">
            {t("codeResent")}
          </p>
        )}

        <Button asChild variant="secondary" size="lg" className="w-full">
          <Link href={continueUrl}>{t("later")}</Link>
        </Button>

        <p className="text-field-label text-text-muted">
          {t.rich("footnote", {
            profile: (chunks) => (
              <Link href="/account/profile" className={cn("font-medium text-primary-text hover:underline", coarseTextHitArea)}>
                {chunks}
              </Link>
            ),
          })}
        </p>
      </div>
    </AuthPageStage>
  );
}
