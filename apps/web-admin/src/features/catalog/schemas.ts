import { z } from "zod";
import type { CatalogStatus } from "./api";
import { CATALOG_CODE_REGEX } from "./utils";

export interface CatalogValidationMessages {
  codeRequired: string;
  codePattern: string;
  nameViRequired: string;
  displayOrderInvalid: string;
  reasonRequired: string;
}

export interface GroupValidationMessages extends CatalogValidationMessages {
  levelsCount: string;
  levelsRequired: string;
  levelsDuplicate: string;
}

export interface GroupFormValues {
  code: string;
  nameVi: string;
  nameEn: string;
  status: CatalogStatus;
  kind: "SIMPLE" | "TREE";
  levels: string[];
  refGroupIds: string[];
  description: string;
  reason: string;
}

export function createGroupFormSchema(
  initialStatus: CatalogStatus | undefined,
  messages: GroupValidationMessages,
) {
  return z
    .object({
      code: z
        .string()
        .trim()
        .min(1, messages.codeRequired)
        .regex(CATALOG_CODE_REGEX, messages.codePattern),
      nameVi: z.string().trim().min(1, messages.nameViRequired),
      nameEn: z.string(),
      status: z.enum(["ACTIVE", "INACTIVE"]),
      kind: z.enum(["SIMPLE", "TREE"]),
      levels: z.array(z.string()),
      refGroupIds: z.array(z.string()),
      description: z.string(),
      reason: z.string(),
    })
    .superRefine((values, ctx) => {
      if (initialStatus === "ACTIVE" && values.status === "INACTIVE" && !values.reason.trim()) {
        ctx.addIssue({
          code: "custom",
          path: ["reason"],
          message: messages.reasonRequired,
        });
      }
      if (values.kind === "TREE") {
        if (values.levels.length < 2 || values.levels.length > 6) {
          ctx.addIssue({
            code: "custom",
            path: ["levels"],
            message: messages.levelsCount,
          });
          return;
        }
        const trimmedLevels = values.levels.map((l) => l.trim());
        if (trimmedLevels.some((l) => l.length === 0)) {
          ctx.addIssue({
            code: "custom",
            path: ["levels"],
            message: messages.levelsRequired,
          });
          return;
        }
        const normalized = new Set(trimmedLevels.map((l) => l.toLowerCase()));
        if (normalized.size !== trimmedLevels.length) {
          ctx.addIssue({
            code: "custom",
            path: ["levels"],
            message: messages.levelsDuplicate,
          });
        }
      }
    });
}

export interface ItemFormValues {
  code: string;
  displayOrder: string;
  nameVi: string;
  nameEn: string;
  status: CatalogStatus;
  description: string;
  reason: string;
}

export function createItemFormSchema(
  initialStatus: CatalogStatus | undefined,
  messages: CatalogValidationMessages,
) {
  return z
    .object({
      code: z
        .string()
        .trim()
        .min(1, messages.codeRequired)
        .regex(CATALOG_CODE_REGEX, messages.codePattern),
      displayOrder: z
        .string()
        .trim()
        .refine(
          (value) => value === "" || /^\d+$/.test(value),
          messages.displayOrderInvalid,
        ),
      nameVi: z.string().trim().min(1, messages.nameViRequired),
      nameEn: z.string(),
      status: z.enum(["ACTIVE", "INACTIVE"]),
      description: z.string(),
      reason: z.string(),
    })
    .superRefine((values, ctx) => {
      if (initialStatus === "ACTIVE" && values.status === "INACTIVE" && !values.reason.trim()) {
        ctx.addIssue({
          code: "custom",
          path: ["reason"],
          message: messages.reasonRequired,
        });
      }
    });
}

export interface CombinationValidationMessages extends CatalogValidationMessages {
  examRequired: string;
  subjectsRequired: string;
}

export interface CombinationFormValues extends ItemFormValues {
  examItemId: string;
  subjectItemIds: string[];
  sourceNote: string;
}

export function createCombinationFormSchema(
  initialStatus: CatalogStatus | undefined,
  messages: CombinationValidationMessages,
) {
  return z
    .object({
      code: z
        .string()
        .trim()
        .min(1, messages.codeRequired)
        .regex(CATALOG_CODE_REGEX, messages.codePattern),
      displayOrder: z
        .string()
        .trim()
        .refine(
          (value) => value === "" || /^\d+$/.test(value),
          messages.displayOrderInvalid,
        ),
      nameVi: z.string().trim().min(1, messages.nameViRequired),
      nameEn: z.string(),
      status: z.enum(["ACTIVE", "INACTIVE"]),
      examItemId: z.string().trim().min(1, messages.examRequired),
      subjectItemIds: z.array(z.string()).min(1, messages.subjectsRequired),
      sourceNote: z.string(),
      description: z.string(),
      reason: z.string(),
    })
    .superRefine((values, ctx) => {
      if (initialStatus === "ACTIVE" && values.status === "INACTIVE" && !values.reason.trim()) {
        ctx.addIssue({
          code: "custom",
          path: ["reason"],
          message: messages.reasonRequired,
        });
      }
    });
}

export interface TreeNodeFormValues extends ItemFormValues {
  refItemIds: string[];
}

export function createTreeNodeFormSchema(
  initialStatus: CatalogStatus | undefined,
  messages: CatalogValidationMessages,
) {
  return createItemFormSchema(initialStatus, messages).extend({
    refItemIds: z.array(z.string()),
  });
}
