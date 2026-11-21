"use client";

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
  useUiStrings,
} from "@thi360/ui";
import { AlertCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";
import { useIdempotencyKey } from "@/features/exams/review-queries";
import { resolveAttemptErrorMessage, type AttemptApiDeps } from "../api";
import { useCompensateAttempt, useFaultAttempt } from "../queries";
import { shortAttemptCode } from "../status";
import type { AdminAttemptDetailResponse } from "../types";

export interface FaultAttemptDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  attempt: AdminAttemptDetailResponse;
  deps: AttemptApiDeps;
  onSuccess: () => void;
}

export function FaultAttemptDialog({
  open,
  onOpenChange,
  attempt,
  deps,
  onSuccess,
}: FaultAttemptDialogProps) {
  const t = useTranslations("attempts.dialogs.fault");
  const attemptId = attempt.attemptId ?? "";
  const faultMutation = useFaultAttempt(attemptId, deps);
  const busy = faultMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={(next) => (busy && !next ? undefined : onOpenChange(next))}>
      <DialogContent>
        <FaultDialogBody
          attempt={attempt}
          faultMutation={faultMutation}
          onClose={() => onOpenChange(false)}
          onSuccess={onSuccess}
          t={t}
        />
      </DialogContent>
    </Dialog>
  );
}

function FaultDialogBody({
  attempt,
  faultMutation,
  onClose,
  onSuccess,
  t,
}: {
  attempt: AdminAttemptDetailResponse;
  faultMutation: ReturnType<typeof useFaultAttempt>;
  onClose: () => void;
  onSuccess: () => void;
  t: ReturnType<typeof useTranslations<"attempts.dialogs.fault">>;
}) {
  const { common: commonUi } = useUiStrings();
  const tErrors = useTranslations("attempts.errors");
  const [reason, setReason] = useState("");
  const [confirmedEmpty, setConfirmedEmpty] = useState(false);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);

  const attemptCode = shortAttemptCode(attempt.attemptId);
  const learner = attempt.username?.trim() || shortAttemptCode(attempt.userId);
  const failedParts = (attempt.parts ?? []).filter((part) => part.state === "GENERATION_FAILED");
  const isOpen = attempt.state === "IN_PROGRESS";

  async function handleConfirm() {
    const trimmed = reason.trim();
    if (!trimmed) {
      setConfirmedEmpty(true);
      reasonRef.current?.focus();
      return;
    }
    setSubmitError(null);
    try {
      await faultMutation.mutateAsync({ reason: trimmed });
      notify.success(t("successToast", { code: attemptCode }));
      onSuccess();
      onClose();
    } catch (error) {
      setSubmitError(error);
    }
  }

  const trimmed = reason.trim();
  const reasonMissing = trimmed === "";

  return (
    <>
      <DialogHeader>
        <DialogTitle>{t("title", { code: attemptCode })}</DialogTitle>
        <DialogDescription>
          {isOpen ? t("descriptionOpen") : t("descriptionClosed")}
        </DialogDescription>
      </DialogHeader>

      <div className="flex flex-col gap-3">
        <dl className="m-0 flex flex-wrap gap-x-6 gap-y-2 rounded-xl bg-subtle px-3.5 py-2.5 text-body">
          <div className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-muted">{t("learnerLabel")}</dt>
            <dd className="m-0 font-semibold text-text">{learner}</dd>
          </div>
          {attempt.examName && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("examLabel")}</dt>
              <dd className="m-0 font-semibold text-text">
                <span data-exam-content>{attempt.examName}</span>
                {attempt.attemptNo != null && (
                  <> · {t("attemptNo", { no: attempt.attemptNo })}</>
                )}
              </dd>
            </div>
          )}
          {failedParts.length > 0 && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("failedPartLabel")}</dt>
              <dd className="m-0 font-semibold text-danger">
                {failedParts.map((part, idx) => (
                  <span key={part.partId ?? idx}>
                    {idx > 0 && ", "}
                    <span data-exam-content>{part.partName || part.partCode}</span>
                  </span>
                ))}
              </dd>
            </div>
          )}
        </dl>

        <FormField
          label={t("reasonLabel")}
          required
          error={confirmedEmpty && reasonMissing ? t("reasonRequired") : undefined}
        >
          {(field) => (
            <Textarea
              {...field}
              ref={reasonRef}
              rows={3}
              maxLength={1000}
              value={reason}
              disabled={faultMutation.isPending}
              placeholder={t("reasonPlaceholder")}
              onChange={(event) => {
                setReason(event.target.value);
                if (submitError) setSubmitError(null);
              }}
            />
          )}
        </FormField>

        {submitError ? (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2.5 text-caption text-danger"
          >
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>{resolveAttemptErrorMessage(submitError, tErrors)}</span>
          </div>
        ) : null}
      </div>

      <DialogFooter>
        <Button
          type="button"
          variant="secondary"
          disabled={faultMutation.isPending}
          onClick={onClose}
        >
          {commonUi.cancel}
        </Button>
        <Button
          type="button"
          variant="danger"
          loading={faultMutation.isPending}
          onClick={() => void handleConfirm()}
        >
          {isOpen ? t("confirmOpen") : t("confirmClosed")}
        </Button>
      </DialogFooter>
    </>
  );
}

