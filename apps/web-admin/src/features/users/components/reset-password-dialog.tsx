"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import {
  Button,
  Checkbox,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  Label,
  notify,
  PasswordField,
  Textarea,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  resetUserPassword as defaultResetUserPassword,
  type ResetPasswordRequest,
  type UserDetail,
  UsersApiError,
} from "../api";
import { createResetPasswordSchema, type ResetPasswordFormValues, USER_FIELD_MAX_LENGTH } from "../schema";
import { mapUserErrorCodeToKey, resolveUsersErrorMessage } from "../utils";

export interface ResetPasswordTarget {
  id: string;
  username: string;
}

export interface ResetPasswordDialogProps {
  target: ResetPasswordTarget | null;
  onClose: () => void;
  onSuccess: (updated: UserDetail) => void;
  resetUserPassword?: typeof defaultResetUserPassword;
}

export function ResetPasswordDialog({
  target,
  onClose,
  onSuccess,
  resetUserPassword = defaultResetUserPassword,
}: ResetPasswordDialogProps) {
  const t = useTranslations("users");
  const [submitting, setSubmitting] = useState(false);
  const [discardOpen, setDiscardOpen] = useState(false);
  // Biểu mẫu con báo đã có dữ liệu hay chưa; chỉ đọc trong xử lý sự kiện đóng nên giữ bằng ref, không cần render lại.
  const dirtyRef = useRef(false);
  const handleDirtyChange = useCallback((dirty: boolean) => {
    dirtyRef.current = dirty;
  }, []);

  function requestClose() {
    if (submitting) return;
    if (dirtyRef.current) {
      setDiscardOpen(true);
      return;
    }
    onClose();
  }

  return (
    <>
    <Dialog open={target !== null} onOpenChange={(next) => !next && requestClose()}>
      <DialogContent
        size="md"
        closeLabel={t("resetPasswordDialog.close")}
        onEscapeKeyDown={(event) => {
          if (submitting) event.preventDefault();
        }}
        onPointerDownOutside={(event) => {
          if (submitting || dirtyRef.current) event.preventDefault();
        }}
      >
        <DialogHeader>
          <DialogTitle>{t("resetPasswordDialog.title")}</DialogTitle>
          <DialogDescription>
            {target ? t("resetPasswordDialog.description", { username: target.username }) : ""}
          </DialogDescription>
        </DialogHeader>

        {target && (
          <ResetPasswordFormBody
            target={target}
            onCancel={requestClose}
            onSubmittingChange={setSubmitting}
            onDirtyChange={handleDirtyChange}
            onSuccess={(updated) => {
              onClose();
              onSuccess(updated);
            }}
            resetUserPassword={resetUserPassword}
          />
        )}
      </DialogContent>
    </Dialog>
      {/* Biểu mẫu đã có dữ liệu: Esc, nút × và Hủy hỏi lại trước khi bỏ; bấm ra ngoài thì không đóng. */}
      <ConfirmDialog
        open={discardOpen}
        onOpenChange={setDiscardOpen}
        title={t("discardDialog.title")}
        description={t("discardDialog.description")}
        confirmLabel={t("discardDialog.confirm")}
        cancelLabel={t("discardDialog.cancel")}
        tone="danger"
        onConfirm={() => {
          setDiscardOpen(false);
          dirtyRef.current = false;
          onClose();
        }}
      />
    </>
  );
}

