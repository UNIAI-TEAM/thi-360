"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  cn,
  EmptyState,
  FormField,
  Input,
  LiveStatus,
  notify,
  PasswordField,
  Skeleton,
  StatusChip,
} from "@thi360/ui";
import { initialsOf } from "@thi360/web-kit/client";
import { AlertCircle, CircleAlert, Clock, Lock, TriangleAlert, Upload } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { type ChangeEvent, useEffect, useId, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import {
  confirmEmailChange,
  EmailCodeStatusError,
  loadProfile,
  PROFILE_QUERY_KEY,
  type ProfileResponse,
  removeAvatar,
  requestEmailChange,
  updateProfile,
  uploadAvatar,
} from "@/features/account/api";
import { cooldownAnnouncement, formatClock, LONG_COOLDOWN_SECONDS, useCooldown } from "@/features/account/cooldown";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { touchFieldText } from "@/lib/styles";

/** Giới hạn ảnh đại diện (TM E20): kiểm trước khi gửi để ảnh hiện tại không bị đụng tới khi vi phạm hiển nhiên. */
export const AVATAR_MAX_BYTES = 5 * 1024 * 1024;
const AVATAR_ACCEPT = "image/jpeg,image/png,image/webp";

const PHONE_PATTERN = /^0\d{9}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const FULL_NAME_MAX = 200;

const CARD_CLASS = "flex flex-col gap-4 rounded-xl border border-border bg-surface p-6";

type InfoErrorKey = "fullNameRequired" | "fullNameTooLong" | "phoneInvalid";
type ChangeErrorKey =
  | "newEmailRequired"
  | "newEmailInvalid"
  | "newEmailTaken"
  | "currentPasswordRequired"
  | "wrongPassword";
type CodeErrorKey = "codeRequired" | "codeInvalid";

/** Lỗi ảnh đại diện: khóa dịch `account.profile.avatar.errors.<mã>`. */
type AvatarErrorCode = "TOO_LARGE" | "INVALID_IMAGE" | "DIMENSIONS_EXCEEDED" | "generic";

function avatarErrorCode(error: unknown): AvatarErrorCode {
  if (error instanceof ApiStatusError) {
    if (error.status === 413 || error.code === "AVATAR_TOO_LARGE") return "TOO_LARGE";
    if (error.code === "AVATAR_DIMENSIONS_EXCEEDED") return "DIMENSIONS_EXCEEDED";
    if (error.code === "AVATAR_INVALID_IMAGE") return "INVALID_IMAGE";
  }
  return "generic";
}

type InfoValues = { fullName: string; phone: string };
type ChangeValues = { newEmail: string; currentPassword: string };
type CodeValues = { code: string };

export function ProfileView({ loadCurrentSession = loadSession }: { loadCurrentSession?: SessionLoader }) {
  const t = useTranslations("account.profile");
  const common = useTranslations("common");
  const queryClient = useQueryClient();

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });
  const profileQuery = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: loadProfile,
    staleTime: 0,
  });

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  function applyProfile(next: ProfileResponse) {
    queryClient.setQueryData(PROFILE_QUERY_KEY, next);
    // Họ tên và ảnh hiện cả ở menu tài khoản.
    void queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />
      <main className="mx-auto flex max-w-[1170px] flex-col gap-6 px-4 py-10 sm:px-6">
        {profileQuery.isLoading && (
          <div role="status" aria-label={common("loading")} className="flex flex-col gap-6">
            <div className="flex flex-col gap-1.5">
              <Skeleton className="h-9.5 w-40" />
              <Skeleton className="h-5.25 w-96 max-w-full" />
            </div>
            <div className="flex flex-wrap items-start gap-6">
              <Skeleton className="h-80 min-w-0 flex-1 basis-70 rounded-xl" />
              <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-6">
                <Skeleton className="h-72 w-full rounded-xl" />
                <Skeleton className="h-32 w-full rounded-xl" />
                <Skeleton className="h-24 w-full rounded-xl" />
              </div>
            </div>
          </div>
        )}

        {profileQuery.isError && (
          <EmptyState
            icon={<TriangleAlert />}
            title={t("errorTitle")}
            description={t("errorDescription")}
            action={<Button onClick={() => void profileQuery.refetch()}>{common("retry")}</Button>}
          />
        )}

        {profileQuery.data && <ProfileContent profile={profileQuery.data} onProfileChange={applyProfile} />}
      </main>
    </div>
  );
}

