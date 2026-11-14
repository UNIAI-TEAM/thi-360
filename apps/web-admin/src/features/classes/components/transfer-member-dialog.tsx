"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  notify,
  Select,
  Textarea,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  CLASSES_QUERY_KEYS,
  type ClassMember,
  type ClassSummary,
  listAllClasses as defaultListAllClasses,
  transferClassMember as defaultTransferClassMember,
  type TransferClassMemberResponse,
} from "../api";
import {
  createTransferMemberSchema,
  type TransferMemberFormValues,
} from "../schema";
import { resolveClassesErrorMessage } from "../utils";

export interface TransferMemberDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  sourceClassId: string;
  sourceClassCode: string;
  member: ClassMember | null;
  onTransferred: (result: TransferClassMemberResponse) => void;
  listAllClasses?: () => Promise<ClassSummary[]>;
  transferClassMember?: typeof defaultTransferClassMember;
}

export function TransferMemberDialog({
  open,
  onOpenChange,
  sourceClassId,
  sourceClassCode,
  member,
  onTransferred,
  listAllClasses = defaultListAllClasses,
  transferClassMember = defaultTransferClassMember,
}: TransferMemberDialogProps) {
  const t = useTranslations("classes");
  const [submitting, setSubmitting] = useState(false);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!submitting) onOpenChange(nextOpen);
      }}
    >
      <DialogContent
        size="md"
        closeLabel={t("transferDialog.close")}
        className="flex max-h-[85vh] flex-col overflow-hidden"
      >
        <DialogHeader className="shrink-0">
          <DialogTitle>{t("transferDialog.title")}</DialogTitle>
          <DialogDescription>
            {member
              ? t("transferDialog.description", {
                  username: member.username,
                  sourceCode: sourceClassCode,
                })
              : ""}
          </DialogDescription>
        </DialogHeader>

        {open && member && (
          <TransferMemberBody
            key={`${sourceClassId}-${member.userId}`}
            sourceClassId={sourceClassId}
            member={member}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onTransferred={(res) => {
              onOpenChange(false);
              onTransferred(res);
            }}
            listAllClasses={listAllClasses}
            transferClassMember={transferClassMember}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function TransferMemberBody({
  sourceClassId,
  member,
  onCancel,
  onSubmittingChange,
  onTransferred,
  listAllClasses,
  transferClassMember,
}: {
  sourceClassId: string;
  member: ClassMember;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onTransferred: (result: TransferClassMemberResponse) => void;
  listAllClasses: () => Promise<ClassSummary[]>;
  transferClassMember: typeof defaultTransferClassMember;
}) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const [submitError, setSubmitError] = useState<string | null>(null);

  const classesQuery = useQuery({
    queryKey: CLASSES_QUERY_KEYS.options(),
    queryFn: () => listAllClasses(),
  });

  const schema = useMemo(
    () =>
      createTransferMemberSchema({
        targetClassRequired: t("validation.targetClassRequired"),
        reasonMax: t("validation.reasonMax"),
      }),
    [t],
  );

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<TransferMemberFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      targetClassId: "",
      reason: "",
    },
  });

  const { targetOptions, hasInactiveOptions, hasActiveOptions } = useMemo(() => {
    const others = (classesQuery.data ?? []).filter((item) => item.id !== sourceClassId);
    const hasInactive = others.some((item) => item.status === "INACTIVE");
    const hasActive = others.some((item) => item.status === "ACTIVE");
    const options = others.map((item) => ({
      value: item.id,
      label:
        item.status === "INACTIVE"
          ? `${item.code} — ${item.name} (${t("statuses.INACTIVE")})`
          : `${item.code} — ${item.name}`,
      disabled: item.status === "INACTIVE",
    }));
    return {
      targetOptions: options,
      hasInactiveOptions: hasInactive,
      hasActiveOptions: hasActive,
    };
  }, [classesQuery.data, sourceClassId, t]);

  const mutation = useMutation({
    mutationFn: (values: TransferMemberFormValues) => {
      onSubmittingChange(true);
      return transferClassMember(sourceClassId, member.userId, {
        targetClassId: values.targetClassId,
        ...(values.reason ? { reason: values.reason } : {}),
      });
    },
    onSettled: () => {
      onSubmittingChange(false);
    },
    onSuccess: (res) => {
      notify.success(t("transferDialog.success", { username: member.username }));
      onTransferred(res);
    },
    onError: (error) => {
      const msg = resolveClassesErrorMessage(
        error,
        (code) => t(`errorCodes.${code}`),
        t("errorCodes.INTERNAL_ERROR"),
      );
      setSubmitError(msg);
      notify.error(msg);
    },
  });

  return (
    <form
      onSubmit={handleSubmit((values) => {
        setSubmitError(null);
        mutation.mutate(values);
      })}
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
          label={t("transferDialog.targetClassLabel")}
          required
          error={errors.targetClassId?.message}
          hint={
            classesQuery.isLoading
              ? t("transferDialog.loadingClasses")
              : classesQuery.isError
                ? undefined
                : !hasActiveOptions
                  ? t("transferDialog.noActiveTargetClasses")
                  : hasInactiveOptions
                    ? t("transferDialog.inactiveDisabledHint")
                    : undefined
          }
        >
          {(controlProps) => (
            <div className="flex flex-col gap-2">
              <Controller
                control={control}
                name="targetClassId"
                render={({ field }) => (
                  <Select
                    {...controlProps}
                    value={field.value}
                    onValueChange={(value) => {
                      setSubmitError(null);
                      field.onChange(value);
                    }}
                    placeholder={t("transferDialog.targetClassPlaceholder")}
                    options={targetOptions}
                    disabled={
                      mutation.isPending ||
                      classesQuery.isLoading ||
                      classesQuery.isError ||
                      targetOptions.length === 0
                    }
                  />
                )}
              />
              {classesQuery.isError && (
                <div
                  role="alert"
                  className="flex items-center justify-between gap-2 rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
                >
                  <span>{t("transferDialog.errorDescription")}</span>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => void classesQuery.refetch()}
                  >
                    {common("retry")}
                  </Button>
                </div>
              )}
            </div>
          )}
        </FormField>

        <FormField label={t("transferDialog.reasonLabel")} error={errors.reason?.message}>
          {(controlProps) => (
            <Textarea
              {...controlProps}
              {...register("reason", {
                onChange: () => setSubmitError(null),
              })}
              placeholder={t("transferDialog.reasonPlaceholder")}
              rows={3}
              disabled={mutation.isPending}
            />
          )}
        </FormField>
      </div>

      <DialogFooter className="shrink-0 pt-2">
        <Button
          type="button"
          variant="secondary"
          disabled={mutation.isPending}
          onClick={onCancel}
        >
          {t("transferDialog.cancel")}
        </Button>
        <Button
          type="submit"
          loading={mutation.isPending}
          disabled={classesQuery.isLoading || classesQuery.isError || !hasActiveOptions}
        >
          {t("transferDialog.submit")}
        </Button>
      </DialogFooter>
    </form>
  );
}
