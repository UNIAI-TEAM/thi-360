"use client";

import { Button, Checkbox, Chip, Input, Label, cn, focusRing } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { Search, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useId, useMemo, useRef, useState } from "react";

import { usePermissions } from "@/features/session/session-provider";

import {
  useClassPickerQuery,
  useExamKindOptionsQuery,
  useExamPickerQuery,
  useUserPickerQuery,
} from "../queries";
import type {
  ClassPickerOption,
  ExamKindOption,
  ExamPickerOption,
  PromotionAudienceClassView,
  PromotionAudienceUserView,
  PromotionScopeItemView,
  UserPickerOption,
} from "../types";

function toggleId(ids: readonly string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id];
}

function useDebouncedSearch() {
  const [searchText, setSearchText] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    };
  }, []);

  const handleChange = (next: string) => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setSearchText(next);
    if (next === "") {
      setDebouncedQ("");
      return;
    }
    timerRef.current = window.setTimeout(() => {
      setDebouncedQ(next);
      timerRef.current = null;
    }, 250);
  };

  return { searchText, debouncedQ, handleChange };
}

export function ExamKindPicker({
  selectedIds,
  onChange,
  disabled,
  initialScopeItems = [],
  error,
  inUseDescriptionId,
}: {
  selectedIds: readonly string[];
  onChange: (nextIds: string[]) => void;
  disabled?: boolean;
  initialScopeItems?: readonly PromotionScopeItemView[];
  error?: string;
  inUseDescriptionId?: string;
}) {
  const t = useTranslations("promotions");
  const uiLocale = normalizeUiLocale(useLocale());
  const query = useExamKindOptionsQuery(true);
  const labelId = useId();

  const options = useMemo(() => {
    const map = new Map<string, ExamKindOption>();
    for (const item of query.data ?? []) {
      map.set(item.id, item);
    }
    for (const fallback of initialScopeItems) {
      if (fallback.type === "EXAM_KIND" && !map.has(fallback.id)) {
        map.set(fallback.id, {
          id: fallback.id,
          code: fallback.code ?? fallback.id.slice(0, 8),
          nameVi: fallback.name ?? fallback.code ?? fallback.id,
          nameEn: fallback.nameEn,
        });
      }
    }
    return Array.from(map.values());
  }, [initialScopeItems, query.data]);

  return (
    <div
      role="group"
      aria-labelledby={labelId}
      aria-describedby={disabled && inUseDescriptionId ? inUseDescriptionId : undefined}
      className="flex flex-col gap-2"
    >
      <div className="flex items-center gap-2">
        <span id={labelId} className="text-field-label font-medium text-text-secondary">
          {t("form.fields.examKindsLabel")}
        </span>
        {disabled && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
      </div>

      {query.isLoading && <p className="text-caption text-text-muted">{t("common.loading")}</p>}
      {query.isError && (
        <div role="alert" className="flex flex-wrap items-center gap-2 text-caption text-danger">
          <span>{t("form.picker.error")}</span>
          <Button type="button" variant="secondary" size="sm" onClick={() => void query.refetch()}>
            {t("actions.retry")}
          </Button>
        </div>
      )}
      {!query.isLoading && !query.isError && options.length === 0 && (
        <p className="text-caption text-text-muted">{t("form.picker.empty")}</p>
      )}

      {options.length > 0 && (
        <div className="grid grid-cols-1 gap-2 @md:grid-cols-2">
          {options.map((item) => {
            const checked = selectedIds.includes(item.id);
            const displayName =
              pickName({ nameVi: item.nameVi, nameEn: item.nameEn }, uiLocale) || item.code;
            const checkboxId = `exam-kind-${item.id}`;
            return (
              <label
                key={item.id}
                htmlFor={checkboxId}
                aria-disabled={disabled || undefined}
                className={cn(
                  "flex items-center gap-2.5 rounded-md border px-3 py-2 text-body transition-colors duration-150 pointer-coarse:min-h-11 motion-reduce:transition-none",
                  checked ? "border-primary-600 bg-primary-50" : "border-border bg-surface",
                  disabled ? "cursor-not-allowed opacity-70" : "cursor-pointer hover:bg-subtle",
                )}
              >
                <Checkbox
                  id={checkboxId}
                  checked={checked}
                  disabled={disabled}
                  aria-describedby={disabled && inUseDescriptionId ? inUseDescriptionId : undefined}
                  onCheckedChange={() => {
                    if (!disabled) onChange(toggleId(selectedIds, item.id));
                  }}
                />
                <span className="min-w-0 flex-1 line-clamp-2 font-medium text-text">
                  {displayName}
                </span>
              </label>
            );
          })}
        </div>
      )}

      {error && (
        <p role="alert" className="text-caption text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

export function ExamScopePicker({
  selectedIds,
  onChange,
  disabled,
  initialScopeItems = [],
  error,
  inUseDescriptionId,
}: {
  selectedIds: readonly string[];
  onChange: (nextIds: string[]) => void;
  disabled?: boolean;
  initialScopeItems?: readonly PromotionScopeItemView[];
  error?: string;
  inUseDescriptionId?: string;
}) {
  const t = useTranslations("promotions");
  const { can } = usePermissions();
  const canViewExams = can("EXAM.VIEW");
  const { searchText, debouncedQ, handleChange } = useDebouncedSearch();
  const [cachedLabels, setCachedLabels] = useState<Record<string, ExamPickerOption>>({});
  const headingId = useId();
  const searchInputId = useId();
  const showSearch = canViewExams && !disabled;

  const query = useExamPickerQuery(debouncedQ, showSearch);

  const labelMap = useMemo(() => {
    const map: Record<string, ExamPickerOption> = { ...cachedLabels };
    for (const item of query.data ?? []) {
      map[item.id] = item;
    }
    for (const item of initialScopeItems) {
      if (item.type === "EXAM" && !map[item.id]) {
        map[item.id] = {
          id: item.id,
          code: item.code ?? item.id.slice(0, 8),
          name: item.name ?? item.code ?? item.id,
        };
      }
    }
    return map;
  }, [cachedLabels, initialScopeItems, query.data]);

  return (
    <div
      role="group"
      aria-labelledby={headingId}
      aria-describedby={disabled && inUseDescriptionId ? inUseDescriptionId : undefined}
      className="flex flex-col gap-2.5"
    >
      <div className="flex items-center gap-2">
        {showSearch ? (
          <Label id={headingId} htmlFor={searchInputId}>
            {t("form.fields.examsLabel")}
          </Label>
        ) : (
          <span id={headingId} className="text-field-label font-medium text-text-secondary">
            {t("form.fields.examsLabel")}
          </span>
        )}
        {disabled && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
      </div>

      {selectedIds.length > 0 && (
        <div
          role="group"
          aria-label={t("form.fields.examsSelected", { count: selectedIds.length })}
          className="flex flex-wrap gap-1.5"
        >
          {selectedIds.map((id) => {
            const info = labelMap[id];
            const label = info ? `${info.code} - ${info.name}` : id.slice(0, 8);
            return (
              <span
                key={id}
                className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-border bg-subtle px-2.5 py-1 text-caption text-text"
              >
                <span data-exam-content className="min-w-0 wrap-anywhere">
                  {label}
                </span>
                {!disabled && (
                  <button
                    type="button"
                    aria-label={t("form.picker.removeItem", { label })}
                    onClick={() => onChange(selectedIds.filter((item) => item !== id))}
                    className={cn(
                      "relative inline-flex size-4 items-center justify-center rounded-full text-text-muted hover:bg-surface hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-3.5",
                      focusRing,
                    )}
                  >
                    <X aria-hidden className="size-3" />
                  </button>
                )}
              </span>
            );
          })}
        </div>
      )}

      {!canViewExams && (
        <p className="rounded-md border border-border bg-subtle px-3 py-2 text-caption text-text-secondary">
          {t("form.fields.examsNoPermission")}
        </p>
      )}

      {showSearch && (
        <div className="flex flex-col gap-2 rounded-md border border-border p-3">
          <div className="relative">
            <Search
              aria-hidden
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
            />
            <Input
              id={searchInputId}
              value={searchText}
              onChange={(event) => handleChange(event.target.value)}
              placeholder={t("form.fields.examsPlaceholder")}
              className={cn("pl-9", searchText && "pr-9")}
            />
            {searchText && (
              <button
                type="button"
                aria-label={t("actions.clearSearch")}
                onClick={() => handleChange("")}
                className={cn(
                  "absolute top-1/2 right-2 inline-flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-text-muted hover:bg-subtle hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-2",
                  focusRing,
                )}
              >
                <X aria-hidden className="size-4" />
              </button>
            )}
          </div>

          {query.isLoading && <p className="text-caption text-text-muted">{t("form.picker.loading")}</p>}
          {query.isError && (
            <div role="alert" className="flex flex-wrap items-center gap-2 text-caption text-danger">
              <span>{t("form.picker.error")}</span>
              <Button type="button" variant="secondary" size="sm" onClick={() => void query.refetch()}>
                {t("actions.retry")}
              </Button>
            </div>
          )}
          {!query.isLoading && !query.isError && (query.data?.length ?? 0) === 0 && (
            <p className="text-caption text-text-muted">{t("form.picker.empty")}</p>
          )}

          {(query.data?.length ?? 0) > 0 && (
            <ul
              role="list"
              className="scroll-shadows-y max-h-48 divide-y divide-border overflow-y-auto rounded-md border border-border"
            >
              {query.data!.map((exam) => {
                const checked = selectedIds.includes(exam.id);
                const itemId = `exam-opt-${exam.id}`;
                return (
                  <li key={exam.id}>
                    <label
                      htmlFor={itemId}
                      className="flex cursor-pointer items-center gap-2.5 px-3 py-2 text-body hover:bg-subtle pointer-coarse:min-h-11"
                    >
                      <Checkbox
                        id={itemId}
                        checked={checked}
                        onCheckedChange={() => {
                          setCachedLabels((prev) => ({ ...prev, [exam.id]: exam }));
                          onChange(toggleId(selectedIds, exam.id));
                        }}
                      />
                      <span className="min-w-0 flex-1 line-clamp-2 wrap-anywhere">
                        <span data-exam-content className="font-medium text-text">
                          {exam.name}
                        </span>
                        <span data-exam-content className="ml-2 font-mono text-caption text-text-muted">
                          {exam.code}
                        </span>
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}

      {error && (
        <p role="alert" className="text-caption text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

export function AudienceTargetsPicker({
  selectedClassIds,
  selectedUserIds,
  onChangeClasses,
  onChangeUsers,
  disabled,
  initialClasses = [],
  initialUsers = [],
  error,
  inUseDescriptionId,
}: {
  selectedClassIds: readonly string[];
  selectedUserIds: readonly string[];
  onChangeClasses: (nextIds: string[]) => void;
  onChangeUsers: (nextIds: string[]) => void;
  disabled?: boolean;
  initialClasses?: readonly PromotionAudienceClassView[];
  initialUsers?: readonly PromotionAudienceUserView[];
  error?: string;
  inUseDescriptionId?: string;
}) {
  const t = useTranslations("promotions");
  const { can } = usePermissions();
  const canViewClasses = can("CLASS.VIEW");
  const canViewUsers = can("USER.VIEW");

  const classSearch = useDebouncedSearch();
  const userSearch = useDebouncedSearch();
  const [cachedClasses, setCachedClasses] = useState<Record<string, ClassPickerOption>>({});
  const [cachedUsers, setCachedUsers] = useState<Record<string, UserPickerOption>>({});

  const classHeadingId = useId();
  const classSearchId = useId();
  const userHeadingId = useId();
  const userSearchId = useId();

  const showClassSearch = canViewClasses && !disabled;
  const showUserSearch = canViewUsers && !disabled;
  const hasNullClassMetadata = initialClasses.some((c) => !c.name && !c.code);

  const classQuery = useClassPickerQuery(
    classSearch.debouncedQ,
    canViewClasses && (!disabled || hasNullClassMetadata),
    hasNullClassMetadata ? 100 : 20,
  );
  const userQuery = useUserPickerQuery(userSearch.debouncedQ, showUserSearch);

  const classById = useMemo(() => {
    const map: Record<string, ClassPickerOption> = { ...cachedClasses };
    for (const cls of classQuery.data ?? []) {
      map[cls.id] = cls;
    }
    return map;
  }, [cachedClasses, classQuery.data]);

  const userById = useMemo(() => {
    const map: Record<string, UserPickerOption> = { ...cachedUsers };
    for (const usr of userQuery.data ?? []) {
      map[usr.id] = usr;
    }
    return map;
  }, [cachedUsers, userQuery.data]);

  const classLabelFor = (id: string) => {
    const resolved = classById[id];
    if (resolved) return `${resolved.code} - ${resolved.name}`;
    const init = initialClasses.find((item) => item.id === id);
    if (init?.name || init?.code) {
      return init.code && init.name ? `${init.code} - ${init.name}` : (init.name ?? init.code!);
    }
    return t("audienceLabels.unknownClass", { id: id.slice(0, 8) });
  };

  const userLabelFor = (id: string) => {
    const resolved = userById[id];
    if (resolved) return `${resolved.fullName} (@${resolved.username})`;
    const init = initialUsers.find((item) => item.id === id);
    if (init?.fullName || init?.username) {
      return init.fullName && init.username
        ? `${init.fullName} (@${init.username})`
        : (init.fullName ?? init.username!);
    }
    return t("audienceLabels.unknownUser", { id: id.slice(0, 8) });
  };

  return (
    <div
      aria-describedby={disabled && inUseDescriptionId ? inUseDescriptionId : undefined}
      className="flex flex-col gap-3"
    >
      <div className="grid grid-cols-1 gap-4 @lg:grid-cols-2">
        {/* Classes */}
        <div role="group" aria-labelledby={classHeadingId} className="flex flex-col gap-2">
          {showClassSearch ? (
            <Label id={classHeadingId} htmlFor={classSearchId}>
              {t("form.fields.classesLabel")}
            </Label>
          ) : (
            <span id={classHeadingId} className="text-field-label font-medium text-text-secondary">
              {t("form.fields.classesLabel")}
            </span>
          )}
          {selectedClassIds.length > 0 && (
            <div
              role="group"
              aria-label={t("form.fields.classesSelected", { count: selectedClassIds.length })}
              className="flex flex-wrap gap-1.5"
            >
              {selectedClassIds.map((id) => {
                const clsLabel = classLabelFor(id);
                return (
                  <span
                    key={id}
                    className="inline-flex max-w-full items-center gap-1 rounded-full border border-border bg-subtle px-2.5 py-1 text-caption text-text"
                  >
                    <span data-exam-content className="min-w-0 wrap-anywhere">
                      {clsLabel}
                    </span>
                    {!disabled && (
                      <button
                        type="button"
                        aria-label={t("form.picker.removeItem", { label: clsLabel })}
                        onClick={() => onChangeClasses(selectedClassIds.filter((item) => item !== id))}
                        className={cn(
                          "relative inline-flex size-4 items-center justify-center rounded-full text-text-muted hover:bg-surface hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-3.5",
                          focusRing,
                        )}
                      >
                        <X aria-hidden className="size-3" />
                      </button>
                    )}
                  </span>
                );
              })}
            </div>
          )}

          {!canViewClasses ? (
            <p className="rounded-md border border-border bg-subtle px-3 py-2 text-caption text-text-secondary">
              {t("form.fields.classesNoPermission")}
            </p>
          ) : (
            showClassSearch && (
              <div className="flex flex-col gap-2 rounded-md border border-border p-3">
                <div className="relative">
                  <Search
                    aria-hidden
                    className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
                  />
                  <Input
                    id={classSearchId}
                    value={classSearch.searchText}
                    onChange={(event) => classSearch.handleChange(event.target.value)}
                    placeholder={t("form.fields.classesPlaceholder")}
                    className={cn("pl-9", classSearch.searchText && "pr-9")}
                  />
                  {classSearch.searchText && (
                    <button
                      type="button"
                      aria-label={t("actions.clearSearch")}
                      onClick={() => classSearch.handleChange("")}
                      className={cn(
                        "absolute top-1/2 right-2 inline-flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-text-muted hover:bg-subtle hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-2",
                        focusRing,
                      )}
                    >
                      <X aria-hidden className="size-4" />
                    </button>
                  )}
                </div>
                {classQuery.isLoading && (
                  <p className="text-caption text-text-muted">{t("form.picker.loading")}</p>
                )}
                {classQuery.isError && (
                  <div role="alert" className="flex flex-wrap items-center gap-2 text-caption text-danger">
                    <span>{t("form.picker.error")}</span>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => void classQuery.refetch()}
                    >
                      {t("actions.retry")}
                    </Button>
                  </div>
                )}
                {!classQuery.isLoading && !classQuery.isError && (classQuery.data?.length ?? 0) === 0 && (
                  <p className="text-caption text-text-muted">{t("form.picker.empty")}</p>
                )}
                {(classQuery.data?.length ?? 0) > 0 && (
                  <ul
                    role="list"
                    className="scroll-shadows-y max-h-40 divide-y divide-border overflow-y-auto rounded-md border border-border"
                  >
                    {classQuery.data!.map((cls) => {
                      const checked = selectedClassIds.includes(cls.id);
                      const id = `class-opt-${cls.id}`;
                      return (
                        <li key={cls.id}>
                          <label
                            htmlFor={id}
                            className="flex cursor-pointer items-center gap-2 px-2.5 py-1.5 text-caption hover:bg-subtle pointer-coarse:min-h-11"
                          >
                            <Checkbox
                              id={id}
                              checked={checked}
                              onCheckedChange={() => {
                                setCachedClasses((prev) => ({ ...prev, [cls.id]: cls }));
                                onChangeClasses(toggleId(selectedClassIds, cls.id));
                              }}
                            />
                            <span data-exam-content className="min-w-0 flex-1 line-clamp-2 wrap-anywhere font-medium text-text">
                              {cls.code} - {cls.name}
                            </span>
                          </label>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            )
          )}
        </div>

        {/* Users */}
        <div role="group" aria-labelledby={userHeadingId} className="flex flex-col gap-2">
          {showUserSearch ? (
            <Label id={userHeadingId} htmlFor={userSearchId}>
              {t("form.fields.usersLabel")}
            </Label>
          ) : (
            <span id={userHeadingId} className="text-field-label font-medium text-text-secondary">
              {t("form.fields.usersLabel")}
            </span>
          )}
          {selectedUserIds.length > 0 && (
            <div
              role="group"
              aria-label={t("form.fields.usersSelected", { count: selectedUserIds.length })}
              className="flex flex-wrap gap-1.5"
            >
              {selectedUserIds.map((id) => {
                const usrLabel = userLabelFor(id);
                return (
                  <span
                    key={id}
                    className="inline-flex max-w-full items-center gap-1 rounded-full border border-border bg-subtle px-2.5 py-1 text-caption text-text"
                  >
                    <span data-exam-content className="min-w-0 wrap-anywhere">
                      {usrLabel}
                    </span>
                    {!disabled && (
                      <button
                        type="button"
                        aria-label={t("form.picker.removeItem", { label: usrLabel })}
                        onClick={() => onChangeUsers(selectedUserIds.filter((item) => item !== id))}
                        className={cn(
                          "relative inline-flex size-4 items-center justify-center rounded-full text-text-muted hover:bg-surface hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-3.5",
                          focusRing,
                        )}
                      >
                        <X aria-hidden className="size-3" />
                      </button>
                    )}
                  </span>
                );
              })}
            </div>
          )}

          {!canViewUsers ? (
            <p className="rounded-md border border-border bg-subtle px-3 py-2 text-caption text-text-secondary">
              {t("form.fields.usersNoPermission")}
            </p>
          ) : (
            showUserSearch && (
              <div className="flex flex-col gap-2 rounded-md border border-border p-3">
                <div className="relative">
                  <Search
                    aria-hidden
                    className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
                  />
                  <Input
                    id={userSearchId}
                    value={userSearch.searchText}
                    onChange={(event) => userSearch.handleChange(event.target.value)}
                    placeholder={t("form.fields.usersPlaceholder")}
                    className={cn("pl-9", userSearch.searchText && "pr-9")}
                  />
                  {userSearch.searchText && (
                    <button
                      type="button"
                      aria-label={t("actions.clearSearch")}
                      onClick={() => userSearch.handleChange("")}
                      className={cn(
                        "absolute top-1/2 right-2 inline-flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-text-muted hover:bg-subtle hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-2",
                        focusRing,
                      )}
                    >
                      <X aria-hidden className="size-4" />
                    </button>
                  )}
                </div>
                {userQuery.isLoading && (
                  <p className="text-caption text-text-muted">{t("form.picker.loading")}</p>
                )}
                {userQuery.isError && (
                  <div role="alert" className="flex flex-wrap items-center gap-2 text-caption text-danger">
                    <span>{t("form.picker.error")}</span>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => void userQuery.refetch()}
                    >
                      {t("actions.retry")}
                    </Button>
                  </div>
                )}
                {!userQuery.isLoading && !userQuery.isError && (userQuery.data?.length ?? 0) === 0 && (
                  <p className="text-caption text-text-muted">{t("form.picker.empty")}</p>
                )}
                {(userQuery.data?.length ?? 0) > 0 && (
                  <ul
                    role="list"
                    className="scroll-shadows-y max-h-40 divide-y divide-border overflow-y-auto rounded-md border border-border"
                  >
                    {userQuery.data!.map((usr) => {
                      const checked = selectedUserIds.includes(usr.id);
                      const id = `user-opt-${usr.id}`;
                      return (
                        <li key={usr.id}>
                          <label
                            htmlFor={id}
                            className="flex cursor-pointer items-center gap-2 px-2.5 py-1.5 text-caption hover:bg-subtle pointer-coarse:min-h-11"
                          >
                            <Checkbox
                              id={id}
                              checked={checked}
                              onCheckedChange={() => {
                                setCachedUsers((prev) => ({ ...prev, [usr.id]: usr }));
                                onChangeUsers(toggleId(selectedUserIds, usr.id));
                              }}
                            />
                            <span data-exam-content className="min-w-0 flex-1 line-clamp-2 wrap-anywhere font-medium text-text">
                              {usr.fullName} (@{usr.username})
                            </span>
                          </label>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            )
          )}
        </div>
      </div>

      {error && (
        <p role="alert" className="text-caption text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