function ProfileContent({
  profile,
  onProfileChange,
}: {
  profile: ProfileResponse;
  onProfileChange: (next: ProfileResponse) => void;
}) {
  const t = useTranslations("account.profile");

  return (
    <>
      <div className="flex flex-col gap-1.5">
        <h1 className="text-h1 font-bold text-text">{t("title")}</h1>
        <p className="text-body text-text-muted">{t("intro")}</p>
      </div>

      {!profile.emailVerified && profile.email && (
        <div
          role="status"
          className="flex flex-wrap items-center gap-3 rounded-lg border border-warning/30 bg-warning-bg px-4 py-3 text-body text-warning"
        >
          <TriangleAlert aria-hidden className="size-4.5 shrink-0" />
          <span className="min-w-0 flex-1 basis-80">
            <strong className="font-semibold">{t("unverifiedTitle")}</strong> {t("unverifiedBody")}
          </span>
          <Button asChild variant="secondary">
            <Link href="/verify-email?callbackUrl=%2Faccount%2Fprofile">{t("verifyEmail")}</Link>
          </Button>
        </div>
      )}

      <div className="flex flex-wrap items-start gap-6">
        <AvatarCard profile={profile} onProfileChange={onProfileChange} />
        <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-6">
          <InfoForm profile={profile} onProfileChange={onProfileChange} />
          <EmailSection profile={profile} onProfileChange={onProfileChange} />
          <section aria-labelledby="profile-password-title" className={cn(CARD_CLASS, "flex-row flex-wrap items-center justify-between")}>
            <div className="flex flex-col gap-1">
              <h2 id="profile-password-title" className="text-h3 font-bold text-text">
                {t("password.title")}
              </h2>
              <p className="text-body text-text-muted">{t("password.description")}</p>
            </div>
            <Button asChild variant="secondary">
              <Link href="/change-password?source=account-menu">{t("password.change")}</Link>
            </Button>
          </section>
        </div>
      </div>
    </>
  );
}

function AvatarCard({
  profile,
  onProfileChange,
}: {
  profile: ProfileResponse;
  onProfileChange: (next: ProfileResponse) => void;
}) {
  const t = useTranslations("account.profile.avatar");
  const [error, setError] = useState<AvatarErrorCode | null>(null);
  const [errorFile, setErrorFile] = useState<{ name: string; size: number } | null>(null);
  const [busy, setBusy] = useState(false);
  const sizeFormatter = useMemo(() => new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }), []);

  async function onFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Cho phép chọn lại đúng tệp vừa chọn.
    event.target.value = "";
    if (!file) return;
    setError(null);
    setErrorFile(null);
    // Vượt giới hạn hiển nhiên: báo ngay kèm tên tệp và dung lượng, ảnh hiện tại giữ nguyên, không gọi API.
    if (file.size > AVATAR_MAX_BYTES) {
      setErrorFile({ name: file.name, size: file.size });
      setError("TOO_LARGE");
      return;
    }
    setBusy(true);
    try {
      onProfileChange(await uploadAvatar(file));
      notify.success(t("uploaded"));
      setErrorFile(null);
    } catch (uploadError) {
      setError(avatarErrorCode(uploadError));
    } finally {
      setBusy(false);
    }
  }

  async function onRemove() {
    setError(null);
    setErrorFile(null);
    setBusy(true);
    try {
      await removeAvatar();
      onProfileChange({ ...profile, avatarAssetId: null, avatarUrl: null });
      notify.success(t("removed"));
    } catch {
      setError("generic");
    } finally {
      setBusy(false);
    }
  }

  const initials = initialsOf(profile.fullName, profile.username);

  return (
    <section
      aria-labelledby="profile-avatar-title"
      className={cn(CARD_CLASS, "min-w-0 flex-1 basis-70 items-center gap-3.5 text-center")}
    >
      <h2 id="profile-avatar-title" className="self-stretch text-left text-h3 font-bold text-text">
        {t("title")}
      </h2>
      {profile.avatarUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- ảnh có xác thực qua proxy API, không qua trình tối ưu ảnh của Next
        <img
          src={profile.avatarUrl}
          alt={t("current")}
          width={112}
          height={112}
          className="size-28 rounded-full object-cover ring-4 ring-primary-50"
        />
      ) : (
        <span
          role="img"
          aria-label={t("currentEmpty")}
          className="flex size-28 items-center justify-center rounded-full bg-primary-fill text-h2 font-bold text-on-primary ring-4 ring-primary-50"
        >
          {initials}
        </span>
      )}
      <div>
        <div className="text-title font-semibold text-text">{profile.fullName}</div>
        <div className="text-body text-text-muted">{profile.username}</div>
      </div>

      <div className="flex flex-wrap justify-center gap-2">
        <label
          className={cn(
            "relative inline-flex h-11 cursor-pointer items-center gap-2 rounded-lg border border-primary-200 bg-surface px-4 text-label font-semibold text-primary-800 transition-colors duration-150 hover:bg-primary-50 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-primary-600",
            busy && "pointer-events-none opacity-60",
          )}
        >
          <input
            type="file"
            accept={AVATAR_ACCEPT}
            disabled={busy}
            onChange={(event) => void onFileChange(event)}
            className="sr-only"
          />
          <Upload aria-hidden className="size-4" />
          {t("upload")}
        </label>
        {profile.avatarUrl && (
          <Button type="button" variant="secondary" disabled={busy} onClick={() => void onRemove()} className="text-danger">
            {t("remove")}
          </Button>
        )}
      </div>
      <p className="text-caption text-text-muted">{t("hint")}</p>

      {error && (
        <div
          role="alert"
          className="flex items-start gap-2.5 self-stretch rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-left text-body text-danger"
        >
          <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
          <span>
            {error === "TOO_LARGE" && errorFile
              ? t("errors.TOO_LARGE_FILE", {
                  name: errorFile.name,
                  size: sizeFormatter.format(errorFile.size / (1024 * 1024)),
                })
              : t(`errors.${error}`)}
          </span>
        </div>
      )}
    </section>
  );
}

