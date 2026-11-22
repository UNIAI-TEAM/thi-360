"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Button,
  Checkbox,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  FormField,
  Input,
  Label,
  notify,
  Select,
  Skeleton,
  StatusChip,
  Textarea,
} from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { ClipboardList, Search } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useRef, useState } from "react";
import {
  type AssignableExamItem,
  type AssignClassExamsResponse,
  assignClassExams as defaultAssignClassExams,
  CLASSES_QUERY_KEYS,
  type ClassExamResultView,
  listAssignableClassExams as defaultListAssignableClassExams,
} from "../api";
import { resolveClassesErrorMessage } from "../utils";

const VALID_RESULT_VIEWS: readonly ClassExamResultView[] = [
  "SCORE",
  "SCORE_AND_ANSWERS",
  "FULL_SOLUTION",
];

function isResultView(value: string): value is ClassExamResultView {
  return (VALID_RESULT_VIEWS as readonly string[]).includes(value);
}

export interface AssignExamsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  classId: string;
  classCode: string;
  onAssigned: (result: AssignClassExamsResponse) => void;
  listAssignableClassExams?: typeof defaultListAssignableClassExams;
  assignClassExams?: typeof defaultAssignClassExams;
}

export function AssignExamsDialog({
  open,
  onOpenChange,
  classId,
  classCode,
  onAssigned,
  listAssignableClassExams = defaultListAssignableClassExams,
  assignClassExams = defaultAssignClassExams,
}: AssignExamsDialogProps) {
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
        size="lg"
        closeLabel={t("assignExamsDialog.close")}
        className="flex max-h-[88vh] flex-col overflow-hidden"
      >
        <DialogHeader className="shrink-0">
          <DialogTitle>{t("assignExamsDialog.title", { code: classCode })}</DialogTitle>
          <DialogDescription>{t("assignExamsDialog.description")}</DialogDescription>
        </DialogHeader>

        {open && (
          <AssignExamsBody
            classId={classId}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onAssigned={(res) => {
              onOpenChange(false);
              onAssigned(res);
            }}
            listAssignableClassExams={listAssignableClassExams}
            assignClassExams={assignClassExams}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function AssignExamsBody({
  classId,
  onCancel,
  onSubmittingChange,
  onAssigned,
  listAssignableClassExams,
  assignClassExams,
}: {
  classId: string;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onAssigned: (result: AssignClassExamsResponse) => void;
  listAssignableClassExams: typeof defaultListAssignableClassExams;
  assignClassExams: typeof defaultAssignClassExams;
}) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const locale = useLocale();

  const [searchInput, setSearchInput] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [attemptsInput, setAttemptsInput] = useState("1");
  const [resultView, setResultView] = useState<ClassExamResultView>("SCORE_AND_ANSWERS");
  const [resultViewTouched, setResultViewTouched] = useState(false);
  const [autoGrantNewMembers, setAutoGrantNewMembers] = useState(true);
  const [note, setNote] = useState("");
  const [examError, setExamError] = useState<string | null>(null);
  const [attemptsError, setAttemptsError] = useState<string | null>(null);
  const [noteError, setNoteError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const idempotencyKeyRef = useRef<string>(crypto.randomUUID());
  const rotateIdempotencyKey = () => {
    idempotencyKeyRef.current = crypto.randomUUID();
  };

  const examsQuery = useQuery({
    queryKey: CLASSES_QUERY_KEYS.assignableExams(classId),
    queryFn: () => listAssignableClassExams(classId),
  });

  const allExams = useMemo(() => examsQuery.data ?? [], [examsQuery.data]);
  const filteredExams = useMemo(() => {
    const q = searchInput.trim().toLowerCase();
    if (!q) return allExams;
    return allExams.filter(
      (exam) =>
        exam.code.toLowerCase().includes(q) || exam.name.toLowerCase().includes(q),
    );
  }, [allExams, searchInput]);

  const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds]);

  const resultViewOptions = useMemo(
    () =>
      VALID_RESULT_VIEWS.map((view) => ({
        value: view,
        label: t(`examAssignments.resultViews.${view}`),
      })),
    [t],
  );

  function toggleExam(exam: AssignableExamItem, checked: boolean) {
    setExamError(null);
    setSubmitError(null);
    rotateIdempotencyKey();
    setSelectedIds((prev) => {
      if (checked) {
        if (prev.includes(exam.examId)) return prev;
        const next = [...prev, exam.examId];
        if (prev.length === 0 && !resultViewTouched && isResultView(exam.defaultResultView)) {
          setResultView(exam.defaultResultView);
        }
        return next;
      }
      return prev.filter((id) => id !== exam.examId);
    });
  }

  const mutation = useMutation({
    mutationFn: (payload: {
      examIds: string[];
      attemptsGranted: number;
      resultView: ClassExamResultView;
      autoGrantNewMembers: boolean;
      note?: string;
    }) => {
      onSubmittingChange(true);
      return assignClassExams(classId, payload, idempotencyKeyRef.current);
    },
    onSettled: () => {
      onSubmittingChange(false);
    },
    onSuccess: (res) => {
      notify.success(
        t("assignExamsDialog.success", {
          examCount: res.assignments.length,
          grants: res.totalGrantsCreated,
        }),
      );
      onAssigned(res);
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

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setExamError(null);
    setAttemptsError(null);
    setNoteError(null);
    setSubmitError(null);

    let hasValidationError = false;
    if (selectedIds.length === 0) {
      setExamError(t("assignExamsDialog.examRequired"));
      hasValidationError = true;
    }

    const parsedAttempts = Number(attemptsInput.trim());
    if (
      !Number.isInteger(parsedAttempts) ||
      parsedAttempts < 1 ||
      parsedAttempts > 100
    ) {
      setAttemptsError(t("assignExamsDialog.attemptsInvalid"));
      hasValidationError = true;
    }

    const trimmedNote = note.trim();
    if (trimmedNote.length > 500) {
      setNoteError(t("assignExamsDialog.noteMax"));
      hasValidationError = true;
    }

    if (hasValidationError) return;

    mutation.mutate({
      examIds: selectedIds,
      attemptsGranted: parsedAttempts,
      resultView,
      autoGrantNewMembers,
      ...(trimmedNote ? { note: trimmedNote } : {}),
    });
  }

  return (
    <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col gap-3.5" noValidate>
      <div className="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto pr-1">
        {submitError && (
          <div
            role="alert"
            className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            {submitError}
          </div>
        )}

        <Input
          type="search"
          leadingIcon={<Search />}
          aria-label={t("assignExamsDialog.searchLabel")}
          placeholder={t("assignExamsDialog.searchPlaceholder")}
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          disabled={mutation.isPending}
        />

        <div className="flex items-center justify-between text-caption text-text-muted">
          <span>{t("assignExamsDialog.selectedCount", { count: selectedIds.length })}</span>
          {selectedIds.length > 0 && (
            <button
              type="button"
              className="font-medium text-primary-text hover:underline"
              onClick={() => {
                setSelectedIds([]);
                rotateIdempotencyKey();
              }}
              disabled={mutation.isPending}
            >
              {t("assignExamsDialog.clearSelection")}
            </button>
          )}
        </div>

        <div className="max-h-60 min-h-40 overflow-y-auto rounded-lg border border-border">
          {examsQuery.isPending ? (
            <div role="status" aria-label={common("loading")} className="flex flex-col gap-2 p-3">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : examsQuery.isError ? (
            <EmptyState
              icon={<ClipboardList />}
              title={t("assignExamsDialog.errorTitle")}
              description={t("assignExamsDialog.errorDescription")}
              action={
                <Button variant="secondary" size="sm" onClick={() => void examsQuery.refetch()}>
                  {common("retry")}
                </Button>
              }
            />
          ) : allExams.length === 0 ? (
            <EmptyState
              icon={<ClipboardList />}
              title={t("assignExamsDialog.emptyTitle")}
              description={t("assignExamsDialog.emptyDescription")}
            />
          ) : filteredExams.length === 0 ? (
            <EmptyState
              icon={<ClipboardList />}
              title={t("assignExamsDialog.emptyFilteredTitle")}
              description={t("assignExamsDialog.emptyFilteredDescription")}
            />
          ) : (
            <ul className="divide-y divide-border" aria-label={t("assignExamsDialog.listAriaLabel")}>
              {filteredExams.map((exam) => {
                const checkboxId = `assign-exam-${exam.examId}`;
                const checked = selectedSet.has(exam.examId);
                const kindLabel = pickName(exam.examKind, locale) || exam.examKind.code;
                return (
                  <li
                    key={exam.examId}
                    className="flex items-center gap-3 px-3 py-2.5 transition-colors hover:bg-subtle"
                  >
                    <Checkbox
                      id={checkboxId}
                      checked={checked}
                      onCheckedChange={(next) => toggleExam(exam, next === true)}
                      disabled={mutation.isPending}
                    />
                    <Label
                      htmlFor={checkboxId}
                      className="flex min-w-0 flex-1 cursor-pointer flex-col gap-0.5 font-normal"
                    >
                      <span data-exam-content className="truncate font-semibold text-text">
                        {exam.name}
                      </span>
                      <span className="truncate text-caption text-text-muted">
                        {[
                          exam.code,
                          kindLabel,
                          t("assignExamsDialog.versionBadge", { version: exam.versionNo }),
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    </Label>
                    {exam.activeInClass && (
                      <StatusChip tone="info">{t("assignExamsDialog.alreadyActive")}</StatusChip>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
        {examError && (
          <p role="alert" className="text-caption text-danger">
            {examError}
          </p>
        )}

        <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
          <FormField
            label={t("assignExamsDialog.attemptsLabel")}
            required
            hint={t("assignExamsDialog.attemptsHint")}
            error={attemptsError ?? undefined}
          >
            {(controlProps) => (
              <Input
                {...controlProps}
                type="number"
                min={1}
                max={100}
                step={1}
                value={attemptsInput}
                onChange={(event) => {
                  setAttemptsError(null);
                  setSubmitError(null);
                  rotateIdempotencyKey();
                  setAttemptsInput(event.target.value);
                }}
                disabled={mutation.isPending}
              />
            )}
          </FormField>

          <FormField label={t("assignExamsDialog.resultViewLabel")} required>
            {(controlProps) => (
              <Select
                {...controlProps}
                value={resultView}
                onValueChange={(val) => {
                  if (isResultView(val)) {
                    setResultViewTouched(true);
                    setSubmitError(null);
                    rotateIdempotencyKey();
                    setResultView(val);
                  }
                }}
                options={resultViewOptions}
                disabled={mutation.isPending}
              />
            )}
          </FormField>
        </div>

        <div className={cn("flex items-start gap-2.5 rounded-lg border border-border bg-subtle px-3 py-2.5")}>
          <Checkbox
            id="assign-auto-grant-checkbox"
            checked={autoGrantNewMembers}
            onCheckedChange={(next) => {
              setSubmitError(null);
              rotateIdempotencyKey();
              setAutoGrantNewMembers(next === true);
            }}
            disabled={mutation.isPending}
          />
          <Label
            htmlFor="assign-auto-grant-checkbox"
            className="flex cursor-pointer flex-col gap-0.5 font-normal"
          >
            <span className="font-medium text-text">{t("assignExamsDialog.autoGrantLabel")}</span>
            <span className="text-caption text-text-muted">
              {t("assignExamsDialog.autoGrantDescription")}
            </span>
          </Label>
        </div>

        <FormField label={t("assignExamsDialog.noteLabel")} error={noteError ?? undefined}>
          {(controlProps) => (
            <Textarea
              {...controlProps}
              rows={2}
              maxLength={500}
              placeholder={t("assignExamsDialog.notePlaceholder")}
              value={note}
              onChange={(event) => {
                setNoteError(null);
                setSubmitError(null);
                rotateIdempotencyKey();
                setNote(event.target.value);
              }}
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
          {t("assignExamsDialog.cancel")}
        </Button>
        <Button
          type="submit"
          loading={mutation.isPending}
          disabled={examsQuery.isPending || examsQuery.isError || allExams.length === 0}
        >
          {selectedIds.length > 0
            ? t("assignExamsDialog.submit", { count: selectedIds.length })
            : t("assignExamsDialog.submitEmpty")}
        </Button>
      </DialogFooter>
    </form>
  );
}
