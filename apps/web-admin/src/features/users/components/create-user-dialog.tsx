"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  Input,
  Label,
  notify,
  PasswordField,
  Select,
} from "@thi360/ui";
import { useMutation } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useId, useMemo, useRef, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { usePermissions } from "@/features/session/session-provider";
import {
  checkUsernameAvailability as defaultCheckUsernameAvailability,
  createUser as defaultCreateUser,
  type CreateUserRequest,
  type RoleSummary,
  type UserDetail,
  UsersApiError,
} from "../api";
import { createCreateUserSchema, type CreateUserFormValues } from "../schema";
import { formatRoleLabel, mapUserErrorCodeToKey, resolveUsersErrorMessage } from "../utils";

const DEFAULT_STUDENT_ROLE_VALUE = "__DEFAULT_STUDENT__";

export interface CreateUserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  roles: RoleSummary[];
  onCreated: (user: UserDetail) => void;
  createUser?: typeof defaultCreateUser;
  checkUsernameAvailability?: typeof defaultCheckUsernameAvailability;
}

export function CreateUserDialog({
  open,
  onOpenChange,
  roles,
  onCreated,
  createUser = defaultCreateUser,
  checkUsernameAvailability = defaultCheckUsernameAvailability,
}: CreateUserDialogProps) {
  const t = useTranslations("users");
  const [submitting, setSubmitting] = useState(false);

  return (
    <Dialog open={open} onOpenChange={(next) => !submitting && onOpenChange(next)}>
      <DialogContent
        size="md"
        closeLabel={t("createDialog.close")}
        onEscapeKeyDown={(event) => {
          if (submitting) event.preventDefault();
        }}
        onPointerDownOutside={(event) => {
          if (submitting) event.preventDefault();
        }}
      >
        <DialogHeader>
          <DialogTitle>{t("createDialog.title")}</DialogTitle>
          <DialogDescription>{t("createDialog.description")}</DialogDescription>
        </DialogHeader>

        {open && (
          <CreateUserFormBody
            roles={roles}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onCreated={(user) => {
              onOpenChange(false);
              onCreated(user);
            }}
            createUser={createUser}
            checkUsernameAvailability={checkUsernameAvailability}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function CreateUserFormBody({
  roles,
  onCancel,
  onSubmittingChange,
  onCreated,
  createUser,
  checkUsernameAvailability,
}: {
  roles: RoleSummary[];
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onCreated: (user: UserDetail) => void;
  createUser: typeof defaultCreateUser;
  checkUsernameAvailability: typeof defaultCheckUsernameAvailability;
}) {
  const t = useTranslations("users");
  const rbacT = useTranslations("rbac");
  const { can } = usePermissions();
  const canAssignRole = can("USER.ASSIGN") && can("ROLE.VIEW");
  const mustChangeId = useId();

  const [usernameStatus, setUsernameStatus] = useState<
    | { state: "idle" }
    | { state: "checking"; normalized: string }
    | { state: "available"; normalized: string }
    | { state: "unavailable"; normalized: string; message: string }
  >({ state: "idle" });
  const [submitError, setSubmitError] = useState<string | null>(null);
  const latestUsernameCheckRef = useRef<string>("");

  const schema = useMemo(
    () =>
      createCreateUserSchema({
        usernameRequired: t("validation.usernameRequired"),
        usernameInvalid: t("validation.usernameInvalid"),
        usernameReserved: t("validation.usernameReserved"),
        passwordWeak: t("validation.passwordWeak"),
        passwordSameAsUsername: t("validation.passwordSameAsUsername"),
        fullNameRequired: t("validation.fullNameRequired"),
        fullNameMax: t("validation.fullNameMax"),
        emailInvalid: t("validation.emailInvalid"),
        emailMax: t("validation.emailMax"),
        phoneMax: t("validation.phoneMax"),
      }),
    [t],
  );

  const defaultRoleOption = useMemo(() => {
    const student = roles.find((r) => r.code.toLowerCase() === "student");
    return student?.id ?? DEFAULT_STUDENT_ROLE_VALUE;
  }, [roles]);

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    clearErrors,
    getValues,
    formState: { errors },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      username: "",
      password: "",
      fullName: "",
      email: "",
      phone: "",
      roleId: defaultRoleOption,
      mustChangePassword: false,
    },
  });

  const mutation = useMutation({
    mutationFn: (payload: CreateUserRequest) => {
      onSubmittingChange(true);
      return createUser(payload);
    },
    onSettled: () => {
      onSubmittingChange(false);
    },
    onSuccess: (created) => {
      notify.success(t("createDialog.success", { username: created.username }));
      reset();
      onCreated(created);
    },
    onError: (error) => {
      if (error instanceof UsersApiError) {
        let appliedFieldError = false;
        if (
          error.code === "USERNAME_TAKEN" ||
          error.code === "USERNAME_INVALID" ||
          error.code === "USERNAME_RESERVED" ||
          error.code === "USERNAME_REQUIRED"
        ) {
          const key = mapUserErrorCodeToKey(error.code);
          if (key) {
            setError("username", { message: t(`errorCodes.${key}`) });
            appliedFieldError = true;
          }
        } else if (error.code === "EMAIL_TAKEN" || error.code === "EMAIL_INVALID") {
          const key = mapUserErrorCodeToKey(error.code);
          if (key) {
            setError("email", { message: t(`errorCodes.${key}`) });
            appliedFieldError = true;
          }
        } else if (error.code === "PASSWORD_WEAK") {
          const fieldKey = mapUserErrorCodeToKey(error.fieldErrors[0]?.code) ?? "PASSWORD_WEAK";
          setError("password", { message: t(`errorCodes.${fieldKey}`) });
          appliedFieldError = true;
        }

        for (const fe of error.fieldErrors) {
          const mappedKey = mapUserErrorCodeToKey(fe.code);
          const msg = mappedKey ? t(`errorCodes.${mappedKey}`) : t("errorCodes.VALIDATION_FAILED");
          if (
            fe.field === "username" ||
            fe.field === "password" ||
            fe.field === "fullName" ||
            fe.field === "email" ||
            fe.field === "phone"
          ) {
            setError(fe.field, { message: msg });
            appliedFieldError = true;
          }
        }

        if (appliedFieldError) {
          setSubmitError(null);
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

  async function handleUsernameBlur() {
    const raw = getValues("username").trim();
    if (!raw) {
      latestUsernameCheckRef.current = "";
      setUsernameStatus({ state: "idle" });
      return;
    }

    const normalized = raw.toLowerCase();
    latestUsernameCheckRef.current = normalized;
    setUsernameStatus({ state: "checking", normalized });

    try {
      const result = await checkUsernameAvailability(raw);
      if (latestUsernameCheckRef.current !== normalized) return;
      if (result.available) {
        clearErrors("username");
        setUsernameStatus({ state: "available", normalized });
      } else {
        const key = mapUserErrorCodeToKey(result.reasonCode) ?? "USERNAME_TAKEN";
        const message = t(`errorCodes.${key}`);
        setError("username", { message });
        setUsernameStatus({ state: "unavailable", normalized, message });
      }
    } catch {
      if (latestUsernameCheckRef.current !== normalized) return;
      setUsernameStatus({ state: "idle" });
    }
  }

  function handleFormSubmit(values: CreateUserFormValues) {
    setSubmitError(null);
    const email = values.email.trim();
    const phone = values.phone.trim();
    const selectedRole = roles.find((r) => r.id === values.roleId);
    const isDefaultStudent =
      !canAssignRole ||
      !values.roleId ||
      values.roleId === DEFAULT_STUDENT_ROLE_VALUE ||
      selectedRole?.code.toLowerCase() === "student";

    const payload: CreateUserRequest = {
      username: values.username.trim(),
      password: values.password,
      fullName: values.fullName.trim(),
      mustChangePassword: values.mustChangePassword,
      ...(email ? { email } : {}),
      ...(phone ? { phone } : {}),
      ...(!isDefaultStudent && values.roleId ? { roleIds: [values.roleId] } : {}),
    };

    mutation.mutate(payload);
  }

  const roleSelectOptions = useMemo(() => {
    if (roles.length === 0) {
      return [{ value: DEFAULT_STUDENT_ROLE_VALUE, label: t("createDialog.roleDefaultValue") }];
    }
    return roles.map((role) => ({
      value: role.id,
      label: formatRoleLabel(role, (code) => rbacT(`systemRoles.${code}`)),
    }));
  }, [roles, t, rbacT]);

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
        label={t("createDialog.usernameLabel")}
        required
        error={errors.username?.message}
        hint={
          usernameStatus.state === "checking"
            ? t("createDialog.usernameChecking")
            : usernameStatus.state === "available"
              ? undefined
              : t("createDialog.usernameHint")
        }
      >
        {(controlProps) => (
          <div className="flex flex-col gap-1">
            <Controller
              control={control}
              name="username"
              render={({ field }) => (
                <Input
                  {...controlProps}
                  ref={field.ref}
                  name={field.name}
                  value={field.value}
                  onChange={(event) => {
                    latestUsernameCheckRef.current = "";
                    setUsernameStatus({ state: "idle" });
                    setSubmitError(null);
                    field.onChange(event);
                  }}
                  onBlur={() => {
                    field.onBlur();
                    void handleUsernameBlur();
                  }}
                  autoComplete="off"
                  placeholder={t("createDialog.usernamePlaceholder")}
                  disabled={mutation.isPending}
                />
              )}
            />
            {usernameStatus.state === "available" && !errors.username && (
              <p className="text-caption font-medium text-primary-text">
                {t("createDialog.usernameAvailable")}
              </p>
            )}
          </div>
        )}
      </FormField>

      <FormField
        label={t("createDialog.passwordLabel")}
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
                value={field.value}
                onChange={(event) => {
                  setSubmitError(null);
                  field.onChange(event);
                }}
                onBlur={field.onBlur}
                autoComplete="new-password"
                placeholder={t("createDialog.passwordPlaceholder")}
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

      <FormField
        label={t("createDialog.fullNameLabel")}
        required
        error={errors.fullName?.message}
      >
        {(controlProps) => (
          <Input
            {...controlProps}
            {...register("fullName", {
              onChange: () => setSubmitError(null),
            })}
            placeholder={t("createDialog.fullNamePlaceholder")}
            disabled={mutation.isPending}
          />
        )}
      </FormField>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FormField label={t("createDialog.emailLabel")} error={errors.email?.message}>
          {(controlProps) => (
            <Input
              {...controlProps}
              type="email"
              {...register("email", {
                onChange: () => setSubmitError(null),
              })}
              placeholder={t("createDialog.emailPlaceholder")}
              disabled={mutation.isPending}
            />
          )}
        </FormField>

        <FormField label={t("createDialog.phoneLabel")} error={errors.phone?.message}>
          {(controlProps) => (
            <Input
              {...controlProps}
              type="tel"
              {...register("phone", {
                onChange: () => setSubmitError(null),
              })}
              placeholder={t("createDialog.phonePlaceholder")}
              disabled={mutation.isPending}
            />
          )}
        </FormField>
      </div>

      {canAssignRole ? (
        <FormField
          label={t("createDialog.roleLabel")}
          hint={t("createDialog.roleHint")}
          error={errors.roleId?.message}
        >
          {(controlProps) => (
            <Controller
              control={control}
              name="roleId"
              render={({ field }) => (
                <Select
                  {...controlProps}
                  value={field.value || defaultRoleOption}
                  onValueChange={field.onChange}
                  options={roleSelectOptions}
                  disabled={mutation.isPending}
                />
              )}
            />
          )}
        </FormField>
      ) : (
        <FormField
          label={t("createDialog.roleLabel")}
          hint={t("createDialog.roleHintStudentOnly")}
        >
          {(controlProps) => (
            <Input
              {...controlProps}
              value={t("createDialog.roleDefaultValue")}
              readOnly
              disabled
            />
          )}
        </FormField>
      )}

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
          {t("createDialog.mustChangePasswordLabel")}
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
          {t("createDialog.cancel")}
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          {t("createDialog.submit")}
        </Button>
      </DialogFooter>
    </form>
  );
}
