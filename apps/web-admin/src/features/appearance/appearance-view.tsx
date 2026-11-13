"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  buildThemeVars,
  Button,
  Card,
  checkPaletteWarnings,
  cn,
  ConfirmDialog,
  deriveTokens,
  EmptyState,
  findPresetPalette,
  formatContrastRatio,
  FormField,
  getContrastRatio,
  Input,
  isValidHexColor,
  notify,
  PageHeader,
  pickOnColor,
  PRESET_PALETTES,
  Skeleton,
  StatusChip,
  Tabs,
  TabsList,
  WHITE_COLOR,
  TabsTrigger,
  type AppearancePalette,
  type CustomPaletteColors,
  type PresetId,
  type ThemeMode,
} from "@thi360/ui";
import { AlertTriangle, Check, CheckCircle2, SlidersVertical, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { type CSSProperties, useMemo, useState } from "react";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { getErrorStatus } from "@/lib/api/query-client";
import { revalidateAppearance } from "./actions";
import {
  APPEARANCE_QUERY_KEY,
  AppearanceApiError,
  applyAppearanceToDocument,
  loadAppearance as defaultLoadAppearance,
  resetAppearance as defaultResetAppearance,
  saveAppearance as defaultSaveAppearance,
  type AppearanceLoader,
  type AppearanceResetter,
  type AppearanceResponse,
  type AppearanceSaver,
} from "./api";

const PREVIEW_SCALE_STEPS = [50, 100, 200, 500, 600, 700, 800, 900] as const;
const DEFAULT_FALLBACK_PRESET = PRESET_PALETTES[0];
const ERROR_CODE_KEYS = {
  INTERNAL_ERROR: "INTERNAL_ERROR",
  RATE_LIMITED: "RATE_LIMITED",
  VALIDATION_FAILED: "VALIDATION_FAILED",
  VERSION_CONFLICT: "VERSION_CONFLICT",
} as const;
export interface AppearanceViewProps {
  loadAppearance?: AppearanceLoader;
  saveAppearance?: AppearanceSaver;
  resetAppearance?: AppearanceResetter;
}

interface AppearanceDraft {
  mode: ThemeMode;
  presetId: PresetId | "custom";
  customPrimary: string;
  customAccent: string;
  /** Giữ lại cặp màu tùy chỉnh đã nhập khi người dùng chuyển qua bộ cố định rồi quay lại. */
  hasCustomColors: boolean;
}

function draftFromServer(server: AppearanceResponse): AppearanceDraft {
  const fallbackPreset = findPresetPalette(server.palette.presetId) ?? DEFAULT_FALLBACK_PRESET;
  const custom = server.palette.custom;
  return {
    mode: server.mode,
    presetId: server.palette.presetId,
    customPrimary: (custom?.primary ?? fallbackPreset.primary).toUpperCase(),
    customAccent: (custom?.accent ?? fallbackPreset.accent).toUpperCase(),
    hasCustomColors: Boolean(custom),
  };
}

function buildPalettePayload(draft: AppearanceDraft): AppearancePalette {
  const customValid =
    isValidHexColor(draft.customPrimary) && isValidHexColor(draft.customAccent);
  const custom: CustomPaletteColors | undefined =
    draft.hasCustomColors && customValid
      ? {
          primary: draft.customPrimary.trim().toUpperCase(),
          accent: draft.customAccent.trim().toUpperCase(),
        }
      : undefined;

  if (draft.presetId === "custom") {
    return {
      presetId: "custom",
      custom: {
        primary: draft.customPrimary.trim().toUpperCase(),
        accent: draft.customAccent.trim().toUpperCase(),
      },
    };
  }
  return {
    presetId: draft.presetId,
    ...(custom ? { custom } : {}),
  };
}

export function AppearanceView({
  loadAppearance = defaultLoadAppearance,
  saveAppearance = defaultSaveAppearance,
  resetAppearance = defaultResetAppearance,
}: AppearanceViewProps) {
  const sessionState = useSession();
  const { can } = usePermissions();

  if (sessionState.status === "loading") {
    return <AppearanceSkeleton />;
  }

  if (sessionState.status === "ready" && !can("APPEARANCE.MODIFY")) {
    return <ForbiddenView />;
  }

  return (
    <AppearanceEditor
      loadAppearance={loadAppearance}
      saveAppearance={saveAppearance}
      resetAppearance={resetAppearance}
    />
  );
}

function AppearanceSkeleton() {
  const t = useTranslations();
  return (
    <div role="status" aria-label={t("common.loading")} className="flex flex-col gap-4">
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-5 w-96 max-w-full" />
      <div className="flex flex-wrap gap-4">
        <div className="flex min-w-0 flex-[3_1_520px] flex-col gap-4">
          <Skeleton className="h-24" />
          <Skeleton className="h-80" />
        </div>
        <Skeleton className="h-96 min-w-0 flex-[2_1_360px]" />
      </div>
    </div>
  );
}

function AppearanceEditor({
  loadAppearance,
  saveAppearance,
  resetAppearance,
}: Required<AppearanceViewProps>) {
  const t = useTranslations("appearance");
  const commonT = useTranslations("common");
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: APPEARANCE_QUERY_KEY,
    queryFn: loadAppearance,
  });

  const [draftOverride, setDraftOverride] = useState<{
    version: number;
    draft: AppearanceDraft;
  } | null>(null);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [staleConflict, setStaleConflict] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const serverData = query.data;
  const activeDraft = useMemo<AppearanceDraft | null>(() => {
    if (!serverData) return null;
    if (draftOverride && draftOverride.version === serverData.version) {
      return draftOverride.draft;
    }
    return draftFromServer(serverData);
  }, [draftOverride, serverData]);

  function updateDraft(updater: (current: AppearanceDraft) => AppearanceDraft) {
    if (!serverData || !activeDraft) return;
    setServerError(null);
    setDraftOverride({
      version: serverData.version,
      draft: updater(activeDraft),
    });
  }

  function resolveMutationErrorMessage(
    error: unknown,
    fallbackKey: "saveFailed" | "resetFailed",
  ): string {
    const code = error instanceof AppearanceApiError ? error.code : undefined;
    if (code && code in ERROR_CODE_KEYS) {
      return t(`errorCodes.${code as keyof typeof ERROR_CODE_KEYS}`);
    }
    const status = getErrorStatus(error);
    if (status === 400) return t("errorCodes.VALIDATION_FAILED");
    if (status === 429) return t("errorCodes.RATE_LIMITED");
    if (status !== null && status >= 500) return t("errorCodes.INTERNAL_ERROR");
    return t(fallbackKey);
  }

  const saveMutation = useMutation({
    mutationFn: (payload: Parameters<AppearanceSaver>[0]) => saveAppearance(payload),
    onSuccess: (saved) => {
      setStaleConflict(false);
      setServerError(null);
      queryClient.setQueryData(APPEARANCE_QUERY_KEY, saved);
      setDraftOverride(null);
      applyAppearanceToDocument(saved);
      void revalidateAppearance();
      notify.success(t("saveSuccess"));
    },
    onError: (error) => {
      const status = getErrorStatus(error);
      const code = error instanceof AppearanceApiError ? error.code : undefined;
      if (status === 412 || code === "VERSION_CONFLICT") {
        setStaleConflict(true);
        return;
      }
      if (status === 401 || status === 403) return;
      const msg = resolveMutationErrorMessage(error, "saveFailed");
      setServerError(msg);
      notify.error(msg);
    },
  });

  const resetMutation = useMutation({
    mutationFn: (payload: Parameters<AppearanceResetter>[0]) => resetAppearance(payload),
    onSuccess: (resetConfig) => {
      setStaleConflict(false);
      setServerError(null);
      setResetDialogOpen(false);
      queryClient.setQueryData(APPEARANCE_QUERY_KEY, resetConfig);
      setDraftOverride(null);
      applyAppearanceToDocument(resetConfig);
      void revalidateAppearance();
      notify.success(t("resetSuccess"));
    },
    onError: (error) => {
      setResetDialogOpen(false);
      const status = getErrorStatus(error);
      const code = error instanceof AppearanceApiError ? error.code : undefined;
      if (status === 412 || code === "VERSION_CONFLICT") {
        setStaleConflict(true);
        return;
      }
      if (status === 401 || status === 403) return;
      const msg = resolveMutationErrorMessage(error, "resetFailed");
      setServerError(msg);
      notify.error(msg);
    },
  });

  if (query.isPending) {
    return <AppearanceSkeleton />;
  }

  if (query.isError) {
    return (
      <EmptyState
        icon={<TriangleAlert />}
        title={t("errorTitle")}
        description={t("errorDescription")}
        action={<Button onClick={() => void query.refetch()}>{commonT("retry")}</Button>}
      />
    );
  }

  if (!serverData || !activeDraft) {
    return (
      <EmptyState
        icon={<SlidersVertical />}
        title={t("emptyTitle")}
        description={t("emptyDescription")}
        action={
          <Button
            loading={resetMutation.isPending}
            onClick={() => resetMutation.mutate(undefined)}
          >
            {t("emptyAction")}
          </Button>
        }
      />
    );
  }

  const isCustom = activeDraft.presetId === "custom";
  const currentPreset = findPresetPalette(activeDraft.presetId) ?? DEFAULT_FALLBACK_PRESET;
  const primaryValid = isValidHexColor(activeDraft.customPrimary);
  const accentValid = isValidHexColor(activeDraft.customAccent);
  const pickerPrimary = primaryValid
    ? activeDraft.customPrimary.trim().toLowerCase()
    : currentPreset.primary.toLowerCase();
  const pickerAccent = accentValid
    ? activeDraft.customAccent.trim().toLowerCase()
    : currentPreset.accent.toLowerCase();

  const resolvedPrimary = isCustom
    ? primaryValid
      ? activeDraft.customPrimary.trim().toUpperCase()
      : currentPreset.primary
    : currentPreset.primary;
  const resolvedAccent = isCustom
    ? accentValid
      ? activeDraft.customAccent.trim().toUpperCase()
      : currentPreset.accent
    : currentPreset.accent;

  const previewTokens = deriveTokens({
    primary: resolvedPrimary,
    accent: resolvedAccent,
    mode: activeDraft.mode,
  });

  const previewVars = buildThemeVars({
    palette: isCustom
      ? {
          presetId: "custom",
          custom: { primary: resolvedPrimary, accent: resolvedAccent },
        }
      : { presetId: currentPreset.id },
    mode: activeDraft.mode,
  }) as CSSProperties;

  const customWarnings =
    isCustom && primaryValid && accentValid
      ? checkPaletteWarnings({
          primary: resolvedPrimary,
          accent: resolvedAccent,
          mode: activeDraft.mode,
        })
      : [];

  const customSwatchTokens = deriveTokens({
    primary: pickerPrimary,
    accent: pickerAccent,
    mode: "light",
  });

  const invalidCustom = isCustom && (!primaryValid || !accentValid);
  const isMutating = saveMutation.isPending || resetMutation.isPending;
  const saveDisabled = invalidCustom || isMutating;

  const onPrimaryRatio = formatContrastRatio(
    getContrastRatio(previewTokens.onPrimary, previewTokens.primaryFill),
  );
  const onAccentRatio = formatContrastRatio(
    getContrastRatio(previewTokens.onAccent, previewTokens.accent500),
  );
  const linkRatio = formatContrastRatio(
    getContrastRatio(previewTokens.link, previewTokens.surface),
  );

  const onPrimaryTone =
    previewTokens.onPrimary.toLowerCase() === WHITE_COLOR ? t("whiteText") : t("darkText");
  const onAccentTone =
    previewTokens.onAccent.toLowerCase() === WHITE_COLOR ? t("whiteText") : t("darkText");
  function handleSelectPreset(presetId: PresetId) {
    updateDraft((current) => ({
      ...current,
      presetId,
    }));
  }

  function handleSelectCustom() {
    updateDraft((current) => {
      if (current.presetId === "custom") return current;
      const sourcePreset = findPresetPalette(current.presetId) ?? DEFAULT_FALLBACK_PRESET;
      if (!current.hasCustomColors) {
        return {
          ...current,
          presetId: "custom",
          customPrimary: sourcePreset.primary.toUpperCase(),
          customAccent: sourcePreset.accent.toUpperCase(),
          hasCustomColors: true,
        };
      }
      return {
        ...current,
        presetId: "custom",
      };
    });
  }

  function handleSave() {
    if (!serverData || !activeDraft || invalidCustom) return;
    saveMutation.mutate({
      palette: buildPalettePayload(activeDraft),
      mode: activeDraft.mode,
      version: serverData.version,
    });
  }

  async function handleReloadLatest() {
    setStaleConflict(false);
    setServerError(null);
    setDraftOverride(null);
    await query.refetch();
  }

  return (
    <>
      <PageHeader title={t("title")} description={t("description")} />

      <div className="flex flex-wrap items-start gap-4">
        <div className="flex min-w-0 flex-[3_1_520px] flex-col gap-4">
          <Card padding="md" className="flex flex-wrap items-center justify-between gap-3 p-5">
            <h2 id="sec-mode" className="text-title font-semibold text-text">
              {t("modeSection")}
            </h2>
            <Tabs
              variant="segmented"
              value={activeDraft.mode}
              onValueChange={(value) =>
                updateDraft((current) => ({
                  ...current,
                  mode: value === "dark" ? "dark" : "light",
                }))
              }
              className="w-full sm:w-70"
            >
              <TabsList aria-labelledby="sec-mode">
                <TabsTrigger value="light">{t("modeLight")}</TabsTrigger>
                <TabsTrigger value="dark">{t("modeDark")}</TabsTrigger>
              </TabsList>
            </Tabs>
          </Card>

          <Card padding="md" className="flex flex-col gap-3.5 p-5">
            <div>
              <h2 id="sec-palette" className="text-title font-semibold text-text">
                {t("paletteSection")}
              </h2>
              <p className="mt-0.5 text-body text-text-muted">{t("paletteDescription")}</p>
            </div>

            <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 xl:grid-cols-3">
              {PRESET_PALETTES.map((preset) => {
                const selected = activeDraft.presetId === preset.id;
                const tokens = deriveTokens({
                  primary: preset.primary,
                  accent: preset.accent,
                  mode: "light",
                });
                return (
                  <button
                    key={preset.id}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => handleSelectPreset(preset.id)}
                    className={cn(
                      "flex min-h-24 flex-col gap-2.5 rounded-lg bg-surface p-3 text-left transition-colors duration-150 motion-reduce:transition-none",
                      "focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none",
                      selected
                        ? "border-2 border-primary-600"
                        : "border border-border hover:border-border-strong",
                    )}
                  >
                    <span className="flex w-full items-center justify-between gap-2">
                      <span aria-hidden className="flex gap-1.5">
                        <span
                          className="flex h-8 w-11 items-center justify-center rounded-sm text-caption font-semibold"
                          style={{
                            backgroundColor: tokens.primaryFill,
                            color: tokens.onPrimary,
                          }}
                        >
                          Aa
                        </span>
                        <span
                          className="flex h-8 w-11 items-center justify-center rounded-sm text-caption font-semibold"
                          style={{
                            backgroundColor: tokens.accent500,
                            color: tokens.onAccent,
                          }}
                        >
                          Aa
                        </span>
                      </span>
                      {selected && (
                        <Check aria-hidden className="size-4 shrink-0 text-primary-text" />
                      )}
                    </span>
                    <span className="block">
                      <span className="block font-semibold text-text">
                        {t(`presets.${preset.id}`)}
                      </span>
                      <span className="block text-caption text-text-muted">
                        {preset.primary} · {preset.accent}
                      </span>
                    </span>
                  </button>
                );
              })}

              <button
                type="button"
                aria-pressed={isCustom}
                onClick={handleSelectCustom}
                className={cn(
                  "flex min-h-24 flex-col gap-2.5 rounded-lg bg-surface p-3 text-left transition-colors duration-150 motion-reduce:transition-none",
                  "focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none",
                  isCustom
                    ? "border-2 border-primary-600"
                    : "border border-dashed border-border-strong hover:border-text-muted",
                )}
              >
                <span className="flex w-full items-center justify-between gap-2">
                  <span aria-hidden className="flex gap-1.5">
                    <span
                      className="flex h-8 w-11 items-center justify-center rounded-sm text-caption font-semibold"
                      style={{
                        backgroundColor: customSwatchTokens.primaryFill,
                        color: customSwatchTokens.onPrimary,
                      }}
                    >
                      Aa
                    </span>
                    <span
                      className="flex h-8 w-11 items-center justify-center rounded-sm text-caption font-semibold"
                      style={{
                        backgroundColor: customSwatchTokens.accent500,
                        color: customSwatchTokens.onAccent,
                      }}
                    >
                      Aa
                    </span>
                  </span>
                  {isCustom && (
                    <Check aria-hidden className="size-4 shrink-0 text-primary-text" />
                  )}
                </span>
                <span className="block">
                  <span className="block font-semibold text-text">{t("customCardTitle")}</span>
                  <span className="block text-caption text-text-muted">
                    {t("customCardDescription")}
                  </span>
                </span>
              </button>
            </div>

            {isCustom && (
              <div className="flex flex-col gap-3.5 rounded-lg border border-border bg-page p-4">
                <div className="font-semibold text-text">{t("customCardTitle")}</div>
                <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
                  <FormField
                    label={t("primaryLabel")}
                    error={primaryValid ? undefined : t("primaryInvalid")}
                    hint={primaryValid ? t("primaryHelper") : undefined}
                  >
                    {(controlProps) => (
                      <div className="flex gap-2">
                        <input
                          type="color"
                          aria-label={t("primaryPickerAria")}
                          value={pickerPrimary}
                          onChange={(event) => {
                            const next = event.target.value.toUpperCase();
                            updateDraft((current) => ({
                              ...current,
                              customPrimary: next,
                              hasCustomColors: true,
                            }));
                          }}
                          className="h-10 w-12 shrink-0 cursor-pointer rounded-md border border-border bg-surface p-1"
                        />
                        <Input
                          {...controlProps}
                          value={activeDraft.customPrimary}
                          onChange={(event) => {
                            const next = event.target.value.toUpperCase();
                            updateDraft((current) => ({
                              ...current,
                              customPrimary: next,
                              hasCustomColors: true,
                            }));
                          }}
                          maxLength={7}
                          spellCheck={false}
                          className="uppercase"
                        />
                      </div>
                    )}
                  </FormField>

                  <FormField
                    label={t("accentLabel")}
                    error={accentValid ? undefined : t("accentInvalid")}
                    hint={accentValid ? t("accentHelper") : undefined}
                  >
                    {(controlProps) => (
                      <div className="flex gap-2">
                        <input
                          type="color"
                          aria-label={t("accentPickerAria")}
                          value={pickerAccent}
                          onChange={(event) => {
                            const next = event.target.value.toUpperCase();
                            updateDraft((current) => ({
                              ...current,
                              customAccent: next,
                              hasCustomColors: true,
                            }));
                          }}
                          className="h-10 w-12 shrink-0 cursor-pointer rounded-md border border-border bg-surface p-1"
                        />
                        <Input
                          {...controlProps}
                          value={activeDraft.customAccent}
                          onChange={(event) => {
                            const next = event.target.value.toUpperCase();
                            updateDraft((current) => ({
                              ...current,
                              customAccent: next,
                              hasCustomColors: true,
                            }));
                          }}
                          maxLength={7}
                          spellCheck={false}
                          className="uppercase"
                        />
                      </div>
                    )}
                  </FormField>
                </div>

                {primaryValid && accentValid && (
                  <ul
                    aria-live="polite"
                    className="m-0 flex list-none flex-col gap-1.5 p-0"
                  >
                    {customWarnings.length > 0 ? (
                      customWarnings.map((warning) => (
                        <li
                          key={warning.code}
                          className="flex items-center gap-2 rounded-md border border-border bg-warning-bg px-3 py-2 text-field-label text-warning"
                        >
                          <AlertTriangle aria-hidden className="size-4 shrink-0" />
                          <span>
                            {warning.code === "PRIMARY_USES_DARK_TEXT"
                              ? t("warnings.PRIMARY_USES_DARK_TEXT", { ratio: onPrimaryRatio })
                              : t(`warnings.${warning.code}`)}
                          </span>
                        </li>
                      ))
                    ) : (
                      <li className="flex items-center gap-2 rounded-md border border-primary-200 bg-success-bg px-3 py-2 text-field-label text-success">
                        <CheckCircle2 aria-hidden className="size-4 shrink-0" />
                        <span>{t("warningsOk")}</span>
                      </li>
                    )}
                  </ul>
                )}
              </div>
            )}
          </Card>

          {staleConflict && (
            <div
              role="alert"
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-danger bg-danger-bg p-3.5 text-field-label text-danger"
            >
              <span>{t("errorCodes.VERSION_CONFLICT")}</span>
              <Button variant="secondary" size="sm" onClick={() => void handleReloadLatest()}>
                {t("reloadLatest")}
              </Button>
            </div>
          )}

          {serverError && !staleConflict && (
            <div
              role="alert"
              className="rounded-lg border border-danger bg-danger-bg p-3.5 text-field-label text-danger"
            >
              {serverError}
            </div>
          )}

          <div className="flex flex-wrap justify-end gap-2.5">
            <Button
              variant="secondary"
              disabled={isMutating}
              onClick={() => setResetDialogOpen(true)}
            >
              {t("resetAction")}
            </Button>
            <Button
              variant="primary"
              loading={saveMutation.isPending}
              disabled={saveDisabled}
              onClick={handleSave}
            >
              {t("saveAction")}
            </Button>
          </div>
        </div>

        <Card
          padding="md"
          aria-labelledby="sec-preview"
          className="flex min-w-0 flex-[2_1_360px] flex-col gap-3.5"
        >
          <div>
            <h2 id="sec-preview" className="text-title font-semibold text-text">
              {t("previewTitle")}
            </h2>
            <p className="text-body text-text-muted">
              {t("previewSubtitle", {
                palette: isCustom ? t("customCardTitle") : t(`presets.${currentPreset.id}`),
                mode: activeDraft.mode === "dark" ? t("modeDark") : t("modeLight"),
              })}
            </p>
          </div>

          <div>
            <div className="mb-1.5 text-caption text-text-muted">{t("previewScaleLabel")}</div>
            <div className="grid grid-cols-8 overflow-hidden rounded-md border border-border">
              {PREVIEW_SCALE_STEPS.map((step) => {
                const bg = previewTokens.scale[step];
                const fg = pickOnColor(bg);
                return (
                  <div
                    key={step}
                    className="flex h-11 items-end justify-center pb-1 text-caption font-semibold tabular-nums"
                    style={{ backgroundColor: bg, color: fg }}
                  >
                    {step}
                  </div>
                );
              })}
            </div>
          </div>

          <div
            data-testid="appearance-preview"
            data-theme={activeDraft.mode}
            style={previewVars}
            className="flex flex-col gap-3 rounded-lg border border-border bg-page p-4 text-text"
          >
            <div className="flex items-center gap-2.5">
              <div
                aria-hidden
                className="flex size-9 items-center justify-center rounded-md bg-primary-fill text-field-label font-bold text-on-primary"
              >
                {t("previewLogoMark")}
              </div>
              <div className="font-bold text-text">{t("previewBrand")}</div>
            </div>

            <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4">
              <div className="text-h3 font-bold text-primary-900">{t("previewHeading")}</div>
              <p className="text-body text-text-muted">
                {t("previewMuted")}{" "}
                <span className="font-semibold text-primary-text underline">
                  {t("previewLink")}
                </span>
              </p>

              <div className="flex flex-wrap gap-2">
                <Button type="button">{t("previewPrimaryButton")}</Button>
                <Button type="button" variant="secondary">
                  {t("previewSecondaryButton")}
                </Button>
                <Button type="button" variant="exam">
                  {t("previewAccentButton")}
                </Button>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <span className="inline-flex h-10 items-center rounded-sm bg-primary-50 px-3 text-label font-semibold text-primary-text">
                  {t("previewNavItem")}
                </span>
                <StatusChip tone="success">{t("previewActiveChip")}</StatusChip>
                <StatusChip tone="danger">{t("previewDangerChip")}</StatusChip>
              </div>

              <Input
                readOnly
                value={t("previewInputValue")}
                aria-label={t("previewInputValue")}
                className="border-primary-600 ring-3 ring-primary-600/15"
              />
            </div>
          </div>

          <dl className="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-field-label">
            <dt className="text-text-muted">{t("contrastOnPrimaryLabel")}</dt>
            <dd className="m-0 text-text tabular-nums">
              {t("contrastValue", { tone: onPrimaryTone, ratio: onPrimaryRatio })}
            </dd>
            <dt className="text-text-muted">{t("contrastOnAccentLabel")}</dt>
            <dd className="m-0 text-text tabular-nums">
              {t("contrastValue", { tone: onAccentTone, ratio: onAccentRatio })}
            </dd>
            <dt className="text-text-muted">{t("contrastLinkLabel")}</dt>
            <dd className="m-0 text-text tabular-nums">{linkRatio}</dd>
          </dl>

          <p className="m-0 text-caption text-text-muted">{t("previewFootnote")}</p>
        </Card>
      </div>

      <ConfirmDialog
        open={resetDialogOpen}
        onOpenChange={setResetDialogOpen}
        tone="danger"
        title={t("resetConfirmTitle")}
        description={t("resetConfirmDescription")}
        confirmLabel={t("resetAction")}
        cancelLabel={t("cancel")}
        closeLabel={t("close")}
        loading={resetMutation.isPending}
        onConfirm={() =>
          resetMutation.mutate(
            serverData.version !== undefined ? { version: serverData.version } : undefined,
          )
        }
      />
    </>
  );
}
