"use client";

import { Checkbox, cn } from "@thi360/ui";
import {
  Eye,
  GraduationCap,
  History,
  KeyRound,
  List,
  Pencil,
  Shield,
  SlidersVertical,
  Users,
  type LucideIcon,
} from "lucide-react";
import { useTranslations } from "next-intl";
import {
  resolveObjectName,
  resolvePermissionName,
  type RbacTranslator,
} from "../messages";
import type { PermissionObjectGroup, PermissionResponse } from "../types";

export interface PickerSelection {
  levels: Record<string, "VIEW" | "MODIFY">;
  extras: Record<string, boolean>;
}

const OBJECT_ICONS: Record<string, LucideIcon> = {
  USER: Users,
  CLASS: GraduationCap,
  ROLE: Shield,
  PERMISSION: KeyRound,
  CATALOG: List,
  AUDIT: History,
  APPEARANCE: SlidersVertical,
};

export function groupPermissionsByObject(
  permissions: readonly PermissionResponse[],
  t: RbacTranslator,
): PermissionObjectGroup[] {
  const byObject = new Map<string, PermissionResponse[]>();
  for (const perm of permissions) {
    const bucket = byObject.get(perm.groupCode);
    if (bucket) bucket.push(perm);
    else byObject.set(perm.groupCode, [perm]);
  }

  const groups: PermissionObjectGroup[] = [];
  for (const [objectCode, items] of byObject.entries()) {
    const viewCode = `${objectCode}.VIEW`;
    const modifyCode = `${objectCode}.MODIFY`;
    const viewPermission = items.find((p) => p.code === viewCode);
    const modifyPermission = items.find((p) => p.code === modifyCode);
    const extraPermissions = items.filter(
      (p) => p.code !== viewCode && p.code !== modifyCode,
    );
    const levelMode: PermissionObjectGroup["levelMode"] =
      viewPermission && modifyPermission
        ? "both"
        : modifyPermission
          ? "modify"
          : "view";

    groups.push({
      objectCode,
      objectName: resolveObjectName(t, objectCode, items[0]?.groupName),
      viewPermission,
      modifyPermission,
      extraPermissions,
      levelMode,
    });
  }

  return groups;
}

export function codesToPickerSelection(
  codes: readonly string[],
  groups: readonly PermissionObjectGroup[],
): PickerSelection {
  const codeSet = new Set(codes);
  const levels: Record<string, "VIEW" | "MODIFY"> = {};
  const extras: Record<string, boolean> = {};

  for (const group of groups) {
    if (group.modifyPermission && codeSet.has(group.modifyPermission.code)) {
      levels[group.objectCode] = "MODIFY";
    } else if (group.viewPermission && codeSet.has(group.viewPermission.code)) {
      levels[group.objectCode] = "VIEW";
    }

    const granted = levels[group.objectCode] !== undefined;
    if (granted) {
      for (const extra of group.extraPermissions) {
        if (codeSet.has(extra.code)) {
          extras[extra.code] = true;
        }
      }
    }
  }

  return { levels, extras };
}

export function pickerSelectionToCodes(
  selection: PickerSelection,
  groups: readonly PermissionObjectGroup[],
  lockedByScope = false,
): string[] {
  if (lockedByScope) return [];

  const codes: string[] = [];
  for (const group of groups) {
    const level = selection.levels[group.objectCode];
    if (!level) continue;

    if (level === "VIEW") {
      if (group.viewPermission) codes.push(group.viewPermission.code);
    } else {
      if (group.viewPermission) codes.push(group.viewPermission.code);
      if (group.modifyPermission) codes.push(group.modifyPermission.code);
    }

    for (const extra of group.extraPermissions) {
      if (selection.extras[extra.code]) {
        codes.push(extra.code);
      }
    }
  }

  return codes;
}

export function toggleObjectSelection(
  selection: PickerSelection,
  group: PermissionObjectGroup,
): PickerSelection {
  const nextLevels = { ...selection.levels };
  const nextExtras = { ...selection.extras };

  if (nextLevels[group.objectCode]) {
    delete nextLevels[group.objectCode];
    for (const extra of group.extraPermissions) {
      delete nextExtras[extra.code];
    }
  } else {
    nextLevels[group.objectCode] = group.levelMode === "modify" ? "MODIFY" : "VIEW";
  }

  return { levels: nextLevels, extras: nextExtras };
}

export function setObjectLevelSelection(
  selection: PickerSelection,
  objectCode: string,
  level: "VIEW" | "MODIFY",
): PickerSelection {
  return {
    ...selection,
    levels: {
      ...selection.levels,
      [objectCode]: level,
    },
  };
}

