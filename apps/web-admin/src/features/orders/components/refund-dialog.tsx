"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle, AlertTriangle, Check, RefreshCw } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { type FormEvent, useEffect, useId, useRef, useState } from "react";

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
  Textarea,
  cn,
  focusRing,
  notify,
} from "@thi360/ui";

import { ApiStatusError } from "@/lib/api/query-client";

import { orderErrorCode, orderFieldErrors, resolveOrderErrorMessage } from "../errors";
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

type Step1FieldName = "amount" | "externalRef" | "verifiedSystemError" | "reason";
type AmountErrorKey = "amountInvalid" | "amountExceeded";

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
  const verifiedErrorId = useId();

  const unknownRefund = payment.refunds.find((r) => r.state === "UNKNOWN");
  const processingRefund = payment.refunds.find((r) => r.state === "PROCESSING");
  const activeRefund = unknownRefund ?? processingRefund;
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
  const [markFailedEvidenceErrorKey, setMarkFailedEvidenceErrorKey] = useState<
    "evidenceRequired" | null
  >(null);
  const [markFailedReasonErrorKey, setMarkFailedReasonErrorKey] = useState<
    "reasonRequired" | null
  >(null);

  const [amountErrorKey, setAmountErrorKey] = useState<AmountErrorKey | null>(null);
  const [externalRefErrorKey, setExternalRefErrorKey] = useState<"externalRefRequired" | null>(
    null,
  );
  const [verifiedErrorKey, setVerifiedErrorKey] = useState<"verifiedSystemErrorRequired" | null>(
    null,
  );
  const [reasonErrorKey, setReasonErrorKey] = useState<"reasonRequired" | null>(null);
  const [submitError, setSubmitError] = useState<unknown>(null);

  const amountRef = useRef<HTMLInputElement>(null);
  const externalRefInputRef = useRef<HTMLInputElement>(null);
  const verifiedCheckboxRef = useRef<HTMLButtonElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const markEvidenceRef = useRef<HTMLInputElement>(null);
  const markReasonRef = useRef<HTMLTextAreaElement>(null);
  const openMarkFailedBtnRef = useRef<HTMLButtonElement>(null);
  const step2HeadingRef = useRef<HTMLParagraphElement>(null);
  const amountEditedRef = useRef(false);
  const prevHasActiveRef = useRef(Boolean(activeRefund));
  const prevRefundableRef = useRef(payment.refundableAmountVnd);
  const pendingFocusFieldRef = useRef<Step1FieldName | null>(null);
  const prevStepRef = useRef<1 | 2>(1);
  const prevShowMarkFailedRef = useRef(false);
  const idempotencyRef = useRef<{ fingerprint: string; key: string } | null>(null);
  const submittingRef = useRef(false);

  const createMutation = useCreateRefundMutation(order.code);
  const queryMutation = useQueryRefundMutation(order.code);
  const markFailedMutation = useMarkRefundFailedMutation(order.code);

  const isBusy =
    createMutation.isPending || queryMutation.isPending || markFailedMutation.isPending;

  useEffect(() => {
    const hadActive = prevHasActiveRef.current;
    const prevRefundable = prevRefundableRef.current;
    const hasActive = Boolean(activeRefund);
    prevHasActiveRef.current = hasActive;
    prevRefundableRef.current = payment.refundableAmountVnd;

    const unlockedFromActive = hadActive && !hasActive && payment.refundableAmountVnd > 0;
    const unlockedFromZero = prevRefundable <= 0 && payment.refundableAmountVnd > 0;
    if (unlockedFromActive || unlockedFromZero || !amountEditedRef.current) {
      setAmountInput(String(Math.max(0, payment.refundableAmountVnd)));
      amountEditedRef.current = false;
    }
  }, [activeRefund, payment.refundableAmountVnd]);

  const focusStep1Field = (field: Step1FieldName) => {
    if (field === "amount") amountRef.current?.focus();
    else if (field === "externalRef") externalRefInputRef.current?.focus();
    else if (field === "verifiedSystemError") verifiedCheckboxRef.current?.focus();
    else reasonRef.current?.focus();
  };

  useEffect(() => {
    const prevStep = prevStepRef.current;
    prevStepRef.current = step;
    if (step === 2 && kind === "MANUAL" && prevStep === 1) {
      step2HeadingRef.current?.focus();
    } else if (step === 1 && prevStep === 2) {
      const target = pendingFocusFieldRef.current ?? "amount";
      pendingFocusFieldRef.current = null;
      focusStep1Field(target);
    }
  }, [step, kind]);

  useEffect(() => {
    const prevOpen = prevShowMarkFailedRef.current;
    prevShowMarkFailedRef.current = showMarkFailedForm;
    if (showMarkFailedForm && !prevOpen) {
      markEvidenceRef.current?.focus();
    } else if (!showMarkFailedForm && prevOpen && unknownRefund) {
      openMarkFailedBtnRef.current?.focus();
    }
  }, [showMarkFailedForm, unknownRefund]);

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

  const handleQueryRefund = async (refundId: string) => {
    if (submittingRef.current || isBusy) return;
    setSubmitError(null);
    submittingRef.current = true;
    onBusyChange(true);
    try {
      const updated = await queryMutation.mutateAsync(refundId);
      notify.success(t("querySuccessToast"));
      if (updated.state === "SUCCEEDED") {
        onClose();
      }
    } catch (error) {
      setSubmitError(error);
      if (error instanceof ApiStatusError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(order.code) });
        void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      }
    } finally {
      submittingRef.current = false;
      onBusyChange(false);
    }
  };

  const handleMarkFailedSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!unknownRefund || submittingRef.current || isBusy) return;

    const trimmedEv = markFailedEvidence.trim();
    const trimmedRe = markFailedReason.trim();
    if (!trimmedEv) {
      setMarkFailedEvidenceErrorKey("evidenceRequired");
      markEvidenceRef.current?.focus();
      return;
    }
    if (!trimmedRe) {
      setMarkFailedReasonErrorKey("reasonRequired");
      markReasonRef.current?.focus();
      return;
    }

    setMarkFailedEvidenceErrorKey(null);
    setMarkFailedReasonErrorKey(null);
    setSubmitError(null);
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
      setSubmitError(error);
      if (error instanceof ApiStatusError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(order.code) });
        void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      }
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
      setAmountErrorKey("amountInvalid");
      amountRef.current?.focus();
      return null;
    }
    if (parsedAmount > payment.refundableAmountVnd) {
      setAmountErrorKey("amountExceeded");
      amountRef.current?.focus();
      return null;
    }

    const trimmedExternalRef = externalRef.trim();
    if (kind === "MANUAL" && !trimmedExternalRef) {
      setExternalRefErrorKey("externalRefRequired");
      externalRefInputRef.current?.focus();
      return null;
    }

    if (!verifiedSystemError) {
      setVerifiedErrorKey("verifiedSystemErrorRequired");
      verifiedCheckboxRef.current?.focus();
      return null;
    }

    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setReasonErrorKey("reasonRequired");
      reasonRef.current?.focus();
      return null;
    }

    setAmountErrorKey(null);
    setExternalRefErrorKey(null);
    setVerifiedErrorKey(null);
    setReasonErrorKey(null);
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

    setSubmitError(null);
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
      const fields = orderFieldErrors(error);
      setSubmitError(error);

      let firstInvalid: Step1FieldName | null = null;
      const markInvalid = (field: Step1FieldName) => {
        if (!firstInvalid) firstInvalid = field;
      };

      if (code === "REFUND_AMOUNT_EXCEEDED" || fields.some((f) => f.field === "amountVnd")) {
        setAmountErrorKey("amountExceeded");
        markInvalid("amount");
      }
      if (
        code === "REFUND_EXTERNAL_REF_REQUIRED" ||
        fields.some((f) => f.field === "externalRef")
      ) {
        setExternalRefErrorKey("externalRefRequired");
        markInvalid("externalRef");
      }
      if (
        code === "REFUND_SYSTEM_ERROR_NOT_VERIFIED" ||
        fields.some((f) => f.field === "verifiedSystemError")
      ) {
        setVerifiedErrorKey("verifiedSystemErrorRequired");
        markInvalid("verifiedSystemError");
      }
      if (fields.some((f) => f.field === "reason")) {
        setReasonErrorKey("reasonRequired");
        markInvalid("reason");
      }

      if (error instanceof ApiStatusError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(order.code) });
        void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      }

      const shouldReturnToStep1 =
        step === 2 &&
        (code === "REFUND_AMOUNT_EXCEEDED" ||
          code === "REFUND_EXTERNAL_REF_REQUIRED" ||
          code === "REFUND_SYSTEM_ERROR_NOT_VERIFIED" ||
          code === "VALIDATION_FAILED");

      if (shouldReturnToStep1) {
        pendingFocusFieldRef.current = firstInvalid;
        setStep(1);
      } else if (firstInvalid) {
        focusStep1Field(firstInvalid);
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
        setSubmitError(null);
        setStep(2);
      }
      return;
    }
    void submitRefund();
  };

  const parsedPreviewAmount = parseDigits(amountInput) ?? 0;
  const amountError = amountErrorKey ? t(amountErrorKey) : undefined;
  const externalRefError = externalRefErrorKey ? t(externalRefErrorKey) : undefined;
  const verifiedError = verifiedErrorKey ? t(verifiedErrorKey) : undefined;
  const reasonError = reasonErrorKey ? t(reasonErrorKey) : undefined;
  const markFailedEvidenceError = markFailedEvidenceErrorKey
    ? tLate(markFailedEvidenceErrorKey)
    : undefined;
  const markFailedReasonError = markFailedReasonErrorKey
    ? t(markFailedReasonErrorKey)
    : undefined;
  const submitErrorMessage = submitError
    ? resolveOrderErrorMessage(submitError, tErrors)
    : undefined;

  return (
    <>
      <DialogHeader>
        <DialogTitle>
          {unknownRefund
            ? t("titleResolveUnknown")
            : processingRefund
              ? t("titleProcessing")
              : kind === "MANUAL"
                ? t("titleManual")
                : t("title")}
        </DialogTitle>
        <DialogDescription>
          {unknownRefund
            ? t("descriptionUnknown")
            : processingRefund
              ? t("descriptionProcessing")
              : t("description")}
        </DialogDescription>
      </DialogHeader>

      <div className="flex flex-col gap-4">
        <dl className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-subtle p-3 text-xs sm:grid-cols-4">
          <div className="min-w-0">
            <dt className="text-text-secondary">{t("summaryTxnRef")}</dt>
            <dd className="mt-0.5 font-mono font-semibold wrap-anywhere text-text">
              {payment.txnRef}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="text-text-secondary">{t("summaryPaymentAmount")}</dt>
            <dd className="mt-0.5 font-mono font-semibold text-text">
              {formatVnd(payment.amountVnd, locale)}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="text-text-secondary">{t("summaryAlreadyRefunded")}</dt>
            <dd className="mt-0.5 font-mono font-semibold text-text">
              {formatVnd(payment.refundedAmountVnd, locale)}
            </dd>
          </div>
          <div className="min-w-0">
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

        {submitErrorMessage ? (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2.5 text-sm text-danger"
          >
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{submitErrorMessage}</span>
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
              <p className="font-mono text-xs wrap-anywhere text-text-secondary">
                {t("unknownRefundId", { id: unknownRefund.id })}
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              {unknownRefund.kind === "API" ? (
                <Button
                  type="button"
                  variant="primary"
                  loading={queryMutation.isPending}
                  disabled={isBusy}
                  onClick={() => void handleQueryRefund(unknownRefund.id)}
                >
                  <RefreshCw className="h-4 w-4" aria-hidden="true" />
                  <span>{t("queryAction")}</span>
                </Button>
              ) : null}
              <Button
                ref={openMarkFailedBtnRef}
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
                        if (markFailedEvidenceErrorKey) setMarkFailedEvidenceErrorKey(null);
                      }}
                      placeholder={tLate("evidencePlaceholder")}
                    />
                  )}
                </FormField>

                <FormField
                  label={t("markFailedReasonLabel")}
                  required
                  error={markFailedReasonError}
                >
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
                        if (markFailedReasonErrorKey) setMarkFailedReasonErrorKey(null);
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
        ) : processingRefund ? (
          <div className="flex flex-col gap-4">
            <div
              role="status"
              className="flex flex-col gap-1.5 rounded-lg border border-info/40 bg-info-bg px-3 py-3 text-sm text-text"
            >
              <div className="flex items-center gap-2 font-semibold text-info">
                <RefreshCw className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{t("processingNoticeTitle")}</span>
              </div>
              <p className="text-xs text-text">{t("processingNoticeBody")}</p>
              <p className="font-mono text-xs wrap-anywhere text-text-secondary">
                {t("processingRefundId", { id: processingRefund.id })}
              </p>
            </div>

            <DialogFooter>
              <Button type="button" variant="secondary" disabled={isBusy} onClick={onClose}>
                {tDialogs("close")}
              </Button>
              {processingRefund.kind === "API" ? (
                <Button
                  type="button"
                  variant="primary"
                  loading={queryMutation.isPending}
                  disabled={isBusy}
                  onClick={() => void handleQueryRefund(processingRefund.id)}
                >
                  <RefreshCw className="h-4 w-4" aria-hidden="true" />
                  <span>{t("queryAction")}</span>
                </Button>
              ) : null}
            </DialogFooter>
          </div>
        ) : payment.refundableAmountVnd <= 0 ? (
          <div className="flex flex-col gap-4">
            <div
              role="status"
              className="flex flex-col gap-1.5 rounded-lg border border-border bg-subtle px-3 py-3 text-sm text-text"
            >
              <p className="font-semibold">{t("noRemainingTitle")}</p>
              <p className="text-xs text-text-secondary">{t("noRemainingBody")}</p>
            </div>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={onClose}>
                {tDialogs("close")}
              </Button>
            </DialogFooter>
          </div>
        ) : step === 2 && kind === "MANUAL" ? (
          <div className="flex flex-col gap-4">
            <div
              role="alert"
              className="flex flex-col gap-1.5 rounded-lg border border-warning/40 bg-warning-bg px-3 py-3 text-sm text-text"
            >
              <p
                ref={step2HeadingRef}
                tabIndex={-1}
                className="font-semibold focus:outline-hidden"
              >
                {t("manualStep2Title")}
              </p>
              <p className="text-xs text-text-secondary">{t("manualStep2Warning")}</p>
              <p className="font-mono text-xs font-medium wrap-anywhere">
                {t("manualStep2Summary", {
                  amount: formatVnd(parsedPreviewAmount, locale),
                  externalRef: externalRef.trim(),
                })}
              </p>
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="secondary"
                disabled={isBusy}
                onClick={() => {
                  pendingFocusFieldRef.current = "amount";
                  setStep(1);
                }}
              >
                {tDialogs("back")}
              </Button>
              <Button
                type="button"
                variant="danger"
                loading={createMutation.isPending}
                onClick={() => void submitRefund()}
              >
                {t("manualStep2Confirm")}
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
                          setSubmitError(null);
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
                    amountEditedRef.current = true;
                    setAmountInput(e.target.value.replace(/\D+/g, ""));
                    if (amountErrorKey) setAmountErrorKey(null);
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
                      if (externalRefErrorKey) setExternalRefErrorKey(null);
                    }}
                    placeholder={t("externalRefPlaceholder")}
                  />
                )}
              </FormField>
            ) : null}

            <div
              className={cn(
                "flex flex-col gap-1.5 rounded-lg border p-3",
                verifiedError ? "border-danger bg-danger-bg/40" : "border-border bg-subtle",
              )}
            >
              <div className="flex items-start gap-2.5">
                <Checkbox
                  ref={verifiedCheckboxRef}
                  id={`refund-verified-${payment.id}`}
                  checked={verifiedSystemError}
                  aria-invalid={verifiedError ? true : undefined}
                  aria-describedby={verifiedError ? verifiedErrorId : undefined}
                  onCheckedChange={(checked) => {
                    setVerifiedSystemError(checked === true);
                    if (checked === true && verifiedErrorKey) setVerifiedErrorKey(null);
                  }}
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
              {verifiedError ? (
                <p id={verifiedErrorId} role="alert" className="text-caption text-danger">
                  {verifiedError}
                </p>
              ) : null}
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
                    if (reasonErrorKey) setReasonErrorKey(null);
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
                {kind === "MANUAL" ? t("submitManualNext") : t("submitApi")}
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
