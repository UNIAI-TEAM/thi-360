"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Badge,
  Button,
  Card,
  CardList,
  CardListItem,
  CardListSkeletonItem,
  Checkbox,
  ConfirmDialog,
  FormField,
  IconButton,
  Input,
  ListCard,
  LiveStatus,
  Select,
  type SelectOption,
  Skeleton,
  StatusChip,
  Textarea,
  cn,
  focusRing,
  focusRingInset,
  notify,
  useCardMode,
} from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  Check,
  CircleAlert,
  Lock,
  Save,
  WifiOff,
  X,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { Fragment, useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { Controller, type FieldErrors, useForm, useWatch } from "react-hook-form";

import { BlockRadioGroup } from "@/features/exams/components/block-radio-group";

import {
  bundleErrorCode,
  bundleFieldErrors,
  isBundleRevisionConflict,
  resolveBundleErrorMessage,
} from "../errors";
import {
  formatDecimalRange,
  formatIntRange,
  formatNumber,
  formatVnd,
  integerOf,
  priceDigits,
} from "../format";
import {
  useBundleCandidatesQuery,
  useCreateBundleMutation,
  useExamKindOptionsQuery,
  useUpdateBundleMutation,
} from "../queries";
import {
  BUNDLE_LIMITS,
  type BundleFormValues,
  buildBundleFormSchema,
  buildCreateBundleRequest,
  buildUpdateBundleRequest,
  createInitialBundleFormValues,
  detailToBundleFormValues,
} from "../schema";
import {
  BUNDLE_RESULT_VIEWS,
  type BundleCandidateResponse,
  type BundleDetailResponse,
  type BundleItemView,
  type ExamKindOption,
  type IntRange,
  type LocalizedName,
} from "../types";
import { BundleItemSaleChip } from "./bundle-status-chips";

interface SelectedItemDisplay {
  examId: string;
  code: string;
  name: string;
  buildMode: string | null | undefined;
  questionCount: IntRange | null | undefined;
  saleState: string;
  subjectLabel: string;
}

const BENEFITS = [
  "benefitAiExplanation",
  "benefitStrengthWeakness",
  "benefitPractice",
] as const;

const FIELD_MAP: Readonly<Record<string, keyof BundleFormValues>> = {
  code: "code",
  name: "name",
  summary: "summary",
  description: "description",
  examKindItemId: "examKindItemId",
  examIds: "examIds",
  listPriceVnd: "listPriceVnd",
  attemptsPerExam: "attemptsPerExam",
  benefitNote: "benefitNote",
  resultView: "resultView",
};

const FIELD_ORDER: readonly (keyof BundleFormValues)[] = [
  "examKindItemId",
  "code",
  "name",
  "summary",
  "description",
  "examIds",
  "listPriceVnd",
  "attemptsPerExam",
  "benefitNote",
  "resultView",
];

function mergeDescribedBy(...ids: (string | undefined | false)[]): string | undefined {
  const joined = ids.filter(Boolean).join(" ");
  return joined || undefined;
}

export function BundleForm({
  mode,
  formId,
  initialDetail,
  canModify,
  onDirtyChange,
  onConflict,
  onSaved,
}: {
  mode: "create" | "edit";
  formId: string;
  initialDetail?: BundleDetailResponse;
  canModify: boolean;
  onDirtyChange?: (isDirty: boolean) => void;
  onConflict?: () => Promise<void> | void;
  onSaved?: () => void;
}) {
  const t = useTranslations("bundles");
  const tv = useTranslations("bundles.validation");
  const tErrors = useTranslations("bundles.errors");
  const locale = useLocale();
  const uiLocale = normalizeUiLocale(locale);
  const router = useRouter();

  const isDiscontinued = initialDetail?.status === "DISCONTINUED";
  const isReadOnly = !canModify || isDiscontinued;

  const schema = useMemo(
    () =>
      buildBundleFormSchema({
        examKindRequired: tv("examKindRequired"),
        codeRequired: tv("codeRequired"),
        codeMax: tv("codeMax", { max: BUNDLE_LIMITS.code }),
        nameRequired: tv("nameRequired"),
        nameMax: tv("nameMax", { max: BUNDLE_LIMITS.name }),
        summaryMax: tv("summaryMax", { max: BUNDLE_LIMITS.summary }),
        descriptionMax: tv("descriptionMax", { max: BUNDLE_LIMITS.description }),
        itemsRange: tv("itemsRange", {
          min: BUNDLE_LIMITS.itemsMin,
          max: BUNDLE_LIMITS.itemsMax,
        }),
        priceInvalid: tv("priceInvalid"),
        attemptsRange: tv("attemptsRange", {
          min: BUNDLE_LIMITS.attemptsMin,
          max: BUNDLE_LIMITS.attemptsMax,
        }),
        benefitNoteMax: tv("benefitNoteMax", { max: BUNDLE_LIMITS.benefitNote }),
      }),
    [tv],
  );

  const defaultValues = useMemo(
    () =>
      initialDetail
        ? detailToBundleFormValues(initialDetail)
        : createInitialBundleFormValues(),
    [initialDetail],
  );

  const {
    control,
    register,
    handleSubmit,
    setValue,
    setError,
    setFocus,
    formState: { errors, isDirty },
  } = useForm<BundleFormValues>({
    defaultValues,
    resolver: zodResolver(schema),
    mode: "onTouched",
    shouldFocusError: false,
  });

  const values = useWatch({ control, defaultValue: defaultValues }) as BundleFormValues;
  const activeExamKindId = values.examKindItemId;

  const examKindsQuery = useExamKindOptionsQuery(true);
  const candidatesQuery = useBundleCandidatesQuery(
    activeExamKindId,
    Boolean(activeExamKindId),
  );
  const isCandidatesOffline =
    Boolean(activeExamKindId) &&
    candidatesQuery.fetchStatus === "paused" &&
    (!candidatesQuery.data || candidatesQuery.isPlaceholderData);

  const createMutation = useCreateBundleMutation();
  const updateMutation = useUpdateBundleMutation(initialDetail?.id ?? "");
  const isSaving = createMutation.isPending || updateMutation.isPending;

  const [submitError, setSubmitError] = useState<unknown>(null);
  const [orderLiveMessage, setOrderLiveMessage] = useState("");
  const [pendingExamKindId, setPendingExamKindId] = useState<string | null>(null);
  const [candidateCache, setCandidateCache] = useState<
    Record<string, BundleCandidateResponse>
  >({});
  const [pickerRef, isCardMode] = useCardMode<HTMLDivElement>(true, 500);
  const examKindWrapperRef = useRef<HTMLDivElement | null>(null);
  const descriptionRef = useRef<HTMLTextAreaElement | null>(null);
  const priceInputRef = useRef<HTMLInputElement | null>(null);
  const attemptsInputRef = useRef<HTMLInputElement | null>(null);
  const orderHeadingRef = useRef<HTMLHeadingElement | null>(null);
  const removeButtonRefs = useRef<Map<string, HTMLButtonElement>>(new Map());
  const pendingOrderFocusRef = useRef<{ targetExamId: string | null } | null>(null);

  useEffect(() => {
    onDirtyChange?.(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => {
    if (!isDirty || isReadOnly) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [isDirty, isReadOnly]);

  useEffect(() => {
    const pending = pendingOrderFocusRef.current;
    if (!pending) return;
    pendingOrderFocusRef.current = null;
    if (pending.targetExamId) {
      removeButtonRefs.current.get(pending.targetExamId)?.focus();
    } else {
      orderHeadingRef.current?.focus();
    }
  }, [values.examIds]);

  const examKindOptionsList = useMemo<ExamKindOption[]>(() => {
    const map = new Map<string, ExamKindOption>();
    for (const opt of examKindsQuery.data ?? []) {
      map.set(opt.id, opt);
    }
    if (initialDetail && !map.has(initialDetail.examKindItemId)) {
      map.set(initialDetail.examKindItemId, {
        id: initialDetail.examKindItemId,
        code: initialDetail.examKindCode,
        nameVi: initialDetail.examKindNameVi,
        nameEn: initialDetail.examKindNameEn,
      });
    }
    return Array.from(map.values());
  }, [examKindsQuery.data, initialDetail]);

  const examKindSelectOptions = useMemo<SelectOption[]>(
    () =>
      examKindOptionsList.map((opt) => ({
        value: opt.id,
        label:
          pickName({ nameVi: opt.nameVi, nameEn: opt.nameEn }, uiLocale) ||
          opt.code,
      })),
    [examKindOptionsList, uiLocale],
  );

  const examKindNameById = useMemo(() => {
    const map = new Map<string, string>();
    for (const opt of examKindOptionsList) {
      map.set(
        opt.id,
        pickName({ nameVi: opt.nameVi, nameEn: opt.nameEn }, uiLocale) ||
          opt.code,
      );
    }
    return map;
  }, [examKindOptionsList, uiLocale]);

  const subjectLabelOf = useMemo(
    () => (subjectName?: LocalizedName | null, subjectCode?: string | null) =>
      pickName(
        { nameVi: subjectName?.nameVi, nameEn: subjectName?.nameEn },
        uiLocale,
      ) ||
      subjectCode ||
      t("form.candidates.otherSubject"),
    [t, uiLocale],
  );

  const groupedCandidates = useMemo(() => {
    const candidates = candidatesQuery.data ?? [];
    const groups: {
      key: string;
      subjectLabel: string;
      items: BundleCandidateResponse[];
    }[] = [];
    const aliasToGroup = new Map<
      string,
      { key: string; subjectLabel: string; items: BundleCandidateResponse[] }
    >();
    for (const candidate of candidates) {
      const subjectLabel = subjectLabelOf(
        candidate.subjectName,
        candidate.subjectCode,
      );
      const normalizedCode = candidate.subjectCode?.trim().toUpperCase() || null;
      const normalizedItemId = candidate.subjectItemId?.trim() || null;
      const hasSubject = Boolean(
        normalizedCode ||
          normalizedItemId ||
          candidate.subjectName?.nameVi?.trim() ||
          candidate.subjectName?.nameEn?.trim(),
      );
      const normalizedLabel = hasSubject
        ? `label:${subjectLabel.trim().toLocaleLowerCase(uiLocale)}`
        : "__OTHER__";
      const aliases = [
        normalizedCode ? `code:${normalizedCode}` : null,
        normalizedItemId ? `id:${normalizedItemId}` : null,
        normalizedLabel,
      ].filter((alias): alias is string => Boolean(alias));

      let group:
        | { key: string; subjectLabel: string; items: BundleCandidateResponse[] }
        | undefined;
      for (const alias of aliases) {
        group = aliasToGroup.get(alias);
        if (group) break;
      }
      if (!group) {
        group = {
          key: aliases[0] ?? "__OTHER__",
          subjectLabel,
          items: [],
        };
        groups.push(group);
      }
      for (const alias of aliases) {
        if (!aliasToGroup.has(alias)) {
          aliasToGroup.set(alias, group);
        }
      }
      group.items.push(candidate);
    }
    return groups;
  }, [candidatesQuery.data, subjectLabelOf, uiLocale]);

  const itemMetadataById = useMemo(() => {
    const map = new Map<string, SelectedItemDisplay>();
    const addDetailItem = (item: BundleItemView) => {
      map.set(item.examId, {
        examId: item.examId,
        code: item.code ?? item.examId.slice(0, 8),
        name: item.name ?? item.code ?? item.examId,
        buildMode: item.buildMode,
        questionCount: item.questionCount,
        saleState: item.saleState,
        subjectLabel: subjectLabelOf(item.subjectName, item.subjectCode),
      });
    };
    const addCandidate = (c: BundleCandidateResponse) => {
      map.set(c.examId, {
        examId: c.examId,
        code: c.code,
        name: c.name,
        buildMode: c.buildMode,
        questionCount: c.questionCount,
        saleState: "ON_SALE",
        subjectLabel: subjectLabelOf(c.subjectName, c.subjectCode),
      });
    };

    for (const item of initialDetail?.items ?? []) {
      addDetailItem(item);
    }
    for (const cached of Object.values(candidateCache)) {
      addCandidate(cached);
    }
    for (const c of candidatesQuery.data ?? []) {
      addCandidate(c);
    }
    return map;
  }, [candidateCache, candidatesQuery.data, initialDetail?.items, subjectLabelOf]);

  const selectedItems = useMemo(
    () =>
      values.examIds.map(
        (id) =>
          itemMetadataById.get(id) ?? {
            examId: id,
            code: id.slice(0, 8),
            name: id,
            buildMode: null,
            questionCount: null,
            saleState: "ON_SALE",
            subjectLabel: t("form.candidates.otherSubject"),
          },
      ),
    [itemMetadataById, t, values.examIds],
  );

  const unavailableItems = useMemo(
    () =>
      (initialDetail?.items ?? []).filter(
        (item) => item.saleState !== "ON_SALE",
      ),
    [initialDetail?.items],
  );

  const sectionInfoId = useId();
  const sectionItemsId = useId();
  const sectionPricingId = useId();
  const sectionBenefitsId = useId();
  const pickerTargetId = useId();
  const descriptionAnchorId = useId();
  const priceAnchorId = useId();
  const attemptsAnchorId = useId();
  const itemsErrorId = useId();
  const summaryTitleId = useId();
  const pricePreviewId = useId();

  const handleExamKindSelect = (nextId: string) => {
    if (isReadOnly || nextId === values.examKindItemId) return;
    if (values.examIds.length > 0) {
      setPendingExamKindId(nextId);
      return;
    }
    setValue("examKindItemId", nextId, {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
  };

  const confirmExamKindChange = () => {
    if (!pendingExamKindId) return;
    setValue("examKindItemId", pendingExamKindId, {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
    setValue("examIds", [], {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
    setCandidateCache({});
    setPendingExamKindId(null);
  };

  const toggleCandidate = (candidate: BundleCandidateResponse) => {
    if (isReadOnly) return;
    setCandidateCache((prev) => ({ ...prev, [candidate.examId]: candidate }));
    const exists = values.examIds.includes(candidate.examId);
    const nextIds = exists
      ? values.examIds.filter((id) => id !== candidate.examId)
      : [...values.examIds, candidate.examId];
    setValue("examIds", nextIds, {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
  };

  const focusExamKindField = () => {
    examKindWrapperRef.current
      ?.querySelector<HTMLElement>('[role="combobox"]')
      ?.focus();
  };

  const focusExamIdsField = () => {
    const firstCheckbox = pickerRef.current?.querySelector<HTMLElement>(
      'button[role="checkbox"]:not([disabled]), input[type="checkbox"]:not([disabled])',
    );
    if (firstCheckbox) {
      firstCheckbox.focus();
      return;
    }
    pickerRef.current?.focus();
  };

  const focusCustomField = (field: keyof BundleFormValues) => {
    if (field === "examKindItemId") focusExamKindField();
    else if (field === "examIds") focusExamIdsField();
  };

  const moveSelected = (index: number, delta: -1 | 1) => {
    if (isReadOnly) return;
    const nextIndex = index + delta;
    if (nextIndex < 0 || nextIndex >= values.examIds.length) return;
    const nextIds = [...values.examIds];
    const [moved] = nextIds.splice(index, 1);
    nextIds.splice(nextIndex, 0, moved!);
    setValue("examIds", nextIds, {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
    const movedItem = selectedItems[index];
    const code = movedItem?.code ?? moved!;
    setOrderLiveMessage(
      t(delta < 0 ? "form.order.movedUp" : "form.order.movedDown", {
        code,
        position: nextIndex + 1,
      }),
    );
  };

  const removeSelected = (examId: string, index: number) => {
    if (isReadOnly) return;
    const nextIds = values.examIds.filter((id) => id !== examId);
    const removedItem = selectedItems[index];
    const code = removedItem?.code ?? examId;
    pendingOrderFocusRef.current = {
      targetExamId:
        nextIds.length > 0
          ? (nextIds[Math.min(index, nextIds.length - 1)] ?? null)
          : null,
    };
    setValue("examIds", nextIds, {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
    setOrderLiveMessage(
      t("form.order.removed", { code, count: nextIds.length }),
    );
  };

  const onInvalidSubmit = (fieldErrors: FieldErrors<BundleFormValues>) => {
    const firstInvalid = FIELD_ORDER.find((field) => Boolean(fieldErrors[field]));
    if (!firstInvalid) return;
    if (firstInvalid === "examKindItemId" || firstInvalid === "examIds") {
      focusCustomField(firstInvalid);
      return;
    }
    setFocus(firstInvalid);
  };

  const onValidSubmit = async (submitted: BundleFormValues) => {
    if (isReadOnly || isSaving) return;
    setSubmitError(null);
    try {
      if (mode === "create") {
        const created = await createMutation.mutateAsync(
          buildCreateBundleRequest(submitted),
        );
        notify.success(t("form.toast.created"));
        router.push(`/bundles/${created.id}`);
        return;
      }

      if (!initialDetail) return;
      await updateMutation.mutateAsync(
        buildUpdateBundleRequest(submitted, initialDetail.revision),
      );
      notify.success(t("form.toast.updated"));
      onSaved?.();
    } catch (err) {
      if (isBundleRevisionConflict(err)) {
        if (onConflict) {
          await onConflict();
        } else {
          setSubmitError(err);
        }
        return;
      }

      const code = bundleErrorCode(err);
      if (code === "BUNDLE_CODE_TAKEN") {
        setError(
          "code",
          { type: "server", message: tErrors("BUNDLE_CODE_TAKEN") },
          { shouldFocus: true },
        );
        return;
      }

      const srvFields = bundleFieldErrors(err);
      if (srvFields.length > 0) {
        let focused = false;
        for (const fe of srvFields) {
          const normalized = (fe.field ?? "")
            .replace(/\[.*$/, "")
            .replace(/\..*$/, "");
          const mapped = FIELD_MAP[normalized];
          if (mapped) {
            setError(
              mapped,
              { type: "server", message: resolveBundleErrorMessage(err, tErrors) },
              { shouldFocus: !focused },
            );
            if (!focused && (mapped === "examKindItemId" || mapped === "examIds")) {
              focusCustomField(mapped);
            }
            focused = true;
          }
        }
      }

      setSubmitError(err);
    }
  };

  const totalQuestionMin = selectedItems.reduce(
    (sum, item) => sum + (item.questionCount?.min ?? 0),
    0,
  );
  const totalQuestionMax = selectedItems.reduce(
    (sum, item) => sum + (item.questionCount?.max ?? 0),
    0,
  );
  const hasAnyQuestions = selectedItems.some((item) => item.questionCount != null);
  const totalQuestionDisplay = hasAnyQuestions
    ? formatIntRange(
        { min: totalQuestionMin, max: totalQuestionMax },
        locale,
      )
    : null;

  const parsedPrice = integerOf(values.listPriceVnd.trim());
  const parsedAttempts = integerOf(values.attemptsPerExam.trim());

  const publishChecklist = useMemo(() => {
    const items: { key: string; label: string; href: string }[] = [];
    if (values.examIds.length < BUNDLE_LIMITS.itemsMin) {
      items.push({
        key: "tooFew",
        label: t("form.aside.issues.tooFew", { min: BUNDLE_LIMITS.itemsMin }),
        href: `#${pickerTargetId}`,
      });
    }
    if (values.examIds.length > BUNDLE_LIMITS.itemsMax) {
      items.push({
        key: "tooMany",
        label: t("form.aside.issues.tooMany", { max: BUNDLE_LIMITS.itemsMax }),
        href: `#${pickerTargetId}`,
      });
    }
    if (!values.description.trim()) {
      items.push({
        key: "description",
        label: t("form.aside.issues.description"),
        href: `#${descriptionAnchorId}`,
      });
    }
    if (parsedPrice === null || parsedPrice <= 0) {
      items.push({
        key: "price",
        label: t("form.aside.issues.price"),
        href: `#${priceAnchorId}`,
      });
    }
    if (
      parsedAttempts === null ||
      parsedAttempts < BUNDLE_LIMITS.attemptsMin ||
      parsedAttempts > BUNDLE_LIMITS.attemptsMax
    ) {
      items.push({
        key: "attempts",
        label: t("form.aside.issues.attempts"),
        href: `#${attemptsAnchorId}`,
      });
    }
    if (selectedItems.some((item) => item.saleState !== "ON_SALE")) {
      items.push({
        key: "unavailableItem",
        label: t("form.aside.issues.unavailableItem"),
        href: `#${pickerTargetId}`,
      });
    }
    return items;
  }, [
    attemptsAnchorId,
    descriptionAnchorId,
    parsedAttempts,
    parsedPrice,
    pickerTargetId,
    priceAnchorId,
    selectedItems,
    t,
    values.description,
    values.examIds.length,
  ]);

  const focusChecklistTarget = (key: string) => {
    if (key === "description") {
      descriptionRef.current?.focus();
      return;
    }
    if (key === "price") {
      priceInputRef.current?.focus();
      return;
    }
    if (key === "attempts") {
      attemptsInputRef.current?.focus();
      return;
    }
    if (key === "unavailableItem") {
      const firstBad = selectedItems.find((item) => item.saleState !== "ON_SALE");
      const removeBtn = firstBad ? removeButtonRefs.current.get(firstBad.examId) : undefined;
      if (removeBtn) {
        removeBtn.focus();
        return;
      }
    }
    focusExamIdsField();
  };

  const counterText = (field: "summary" | "description" | "benefitNote") =>
    t("form.counter", {
      count: values[field].length,
      max: BUNDLE_LIMITS[field],
    });

  const scopeLabelOf = (scope: string) =>
    scope === "PART" ? t("scopes.PART") : t("scopes.SUBJECT");

  const renderCandidateStats = (c: BundleCandidateResponse) => {
    const qText = formatIntRange(c.questionCount, locale);
    const dText = formatIntRange(c.durationMinutes, locale);
    const pText = formatDecimalRange(c.maxPoints, locale);
    return {
      questions: qText
        ? t("form.candidates.questionsValue", { value: qText })
        : t("form.candidates.emptyMetric"),
      duration: dText
        ? t("form.candidates.durationValue", { value: dText })
        : t("form.candidates.emptyMetric"),
      points: pText
        ? t("form.candidates.pointsValue", { value: pText })
        : t("form.candidates.emptyMetric"),
    };
  };

  return (
    <div className="flex flex-col gap-4">
      {!canModify && (
        <p
          role="note"
          className="flex items-start gap-2 rounded-lg border border-border bg-subtle px-3.5 py-2.5 text-body text-text-secondary"
        >
          <Lock aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{t("form.banners.readOnly")}</span>
        </p>
      )}

      {isDiscontinued && canModify && (
        <p
          role="note"
          className="flex items-start gap-2 rounded-lg border border-border bg-subtle px-3.5 py-2.5 text-body text-text-secondary"
        >
          <Lock aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{t("form.banners.discontinuedReadOnly")}</span>
        </p>
      )}

      {initialDetail?.status === "PUBLISHED" &&
        initialDetail.saleState === "UNAVAILABLE" && (
          <div
            role="alert"
            className="flex items-start gap-3 rounded-lg border border-danger bg-danger-bg p-4 text-danger"
          >
            <AlertTriangle aria-hidden className="mt-0.5 size-5 shrink-0" />
            <div className="flex flex-col gap-1">
              <p className="text-body font-semibold">
                {t("form.banners.unavailableTitle")}
              </p>
              <p className="text-caption text-text">
                {t("form.banners.unavailableDescription")}
              </p>
              {unavailableItems.length > 0 && (
                <ul className="m-0 list-disc pl-5 text-caption text-text">
                  {unavailableItems.map((item) => (
                    <li key={item.examId}>
                      <span data-exam-content className="font-semibold">
                        {item.name ?? item.code ?? item.examId}
                      </span>
                      {item.code && (
                        <span data-exam-content className="ml-1 font-mono text-text-secondary">
                          ({item.code})
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}

      {initialDetail?.status === "PUBLISHED" &&
        initialDetail.hasUnpublishedChanges && (
          <div
            role="status"
            className="flex items-start gap-3 rounded-lg border border-warning bg-warning-bg p-4 text-warning"
          >
            <AlertTriangle aria-hidden className="mt-0.5 size-5 shrink-0" />
            <div className="flex flex-col gap-0.5">
              <p className="text-body font-semibold">
                {t("form.banners.unpublishedTitle", {
                  version: initialDetail.servingVersion ?? 1,
                })}
              </p>
              <p className="text-caption text-text-secondary">
                {t("form.banners.unpublishedDescription")}
              </p>
            </div>
          </div>
        )}

      {submitError !== null && submitError !== undefined && (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-lg border border-danger bg-danger-bg px-4 py-3 text-body text-danger"
        >
          <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{resolveBundleErrorMessage(submitError, tErrors)}</span>
        </div>
      )}

      <div className="flex flex-wrap items-start gap-5">
        <form
          id={formId}
          aria-label={t("form.ariaLabel")}
          noValidate
          onSubmit={(event) =>
            void handleSubmit(onValidSubmit, onInvalidSubmit)(event)
          }
          className="flex min-w-0 flex-[999_1_34rem] flex-col gap-4"
        >
          {/* Section 1: Basic Info */}
          <Section id={sectionInfoId} index={1} title={t("form.sections.info")}>
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(16rem,100%),1fr))] gap-4">
              <div className="flex min-w-0 flex-col gap-2">
                <FormField
                  label={t("form.fields.examKind")}
                  required
                  error={errors.examKindItemId?.message}
                  hint={t("form.hints.examKind")}
                >
                  {(controlProps) => (
                    <Controller
                      control={control}
                      name="examKindItemId"
                      render={({ field }) => (
                        <div ref={examKindWrapperRef}>
                          <Select
                            {...controlProps}
                            disabled={isReadOnly || examKindsQuery.isLoading}
                            placeholder={t("form.fields.examKindPlaceholder")}
                            options={examKindSelectOptions}
                            value={field.value || undefined}
                            onValueChange={handleExamKindSelect}
                          />
                        </div>
                      )}
                    />
                  )}
                </FormField>
                {examKindsQuery.isError && (
                  <div
                    role="alert"
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-danger/30 bg-danger-bg px-3 py-2 text-caption text-danger"
                  >
                    <span>{t("form.examKindsError")}</span>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => void examKindsQuery.refetch()}
                    >
                      {t("actions.retry")}
                    </Button>
                  </div>
                )}
              </div>

              <FormField
                label={t("form.fields.code")}
                required
                error={errors.code?.message}
                hint={t("form.hints.code")}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    maxLength={BUNDLE_LIMITS.code}
                    autoComplete="off"
                    spellCheck={false}
                    disabled={isReadOnly}
                    placeholder={t("form.fields.codePlaceholder")}
                    className="font-mono"
                    {...register("code")}
                  />
                )}
              </FormField>
            </div>

            <FormField
              label={t("form.fields.name")}
              required
              error={errors.name?.message}
            >
              {(controlProps) => (
                <Input
                  {...controlProps}
                  maxLength={BUNDLE_LIMITS.name}
                  disabled={isReadOnly}
                  placeholder={t("form.fields.namePlaceholder")}
                  {...register("name")}
                />
              )}
            </FormField>

            <FormField
              label={t("form.fields.summary")}
              error={errors.summary?.message}
              hint={counterText("summary")}
            >
              {(controlProps) => (
                <Input
                  {...controlProps}
                  maxLength={BUNDLE_LIMITS.summary}
                  disabled={isReadOnly}
                  placeholder={t("form.fields.summaryPlaceholder")}
                  {...register("summary")}
                />
              )}
            </FormField>

            <div id={descriptionAnchorId}>
              <FormField
                label={t("form.fields.description")}
                error={errors.description?.message}
                hint={`${counterText("description")} · ${t("form.hints.description")}`}
              >
                {(controlProps) => {
                  const { ref: descRegisterRef, ...descRegister } =
                    register("description");
                  return (
                    <Textarea
                      {...controlProps}
                      rows={3}
                      maxLength={BUNDLE_LIMITS.description}
                      disabled={isReadOnly}
                      placeholder={t("form.fields.descriptionPlaceholder")}
                      {...descRegister}
                      ref={(el) => {
                        descRegisterRef(el);
                        descriptionRef.current = el;
                      }}
                    />
                  );
                }}
              </FormField>
            </div>
          </Section>

          {/* Section 2: Member Exams Picker & Order */}
          <Section
            id={sectionItemsId}
            index={2}
            title={t("form.sections.items")}
            headerExtra={
              <StatusChip
                tone={
                  values.examIds.length >= BUNDLE_LIMITS.itemsMin &&
                  values.examIds.length <= BUNDLE_LIMITS.itemsMax
                    ? "success"
                    : "neutral"
                }
              >
                {t("form.candidates.selectedCountChip", {
                  count: values.examIds.length,
                  min: BUNDLE_LIMITS.itemsMin,
                  max: BUNDLE_LIMITS.itemsMax,
                })}
              </StatusChip>
            }
          >
            <p className="text-body text-text-secondary">
              {t("form.hints.items")}
            </p>

            <div
              id={pickerTargetId}
              tabIndex={-1}
              ref={pickerRef}
              className={cn(
                "overflow-hidden rounded-lg border border-border",
                focusRingInset,
              )}
            >
              {!activeExamKindId ? (
                <p className="p-4 text-body text-text-muted">
                  {t("form.candidates.selectKindFirst")}
                </p>
              ) : candidatesQuery.isLoading ? (
                <div aria-busy="true" aria-label={t("form.candidates.loading")}>
                  {isCardMode ? (
                    <CardList>
                      <CardListSkeletonItem />
                      <CardListSkeletonItem />
                    </CardList>
                  ) : (
                    <div className="flex flex-col gap-2 p-4">
                      <Skeleton className="h-6 w-full" />
                      <Skeleton className="h-6 w-full" />
                      <Skeleton className="h-6 w-4/5" />
                    </div>
                  )}
                </div>
              ) : isCandidatesOffline ? (
                <div
                  role="alert"
                  className="flex flex-wrap items-center justify-between gap-2 bg-subtle p-4 text-body text-text-secondary"
                >
                  <span className="flex items-center gap-2">
                    <WifiOff aria-hidden className="size-4 shrink-0" />
                    <span>{t("form.candidates.offline")}</span>
                  </span>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => void candidatesQuery.refetch()}
                  >
                    {t("actions.retry")}
                  </Button>
                </div>
              ) : candidatesQuery.isError ? (
                <div
                  role="alert"
                  className="flex flex-wrap items-center justify-between gap-2 bg-danger-bg p-4 text-body text-danger"
                >
                  <span>{resolveBundleErrorMessage(candidatesQuery.error, tErrors)}</span>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => void candidatesQuery.refetch()}
                  >
                    {t("actions.retry")}
                  </Button>
                </div>
              ) : groupedCandidates.length === 0 ? (
                <p className="p-4 text-body text-text-muted">
                  {t("form.candidates.empty")}
                </p>
              ) : isCardMode ? (
                <div className="divide-y divide-border">
                  {groupedCandidates.map((group) => (
                    <div key={group.key}>
                      <div className="bg-subtle px-4 py-2 text-overline font-semibold text-text-muted">
                        {group.subjectLabel}
                      </div>
                      <CardList>
                        {group.items.map((candidate) => {
                          const checked = values.examIds.includes(candidate.examId);
                          const stats = renderCandidateStats(candidate);
                          const checkboxId = `cand-${candidate.examId}`;
                          return (
                            <CardListItem
                              key={candidate.examId}
                              className={checked ? "bg-primary-50" : undefined}
                            >
                              <div className="relative z-1 pt-0.5">
                                <Checkbox
                                  id={checkboxId}
                                  checked={checked}
                                  disabled={isReadOnly}
                                  className="relative pointer-coarse:after:absolute pointer-coarse:after:-inset-3"
                                  aria-label={t("form.candidates.selectAria", {
                                    code: candidate.code,
                                  })}
                                  onCheckedChange={() => toggleCandidate(candidate)}
                                />
                              </div>
                              <div className="min-w-0 flex-1">
                                <ListCard
                                  title={
                                    <label
                                      htmlFor={checkboxId}
                                      className="cursor-pointer font-semibold text-text"
                                    >
                                      <span data-exam-content>{candidate.name}</span>
                                    </label>
                                  }
                                  caption={
                                    <span data-exam-content className="font-mono">
                                      {candidate.code}
                                    </span>
                                  }
                                  tags={
                                    <>
                                      <Badge>{scopeLabelOf(candidate.scope)}</Badge>
                                      {candidate.buildMode === "REVIEW" && (
                                        <StatusChip tone="info">
                                          {t("badges.review")}
                                        </StatusChip>
                                      )}
                                    </>
                                  }
                                  meta={[
                                    {
                                      label: t("form.candidates.columns.subject"),
                                      value: group.subjectLabel,
                                    },
                                    {
                                      label: t("form.candidates.columns.questions"),
                                      value: stats.questions,
                                    },
                                    {
                                      label: t("form.candidates.columns.duration"),
                                      value: stats.duration,
                                    },
                                    {
                                      label: t("form.candidates.columns.points"),
                                      value: stats.points,
                                    },
                                  ]}
                                />
                              </div>
                            </CardListItem>
                          );
                        })}
                      </CardList>
                    </div>
                  ))}
                </div>
              ) : (
                <div
                  role="region"
                  aria-label={t("form.candidates.tableAria")}
                  tabIndex={0}
                  className={cn("overflow-x-auto", focusRingInset)}
                >
                  <table
                    aria-label={t("form.candidates.tableAria")}
                    className="w-full border-separate border-spacing-0 text-field-label tabular-nums"
                  >
                    <thead>
                      <tr className="bg-table-head text-left text-caption font-semibold text-text-muted">
                        <th scope="col" className="w-10 border-b border-border py-2.5 pr-1.5 pl-3">
                          <span className="sr-only">
                            {t("form.candidates.columns.select")}
                          </span>
                        </th>
                        <th scope="col" className="border-b border-border px-2 py-2.5">
                          {t("form.candidates.columns.exam")}
                        </th>
                        <th scope="col" className="border-b border-border px-2 py-2.5">
                          {t("form.candidates.columns.scope")}
                        </th>
                        <th scope="col" className="border-b border-border px-2 py-2.5 text-right">
                          {t("form.candidates.columns.questions")}
                        </th>
                        <th scope="col" className="border-b border-border px-2 py-2.5 text-right">
                          {t("form.candidates.columns.duration")}
                        </th>
                        <th scope="col" className="border-b border-border py-2.5 pr-3 pl-2 text-right">
                          {t("form.candidates.columns.points")}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {groupedCandidates.map((group) => (
                        <Fragment key={group.key}>
                          <tr>
                            <th
                              scope="rowgroup"
                              colSpan={6}
                              className="border-b border-border bg-subtle px-3 py-2 text-left text-overline font-semibold text-text-muted"
                            >
                              {group.subjectLabel}
                            </th>
                          </tr>
                          {group.items.map((candidate) => {
                            const checked = values.examIds.includes(candidate.examId);
                            const stats = renderCandidateStats(candidate);
                            const checkboxId = `cand-${candidate.examId}`;
                            return (
                              <tr
                                key={candidate.examId}
                                className={cn(
                                  "transition-colors duration-150 motion-reduce:transition-none",
                                  checked ? "bg-primary-50" : "hover:bg-subtle",
                                )}
                              >
                                <td className="border-b border-border py-2.5 pr-1.5 pl-3">
                                  <Checkbox
                                    id={checkboxId}
                                    checked={checked}
                                    disabled={isReadOnly}
                                    className="relative pointer-coarse:after:absolute pointer-coarse:after:-inset-3"
                                    aria-label={t("form.candidates.selectAria", {
                                      code: candidate.code,
                                    })}
                                    onCheckedChange={() => toggleCandidate(candidate)}
                                  />
                                </td>
                                <td className="min-w-0 border-b border-border px-2 py-2.5">
                                  <label
                                    htmlFor={checkboxId}
                                    className="flex cursor-pointer flex-col gap-0.5"
                                  >
                                    <span className="flex flex-wrap items-center gap-1.5">
                                      <span
                                        data-exam-content
                                        className="font-semibold wrap-break-word text-text"
                                      >
                                        {candidate.name}
                                      </span>
                                      {candidate.buildMode === "REVIEW" && (
                                        <StatusChip tone="info">
                                          {t("badges.review")}
                                        </StatusChip>
                                      )}
                                    </span>
                                    <span className="text-caption wrap-break-word text-text-muted">
                                      <span data-exam-content className="font-mono">
                                        {candidate.code}
                                      </span>
                                      {candidate.listPriceVnd != null && (
                                        <>
                                          {" · "}
                                          {formatVnd(candidate.listPriceVnd, locale)}
                                        </>
                                      )}
                                    </span>
                                  </label>
                                </td>
                                <td className="border-b border-border px-2 py-2.5 text-caption text-text-secondary">
                                  {scopeLabelOf(candidate.scope)}
                                </td>
                                <td className="border-b border-border px-2 py-2.5 text-right text-caption whitespace-nowrap text-text">
                                  {stats.questions}
                                </td>
                                <td className="border-b border-border px-2 py-2.5 text-right text-caption whitespace-nowrap text-text">
                                  {stats.duration}
                                </td>
                                <td className="border-b border-border py-2.5 pr-3 pl-2 text-right text-caption whitespace-nowrap text-text">
                                  {stats.points}
                                </td>
                              </tr>
                            );
                          })}
                        </Fragment>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {errors.examIds?.message && (
              <p id={itemsErrorId} role="alert" className="text-caption text-danger">
                {errors.examIds.message}
              </p>
            )}

            <div className="flex flex-col gap-2 pt-1">
              <LiveStatus message={orderLiveMessage} />
              <h3
                ref={orderHeadingRef}
                tabIndex={-1}
                className={cn("w-fit rounded-sm text-label font-semibold text-text", focusRing)}
              >
                {t("form.order.title")}
              </h3>
              {selectedItems.length === 0 ? (
                <p className="text-caption text-text-muted">
                  {t("form.order.empty")}
                </p>
              ) : (
                <ol
                  aria-label={t("form.order.listAria")}
                  aria-describedby={errors.examIds?.message ? itemsErrorId : undefined}
                  className="m-0 flex list-none flex-col gap-2 p-0"
                >
                  {selectedItems.map((item, index) => (
                    <li
                      key={item.examId}
                      className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-surface px-3 py-2"
                    >
                      <span className="w-6 font-bold text-text-muted tabular-nums">
                        {index + 1}
                      </span>
                      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span data-exam-content className="font-medium text-text">
                            {item.name}
                          </span>
                          {item.buildMode === "REVIEW" && (
                            <StatusChip tone="info">
                              {t("badges.review")}
                            </StatusChip>
                          )}
                          <BundleItemSaleChip saleState={item.saleState} />
                        </div>
                        <span className="text-caption text-text-muted">
                          <span data-exam-content className="font-mono">
                            {item.code}
                          </span>
                          {" · "}
                          <span>{item.subjectLabel}</span>
                        </span>
                      </div>
                      <div className="flex items-center gap-1">
                        <IconButton
                          icon={<ArrowUp />}
                          aria-label={t("form.order.moveUp", { code: item.code })}
                          tooltip={t("form.order.moveUpTooltip")}
                          disabled={isReadOnly || index === 0}
                          onClick={() => moveSelected(index, -1)}
                        />
                        <IconButton
                          icon={<ArrowDown />}
                          aria-label={t("form.order.moveDown", { code: item.code })}
                          tooltip={t("form.order.moveDownTooltip")}
                          disabled={isReadOnly || index === selectedItems.length - 1}
                          onClick={() => moveSelected(index, 1)}
                        />
                        <IconButton
                          ref={(el) => {
                            if (el) removeButtonRefs.current.set(item.examId, el);
                            else removeButtonRefs.current.delete(item.examId);
                          }}
                          icon={<X />}
                          aria-label={t("form.order.remove", { code: item.code })}
                          tooltip={t("form.order.removeTooltip")}
                          disabled={isReadOnly}
                          onClick={() => removeSelected(item.examId, index)}
                        />
                      </div>
                    </li>
                  ))}
                </ol>
              )}
              <p className="text-caption text-text-muted">
                {t("form.order.hint")}
              </p>
            </div>
          </Section>

          {/* Section 3: Pricing & Attempts */}
          <Section id={sectionPricingId} index={3} title={t("form.sections.pricing")}>
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(16rem,100%),1fr))] gap-4">
              <div id={priceAnchorId} className="min-w-0">
                <FormField
                  label={t("form.fields.listPriceVnd")}
                  error={errors.listPriceVnd?.message}
                  hint={t("form.hints.listPriceVnd")}
                >
                  {(controlProps) => (
                    <Controller
                      control={control}
                      name="listPriceVnd"
                      render={({ field }) => {
                        const amount = integerOf(field.value.trim());
                        return (
                          <div className="flex flex-col gap-1">
                            <div className="flex items-center gap-2">
                              <Input
                                {...controlProps}
                                aria-describedby={mergeDescribedBy(
                                  controlProps["aria-describedby"],
                                  amount !== null && pricePreviewId,
                                )}
                                name={field.name}
                                ref={(el) => {
                                  field.ref(el);
                                  priceInputRef.current = el;
                                }}
                                type="text"
                                inputMode="numeric"
                                autoComplete="off"
                                disabled={isReadOnly}
                                className="text-right tabular-nums"
                                value={field.value}
                                onChange={(event) =>
                                  field.onChange(priceDigits(event.target.value))
                                }
                                onBlur={field.onBlur}
                              />
                              <span aria-hidden className="text-text-secondary">
                                {t("form.currency")}
                              </span>
                            </div>
                            {amount !== null && (
                              <p id={pricePreviewId} className="text-caption text-text-muted">
                                {formatVnd(amount, locale)}
                              </p>
                            )}
                          </div>
                        );
                      }}
                    />
                  )}
                </FormField>
              </div>

              <div id={attemptsAnchorId} className="min-w-0">
                <FormField
                  label={t("form.fields.attemptsPerExam")}
                  error={errors.attemptsPerExam?.message}
                  hint={t("form.hints.attemptsPerExam", {
                    min: BUNDLE_LIMITS.attemptsMin,
                    max: BUNDLE_LIMITS.attemptsMax,
                  })}
                >
                  {(controlProps) => (
                    <Controller
                      control={control}
                      name="attemptsPerExam"
                      render={({ field }) => (
                        <Input
                          {...controlProps}
                          name={field.name}
                          ref={(el) => {
                            field.ref(el);
                            attemptsInputRef.current = el;
                          }}
                          type="text"
                          inputMode="numeric"
                          autoComplete="off"
                          disabled={isReadOnly}
                          className="tabular-nums"
                          value={field.value}
                          onChange={(event) =>
                            field.onChange(priceDigits(event.target.value))
                          }
                          onBlur={field.onBlur}
                        />
                      )}
                    />
                  )}
                </FormField>
              </div>
            </div>
          </Section>

          {/* Section 4: Benefits & Result View */}
          <Section
            id={sectionBenefitsId}
            index={4}
            title={t("form.sections.benefits")}
          >
            <fieldset className="flex min-w-0 flex-col gap-1" disabled={isReadOnly}>
              <legend className="mb-1.5 text-field-label font-medium text-text-secondary">
                {t("form.benefits.legend")}
              </legend>
              {BENEFITS.map((name) => (
                <Controller
                  key={name}
                  control={control}
                  name={name}
                  render={({ field }) => (
                    <label className="flex min-h-11 cursor-pointer items-start gap-2.5 py-2">
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={(checked) => field.onChange(checked === true)}
                        onBlur={field.onBlur}
                        ref={field.ref}
                        disabled={isReadOnly}
                        className="mt-0.5"
                      />
                      <span className="flex flex-col">
                        <span className="font-medium text-text">
                          {t(`form.benefits.items.${name}.label`)}
                        </span>
                        <span className="text-caption text-text-muted">
                          {t(`form.benefits.items.${name}.description`)}
                        </span>
                      </span>
                    </label>
                  )}
                />
              ))}
            </fieldset>

            <Controller
              control={control}
              name="resultView"
              render={({ field }) => (
                <BlockRadioGroup
                  legend={t("form.resultView.legend")}
                  value={field.value}
                  disabled={isReadOnly}
                  onChange={field.onChange}
                  options={BUNDLE_RESULT_VIEWS.map((value) => ({
                    value,
                    label: t(`form.resultView.options.${value}.label`),
                    hint: t(`form.resultView.options.${value}.description`),
                  }))}
                />
              )}
            />

            <FormField
              label={t("form.fields.benefitNote")}
              error={errors.benefitNote?.message}
              hint={counterText("benefitNote")}
            >
              {(controlProps) => (
                <Textarea
                  {...controlProps}
                  rows={2}
                  maxLength={BUNDLE_LIMITS.benefitNote}
                  disabled={isReadOnly}
                  placeholder={t("form.fields.benefitNotePlaceholder")}
                  {...register("benefitNote")}
                />
              )}
            </FormField>

            {!isReadOnly && (
              <div className="flex justify-end border-t border-border pt-4">
                <Button type="submit" loading={isSaving}>
                  <Save />
                  <span>
                    {mode === "create"
                      ? t("actions.saveDraft")
                      : t("actions.saveChanges")}
                  </span>
                </Button>
              </div>
            )}
          </Section>
        </form>

        {/* Sticky summary sidebar */}
        <aside
          aria-labelledby={summaryTitleId}
          className="flex min-w-0 flex-[1_1_20rem] flex-col gap-3 lg:sticky lg:top-6"
        >
          <Card padding="lg" className="flex flex-col gap-4">
            <h2 id={summaryTitleId} className="text-title font-bold text-text">
              {t("form.aside.title")}
            </h2>

            <dl className="m-0 flex flex-col gap-2.5 text-body tabular-nums">
              <div className="flex items-baseline justify-between gap-3">
                <dt className="shrink-0 whitespace-nowrap text-text-muted">
                  {t("form.aside.examKind")}
                </dt>
                <dd className="m-0 min-w-0 text-right font-semibold wrap-break-word text-text">
                  {examKindNameById.get(values.examKindItemId) ??
                    t("form.aside.notSelected")}
                </dd>
              </div>
              <div className="flex items-baseline justify-between gap-3">
                <dt className="shrink-0 whitespace-nowrap text-text-muted">
                  {t("form.aside.examCount")}
                </dt>
                <dd className="m-0 text-right font-semibold text-text">
                  {t("form.aside.examCountValue", {
                    count: formatNumber(values.examIds.length, locale),
                  })}
                </dd>
              </div>
              <div className="flex items-baseline justify-between gap-3">
                <dt className="shrink-0 whitespace-nowrap text-text-muted">
                  {t("form.aside.totalQuestions")}
                </dt>
                <dd className="m-0 text-right font-semibold text-text">
                  {totalQuestionDisplay
                    ? t("form.aside.questionsValue", {
                        value: totalQuestionDisplay,
                      })
                    : t("form.aside.notAvailable")}
                </dd>
              </div>
              <div className="flex items-baseline justify-between gap-3">
                <dt className="shrink-0 whitespace-nowrap text-text-muted">
                  {t("form.aside.price")}
                </dt>
                <dd className="m-0 text-right font-semibold text-text">
                  {parsedPrice !== null
                    ? formatVnd(parsedPrice, locale)
                    : t("form.aside.notSet")}
                </dd>
              </div>
              <div className="flex items-baseline justify-between gap-3">
                <dt className="shrink-0 whitespace-nowrap text-text-muted">
                  {t("form.aside.attempts")}
                </dt>
                <dd className="m-0 text-right font-semibold text-text">
                  {parsedAttempts !== null
                    ? t("form.aside.attemptsValue", {
                        count: formatNumber(parsedAttempts, locale),
                      })
                    : t("form.aside.notSet")}
                </dd>
              </div>
            </dl>

            <div className="flex flex-col gap-2 border-t border-border pt-3">
              <h3 className="text-label font-semibold text-text">
                {t("form.aside.checklistTitle")}
              </h3>
              {publishChecklist.length === 0 ? (
                <p className="flex items-center gap-1.5 text-caption font-medium text-success">
                  <Check aria-hidden className="size-4 shrink-0" />
                  <span>{t("form.aside.ready")}</span>
                </p>
              ) : (
                <ul className="m-0 flex list-none flex-col gap-1.5 p-0 text-caption">
                  {publishChecklist.map((item) => (
                    <li key={item.key}>
                      <a
                        href={item.href}
                        onClick={(event) => {
                          event.preventDefault();
                          focusChecklistTarget(item.key);
                        }}
                        className={cn(
                          "font-medium text-danger hover:underline",
                          focusRing,
                        )}
                      >
                        {item.label}
                      </a>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <p className="text-caption text-text-muted">
              {t("form.aside.publishNote")}
            </p>
          </Card>
        </aside>
      </div>

      <ConfirmDialog
        open={pendingExamKindId !== null}
        onOpenChange={(open) => {
          if (!open) setPendingExamKindId(null);
        }}
        tone="primary"
        title={t("dialogs.changeExamKind.title", {
          nextKind:
            (pendingExamKindId && examKindNameById.get(pendingExamKindId)) ||
            "",
        })}
        description={t("dialogs.changeExamKind.description", {
          count: values.examIds.length,
          currentKind: examKindNameById.get(values.examKindItemId) ?? "",
        })}
        confirmLabel={t("dialogs.changeExamKind.confirm")}
        cancelLabel={t("dialogs.changeExamKind.cancel", {
          currentKind: examKindNameById.get(values.examKindItemId) ?? "",
        })}
        closeLabel={t("actions.close")}
        onConfirm={confirmExamKindChange}
      />
    </div>
  );
}

function Section({
  id,
  index,
  title,
  headerExtra,
  children,
}: {
  id: string;
  index: number;
  title: string;
  headerExtra?: ReactNode;
  children: ReactNode;
}) {
  const headingId = `${id}-heading`;
  return (
    <Card
      id={id}
      padding="lg"
      role="group"
      aria-labelledby={headingId}
      className="flex flex-col gap-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2
          id={headingId}
          className="flex items-center gap-2.5 text-title font-bold text-text"
        >
          <span
            aria-hidden
            className="inline-flex size-6.5 items-center justify-center rounded-full bg-primary-50 text-label text-primary-text"
          >
            {index}
          </span>
          <span>{title}</span>
        </h2>
        {headerExtra}
      </div>
      {children}
    </Card>
  );
}