export function toggleExtraSelection(
  selection: PickerSelection,
  objectCode: string,
  extraCode: string,
): PickerSelection {
  if (!selection.levels[objectCode]) return selection;
  const nextExtras = { ...selection.extras };
  if (nextExtras[extraCode]) {
    delete nextExtras[extraCode];
  } else {
    nextExtras[extraCode] = true;
  }
  return { ...selection, extras: nextExtras };
}

export interface ObjectPermissionPickerProps {
  groups: readonly PermissionObjectGroup[];
  selectedCodes: readonly string[];
  onChange: (nextCodes: string[]) => void;
  /** Khi `true`, toàn bộ danh sách hiển thị ở trạng thái khóa (phạm vi Thi). */
  examLocked?: boolean;
  /** Chế độ chỉ xem: vẫn hiện dấu tick hiện có nhưng khóa chỉnh sửa. */
  readOnly?: boolean;
  /** Khóa theo từng đối tượng (ví dụ đã có đủ từ vai trò trong dialog gán quyền trực tiếp). */
  lockedObjects?: Record<string, string>;
  sectionTitle?: string;
}

export function ObjectPermissionPicker({
  groups,
  selectedCodes,
  onChange,
  examLocked = false,
  readOnly = false,
  lockedObjects,
  sectionTitle,
}: ObjectPermissionPickerProps) {
  const t = useTranslations("rbac");
  const rbacT: RbacTranslator = (key, values) => t(key, values);

  const selection = codesToPickerSelection(selectedCodes, groups);
  const objectCount = examLocked
    ? 0
    : groups.filter((g) => Boolean(selection.levels[g.objectCode])).length;
  const extraCount = examLocked
    ? 0
    : groups.reduce(
        (sum, g) =>
          sum +
          g.extraPermissions.filter(
            (e) => Boolean(selection.levels[g.objectCode]) && Boolean(selection.extras[e.code]),
          ).length,
        0,
      );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        {sectionTitle && (
          <h3 className="text-title font-semibold text-text">{sectionTitle}</h3>
        )}
        <div className="text-body text-text-muted">
          {t("picker.counter", { objectCount, extraCount })}
        </div>
      </div>

      <div className="flex flex-col gap-2">
        {groups.map((group) => {
          const Icon = OBJECT_ICONS[group.objectCode] ?? Shield;
          const lockedReason = lockedObjects?.[group.objectCode];
          const isObjectLocked = examLocked || readOnly || Boolean(lockedReason);
          const currentLevel = examLocked ? undefined : selection.levels[group.objectCode];
          const isGranted = Boolean(currentLevel);
          const isControlActive = isGranted && !isObjectLocked;

          return (
            <div
              key={group.objectCode}
              className={cn(
                "flex flex-col rounded-xl border px-3.5 py-2.5 transition-colors duration-150 motion-reduce:transition-none",
                isGranted && !examLocked
                  ? "border-primary-200 bg-surface"
                  : "border-border bg-subtle",
                examLocked && "opacity-70",
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <label
                  className={cn(
                    "flex min-h-11 min-w-0 flex-[1_1_220px] items-center gap-3",
                    isObjectLocked ? "cursor-not-allowed" : "cursor-pointer",
                  )}
                >
                  <Checkbox
                    checked={isGranted}
                    disabled={isObjectLocked}
                    aria-label={t("picker.grantAria", { name: group.objectName })}
                    onCheckedChange={() => {
                      if (isObjectLocked) return;
                      const next = toggleObjectSelection(selection, group);
                      onChange(pickerSelectionToCodes(next, groups, examLocked));
                    }}
                  />
                  <span
                    aria-hidden
                    className={cn(
                      "flex size-9 shrink-0 items-center justify-center rounded-md",
                      isGranted && !examLocked
                        ? "bg-primary-50 text-primary-text"
                        : "bg-surface text-text-muted",
                    )}
                  >
                    <Icon className="size-[18px]" />
                  </span>
                  <span className="min-w-0">
                    <span className="block font-semibold text-text">{group.objectName}</span>
                    {lockedReason && (
                      <span className="block text-caption text-text-muted">{lockedReason}</span>
                    )}
                  </span>
                </label>

                <div className="w-full shrink-0 sm:w-[276px]">
                  {group.levelMode === "both" ? (
                    <div
                      role="group"
                      aria-label={t("picker.levelGroupAria", { name: group.objectName })}
                      className={cn(
                        "grid h-11 grid-cols-2 gap-1 rounded-lg p-1",
                        isControlActive ? "bg-primary-100" : "bg-surface",
                      )}
                    >
                      <button
                        type="button"
                        aria-pressed={currentLevel === "VIEW"}
                        disabled={!isControlActive}
                        onClick={() => {
                          if (!isControlActive) return;
                          const next = setObjectLevelSelection(
                            selection,
                            group.objectCode,
                            "VIEW",
                          );
                          onChange(pickerSelectionToCodes(next, groups, examLocked));
                        }}
                        className={cn(
                          "inline-flex h-9 items-center justify-center gap-1.5 rounded-sm text-label transition-colors duration-150 motion-reduce:transition-none",
                          "focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none",
                          "disabled:cursor-not-allowed",
                          currentLevel === "VIEW"
                            ? "bg-surface font-semibold text-primary-text shadow-xs"
                            : isControlActive
                              ? "font-medium text-text-secondary hover:bg-surface/60"
                              : "font-medium text-text-muted",
                        )}
                      >
                        <Eye aria-hidden className="size-4" />
                        <span>{t("picker.levelView")}</span>
                      </button>
                      <button
                        type="button"
                        aria-pressed={currentLevel === "MODIFY"}
                        disabled={!isControlActive}
                        onClick={() => {
                          if (!isControlActive) return;
                          const next = setObjectLevelSelection(
                            selection,
                            group.objectCode,
                            "MODIFY",
                          );
                          onChange(pickerSelectionToCodes(next, groups, examLocked));
                        }}
                        className={cn(
                          "inline-flex h-9 items-center justify-center gap-1.5 rounded-sm text-label transition-colors duration-150 motion-reduce:transition-none",
                          "focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none",
                          "disabled:cursor-not-allowed",
                          currentLevel === "MODIFY"
                            ? "bg-surface font-semibold text-primary-text shadow-xs"
                            : isControlActive
                              ? "font-medium text-text-secondary hover:bg-surface/60"
                              : "font-medium text-text-muted",
                        )}
                      >
                        <Pencil aria-hidden className="size-4" />
                        <span>{t("picker.levelModify")}</span>
                      </button>
                    </div>
                  ) : (
                    <div
                      className={cn(
                        "flex h-11 items-center gap-2 rounded-lg px-3.5 text-label",
                        isGranted && !examLocked
                          ? "bg-primary-100 font-semibold text-primary-text"
                          : "bg-surface font-medium text-text-muted",
                      )}
                    >
                      {group.levelMode === "modify" ? (
                        <>
                          <Pencil aria-hidden className="size-4 shrink-0" />
                          <span>{t("picker.fixedModify")}</span>
                        </>
                      ) : (
                        <>
                          <Eye aria-hidden className="size-4 shrink-0" />
                          <span>{t("picker.fixedView")}</span>
                        </>
                      )}
                    </div>
                  )}
                </div>
              </div>

              {group.extraPermissions.length > 0 && (
                <div
                  className={cn(
                    "mt-2 flex flex-wrap items-center gap-2 border-t border-dashed pt-2.5 sm:pl-14",
                    isGranted && !examLocked ? "border-primary-200" : "border-border",
                  )}
                >
                  <span className="text-caption font-medium text-text-muted">
                    {t("picker.extrasLabel")}
                  </span>
                  {group.extraPermissions.map((extra) => {
                    const extraName = resolvePermissionName(rbacT, extra.code, extra.name);
                    const checked = !examLocked && isGranted && Boolean(selection.extras[extra.code]);
                    return (
                      <label
                        key={extra.code}
                        className={cn(
                          "inline-flex min-h-9 items-center gap-2 rounded-full border px-3 text-body transition-colors duration-150 motion-reduce:transition-none",
                          isControlActive ? "cursor-pointer" : "cursor-not-allowed",
                          checked
                            ? "border-primary-200 bg-primary-50 font-medium text-primary-text"
                            : "border-border bg-surface text-text-secondary",
                          !isControlActive && "opacity-75",
                        )}
                      >
                        <Checkbox
                          checked={checked}
                          disabled={!isControlActive}
                          onCheckedChange={() => {
                            if (!isControlActive) return;
                            const next = toggleExtraSelection(
                              selection,
                              group.objectCode,
                              extra.code,
                            );
                            onChange(pickerSelectionToCodes(next, groups, examLocked));
                          }}
                        />
                        <span>{extraName}</span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <p className="text-caption text-text-muted">
        {t("picker.helper")}
        {examLocked ? ` ${t("picker.examLockedHint")}` : ""}
      </p>
    </div>
  );
}
