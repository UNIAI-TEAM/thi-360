"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
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
  Input,
  Textarea,
  cn,
  focusRing,
  notify,
} from "@thi360/ui";

import { orderErrorCode, resolveOrderErrorMessage } from "../errors";
import { formatVnd } from "../format";
import { orderKeys } from "../keys";
import { useReviewLateSuccessMutation } from "../queries";
import type {
  AdminOrderDetailResponse,
  LateSuccessDecision,
  PaymentDetailView,
} from "../types";
import { handleRadioGroupKeyDown } from "./radio-group-key";

export interface LateSuccessDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  order: AdminOrderDetailResponse;
  payment: PaymentDetailView;
}

function LateSuccessDialogBody({
  order,
  payment,
  onClose,
  onBusyChange,
}: {
  order: AdminOrderDetailResponse;
  payment: PaymentDetailView;
  onClose: () => void;
  onBusyChange: (busy: boolean) => void;
}) {
  const t = useTranslations("orders.dialogs.lateSuccess");
  const tDialogs = useTranslations("orders.dialogs");
  const tErrors = useTranslations("orders.errors");
  const locale = useLocale();
  const queryClient = useQueryClient();

  const [decision, setDecision] = useState<LateSuccessDecision>("ACCEPT");
  const [evidenceRef, setEvidenceRef] = useState("");
  const [reason, setReason] = useState("");
  const [evidenceError, setEvidenceError] = useState<string | undefined>();
  const [reasonError, setReasonError] = useState<string | undefined>();
  const [submitError, setSubmitError] = useState<string | undefined>();

  const evidenceInputRef = useRef<HTMLInputElement>(null);
  const reasonInputRef = useRef<HTMLTextAreaElement>(null);
  const idempotencyRef = useRef<{ fingerprint: string; key: string } | null>(null);
  const submittingRef = useRef(false);

  const mutation = useReviewLateSuccessMutation(order.code);

  const decisionOptions: ReadonlyArray<{
    value: LateSuccessDecision;
    label: string;
    hint: string;
  }> = [
    {
      value: "ACCEPT",
      label: t("acceptOption"),
      hint: t("acceptHint"),
    },
    {
      value: "REFUND",
      label: t("refundOption"),
      hint: t("refundHint"),
    },
  ];

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submittingRef.current || mutation.isPending) return;

    const trimmedEvidence = evidenceRef.trim();
    const trimmedReason = reason.trim();

    if (!trimmedEvidence) {
      setEvidenceError(t("evidenceRequired"));
      evidenceInputRef.current?.focus();
      return;
    }
    if (!trimmedReason) {
      setReasonError(t("reasonRequired"));
      reasonInputRef.current?.focus();
      return;
    }

    const fingerprint = `${payment.id}|${decision}|${trimmedEvidence}|${trimmedReason}`;
    if (idempotencyRef.current?.fingerprint !== fingerprint) {
      idempotencyRef.current = { fingerprint, key: crypto.randomUUID() };
    }

    setEvidenceError(undefined);
    setReasonError(undefined);
    setSubmitError(undefined);
    submittingRef.current = true;
    onBusyChange(true);

    try {
      await mutation.mutateAsync({
        paymentId: payment.id,
        body: {
          decision,
          evidenceRef: trimmedEvidence,
          reason: trimmedReason,
        },
        idempotencyKey: idempotencyRef.current.key,
      });
      notify.success(decision === "ACCEPT" ? t("successAcceptToast") : t("successRefundToast"));
      onClose();
    } catch (error) {
      const code = orderErrorCode(error);
      if (code === "LATE_SUCCESS_ALREADY_RESOLVED") {
        setSubmitError(t("alreadyResolvedError"));
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(order.code) });
        void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      } else {
        setSubmitError(resolveOrderErrorMessage(error, tErrors));
      }
    } finally {
      submittingRef.current = false;
      onBusyChange(false);
    }
  };

  return (
    <form onSubmit={(e) => void handleSubmit(e)} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>{t("title")}</DialogTitle>
        <DialogDescription>{t("description")}</DialogDescription>
      </DialogHeader>

      <div className="rounded-lg border border-warning/40 bg-warning-bg px-3 py-2 text-sm font-medium text-text">
        {t("paymentInfo", {
          txnRef: payment.txnRef,
          amount: formatVnd(payment.amountVnd, locale),
        })}
      </div>

      {submitError ? (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2.5 text-sm text-danger"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{submitError}</span>
        </div>
      ) : null}

      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium text-text">{t("decisionLabel")}</legend>
        <div
          role="radiogroup"
          aria-label={t("decisionLabel")}
          className="grid gap-2.5 sm:grid-cols-2"
        >
          {decisionOptions.map((opt) => {
            const selected = decision === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                role="radio"
                aria-checked={selected}
                tabIndex={selected ? 0 : -1}
                onClick={() => setDecision(opt.value)}
                onKeyDown={(e) => handleRadioGroupKeyDown(e, decision, decisionOptions, setDecision)}
                className={cn(
                  "flex flex-col items-start gap-1 rounded-lg border p-3 text-left transition-colors",
                  focusRing,
                  selected
                    ? "border-primary-600 bg-subtle text-text"
                    : "border-border bg-surface text-text-secondary hover:border-border-strong",
                )}
              >
                <span className="text-sm font-semibold text-text">{opt.label}</span>
                <span className="text-xs text-text-secondary">{opt.hint}</span>
              </button>
            );
          })}
        </div>
      </fieldset>

      <FormField label={t("evidenceLabel")} required error={evidenceError}>
        {({ id, ...a11y }) => (
          <Input
            {...a11y}
            id={id}
            ref={evidenceInputRef}
            maxLength={200}
            value={evidenceRef}
            onChange={(e) => {
              setEvidenceRef(e.target.value);
              if (evidenceError) setEvidenceError(undefined);
            }}
            placeholder={t("evidencePlaceholder")}
          />
        )}
      </FormField>

      <FormField label={t("reasonLabel")} required error={reasonError}>
        {({ id, ...a11y }) => (
          <Textarea
            {...a11y}
            id={id}
            ref={reasonInputRef}
            rows={3}
            maxLength={1000}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              if (reasonError) setReasonError(undefined);
            }}
            placeholder={t("reasonPlaceholder")}
          />
        )}
      </FormField>

      <DialogFooter>
        <Button type="button" variant="secondary" disabled={mutation.isPending} onClick={onClose}>
          {tDialogs("cancel")}
        </Button>
        <Button type="submit" variant="primary" loading={mutation.isPending}>
          {mutation.isPending ? tDialogs("submitting") : t("confirm")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export function LateSuccessDialog({
  open,
  onOpenChange,
  order,
  payment,
}: LateSuccessDialogProps) {
  const [busy, setBusy] = useState(false);

  return (
    <Dialog open={open} onOpenChange={(next) => !busy && onOpenChange(next)}>
      <DialogContent size="lg">
        {open ? (
          <LateSuccessDialogBody
            key={payment.id}
            order={order}
            payment={payment}
            onClose={() => onOpenChange(false)}
            onBusyChange={setBusy}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
