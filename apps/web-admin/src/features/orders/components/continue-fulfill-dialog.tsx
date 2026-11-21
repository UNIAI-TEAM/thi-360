"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { type FormEvent, useRef, useState } from "react";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  Textarea,
  notify,
} from "@thi360/ui";

import { ApiStatusError } from "@/lib/api/query-client";

import { orderFieldErrors, resolveOrderErrorMessage } from "../errors";
import { orderKeys } from "../keys";
import { useRecoverOrderMutation } from "../queries";
import type { AdminOrderDetailResponse } from "../types";

export interface ContinueFulfillDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  order: AdminOrderDetailResponse;
}

function ContinueFulfillDialogBody({
  order,
  onClose,
  onBusyChange,
}: {
  order: AdminOrderDetailResponse;
  onClose: () => void;
  onBusyChange: (busy: boolean) => void;
}) {
  const t = useTranslations("orders.dialogs.continueFulfill");
  const tDialogs = useTranslations("orders.dialogs");
  const tErrors = useTranslations("orders.errors");
  const queryClient = useQueryClient();

  const [reason, setReason] = useState("");
  const [reasonErrorKey, setReasonErrorKey] = useState<"reasonRequired" | null>(null);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const idempotencyRef = useRef<{ fingerprint: string; key: string } | null>(null);
  const submittingRef = useRef(false);

  const mutation = useRecoverOrderMutation(order.code);

  const reasonError = reasonErrorKey ? t(reasonErrorKey) : undefined;
  const submitErrorMessage = submitError
    ? resolveOrderErrorMessage(submitError, tErrors)
    : undefined;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submittingRef.current || mutation.isPending) return;

    const trimmed = reason.trim();
    if (!trimmed) {
      setReasonErrorKey("reasonRequired");
      reasonRef.current?.focus();
      return;
    }

    const fingerprint = `${order.code}|${trimmed}`;
    if (idempotencyRef.current?.fingerprint !== fingerprint) {
      idempotencyRef.current = { fingerprint, key: crypto.randomUUID() };
    }

    setReasonErrorKey(null);
    setSubmitError(null);
    submittingRef.current = true;
    onBusyChange(true);

    try {
      await mutation.mutateAsync({
        body: { reason: trimmed },
        idempotencyKey: idempotencyRef.current.key,
      });
      notify.success(t("successToast", { code: order.code }));
      onClose();
    } catch (error) {
      const fields = orderFieldErrors(error);
      if (fields.some((f) => f.field === "reason")) {
        setReasonErrorKey("reasonRequired");
        reasonRef.current?.focus();
      }
      if (error instanceof ApiStatusError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(order.code) });
        void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.buyersAll() });
      }
      setSubmitError(error);
    } finally {
      submittingRef.current = false;
      onBusyChange(false);
    }
  };

  return (
    <form onSubmit={(e) => void handleSubmit(e)} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>{t("title")}</DialogTitle>
        <DialogDescription>
          {t.rich("description", {
            code: order.code,
            productName: order.productName,
            username: order.buyer.username,
            exam: (chunks) => (
              <span data-exam-content className="font-semibold text-text">
                {chunks}
              </span>
            ),
          })}
        </DialogDescription>
      </DialogHeader>

      <p className="rounded-lg border border-border bg-subtle px-3 py-2 text-xs font-medium text-text-secondary">
        {t("attemptsSummary", { attempts: order.attemptsGranted })}
      </p>

      {submitErrorMessage ? (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2.5 text-sm text-danger"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{submitErrorMessage}</span>
        </div>
      ) : null}

      <FormField label={t("reasonLabel")} required error={reasonError}>
        {({ id, ...a11y }) => (
          <Textarea
            {...a11y}
            id={id}
            ref={reasonRef}
            rows={3}
            maxLength={1000}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              if (reasonErrorKey) setReasonErrorKey(null);
            }}
            placeholder={t("reasonPlaceholder")}
          />
        )}
      </FormField>

      <DialogFooter>
        <Button
          type="button"
          variant="secondary"
          disabled={mutation.isPending}
          onClick={onClose}
        >
          {tDialogs("cancel")}
        </Button>
        <Button type="submit" variant="primary" loading={mutation.isPending}>
          {t("confirm")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export function ContinueFulfillDialog({
  open,
  onOpenChange,
  order,
}: ContinueFulfillDialogProps) {
  const [busy, setBusy] = useState(false);

  return (
    <Dialog open={open} onOpenChange={(next) => !busy && onOpenChange(next)}>
      <DialogContent>
        {open ? (
          <ContinueFulfillDialogBody
            order={order}
            onClose={() => onOpenChange(false)}
            onBusyChange={setBusy}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
