"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle, AlertTriangle, RefreshCw } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { type FormEvent, useRef, useState } from "react";

import {
  Button,
  Checkbox,
  Dialog,
  DialogContent,
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
import { formatVnd, parseDigits } from "../format";
import { orderKeys } from "../keys";
import {
  useCreateRefundMutation,
  useMarkRefundFailedMutation,
  useQueryRefundMutation,
} from "../queries";
import type {
  AdminOrderDetailResponse,
  PaymentDetailView,
  RefundKind,
} from "../types";
import { handleRadioGroupKeyDown } from "./radio-group-key";

export interface RefundDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  order: AdminOrderDetailResponse;
  payment: PaymentDetailView;
}

function RefundDialogBody({
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
  const t = useTranslations("orders.dialogs.refund");
  const tLate = useTranslations("orders.dialogs.lateSuccess");
  const tExcess = useTranslations("orders.excessReasons");
  const tDialogs = useTranslations("orders.dialogs");
  const tErrors = useTranslations("orders.errors");
  const locale = useLocale();
  const queryClient = useQueryClient();

  const activeRefund = payment.refunds.find(
    (r) => r.state === "PROCESSING" || r.state === "UNKNOWN",
  );
  const unknownRefund = payment.refunds.find((r) => r.state === "UNKNOWN");
  const hasFailedApiRefund = payment.refunds.some(
    (r) => r.kind === "API" && r.state === "FAILED",
  );
  const canManual = hasFailedApiRefund && !activeRefund;
  const isExcessPayment = payment.duplicate || Boolean(payment.excessReason);

  const [selectedKind, setSelectedKind] = useState<RefundKind>("API");
  const kind: RefundKind = !canManual && selectedKind === "MANUAL" ? "API" : selectedKind;
  const [amountInput, setAmountInput] = useState(() =>
    String(Math.max(0, payment.refundableAmountVnd)),
  );
  const [externalRef, setExternalRef] = useState("");
  const [reason, setReason] = useState("");
  const [verifiedSystemError, setVerifiedSystemError] = useState(false);
  const [step, setStep] = useState<1 | 2>(1);

  const [showMarkFailedForm, setShowMarkFailedForm] = useState(false);
  const [markFailedEvidence, setMarkFailedEvidence] = useState("");
  const [markFailedReason, setMarkFailedReason] = useState("");
  const [markFailedEvidenceError, setMarkFailedEvidenceError] = useState<string | undefined>();
  const [markFailedReasonError, setMarkFailedReasonError] = useState<string | undefined>();

  const [amountError, setAmountError] = useState<string | undefined>();
  const [externalRefError, setExternalRefError] = useState<string | undefined>();
  const [reasonError, setReasonError] = useState<string | undefined>();
  const [submitError, setSubmitError] = useState<string | undefined>();

  const amountRef = useRef<HTMLInputElement>(null);
  const externalRefInputRef = useRef<HTMLInputElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const markEvidenceRef = useRef<HTMLInputElement>(null);
  const markReasonRef = useRef<HTMLTextAreaElement>(null);
  const idempotencyRef = useRef<{ fingerprint: string; key: string } | null>(null);
  const submittingRef = useRef(false);

  const createMutation = useCreateRefundMutation(order.code);
  const queryMutation = useQueryRefundMutation(order.code);
  const markFailedMutation = useMarkRefundFailedMutation(order.code);

  const isBusy =
    createMutation.isPending || queryMutation.isPending || markFailedMutation.isPending;

  const kindOptions: ReadonlyArray<{
    value: RefundKind;
    label: string;
    hint: string;
    disabled: boolean;
  }> = [
    {
      value: "API",
      label: t("kindApi"),
      hint: t("kindApiHint"),
      disabled: false,
    },
    {
      value: "MANUAL",
      label: t("kindManual"),
      hint: canManual ? t("kindManualHint") : t("kindManualDisabledHint"),
      disabled: !canManual,
    },
  ];

  const handleQueryUnknown = async () => {
    if (!unknownRefund || isBusy) return;
    setSubmitError(undefined);
    onBusyChange(true);
    try {
      const updated = await queryMutation.mutateAsync(unknownRefund.id);
      notify.success(t("querySuccessToast"));
      if (updated.state === "SUCCEEDED") {
        onClose();
      }
    } catch (error) {
      setSubmitError(resolveOrderErrorMessage(error, tErrors));
    } finally {
      onBusyChange(false);
    }
  };

  const handleMarkFailedSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!unknownRefund || submittingRef.current || isBusy) return;

    const trimmedEv = markFailedEvidence.trim();
    const trimmedRe = markFailedReason.trim();
    if (!trimmedEv) {
      setMarkFailedEvidenceError(tLate("evidenceRequired"));
      markEvidenceRef.current?.focus();
      return;
    }
    if (!trimmedRe) {
      setMarkFailedReasonError(t("reasonRequired"));
      markReasonRef.current?.focus();
      return;
    }

    setMarkFailedEvidenceError(undefined);
    setMarkFailedReasonError(undefined);
    setSubmitError(undefined);
    submittingRef.current = true;
    onBusyChange(true);

    try {
      await markFailedMutation.mutateAsync({
        refundId: unknownRefund.id,
        body: { evidenceRef: trimmedEv, reason: trimmedRe },
      });
      notify.success(t("markFailedSuccessToast"));
      setShowMarkFailedForm(false);
    } catch (error) {
      setSubmitError(resolveOrderErrorMessage(error, tErrors));
    } finally {
      submittingRef.current = false;
      onBusyChange(false);
    }
  };

  const validateStep1 = (): {
    amountVnd: number;
    trimmedReason: string;
    trimmedExternalRef: string;
  } | null => {
    const parsedAmount = parseDigits(amountInput);
    if (parsedAmount === null || parsedAmount < 1) {
      setAmountError(t("amountInvalid"));
      amountRef.current?.focus();
      return null;
    }
    if (parsedAmount > payment.refundableAmountVnd) {
      setAmountError(t("amountExceeded"));
      amountRef.current?.focus();
      return null;
    }

    const trimmedExternalRef = externalRef.trim();
    if (kind === "MANUAL" && !trimmedExternalRef) {
      setExternalRefError(t("externalRefRequired"));
      externalRefInputRef.current?.focus();
      return null;
    }

    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setReasonError(t("reasonRequired"));
      reasonRef.current?.focus();
      return null;
    }

    setAmountError(undefined);
    setExternalRefError(undefined);
    setReasonError(undefined);
    return { amountVnd: parsedAmount, trimmedReason, trimmedExternalRef };
  };

  const submitRefund = async () => {
    if (submittingRef.current || isBusy) return;
    const valid = validateStep1();
    if (!valid) return;

    const fingerprint = `${payment.id}|${kind}|${valid.amountVnd}|${valid.trimmedExternalRef}|${valid.trimmedReason}|${verifiedSystemError}`;
    if (idempotencyRef.current?.fingerprint !== fingerprint) {
      idempotencyRef.current = { fingerprint, key: crypto.randomUUID() };
    }

    setSubmitError(undefined);
    submittingRef.current = true;
    onBusyChange(true);

    try {
      await createMutation.mutateAsync({
        paymentId: payment.id,
        body: {
          amountVnd: valid.amountVnd,
          kind,
          reason: valid.trimmedReason,
          verifiedSystemError,
          ...(kind === "MANUAL" ? { externalRef: valid.trimmedExternalRef } : {}),
        },
        idempotencyKey: idempotencyRef.current.key,
      });
      notify.success(t("successToast", { txnRef: payment.txnRef }));
      onClose();
    } catch (error) {
      const code = orderErrorCode(error);
      const msg = resolveOrderErrorMessage(error, tErrors);
      setSubmitError(msg);
      if (code === "REFUND_AMOUNT_EXCEEDED") {
        setAmountError(t("amountExceeded"));
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(order.code) });
      } else if (code === "REFUND_EXTERNAL_REF_REQUIRED") {
        setExternalRefError(t("externalRefRequired"));
      }
      if (step === 2 && (code === "REFUND_AMOUNT_EXCEEDED" || code === "REFUND_EXTERNAL_REF_REQUIRED")) {
        setStep(1);
      }
    } finally {
      submittingRef.current = false;
      onBusyChange(false);
    }
  };

  const handleFormSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (kind === "MANUAL" && step === 1) {
      if (validateStep1()) {
        setSubmitError(undefined);
        setStep(2);
      }
      return;
    }
    void submitRefund();
  };

  const parsedPreviewAmount = parseDigits(amountInput) ?? 0;

  return (
    <>
      <DialogHeader>
        <DialogTitle>
          {unknownRefund
            ? t("titleResolveUnknown")
            : kind === "MANUAL"
              ? t("titleManual")
              : t("title")}
        </DialogTitle>
      </DialogHeader>

      <div className="flex flex-col gap-4">
        <dl className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-subtle p-3 text-xs sm:grid-cols-4">
          <div>
            <dt className="text-text-secondary">{t("summaryTxnRef")}</dt>
            <dd className="mt-0.5 font-mono font-semibold text-text">{payment.txnRef}</dd>
          </div>
          <div>
            <dt className="text-text-secondary">{t("summaryPaymentAmount")}</dt>
            <dd className="mt-0.5 font-mono font-semibold text-text">
              {formatVnd(payment.amountVnd, locale)}
            </dd>
          </div>
          <div>
            <dt className="text-text-secondary">{t("summaryAlreadyRefunded")}</dt>
            <dd className="mt-0.5 font-mono font-semibold text-text">
              {formatVnd(payment.refundedAmountVnd, locale)}
            </dd>
          </div>
          <div>
            <dt className="text-text-secondary">{t("summaryRemaining")}</dt>
            <dd className="mt-0.5 font-mono font-semibold text-primary-text">
              {formatVnd(payment.refundableAmountVnd, locale)}
            </dd>
          </div>
        </dl>

        {isExcessPayment ? (
          <div className="rounded-lg border border-warning/40 bg-warning-bg px-3 py-2.5 text-sm text-text">
            <p className="font-semibold">{t("excessNoticeTitle")}</p>
            <p className="mt-1 text-xs text-text-secondary">{t("excessNoticeBody")}</p>
            {payment.excessReason ? (
              <p className="mt-1 text-xs font-medium text-warning">{tExcess(payment.excessReason)}</p>
            ) : null}
          </div>
        ) : null}

        {submitError ? (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2.5 text-sm text-danger"
          >
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{submitError}</span>
          </div>
        ) : null}

        {unknownRefund ? (
          <div className="flex flex-col gap-4">
            <div
              role="alert"
              className="flex flex-col gap-1.5 rounded-lg border border-danger/40 bg-danger-bg px-3 py-3 text-sm text-danger"
            >
              <div className="flex items-center gap-2 font-semibold">
                <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{t("unknownNoticeTitle")}</span>
              </div>
              <p className="text-xs text-text">{t("unknownNoticeBody")}</p>
              <p className="font-mono text-xs text-text-secondary">
                {t("unknownRefundId", { id: unknownRefund.id })}
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="primary"
                loading={queryMutation.isPending}
                disabled={isBusy}
                onClick={() => void handleQueryUnknown()}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                <span>{t("queryAction")}</span>
              </Button>
              <Button
                type="button"
                variant="danger"
                disabled={isBusy}
                onClick={() => setShowMarkFailedForm((prev) => !prev)}
              >
                {t("openMarkFailed")}
              </Button>
            </div>

            {showMarkFailedForm ? (
              <form
                onSubmit={(e) => void handleMarkFailedSubmit(e)}
                noValidate
                className="flex flex-col gap-3 rounded-lg border border-border bg-subtle p-3"
              >
                <h3 className="text-sm font-semibold text-text">{t("markFailedTitle")}</h3>
                <FormField label={tLate("evidenceLabel")} required error={markFailedEvidenceError}>
                  {({ id, ...a11y }) => (
                    <Input
                      {...a11y}
                      id={id}
                      ref={markEvidenceRef}
                      maxLength={200}
                      value={markFailedEvidence}
                      onChange={(e) => {
                        setMarkFailedEvidence(e.target.value);
                        if (markFailedEvidenceError) setMarkFailedEvidenceError(undefined);
                      }}
                      placeholder={tLate("evidencePlaceholder")}
                    />
                  )}
                </FormField>

                <FormField label={t("markFailedReasonLabel")} required error={markFailedReasonError}>
                  {({ id, ...a11y }) => (
                    <Textarea
                      {...a11y}
                      id={id}
                      ref={markReasonRef}
                      rows={3}
                      maxLength={1000}
                      value={markFailedReason}
                      onChange={(e) => {
                        setMarkFailedReason(e.target.value);
                        if (markFailedReasonError) setMarkFailedReasonError(undefined);
                      }}
                      placeholder={t("markFailedReasonPlaceholder")}
                    />
                  )}
                </FormField>

                <DialogFooter>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={isBusy}
                    onClick={() => setShowMarkFailedForm(false)}
                  >
                    {tDialogs("cancel")}
                  </Button>
                  <Button type="submit" variant="danger" loading={markFailedMutation.isPending}>
                    {t("markFailedConfirm")}
                  </Button>
                </DialogFooter>
              </form>
            ) : (
              <DialogFooter>
                <Button type="button" variant="secondary" disabled={isBusy} onClick={onClose}>
                  {tDialogs("close")}
                </Button>
              </DialogFooter>
            )}
          </div>
        ) : step === 2 && kind === "MANUAL" ? (
          <div className="flex flex-col gap-4">
            <div
              role="alert"
              className="flex flex-col gap-1.5 rounded-lg border border-warning/40 bg-warning-bg px-3 py-3 text-sm text-text"
            >
              <p className="font-semibold">{t("manualStep2Title")}</p>
              <p className="text-xs text-text-secondary">{t("manualStep2Warning")}</p>
              <p className="font-mono text-xs font-medium">
                {t("manualStep2Summary", {
                  amount: formatVnd(parsedPreviewAmount, locale),
                  externalRef: externalRef.trim(),
                })}
              </p>
            </div>

            <DialogFooter>
              <Button type="button" variant="secondary" disabled={isBusy} onClick={() => setStep(1)}>
                {tDialogs("back")}
              </Button>
              <Button
                type="button"
                variant="danger"
                loading={createMutation.isPending}
                onClick={() => void submitRefund()}
              >
                {createMutation.isPending ? tDialogs("submitting") : t("manualStep2Confirm")}
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <form onSubmit={handleFormSubmit} noValidate className="flex flex-col gap-4">
            <fieldset className="flex flex-col gap-2">
              <legend className="text-sm font-medium text-text">{t("kindLabel")}</legend>
              <div role="radiogroup" aria-label={t("kindLabel")} className="grid gap-2.5 sm:grid-cols-2">
                {kindOptions.map((opt) => {
                  const selected = kind === opt.value;
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      aria-disabled={opt.disabled || undefined}
                      disabled={opt.disabled}
                      tabIndex={selected ? 0 : -1}
                      onClick={() => {
                        if (!opt.disabled) {
                          setSelectedKind(opt.value);
                          setStep(1);
                          setSubmitError(undefined);
                        }
                      }}
                      onKeyDown={(e) =>
                        handleRadioGroupKeyDown(e, kind, kindOptions, (next) => {
                          setSelectedKind(next);
                          setStep(1);
                        })
                      }
                      className={cn(
                        "flex flex-col items-start gap-1 rounded-lg border p-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-55",
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

            <FormField
              label={t("amountLabel")}
              required
              hint={t("amountHint", {
                remaining: formatVnd(payment.refundableAmountVnd, locale),
              })}
              error={amountError}
            >
              {({ id, ...a11y }) => (
                <Input
                  {...a11y}
                  id={id}
                  ref={amountRef}
                  inputMode="numeric"
                  value={amountInput}
                  onChange={(e) => {
                    setAmountInput(e.target.value.replace(/\D+/g, ""));
                    if (amountError) setAmountError(undefined);
                  }}
                />
              )}
            </FormField>

            {kind === "MANUAL" ? (
              <FormField label={t("externalRefLabel")} required error={externalRefError}>
                {({ id, ...a11y }) => (
                  <Input
                    {...a11y}
                    id={id}
                    ref={externalRefInputRef}
                    maxLength={200}
                    value={externalRef}
                    onChange={(e) => {
                      setExternalRef(e.target.value);
                      if (externalRefError) setExternalRefError(undefined);
                    }}
                    placeholder={t("externalRefPlaceholder")}
                  />
                )}
              </FormField>
            ) : null}

            <div className="flex items-start gap-2.5 rounded-lg border border-border bg-subtle p-3">
              <Checkbox
                id={`refund-verified-${payment.id}`}
                checked={verifiedSystemError}
                onCheckedChange={(checked) => setVerifiedSystemError(checked === true)}
              />
              <div className="flex flex-col gap-0.5 text-xs">
                <label
                  htmlFor={`refund-verified-${payment.id}`}
                  className="cursor-pointer font-medium text-text"
                >
                  {t("verifiedSystemErrorLabel")}
                </label>
                <p className="text-text-secondary">{t("verifiedSystemErrorHint")}</p>
              </div>
            </div>

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
                    if (reasonError) setReasonError(undefined);
                  }}
                  placeholder={t("reasonPlaceholder")}
                />
              )}
            </FormField>

            <DialogFooter>
              <Button type="button" variant="secondary" disabled={isBusy} onClick={onClose}>
                {tDialogs("cancel")}
              </Button>
              <Button type="submit" variant="primary" loading={createMutation.isPending}>
                {createMutation.isPending
                  ? tDialogs("submitting")
                  : kind === "MANUAL"
                    ? t("submitManualNext")
                    : t("submitApi")}
              </Button>
            </DialogFooter>
          </form>
        )}
      </div>
    </>
  );
}

export function RefundDialog({ open, onOpenChange, order, payment }: RefundDialogProps) {
  const [busy, setBusy] = useState(false);

  return (
    <Dialog open={open} onOpenChange={(next) => !busy && onOpenChange(next)}>
      <DialogContent size="lg">
        {open ? (
          <RefundDialogBody
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
