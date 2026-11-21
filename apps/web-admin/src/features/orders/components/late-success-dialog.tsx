"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle, Check } from "lucide-react";
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

import { orderErrorCode, orderFieldErrors, resolveOrderErrorMessage } from "../errors";
import { formatVnd } from "../format";
import { orderKeys } from "../keys";
import { useReviewLateSuccessMutation } from "../queries";
import type {
  AdminOrderDetailResponse,
  LateSuccessDecision,
  PaymentDetailView,
} from "../types";
import { handleRadioGroupKeyDown } from "./radio-group-key";

const KNOWN_RESOLUTIONS = new Set(["ACCEPTED", "AUTO_EXCESS", "PENDING", "REJECTED"]);

type SubmitErrorState =
  | { kind: "key"; key: "alreadyResolvedError" }
  | { kind: "api"; error: unknown };

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
  const tResolutions = useTranslations("orders.lateSuccessResolutions");
  const tFallback = useTranslations("orders.statusFallback");
  const locale = useLocale();
  const queryClient = useQueryClient();

  const isResolved = payment.lateSuccessReview !== "PENDING";
  const resolutionLabel =
    payment.lateSuccessReview && KNOWN_RESOLUTIONS.has(payment.lateSuccessReview)
      ? tResolutions(payment.lateSuccessReview)
      : tFallback("unknown");

  const [decision, setDecision] = useState<LateSuccessDecision>("ACCEPT");
  const [evidenceRef, setEvidenceRef] = useState("");
  const [reason, setReason] = useState("");
  const [evidenceErrorKey, setEvidenceErrorKey] = useState<"evidenceRequired" | null>(null);
  const [reasonErrorKey, setReasonErrorKey] = useState<"reasonRequired" | null>(null);
  const [submitError, setSubmitError] = useState<SubmitErrorState | null>(null);

  const evidenceInputRef = useRef<HTMLInputElement>(null);
  const reasonInputRef = useRef<HTMLTextAreaElement>(null);
  const idempotencyRef = useRef<{ fingerprint: string; key: string } | null>(null);
  const submittingRef = useRef(false);

  const mutation = useReviewLateSuccessMutation(order.code);

  const evidenceError = evidenceErrorKey ? t(evidenceErrorKey) : undefined;
  const reasonError = reasonErrorKey ? t(reasonErrorKey) : undefined;
  const submitErrorMessage = submitError
    ? submitError.kind === "key"
      ? t(submitError.key)
      : resolveOrderErrorMessage(submitError.error, tErrors)
    : undefined;

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
    if (isResolved || submittingRef.current || mutation.isPending) return;

    const trimmedEvidence = evidenceRef.trim();
    const trimmedReason = reason.trim();

    if (!trimmedEvidence) {
      setEvidenceErrorKey("evidenceRequired");
      evidenceInputRef.current?.focus();
      return;
    }
    if (!trimmedReason) {
      setReasonErrorKey("reasonRequired");
      reasonInputRef.current?.focus();
      return;
    }

    const fingerprint = `${payment.id}|${decision}|${trimmedEvidence}|${trimmedReason}`;
    if (idempotencyRef.current?.fingerprint !== fingerprint) {
      idempotencyRef.current = { fingerprint, key: crypto.randomUUID() };
    }

    setEvidenceErrorKey(null);
    setReasonErrorKey(null);
    setSubmitError(null);
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
        setSubmitError({ kind: "key", key: "alreadyResolvedError" });
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(order.code) });
        void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.buyersAll() });
      } else {
        const fields = orderFieldErrors(error);
        let focused = false;
        if (fields.some((f) => f.field === "evidenceRef")) {
          setEvidenceErrorKey("evidenceRequired");
          evidenceInputRef.current?.focus();
          focused = true;
        }
        if (fields.some((f) => f.field === "reason")) {
          setReasonErrorKey("reasonRequired");
          if (!focused) reasonInputRef.current?.focus();
        }
        setSubmitError({ kind: "api", error });
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

      <div className="rounded-lg border border-warning/40 bg-warning-bg px-3 py-2 text-sm font-medium wrap-anywhere text-text">
        {t("paymentInfo", {
          txnRef: payment.txnRef,
          amount: formatVnd(payment.amountVnd, locale),
        })}
      </div>

      {isResolved ? (
        <div
          role="status"
          className="rounded-lg border border-border bg-subtle px-3 py-2.5 text-sm text-text"
        >
          {t("resolvedNotice", { resolution: resolutionLabel })}
        </div>
      ) : null}

      {submitErrorMessage ? (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2.5 text-sm text-danger"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{submitErrorMessage}</span>
        </div>
      ) : null}

      <fieldset className="flex flex-col gap-2" disabled={isResolved}>
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
                disabled={isResolved}
                tabIndex={selected ? 0 : -1}
                onClick={() => !isResolved && setDecision(opt.value)}
                onKeyDown={(e) =>
                  !isResolved &&
                  handleRadioGroupKeyDown(e, decision, decisionOptions, setDecision)
                }
                className={cn(
                  "flex flex-col items-start gap-1 rounded-lg border p-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-55",
                  focusRing,
                  selected
                    ? "border-2 border-primary-600 bg-subtle text-text"
                    : "border-border bg-surface text-text-secondary hover:border-border-strong",
                )}
              >
                <span className="flex w-full items-center justify-between gap-2">
                  <span className="text-sm font-semibold text-text">{opt.label}</span>
                  <span
                    aria-hidden="true"
                    className={cn(
                      "flex size-4 shrink-0 items-center justify-center rounded-full border",
                      selected
                        ? "border-primary-600 bg-primary-fill text-on-primary"
                        : "border-border-strong bg-surface",
                    )}
                  >
                    {selected ? <Check className="size-3" strokeWidth={3} /> : null}
                  </span>
                </span>
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
            disabled={isResolved}
            maxLength={200}
            value={evidenceRef}
            onChange={(e) => {
              setEvidenceRef(e.target.value);
              if (evidenceErrorKey) setEvidenceErrorKey(null);
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
            disabled={isResolved}
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
        <Button type="button" variant="secondary" disabled={mutation.isPending} onClick={onClose}>
          {isResolved ? tDialogs("close") : tDialogs("cancel")}
        </Button>
        <Button
          type="submit"
          variant="primary"
          disabled={isResolved}
          loading={mutation.isPending}
        >
          {t("confirm")}
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