function ResetPasswordFormBody({
  target,
  onCancel,
  onSubmittingChange,
  onDirtyChange,
  onSuccess,
  resetUserPassword,
}: {
  target: ResetPasswordTarget;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onDirtyChange: (dirty: boolean) => void;
  onSuccess: (updated: UserDetail) => void;
  resetUserPassword: typeof defaultResetUserPassword;
}) {
  const t = useTranslations("users");
  const mustChangeId = useId();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const schema = useMemo(
    () =>
      createResetPasswordSchema(
        {
          passwordWeak: t("validation.passwordWeak"),
          passwordSameAsUsername: t("validation.passwordSameAsUsername"),
          reasonMax: t("validation.reasonMax"),
        },
        target.username,
      ),
    [t, target.username],
  );

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(schema),
    // Kiểm tra khi rời ô (lần đầu) rồi kiểm tra lại theo từng thay đổi (U-16).
    mode: "onTouched",
    defaultValues: {
      password: "",
      reason: "",
      mustChangePassword: false,
    },
  });

  // Báo cho hộp thoại biết biểu mẫu đã có dữ liệu để hỏi lại trước khi đóng (U-11).
  useEffect(() => {
    onDirtyChange(isDirty);
    return () => onDirtyChange(false);
  }, [isDirty, onDirtyChange]);

  const mutation = useMutation({
    mutationFn: (payload: ResetPasswordRequest) => {
      onSubmittingChange(true);
      return resetUserPassword(target.id, payload);
    },
    onSettled: () => {
      onSubmittingChange(false);
    },
    onSuccess: (updated) => {
      notify.success(t("resetPasswordDialog.success", { username: target.username }));
      reset();
      onSuccess(updated);
    },
    onError: (error) => {
      if (error instanceof UsersApiError) {
        if (error.code === "PASSWORD_WEAK") {
          const fieldKey = mapUserErrorCodeToKey(error.fieldErrors[0]?.code) ?? "PASSWORD_WEAK";
          setError("password", { message: t(`errorCodes.${fieldKey}`) });
          return;
        }
        const reasonError = error.fieldErrors.find((fe) => fe.field === "reason");
        if (reasonError) {
          const mappedKey = mapUserErrorCodeToKey(reasonError.code);
          setError("reason", {
            message: mappedKey ? t(`errorCodes.${mappedKey}`) : t("errorCodes.VALIDATION_FAILED"),
          });
          return;
        }
      }
      setSubmitError(
        resolveUsersErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  const passwordLabels = useMemo(
    () => ({
      showPassword: t("passwordField.showPassword"),
      hidePassword: t("passwordField.hidePassword"),
      generate: t("passwordField.generate"),
      rulesLabel: t("passwordField.rulesLabel"),
      ruleLength: t("passwordField.ruleLength"),
      ruleUpper: t("passwordField.ruleUpper"),
      ruleLower: t("passwordField.ruleLower"),
      ruleDigit: t("passwordField.ruleDigit"),
      ruleSpecial: t("passwordField.ruleSpecial"),
    }),
    [t],
  );

  function handleFormSubmit(values: ResetPasswordFormValues) {
    setSubmitError(null);
    const reason = values.reason.trim();
    mutation.mutate({
      password: values.password,
      mustChangePassword: values.mustChangePassword,
      ...(reason ? { reason } : {}),
    });
  }

  return (
    <form
      onSubmit={handleSubmit(handleFormSubmit)}
      className="flex min-h-0 flex-1 flex-col gap-4"
      noValidate
    >
      <div className="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto pr-1">
      {submitError && (
        <div
          role="alert"
          className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
        >
          {submitError}
        </div>
      )}

      <FormField
        label={t("resetPasswordDialog.passwordLabel")}
        required
        error={errors.password?.message}
      >
        {(controlProps) => (
          <Controller
            control={control}
            name="password"
            render={({ field }) => (
              <PasswordField
                {...controlProps}
                ref={field.ref}
                name={field.name}
                value={field.value}
                onChange={(event) => {
                  setSubmitError(null);
                  field.onChange(event);
                }}
                onBlur={field.onBlur}
                autoComplete="new-password"
                placeholder={t("resetPasswordDialog.passwordPlaceholder")}
                showRules
                allowGenerate
                revealOnGenerate
                labels={passwordLabels}
                disabled={mutation.isPending}
              />
            )}
          />
        )}
      </FormField>

      <FormField label={t("resetPasswordDialog.reasonLabel")} error={errors.reason?.message}>
        {(controlProps) => (
          <Textarea
            {...controlProps}
            {...register("reason", {
              onChange: () => setSubmitError(null),
            })}
            rows={2}
            maxLength={USER_FIELD_MAX_LENGTH.reason}
            placeholder={t("resetPasswordDialog.reasonPlaceholder")}
            disabled={mutation.isPending}
          />
        )}
      </FormField>

      <div className="flex min-h-10 items-center gap-2.5">
        <Controller
          control={control}
          name="mustChangePassword"
          render={({ field }) => (
            <Checkbox
              id={mustChangeId}
              checked={field.value}
              onCheckedChange={(checked) => field.onChange(checked === true)}
              disabled={mutation.isPending}
            />
          )}
        />
        <Label htmlFor={mustChangeId} className="cursor-pointer font-normal text-text">
          {t("resetPasswordDialog.mustChangePasswordLabel")}
        </Label>
      </div>

      </div>
      <DialogFooter className="shrink-0 pt-2">
        <Button
          type="button"
          variant="secondary"
          disabled={mutation.isPending}
          onClick={onCancel}
        >
          {t("resetPasswordDialog.cancel")}
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          {t("resetPasswordDialog.submit")}
        </Button>
      </DialogFooter>
    </form>
  );
}
