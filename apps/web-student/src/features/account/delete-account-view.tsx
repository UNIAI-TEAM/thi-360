"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Checkbox, ConfirmDialog, EmptyState, FormField, PasswordField, Skeleton } from "@thi360/ui";
import { AlertCircle, Clock, Lock, ShieldAlert, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useId, useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { ACCOUNT_DELETED_PARAM, ACCOUNT_DELETED_VALUE } from "@/features/account/account-links";
import {
  ACCOUNT_DELETION_CHECK_QUERY_KEY,
  type AccountDeletionBlocker,
  type AccountDeletionCheckResponse,
  type AccountDeletionRequest,
  deleteAccount,
  type DeleteAccountResult,
  loadAccountDeletionCheck,
} from "@/features/account/api";
import {
  clearSessionBestEffort,
  loadSession,
  SESSION_QUERY_KEY,
  type SessionLoader,
} from "@/features/session/api";
import { touchFieldText } from "@/lib/styles";

export type DeleteAccountState =
  | { kind: "checking" }
  | { kind: "checkFailed" }
  | { kind: "blocked"; blockers: AccountDeletionBlocker[] }
  | {
      kind: "ready";
      unusedAttempts: number;
      pendingGrading: boolean;
      password: string;
      acknowledged: boolean;
      fieldError?: "wrongPassword";
      formError?: "throttled" | "failed";
    }
  | {
      kind: "confirming";
      unusedAttempts: number;
      pendingGrading: boolean;
      password: string;
      acknowledged: true;
    }
  | {
      kind: "deleting";
      unusedAttempts: number;
      pendingGrading: boolean;
      password: string;
      acknowledged: true;
    }
  | { kind: "deleted" };

type FormInteraction =
  | {
      kind: "editing";
      password: string;
      acknowledged: boolean;
      fieldError?: "wrongPassword";
      formError?: "throttled" | "failed";
    }
  | { kind: "confirming"; password: string; acknowledged: true }
  | { kind: "deleting"; password: string; acknowledged: true }
  | { kind: "blocked"; blockers: AccountDeletionBlocker[] }
  | { kind: "deleted" };

const BLOCKER_CONFIG: Record<
  AccountDeletionBlocker,
  {
    messageKey: "blockedAttempt" | "blockedOrder" | "blockedPendingPayment";
    link?: { href: string; labelKey: "openMyExams" | "openMyOrders" };
  }
> = {
  ATTEMPT_IN_PROGRESS: {
    messageKey: "blockedAttempt",
    link: { href: "/home", labelKey: "openMyExams" },
  },
  ORDER_BEING_CHECKED: {
    messageKey: "blockedOrder",
  },
  ORDER_PENDING_PAYMENT: {
    messageKey: "blockedPendingPayment",
    link: { href: "/account/orders", labelKey: "openMyOrders" },
  },
};

const CARD_CLASS = "flex flex-col gap-4 rounded-xl border border-border bg-surface p-6";

function resolveDeleteAccountState(
  checkQuery: {
    isPending: boolean;
    isFetching: boolean;
    isError: boolean;
    data?: AccountDeletionCheckResponse;
  },
  interaction: FormInteraction,
): DeleteAccountState {
  if (interaction.kind === "deleted") return { kind: "deleted" };
  if (interaction.kind === "blocked") return { kind: "blocked", blockers: interaction.blockers };
  if (checkQuery.isPending || (checkQuery.isFetching && !checkQuery.data)) return { kind: "checking" };
  if (checkQuery.isError || !checkQuery.data) return { kind: "checkFailed" };
  if (checkQuery.data.blockers.length > 0) {
    return { kind: "blocked", blockers: checkQuery.data.blockers };
  }
  if (!checkQuery.data.canDelete) return { kind: "checkFailed" };
  const { unusedAttempts, pendingGrading } = checkQuery.data;
  if (interaction.kind === "confirming") {
    return {
      kind: "confirming",
      unusedAttempts,
      pendingGrading,
      password: interaction.password,
      acknowledged: true,
    };
  }
  if (interaction.kind === "deleting") {
    return {
      kind: "deleting",
      unusedAttempts,
      pendingGrading,
      password: interaction.password,
      acknowledged: true,
    };
  }
  return {
    kind: "ready",
    unusedAttempts,
    pendingGrading,
    password: interaction.password,
    acknowledged: interaction.acknowledged,
    fieldError: interaction.fieldError,
    formError: interaction.formError,
  };
}

export type DeleteAccountViewProps = {
  loadCurrentSession?: SessionLoader;
  loadCheck?: () => Promise<AccountDeletionCheckResponse>;
  submitDeletion?: (body: AccountDeletionRequest) => Promise<DeleteAccountResult>;
  clearSession?: () => Promise<void>;
};

export function DeleteAccountView({
  loadCurrentSession = loadSession,
  loadCheck = loadAccountDeletionCheck,
  submitDeletion = deleteAccount,
  clearSession = clearSessionBestEffort,
}: DeleteAccountViewProps) {
  const t = useTranslations("account.deletion");
  const common = useTranslations("common");
  const errorsT = useTranslations("errors");
  const router = useRouter();
  const queryClient = useQueryClient();
  const acknowledgeId = useId();
  const passwordInputRef = useRef<HTMLInputElement | null>(null);
  const inFlightRef = useRef(false);

  const [interaction, setInteraction] = useState<FormInteraction>({
    kind: "editing",
    password: "",
    acknowledged: false,
  });

  const isDeleted = interaction.kind === "deleted";

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
    enabled: !isDeleted,
  });

  const checkQuery = useQuery({
    queryKey: ACCOUNT_DELETION_CHECK_QUERY_KEY,
    queryFn: loadCheck,
    staleTime: 0,
    enabled: !isDeleted,
  });

  const deleteMutation = useMutation({
    mutationFn: submitDeletion,
  });

  const state = resolveDeleteAccountState(checkQuery, interaction);

  // Move focus into the cleared password input after ConfirmDialog finishes restoring inert/focus state.
  useEffect(() => {
    if (interaction.kind !== "editing" || interaction.fieldError !== "wrongPassword") return;
    const timer = window.setTimeout(() => {
      passwordInputRef.current?.focus();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [interaction]);

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  function handlePasswordChange(value: string) {
    setInteraction((current) =>
      current.kind === "editing"
        ? { ...current, password: value, fieldError: undefined, formError: undefined }
        : current,
    );
  }

  function handleAcknowledgedChange(checked: boolean) {
    setInteraction((current) =>
      current.kind === "editing" ? { ...current, acknowledged: checked } : current,
    );
  }

  function handleFormSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state.kind !== "ready" || state.password === "" || !state.acknowledged) return;
    setInteraction({
      kind: "confirming",
      password: state.password,
      acknowledged: true,
    });
  }

  function handleCancelConfirm() {
    setInteraction((current) =>
      current.kind === "confirming"
        ? { kind: "editing", password: current.password, acknowledged: true }
        : current,
    );
  }

  async function handleConfirmDelete() {
    if (inFlightRef.current || state.kind !== "confirming") return;
    inFlightRef.current = true;
    const currentPassword = state.password;
    setInteraction({ kind: "deleting", password: currentPassword, acknowledged: true });
    try {
      const result = await deleteMutation.mutateAsync({ currentPassword });
      switch (result.kind) {
        case "deleted": {
          setInteraction({ kind: "deleted" });
          try {
            await clearSession();
          } catch {
            // Cookie cleanup is best-effort; still clear client cache and navigate to /login?deleted=1.
          }
          await queryClient.cancelQueries();
          queryClient.clear();
          router.replace(`/login?${ACCOUNT_DELETED_PARAM}=${ACCOUNT_DELETED_VALUE}`);
          return;
        }
        case "wrongPassword": {
          inFlightRef.current = false;
          setInteraction({
            kind: "editing",
            password: "",
            acknowledged: true,
            fieldError: "wrongPassword",
          });
          return;
        }
        case "throttled": {
          inFlightRef.current = false;
          setInteraction({
            kind: "editing",
            password: currentPassword,
            acknowledged: true,
            formError: "throttled",
          });
          return;
        }
        case "blocked": {
          inFlightRef.current = false;
          setInteraction({ kind: "blocked", blockers: result.blockers });
          return;
        }
        case "notAllowed": {
          inFlightRef.current = false;
          setInteraction({
            kind: "editing",
            password: currentPassword,
            acknowledged: true,
            formError: "failed",
          });
          return;
        }
      }
    } catch {
      inFlightRef.current = false;
      setInteraction({
        kind: "editing",
        password: currentPassword,
        acknowledged: true,
        formError: "failed",
      });
    }
  }

  const isFormStage =
    state.kind === "ready" || state.kind === "confirming" || state.kind === "deleting";
  const canSubmit = state.kind === "ready" && state.password !== "" && state.acknowledged;

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />
      <main className="mx-auto flex max-w-[720px] flex-col gap-6 px-4 py-10 sm:px-6">
        <h1 className="text-h1 font-bold text-text">{t("title")}</h1>

        {(state.kind === "checking" || state.kind === "deleted") && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-36 w-full rounded-xl" />
            <Skeleton className="h-52 w-full rounded-xl" />
          </div>
        )}

        {state.kind === "checkFailed" && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              icon={<TriangleAlert />}
              title={errorsT("upstreamUnavailable")}
              action={
                <Button type="button" onClick={() => void checkQuery.refetch()}>
                  {common("retry")}
                </Button>
              }
            />
          </div>
        )}

        {state.kind === "blocked" && (
          <>
            <section
              role="status"
              className="flex flex-col gap-4 rounded-xl border border-danger/40 bg-surface p-6"
            >
              <ul className="flex flex-col gap-4">
                {state.blockers.map((blocker) => {
                  const config = BLOCKER_CONFIG[blocker];
                  return (
                    <li
                      key={blocker}
                      className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4 last:border-b-0 last:pb-0"
                    >
                      <div className="flex min-w-0 flex-1 basis-64 items-start gap-3">
                        <span
                          aria-hidden
                          className="grid size-9 shrink-0 place-items-center rounded-full bg-danger-bg text-danger"
                        >
                          <ShieldAlert className="size-4.5" />
                        </span>
                        <p className="pt-1 text-body text-text">{t(config.messageKey)}</p>
                      </div>
                      {config.link && (
                        <Button asChild variant="secondary">
                          <Link href={config.link.href}>{t(config.link.labelKey)}</Link>
                        </Button>
                      )}
                    </li>
                  );
                })}
              </ul>
            </section>

            <section className={CARD_CLASS}>
              <p className="text-body leading-relaxed text-text-secondary">{t("explanation")}</p>
            </section>
          </>
        )}

        {isFormStage && (
          <>
            <section className={CARD_CLASS}>
              <p className="text-body leading-relaxed text-text-secondary">{t("explanation")}</p>
            </section>

            {state.unusedAttempts > 0 && (
              <div
                role="status"
                className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning-bg px-4 py-3.5 text-body text-warning"
              >
                <TriangleAlert aria-hidden className="mt-0.5 size-5 shrink-0" />
                <span>{t("unusedAttemptsWarning", { n: state.unusedAttempts })}</span>
              </div>
            )}

            {state.pendingGrading && (
              <div
                role="status"
                className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning-bg px-4 py-3.5 text-body text-warning"
              >
                <Clock aria-hidden className="mt-0.5 size-5 shrink-0" />
                <span>{t("pendingGradingWarning")}</span>
              </div>
            )}

            <form noValidate onSubmit={handleFormSubmit} className={CARD_CLASS}>
              {state.kind === "ready" && state.formError && (
                <div
                  role="alert"
                  className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger"
                >
                  <AlertCircle aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <span>{t(state.formError)}</span>
                </div>
              )}

              <FormField
                label={t("passwordLabel")}
                error={state.kind === "ready" && state.fieldError ? t(state.fieldError) : undefined}
                required
              >
                {(field) => (
                  <PasswordField
                    {...field}
                    ref={passwordInputRef}
                    name="currentPassword"
                    size="lg"
                    required
                    autoComplete="current-password"
                    enterKeyHint="done"
                    value={state.password}
                    disabled={state.kind === "deleting"}
                    onChange={(event) => handlePasswordChange(event.target.value)}
                    className={touchFieldText}
                    leadingIcon={<Lock />}
                  />
                )}
              </FormField>

              <div className="flex items-start gap-3">
                <Checkbox
                  id={acknowledgeId}
                  checked={state.acknowledged}
                  disabled={state.kind === "deleting"}
                  onCheckedChange={(checked) => handleAcknowledgedChange(checked === true)}
                  className="mt-0.5"
                />
                <label htmlFor={acknowledgeId} className="cursor-pointer select-none text-body text-text">
                  {t("acknowledge")}
                </label>
              </div>

              <div className="flex justify-end">
                <Button
                  type="submit"
                  variant="danger"
                  size="lg"
                  disabled={!canSubmit}
                  loading={state.kind === "deleting"}
                  aria-haspopup="dialog"
                >
                  {t("submit")}
                </Button>
              </div>
            </form>

            <ConfirmDialog
              open={state.kind === "confirming" || state.kind === "deleting"}
              onOpenChange={(open) => {
                if (!open) handleCancelConfirm();
              }}
              title={t("confirmTitle")}
              description={t("acknowledge")}
              confirmLabel={t("confirmAction")}
              cancelLabel={t("confirmCancel")}
              tone="danger"
              loading={state.kind === "deleting"}
              onConfirm={() => void handleConfirmDelete()}
            />
          </>
        )}
      </main>
    </div>
  );
}