function InfoForm({
  profile,
  onProfileChange,
}: {
  profile: ProfileResponse;
  onProfileChange: (next: ProfileResponse) => void;
}) {
  const t = useTranslations("account.profile.info");
  const [hasServerError, setHasServerError] = useState(false);

  // Số cũ do quản trị viên nhập theo quy tắc rộng hơn (tối đa 30 ký tự) được giữ nguyên khi người dùng không sửa ô này.
  const currentPhone = (profile.phone ?? "").trim();
  const schema = useMemo(
    () =>
      z.object({
        fullName: z
          .string()
          .trim()
          .min(1, "fullNameRequired" satisfies InfoErrorKey)
          .max(FULL_NAME_MAX, "fullNameTooLong" satisfies InfoErrorKey),
        phone: z
          .string()
          .trim()
          .refine(
            (value) => value === currentPhone || value === "" || PHONE_PATTERN.test(value),
            "phoneInvalid" satisfies InfoErrorKey,
          ),
      }),
    [currentPhone],
  );

  const defaults: InfoValues = { fullName: profile.fullName, phone: profile.phone ?? "" };
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<InfoValues>({
    mode: "onTouched",
    resolver: zodResolver(schema),
    defaultValues: defaults,
  });

  // Sau khi tải lại hồ sơ (lưu xong, hoặc dữ liệu mới về) biểu mẫu lấy giá trị mới làm mốc.
  useEffect(() => {
    reset({ fullName: profile.fullName, phone: profile.phone ?? "" });
  }, [profile.fullName, profile.phone, reset]);

  const fieldError = (message: string | undefined) => (message ? t(message as InfoErrorKey) : undefined);

  async function onSubmit(values: InfoValues) {
    setHasServerError(false);
    try {
      const fullName = values.fullName.trim();
      const phone = values.phone.trim();
      // Không gửi `phone` khi không đổi: máy chủ giữ nguyên số cũ.
      const next = await updateProfile({ fullName, ...(phone !== currentPhone ? { phone } : {}) });
      onProfileChange(next);
      notify.success(t("saved"));
    } catch (error) {
      if (error instanceof ApiStatusError) {
        for (const field of error.fieldErrors) {
          if (field.field === "phone") {
            setError("phone", { message: "phoneInvalid" satisfies InfoErrorKey }, { shouldFocus: true });
            return;
          }
          if (field.field === "fullName") {
            setError(
              "fullName",
              { message: field.code === "SIZE" ? ("fullNameTooLong" satisfies InfoErrorKey) : ("fullNameRequired" satisfies InfoErrorKey) },
              { shouldFocus: true },
            );
            return;
          }
        }
      }
      setHasServerError(true);
    }
  }

  return (
    <form aria-labelledby="profile-info-title" noValidate onSubmit={handleSubmit(onSubmit)} className={cn(CARD_CLASS, "gap-4.5")}>
      <h2 id="profile-info-title" className="text-h3 font-bold text-text">
        {t("title")}
      </h2>

      {hasServerError && (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
        >
          <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
          <span>{t("genericError")}</span>
        </div>
      )}

      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(16.25rem,100%),1fr))] gap-4">
        <FormField label={t("usernameLabel")} hint={t("usernameHint")}>
          {(field) => (
            <Input
              {...field}
              size="lg"
              type="text"
              readOnly
              value={profile.username}
              className={cn("bg-subtle text-text-secondary", touchFieldText)}
              leadingIcon={<Lock />}
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
              maxLength={FULL_NAME_MAX}
              autoComplete="name"
              className={touchFieldText}
            />
          )}
        </FormField>

        <FormField
          label={t("phoneLabel")}
          error={fieldError(errors.phone?.message)}
          hint={profile.phone ? undefined : t("phoneEmpty")}
        >
          {(field) => (
            <Input
              {...field}
              {...register("phone")}
              size="lg"
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              className={touchFieldText}
              placeholder={t("phonePlaceholder")}
            />
          )}
        </FormField>
      </div>

      <div className="flex justify-end gap-3">
        <Button
          type="button"
          variant="secondary"
          disabled={!isDirty || isSubmitting}
          onClick={() => reset({ fullName: profile.fullName, phone: profile.phone ?? "" })}
        >
          {t("cancel")}
        </Button>
        <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
          {t("save")}
        </Button>
      </div>
    </form>
  );
}