export interface CompensateAttemptDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  attempt: AdminAttemptDetailResponse;
  deps: AttemptApiDeps;
  onSuccess: () => void;
}

export function CompensateAttemptDialog({
  open,
  onOpenChange,
  attempt,
  deps,
  onSuccess,
}: CompensateAttemptDialogProps) {
  const t = useTranslations("attempts.dialogs.compensate");
  const attemptId = attempt.attemptId ?? "";
  const compensateMutation = useCompensateAttempt(attemptId, deps);
  const busy = compensateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={(next) => (busy && !next ? undefined : onOpenChange(next))}>
      <DialogContent>
        <CompensateDialogBody
          attempt={attempt}
          compensateMutation={compensateMutation}
          onClose={() => onOpenChange(false)}
          onSuccess={onSuccess}
          t={t}
        />
      </DialogContent>
    </Dialog>
  );
}

function CompensateDialogBody({
  attempt,
  compensateMutation,
  onClose,
  onSuccess,
  t,
}: {
  attempt: AdminAttemptDetailResponse;
  compensateMutation: ReturnType<typeof useCompensateAttempt>;
  onClose: () => void;
  onSuccess: () => void;
  t: ReturnType<typeof useTranslations<"attempts.dialogs.compensate">>;
}) {
  const { common: commonUi } = useUiStrings();
  const tErrors = useTranslations("attempts.errors");
  const [reason, setReason] = useState("");
  const [confirmedEmpty, setConfirmedEmpty] = useState(false);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const idempotency = useIdempotencyKey();

  const attemptId = attempt.attemptId ?? "";
  const attemptCode = shortAttemptCode(attempt.attemptId);
  const learner = attempt.username?.trim() || shortAttemptCode(attempt.userId);

  async function handleConfirm() {
    const trimmed = reason.trim();
    if (!trimmed) {
      setConfirmedEmpty(true);
      reasonRef.current?.focus();
      return;
    }
    setSubmitError(null);
    const idempotencyKey = idempotency.keyFor(`${attemptId}|${trimmed}`);
    try {
      await compensateMutation.mutateAsync({
        body: { reason: trimmed },
        idempotencyKey,
      });
      idempotency.release();
      notify.success(t("successToast", { code: attemptCode }));
      onSuccess();
      onClose();
    } catch (error) {
      setSubmitError(error);
    }
  }

  const trimmed = reason.trim();
  const reasonMissing = trimmed === "";

  return (
    <>
      <DialogHeader>
        <DialogTitle>{t("title", { user: learner })}</DialogTitle>
        <DialogDescription>{t("description", { code: attemptCode })}</DialogDescription>
      </DialogHeader>

      <div className="flex flex-col gap-3">
        <FormField
          label={t("reasonLabel")}
          required
          error={confirmedEmpty && reasonMissing ? t("reasonRequired") : undefined}
        >
          {(field) => (
            <Textarea
              {...field}
              ref={reasonRef}
              rows={3}
              maxLength={1000}
              value={reason}
              disabled={compensateMutation.isPending}
              placeholder={t("reasonPlaceholder")}
              onChange={(event) => {
                setReason(event.target.value);
                if (submitError) setSubmitError(null);
              }}
            />
          )}
        </FormField>

        {submitError ? (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2.5 text-caption text-danger"
          >
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>{resolveAttemptErrorMessage(submitError, tErrors)}</span>
          </div>
        ) : null}
      </div>

      <DialogFooter>
        <Button
          type="button"
          variant="secondary"
          disabled={compensateMutation.isPending}
          onClick={onClose}
        >
          {commonUi.cancel}
        </Button>
        <Button
          type="button"
          variant="primary"
          loading={compensateMutation.isPending}
          onClick={() => void handleConfirm()}
        >
          {t("confirm")}
        </Button>
      </DialogFooter>
    </>
  );
}
