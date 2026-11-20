"use client";

import { Checkbox, Chip, Input, Label, cn, focusRing } from "@thi360/ui";
import { Search, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useMemo, useRef, useState } from "react";

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
}: {
  selectedIds: readonly string[];
  onChange: (nextIds: string[]) => void;
  disabled?: boolean;
  initialScopeItems?: readonly PromotionScopeItemView[];
  error?: string;
}) {
  const t = useTranslations("promotions");
  const locale = useLocale();
  const query = useExamKindOptionsQuery(true);

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
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <Label>{t("form.fields.examKindsLabel")}</Label>
        {disabled && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
      </div>

      {query.isLoading && <p className="text-caption text-text-muted">{t("common.loading")}</p>}
      {query.isError && <p className="text-caption text-danger">{t("form.picker.error")}</p>}

      {options.length > 0 && (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {options.map((item) => {
            const checked = selectedIds.includes(item.id);
            const displayName =
              locale === "en" && item.nameEn ? item.nameEn : item.nameVi || item.code;
            const checkboxId = `exam-kind-${item.id}`;
            return (
              <label
                key={item.id}
                htmlFor={checkboxId}
                className={cn(
                  "flex items-center gap-2.5 rounded-md border px-3 py-2 text-body transition-colors duration-150 motion-reduce:transition-none",
                  checked ? "border-primary-600 bg-primary-50" : "border-border bg-surface",
                  disabled ? "cursor-not-allowed opacity-70" : "cursor-pointer hover:bg-subtle",
                )}
              >
                <Checkbox
                  id={checkboxId}
                  checked={checked}
                  disabled={disabled}
                  onCheckedChange={() => {
                    if (!disabled) onChange(toggleId(selectedIds, item.id));
                  }}
                />
                <span className="min-w-0 flex-1 truncate">
                  <span data-exam-content className="font-medium text-text">
                    {displayName}
                  </span>
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
}: {
  selectedIds: readonly string[];
  onChange: (nextIds: string[]) => void;
  disabled?: boolean;
  initialScopeItems?: readonly PromotionScopeItemView[];
  error?: string;
}) {
  const t = useTranslations("promotions");
  const { can } = usePermissions();
  const canViewExams = can("EXAM.VIEW");
  const { searchText, debouncedQ, handleChange } = useDebouncedSearch();
  const [cachedLabels, setCachedLabels] = useState<Record<string, ExamPickerOption>>({});

  const query = useExamPickerQuery(debouncedQ, canViewExams && !disabled);

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
    <div className="flex flex-col gap-2.5">
      <div className="flex items-center gap-2">
        <Label>{t("form.fields.examsLabel")}</Label>
        {disabled && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
      </div>

      {selectedIds.length > 0 && (
        <div
          className="flex flex-wrap gap-1.5"
          aria-label={t("form.fields.examsSelected", { count: selectedIds.length })}
        >
          {selectedIds.map((id) => {
            const info = labelMap[id];
            const label = info ? `${info.code} - ${info.name}` : id.slice(0, 8);
            return (
              <span
                key={id}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-subtle px-2.5 py-1 text-caption text-text"
              >
                <span data-exam-content className="max-w-56 truncate">
                  {label}
                </span>
                {!disabled && (
                  <button
                    type="button"
                    aria-label={t("form.picker.removeItem", { label })}
                    onClick={() => onChange(selectedIds.filter((item) => item !== id))}
                    className={cn(
                      "inline-flex size-4 items-center justify-center rounded-full text-text-muted hover:bg-surface hover:text-text",
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

      {canViewExams && !disabled && (
        <div className="flex flex-col gap-2 rounded-md border border-border p-3">
          <div className="relative">
            <Search
              aria-hidden
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
            />
            <Input
              value={searchText}
              onChange={(event) => handleChange(event.target.value)}
              placeholder={t("form.fields.examsPlaceholder")}
              className="pl-9"
            />
          </div>

          {query.isLoading && <p className="text-caption text-text-muted">{t("form.picker.loading")}</p>}
          {query.isError && <p className="text-caption text-danger">{t("form.picker.error")}</p>}
          {!query.isLoading && !query.isError && (query.data?.length ?? 0) === 0 && (
            <p className="text-caption text-text-muted">{t("form.picker.empty")}</p>
          )}

          {(query.data?.length ?? 0) > 0 && (
            <ul
              role="list"
              className="max-h-48 divide-y divide-border overflow-y-auto rounded-md border border-border"
            >
              {query.data!.map((exam) => {
                const checked = selectedIds.includes(exam.id);
                const itemId = `exam-opt-${exam.id}`;
                return (
                  <li key={exam.id}>
                    <label
                      htmlFor={itemId}
                      className="flex cursor-pointer items-center gap-2.5 px-3 py-2 text-body hover:bg-subtle"
                    >
                      <Checkbox
                        id={itemId}
                        checked={checked}
                        onCheckedChange={() => {
                          setCachedLabels((prev) => ({ ...prev, [exam.id]: exam }));
                          onChange(toggleId(selectedIds, exam.id));
                        }}
                      />
                      <span className="min-w-0 flex-1 truncate">
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
}: {
  selectedClassIds: readonly string[];
  selectedUserIds: readonly string[];
  onChangeClasses: (nextIds: string[]) => void;
  onChangeUsers: (nextIds: string[]) => void;
  disabled?: boolean;
  initialClasses?: readonly PromotionAudienceClassView[];
  initialUsers?: readonly PromotionAudienceUserView[];
  error?: string;
}) {
  const t = useTranslations("promotions");
  const { can } = usePermissions();
  const canViewClasses = can("CLASS.VIEW");
  const canViewUsers = can("USER.VIEW");

  const classSearch = useDebouncedSearch();
  const userSearch = useDebouncedSearch();
  const [cachedClasses, setCachedClasses] = useState<Record<string, ClassPickerOption>>({});
  const [cachedUsers, setCachedUsers] = useState<Record<string, UserPickerOption>>({});

  const classQuery = useClassPickerQuery(
    classSearch.debouncedQ,
    canViewClasses && (!disabled || initialClasses.some((c) => !c.name)),
  );
  const userQuery = useUserPickerQuery(userSearch.debouncedQ, canViewUsers && !disabled);

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
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {/* Classes */}
        <div className="flex flex-col gap-2">
          <Label>{t("form.fields.classesLabel")}</Label>
          {selectedClassIds.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {selectedClassIds.map((id) => {
                const clsLabel = classLabelFor(id);
                return (
                  <span
                    key={id}
                    className="inline-flex items-center gap-1 rounded-full border border-border bg-subtle px-2.5 py-1 text-caption text-text"
                  >
                    <span data-exam-content className="max-w-44 truncate">
                      {clsLabel}
                    </span>
                    {!disabled && (
                      <button
                        type="button"
                        aria-label={t("form.picker.removeItem", { label: clsLabel })}
                        onClick={() => onChangeClasses(selectedClassIds.filter((item) => item !== id))}
                        className={cn(
                          "inline-flex size-4 items-center justify-center rounded-full text-text-muted hover:bg-surface hover:text-text",
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
            !disabled && (
              <div className="flex flex-col gap-2 rounded-md border border-border p-3">
                <div className="relative">
                  <Search
                    aria-hidden
                    className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
                  />
                  <Input
                    value={classSearch.searchText}
                    onChange={(event) => classSearch.handleChange(event.target.value)}
                    placeholder={t("form.fields.classesPlaceholder")}
                    className="pl-9"
                  />
                </div>
                {classQuery.isLoading && (
                  <p className="text-caption text-text-muted">{t("form.picker.loading")}</p>
                )}
                {classQuery.isError && (
                  <p className="text-caption text-danger">{t("form.picker.error")}</p>
                )}
                {!classQuery.isLoading && !classQuery.isError && (classQuery.data?.length ?? 0) === 0 && (
                  <p className="text-caption text-text-muted">{t("form.picker.empty")}</p>
                )}
                {(classQuery.data?.length ?? 0) > 0 && (
                  <ul
                    role="list"
                    className="max-h-40 divide-y divide-border overflow-y-auto rounded-md border border-border"
                  >
                    {classQuery.data!.map((cls) => {
                      const checked = selectedClassIds.includes(cls.id);
                      const id = `class-opt-${cls.id}`;
                      return (
                        <li key={cls.id}>
                          <label
                            htmlFor={id}
                            className="flex cursor-pointer items-center gap-2 px-2.5 py-1.5 text-caption hover:bg-subtle"
                          >
                            <Checkbox
                              id={id}
                              checked={checked}
                              onCheckedChange={() => {
                                setCachedClasses((prev) => ({ ...prev, [cls.id]: cls }));
                                onChangeClasses(toggleId(selectedClassIds, cls.id));
                              }}
                            />
                            <span data-exam-content className="min-w-0 flex-1 truncate font-medium text-text">
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
        <div className="flex flex-col gap-2">
          <Label>{t("form.fields.usersLabel")}</Label>
          {selectedUserIds.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {selectedUserIds.map((id) => {
                const usrLabel = userLabelFor(id);
                return (
                  <span
                    key={id}
                    className="inline-flex items-center gap-1 rounded-full border border-border bg-subtle px-2.5 py-1 text-caption text-text"
                  >
                    <span data-exam-content className="max-w-44 truncate">
                      {usrLabel}
                    </span>
                    {!disabled && (
                      <button
                        type="button"
                        aria-label={t("form.picker.removeItem", { label: usrLabel })}
                        onClick={() => onChangeUsers(selectedUserIds.filter((item) => item !== id))}
                        className={cn(
                          "inline-flex size-4 items-center justify-center rounded-full text-text-muted hover:bg-surface hover:text-text",
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
            !disabled && (
              <div className="flex flex-col gap-2 rounded-md border border-border p-3">
                <div className="relative">
                  <Search
                    aria-hidden
                    className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
                  />
                  <Input
                    value={userSearch.searchText}
                    onChange={(event) => userSearch.handleChange(event.target.value)}
                    placeholder={t("form.fields.usersPlaceholder")}
                    className="pl-9"
                  />
                </div>
                {userQuery.isLoading && (
                  <p className="text-caption text-text-muted">{t("form.picker.loading")}</p>
                )}
                {userQuery.isError && (
                  <p className="text-caption text-danger">{t("form.picker.error")}</p>
                )}
                {!userQuery.isLoading && !userQuery.isError && (userQuery.data?.length ?? 0) === 0 && (
                  <p className="text-caption text-text-muted">{t("form.picker.empty")}</p>
                )}
                {(userQuery.data?.length ?? 0) > 0 && (
                  <ul
                    role="list"
                    className="max-h-40 divide-y divide-border overflow-y-auto rounded-md border border-border"
                  >
                    {userQuery.data!.map((usr) => {
                      const checked = selectedUserIds.includes(usr.id);
                      const id = `user-opt-${usr.id}`;
                      return (
                        <li key={usr.id}>
                          <label
                            htmlFor={id}
                            className="flex cursor-pointer items-center gap-2 px-2.5 py-1.5 text-caption hover:bg-subtle"
                          >
                            <Checkbox
                              id={id}
                              checked={checked}
                              onCheckedChange={() => {
                                setCachedUsers((prev) => ({ ...prev, [usr.id]: usr }));
                                onChangeUsers(toggleId(selectedUserIds, usr.id));
                              }}
                            />
                            <span data-exam-content className="min-w-0 flex-1 truncate font-medium text-text">
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