/** Giữ chỗ giữa hai lần gửi mã đổi email: 60 giây theo E15/E17, trừ khi máy chủ trả `retryAfter`. */
const DEFAULT_RESEND_SECONDS = 60;

const currentTimeMs = () => Date.now();

function EmailSection({
  profile,
  onProfileChange,
}: {
  profile: ProfileResponse;
  onProfileChange: (next: ProfileResponse) => void;
}) {
  const t = useTranslations("account.profile.email");
  const [changing, setChanging] = useState(false);
  const [changeAlert, setChangeAlert] = useState<"RATE_LIMITED" | "generic" | null>(null);
  const [confirmAlert, setConfirmAlert] = useState<"EXPIRED" | "EMAIL_TAKEN" | "SYNC_FAILED" | "generic" | null>(null);
  const [remainingAttempts, setRemainingAttempts] = useState<number | null>(null);
  const cooldown = useCooldown(currentTimeMs);
  const secondsLeft = cooldown.secondsLeft;
  const changeId = useId();

  const changeSchema = useMemo(
    () =>
      z.object({
        newEmail: z
          .string()
          .trim()
          .min(1, "newEmailRequired" satisfies ChangeErrorKey)
          .refine((value) => EMAIL_PATTERN.test(value), "newEmailInvalid" satisfies ChangeErrorKey),
        currentPassword: z.string().min(1, "currentPasswordRequired" satisfies ChangeErrorKey),
      }),
    [],
  );
  const codeSchema = useMemo(
    () => z.object({ code: z.string().regex(/^\d{6}$/, "codeRequired" satisfies CodeErrorKey) }),
    [],
  );

  const changeForm = useForm<ChangeValues>({
    mode: "onTouched",
    resolver: zodResolver(changeSchema),
    defaultValues: { newEmail: "", currentPassword: "" },
  });
  const codeForm = useForm<CodeValues>({
    resolver: zodResolver(codeSchema),
    defaultValues: { code: "" },
  });

  const changeError = (message: string | undefined) => (message ? t(message as ChangeErrorKey) : undefined);
  const codeError = (message: string | undefined) =>
    message ? t(message as CodeErrorKey, { count: remainingAttempts ?? 0 }) : undefined;

  function openChangeForm(prefill?: string) {
    setChangeAlert(null);
    changeForm.reset({ newEmail: prefill ?? "", currentPassword: "" });
    setChanging(true);
  }

  async function onChangeSubmit(values: ChangeValues) {
    setChangeAlert(null);
    try {
      await requestEmailChange(values.newEmail.trim(), values.currentPassword);
      // Hồ sơ giờ có `pendingEmail`: tải lại để hiện khối chờ xác minh.
      onProfileChange({ ...profile, pendingEmail: values.newEmail.trim() });
      setChanging(false);
      codeForm.reset({ code: "" });
      setConfirmAlert(null);
      setRemainingAttempts(null);
      cooldown.start(DEFAULT_RESEND_SECONDS);
    } catch (error) {
      if (error instanceof EmailCodeStatusError && error.status === 429) {
        // 429: chờ đúng `retryAfter` của máy chủ, nút gửi khóa tới hết thời gian đó (R1-6).
        cooldown.start(error.retryAfter ?? DEFAULT_RESEND_SECONDS);
        setChangeAlert("RATE_LIMITED");
        return;
      }
      if (error instanceof ApiStatusError) {
        if (error.status === 409 && error.code === "EMAIL_TAKEN") {
          changeForm.setError("newEmail", { message: "newEmailTaken" satisfies ChangeErrorKey }, { shouldFocus: true });
          return;
        }
        if (error.fieldErrors.some((field) => field.field === "currentPassword")) {
          changeForm.setError(
            "currentPassword",
            { message: "wrongPassword" satisfies ChangeErrorKey },
            { shouldFocus: true },
          );
          return;
        }
        if (error.fieldErrors.some((field) => field.field === "newEmail")) {
          changeForm.setError("newEmail", { message: "newEmailInvalid" satisfies ChangeErrorKey }, { shouldFocus: true });
          return;
        }
      }
      setChangeAlert("generic");
    }
  }

  async function onConfirmSubmit(values: CodeValues) {
    setConfirmAlert(null);
    setRemainingAttempts(null);
    try {
      await confirmEmailChange(values.code);
      onProfileChange({ ...profile, email: profile.pendingEmail ?? profile.email, emailVerified: true, pendingEmail: null });
      notify.success(t("changed"));
      codeForm.reset({ code: "" });
    } catch (error) {
      if (error instanceof EmailCodeStatusError && error.code === "EMAIL_CODE_INVALID") {
        setRemainingAttempts(error.remainingAttempts ?? null);
        codeForm.setError("code", { message: "codeInvalid" satisfies CodeErrorKey }, { shouldFocus: true });
        return;
      }
      if (error instanceof ApiStatusError) {
        if (error.code === "EMAIL_CODE_EXPIRED") setConfirmAlert("EXPIRED");
        else if (error.status === 409 && error.code === "EMAIL_TAKEN") setConfirmAlert("EMAIL_TAKEN");
        else if (error.status === 503) setConfirmAlert("SYNC_FAILED");
        else setConfirmAlert("generic");
        return;
      }
      setConfirmAlert("generic");
    }
  }

  const passwordToggle = { showPassword: t("passwordShow"), hidePassword: t("passwordHide") };

  return (
    <section aria-labelledby="profile-email-title" className={CARD_CLASS}>
      <h2 id="profile-email-title" className="text-h3 font-bold text-text">
        {t("title")}
      </h2>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border px-4 py-3.5">
        <div className="flex min-w-0 flex-wrap items-center gap-2.5">
          {profile.email ? (
            <>
              <span className="min-w-0 font-semibold break-all text-text">{profile.email}</span>
              {profile.emailVerified ? (
                <StatusChip tone="success">{t("verified")}</StatusChip>
              ) : (
                <StatusChip tone="warning">{t("unverified")}</StatusChip>
              )}
            </>
          ) : (
            <span className="text-body text-text-muted">{t("none")}</span>
          )}
        </div>
        <Button
          type="button"
          variant="secondary"
          aria-expanded={changing}
          aria-controls={changeId}
          onClick={() => (changing ? setChanging(false) : openChangeForm())}
        >
          {t("change")}
        </Button>
      </div>

      {changing && (
        <form
          id={changeId}
          noValidate
          aria-labelledby={`${changeId}-title`}
          onSubmit={changeForm.handleSubmit(onChangeSubmit)}
          className="flex flex-col gap-3 rounded-lg border border-border bg-page p-4"
        >
          <h3 id={`${changeId}-title`} className="text-title font-semibold text-text">
            {t("changeTitle")}
          </h3>
          {changeAlert && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
            >
              <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{t(`changeErrors.${changeAlert}`)}</span>
            </div>
          )}
          <FormField label={t("newEmailLabel")} error={changeError(changeForm.formState.errors.newEmail?.message)} required>
            {(field) => (
              <Input
                {...field}
                {...changeForm.register("newEmail")}
                size="lg"
                type="email"
                required
                autoComplete="email"
                className={touchFieldText}
              />
            )}
          </FormField>
          <FormField
            label={t("currentPasswordLabel")}
            error={changeError(changeForm.formState.errors.currentPassword?.message)}
            required
          >
            {(field) => (
              <PasswordField
                {...field}
                {...changeForm.register("currentPassword")}
                size="lg"
                required
                autoComplete="current-password"
                className={touchFieldText}
                labels={passwordToggle}
              />
            )}
          </FormField>
          <div className="flex justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => setChanging(false)}>
              {t("cancel")}
            </Button>
            <Button type="submit" loading={changeForm.formState.isSubmitting} disabled={secondsLeft > 0}>
              {t("sendCode")}
            </Button>
          </div>
          {/* Thời gian chờ là chữ thường bên ngoài nút khóa: chữ trong nút khóa bị nhạt, dưới 4.5:1. */}
          {secondsLeft > 0 && secondsLeft < LONG_COOLDOWN_SECONDS && (
            <p className="text-body text-text-muted tabular-nums">{t("sendCodeIn", { time: formatClock(secondsLeft) })}</p>
          )}
        </form>
      )}

      {profile.pendingEmail && (
        <form
          noValidate
          aria-labelledby="profile-pending-title"
          onSubmit={codeForm.handleSubmit(onConfirmSubmit)}
          className="flex flex-col gap-3 rounded-lg border border-warning/30 bg-warning-bg p-4"
        >
          <div className="flex items-start gap-2.5">
            <Clock aria-hidden className="mt-1 size-4.5 shrink-0 text-warning" />
            <div className="flex min-w-0 flex-col gap-1">
              <h3 id="profile-pending-title" className="text-label font-semibold break-all text-warning">
                {t("pendingTitle", { email: profile.pendingEmail })}
              </h3>
              <p className="text-body text-text-secondary">{t("pendingBody")}</p>
            </div>
          </div>

          {confirmAlert && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
            >
              <CircleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0" />
              <span>{t(`confirmErrors.${confirmAlert}`)}</span>
            </div>
          )}

          <div className="flex flex-wrap items-end gap-3">
            <FormField
              className="min-w-0 flex-1 basis-55"
              label={t("codeLabel")}
              error={codeError(codeForm.formState.errors.code?.message)}
              required
            >
              {(field) => (
                <Input
                  {...field}
                  {...codeForm.register("code")}
                  size="lg"
                  type="text"
                  required
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  className={cn("tracking-[0.3em] tabular-nums", touchFieldText)}
                  placeholder={t("codePlaceholder")}
                />
              )}
            </FormField>
            <Button type="submit" size="lg" loading={codeForm.formState.isSubmitting}>
              {t("confirm")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="lg"
              disabled={secondsLeft > 0}
              onClick={() => openChangeForm(profile.pendingEmail ?? undefined)}
              className="tabular-nums"
            >
              {t("resend")}
            </Button>
          </div>
          {/* Thời gian chờ là chữ thường bên ngoài nút khóa: chữ trong nút khóa bị nhạt, dưới 4.5:1. */}
          {secondsLeft > 0 && secondsLeft < LONG_COOLDOWN_SECONDS && (
            <p className="text-body text-text-muted tabular-nums">{t("resendIn", { time: formatClock(secondsLeft) })}</p>
          )}
        </form>
      )}

      <LiveStatus
        message={cooldownAnnouncement(cooldown, {
          start: (time) => t("cooldownStart", { time }),
          end: t("cooldownEnd"),
        })}
      />
    </section>
  );
}
