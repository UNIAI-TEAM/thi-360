import { z } from "zod";

import {
  defaultPromotionSchedule,
  isoToVietnamDatetimeLocal,
  parseVietnamDatetimeLocalMs,
  vietnamDatetimeLocalToIso,
} from "./datetime";
import { parseDigits } from "./format";
import type {
  CreatePromotionRequest,
  PromotionAudience,
  PromotionDetail,
  PromotionDiscountType,
  PromotionKind,
  PromotionScope,
  PromotionStatus,
  UpdatePromotionRequest,
} from "./types";

const VOUCHER_CODE_REGEX = /^[A-Z0-9-]+$/;
export const MAX_INT32 = 2_147_483_647;
export const PROMOTION_NAME_MAX_LENGTH = 200;
export const PROMOTION_DESCRIPTION_MAX_LENGTH = 2000;
export const PROMOTION_CODE_MAX_LENGTH = 50;

export interface PromotionFormValues {
  kind: PromotionKind;
  name: string;
  description: string;
  code: string;
  status: PromotionStatus;
  discountType: PromotionDiscountType;
  discountValue: string;
  maxDiscountVnd: string;
  startsAt: string;
  endsAt: string;
  totalLimit: string;
  perUserLimit: string;
  combinable: boolean;
  scope: PromotionScope;
  examKindIds: string[];
  examIds: string[];
  audience: PromotionAudience;
  audienceClassIds: string[];
  audienceUserIds: string[];
}

export interface PromotionSchemaOptions {
  mode: "create" | "edit";
  usedCount: number;
  nowMs?: () => number;
}

export function buildPromotionFormSchema(
  tFieldErrors: (key: string) => string,
  options: PromotionSchemaOptions,
) {
  return z
    .object({
      kind: z.enum(["AUTO", "VOUCHER"]),
      name: z.string(),
      description: z.string(),
      code: z.string(),
      status: z.enum(["ACTIVE", "STOPPED"]),
      discountType: z.enum(["PERCENT", "AMOUNT"]),
      discountValue: z.string(),
      maxDiscountVnd: z.string(),
      startsAt: z.string(),
      endsAt: z.string(),
      totalLimit: z.string(),
      perUserLimit: z.string(),
      combinable: z.boolean(),
      scope: z.enum(["ALL", "EXAM_KIND", "EXAM", "BUNDLE"]),
      examKindIds: z.array(z.string()),
      examIds: z.array(z.string()),
      audience: z.enum(["ALL", "NEW_ACCOUNT", "SELECTED"]),
      audienceClassIds: z.array(z.string()),
      audienceUserIds: z.array(z.string()),
    })
    .superRefine((values, ctx) => {
      const isUsedEdit = options.mode === "edit" && options.usedCount > 0;
      const trimmedName = values.name.trim();
      if (!trimmedName) {
        ctx.addIssue({
          code: "custom",
          path: ["name"],
          message: tFieldErrors("nameRequired"),
        });
      } else if (trimmedName.length > PROMOTION_NAME_MAX_LENGTH) {
        ctx.addIssue({
          code: "custom",
          path: ["name"],
          message: tFieldErrors("nameTooLong"),
        });
      }

      if (values.description.trim().length > PROMOTION_DESCRIPTION_MAX_LENGTH) {
        ctx.addIssue({
          code: "custom",
          path: ["description"],
          message: tFieldErrors("descriptionTooLong"),
        });
      }

      const startMs = parseVietnamDatetimeLocalMs(values.startsAt);
      const endMs = parseVietnamDatetimeLocalMs(values.endsAt);

      const currentNowMs = options.nowMs ? options.nowMs() : Date.now();
      if (!isUsedEdit && startMs === null) {
        ctx.addIssue({
          code: "custom",
          path: ["startsAt"],
          message: tFieldErrors("startsAtRequired"),
        });
      }
      if (endMs === null) {
        ctx.addIssue({
          code: "custom",
          path: ["endsAt"],
          message: tFieldErrors("endsAtRequired"),
        });
      } else if (startMs !== null && endMs <= startMs) {
        ctx.addIssue({
          code: "custom",
          path: ["endsAt"],
          message: tFieldErrors("endsAfterStarts"),
        });
      } else if (options.mode === "create" && endMs <= currentNowMs) {
        ctx.addIssue({
          code: "custom",
          path: ["endsAt"],
          message: tFieldErrors("endsAtFuture"),
        });
      }

      const totalLimitRaw = values.totalLimit.trim();
      if (totalLimitRaw) {
        const parsedLimit = /^\d+$/.test(totalLimitRaw) ? Number(totalLimitRaw) : NaN;
        if (!Number.isSafeInteger(parsedLimit) || parsedLimit < 1) {
          ctx.addIssue({
            code: "custom",
            path: ["totalLimit"],
            message: tFieldErrors("totalLimitMin"),
          });
        } else if (parsedLimit > MAX_INT32) {
          ctx.addIssue({
            code: "custom",
            path: ["totalLimit"],
            message: tFieldErrors("totalLimitMax"),
          });
        } else if (options.mode === "edit" && parsedLimit < options.usedCount) {
          ctx.addIssue({
            code: "custom",
            path: ["totalLimit"],
            message: tFieldErrors("totalLimitTooLow"),
          });
        }
      }

      if (isUsedEdit) return;

      const parsedDiscount =
        values.discountType === "PERCENT"
          ? /^\d+$/.test(values.discountValue.trim())
            ? Number(values.discountValue.trim())
            : null
          : parseDigits(values.discountValue);

      if (parsedDiscount === null || parsedDiscount < 1) {
        ctx.addIssue({
          code: "custom",
          path: ["discountValue"],
          message:
            values.discountType === "PERCENT"
              ? tFieldErrors("discountPercentRange")
              : tFieldErrors("discountAmountMin"),
        });
      } else if (values.discountType === "PERCENT" && (parsedDiscount < 1 || parsedDiscount > 100)) {
        ctx.addIssue({
          code: "custom",
          path: ["discountValue"],
          message: tFieldErrors("discountPercentRange"),
        });
      }

      if (values.discountType === "PERCENT" && values.maxDiscountVnd.trim()) {
        const parsedCap = parseDigits(values.maxDiscountVnd);
        if (parsedCap === null || parsedCap < 1) {
          ctx.addIssue({
            code: "custom",
            path: ["maxDiscountVnd"],
            message: tFieldErrors("maxDiscountMin"),
          });
        }
      }

      if (values.kind === "VOUCHER") {
        const upperCode = values.code.trim().toUpperCase();
        if (!upperCode) {
          ctx.addIssue({
            code: "custom",
            path: ["code"],
            message: tFieldErrors("codeRequired"),
          });
        } else if (
          upperCode.length < 3 ||
          upperCode.length > PROMOTION_CODE_MAX_LENGTH ||
          !VOUCHER_CODE_REGEX.test(upperCode)
        ) {
          ctx.addIssue({
            code: "custom",
            path: ["code"],
            message: tFieldErrors("codeFormat"),
          });
        }

        if (values.perUserLimit.trim()) {
          const perUser = /^\d+$/.test(values.perUserLimit.trim()) ? Number(values.perUserLimit.trim()) : NaN;
          if (!Number.isSafeInteger(perUser) || perUser < 1) {
            ctx.addIssue({
              code: "custom",
              path: ["perUserLimit"],
              message: tFieldErrors("perUserLimitMin"),
            });
          } else if (perUser > MAX_INT32) {
            ctx.addIssue({
              code: "custom",
              path: ["perUserLimit"],
              message: tFieldErrors("perUserLimitMax"),
            });
          }
        }

        if (
          values.audience === "SELECTED" &&
          values.audienceClassIds.length === 0 &&
          values.audienceUserIds.length === 0
        ) {
          ctx.addIssue({
            code: "custom",
            path: ["audienceClassIds"],
            message: tFieldErrors("audienceTargetsRequired"),
          });
        }
      }

      if (values.scope === "EXAM_KIND" && values.examKindIds.length === 0) {
        ctx.addIssue({
          code: "custom",
          path: ["examKindIds"],
          message: tFieldErrors("scopeKindsRequired"),
        });
      }
      if (values.scope === "EXAM" && values.examIds.length === 0) {
        ctx.addIssue({
          code: "custom",
          path: ["examIds"],
          message: tFieldErrors("scopeExamsRequired"),
        });
      }
    });
}

export function createInitialFormValues(initialKind: PromotionKind = "AUTO"): PromotionFormValues {
  const { startsAt, endsAt } = defaultPromotionSchedule();
  return {
    kind: initialKind,
    name: "",
    description: "",
    code: "",
    status: "ACTIVE",
    discountType: "PERCENT",
    discountValue: "",
    maxDiscountVnd: "",
    startsAt,
    endsAt,
    totalLimit: "",
    perUserLimit: "",
    combinable: false,
    scope: "ALL",
    examKindIds: [],
    examIds: [],
    audience: "ALL",
    audienceClassIds: [],
    audienceUserIds: [],
  };
}

export function detailToFormValues(detail: PromotionDetail): PromotionFormValues {
  const scope: PromotionScope =
    detail.scope === "EXAM_KIND" || detail.scope === "EXAM" || detail.scope === "BUNDLE"
      ? detail.scope
      : "ALL";
  const examKindIds = detail.scopeItems.filter((item) => item.type === "EXAM_KIND").map((item) => item.id);
  const examIds = detail.scopeItems.filter((item) => item.type === "EXAM").map((item) => item.id);
  const audience: PromotionAudience =
    detail.audience === "NEW_ACCOUNT" || detail.audience === "SELECTED" ? detail.audience : "ALL";

  return {
    kind: detail.kind,
    name: detail.name,
    description: detail.description ?? "",
    code: detail.code ?? "",
    status: detail.status,
    discountType: detail.discountType,
    discountValue: String(detail.discountValue),
    maxDiscountVnd: detail.maxDiscountVnd != null ? String(detail.maxDiscountVnd) : "",
    startsAt: isoToVietnamDatetimeLocal(detail.startsAt),
    endsAt: isoToVietnamDatetimeLocal(detail.endsAt),
    totalLimit: detail.totalLimit != null ? String(detail.totalLimit) : "",
    perUserLimit: detail.perUserLimit != null ? String(detail.perUserLimit) : "",
    combinable: Boolean(detail.combinable),
    scope,
    examKindIds,
    examIds,
    audience,
    audienceClassIds: detail.audienceClasses.map((item) => item.id),
    audienceUserIds: detail.audienceUsers.map((item) => item.id),
  };
}

export function buildCreatePromotionRequest(values: PromotionFormValues): CreatePromotionRequest {
  const startsAtIso = vietnamDatetimeLocalToIso(values.startsAt)!;
  const endsAtIso = vietnamDatetimeLocalToIso(values.endsAt)!;
  const discountValue =
    values.discountType === "PERCENT"
      ? Number(values.discountValue.trim())
      : (parseDigits(values.discountValue) ?? 0);
  const maxDiscountVnd =
    values.discountType === "PERCENT" ? parseDigits(values.maxDiscountVnd) : null;
  const totalLimit = values.totalLimit.trim() ? Number(values.totalLimit.trim()) : null;
  const trimmedDesc = values.description.trim();

  const body: CreatePromotionRequest = {
    kind: values.kind,
    name: values.name.trim(),
    discountType: values.discountType,
    discountValue,
    startsAt: startsAtIso,
    endsAt: endsAtIso,
    scope: values.scope,
    status: values.status,
  };

  if (trimmedDesc) body.description = trimmedDesc;
  if (maxDiscountVnd != null && maxDiscountVnd > 0) body.maxDiscountVnd = maxDiscountVnd;
  if (totalLimit != null && totalLimit > 0) body.totalLimit = totalLimit;

  if (values.scope === "EXAM_KIND") {
    body.examKindIds = values.examKindIds;
  } else if (values.scope === "EXAM") {
    body.examIds = values.examIds;
  }

  if (values.kind === "VOUCHER") {
    body.code = values.code.trim().toUpperCase();
    body.combinable = values.combinable;
    body.audience = values.audience;
    const perUser = values.perUserLimit.trim() ? Number(values.perUserLimit.trim()) : null;
    if (perUser != null && perUser > 0) body.perUserLimit = perUser;
    if (values.audience === "SELECTED") {
      if (values.audienceClassIds.length > 0) body.audienceClassIds = values.audienceClassIds;
      if (values.audienceUserIds.length > 0) body.audienceUserIds = values.audienceUserIds;
    }
  }

  return body;
}

export function buildUpdatePromotionRequest(
  values: PromotionFormValues,
  detail: PromotionDetail,
): UpdatePromotionRequest {
  const isUsed = detail.usedCount > 0;
  const body: UpdatePromotionRequest = {
    expectedRevision: detail.revision,
    name: values.name.trim(),
    status: values.status,
  };

  const trimmedDesc = values.description.trim();
  if (trimmedDesc) {
    body.description = trimmedDesc;
  } else if (detail.description) {
    body.clearDescription = true;
  }

  const initialEndsLocal = isoToVietnamDatetimeLocal(detail.endsAt);
  if (values.endsAt !== initialEndsLocal) {
    const endsIso = vietnamDatetimeLocalToIso(values.endsAt);
    if (endsIso) body.endsAt = endsIso;
  }

  const totalLimit = values.totalLimit.trim() ? Number(values.totalLimit.trim()) : null;
  if (totalLimit != null) {
    body.totalLimit = totalLimit;
  } else if (detail.totalLimit != null) {
    body.clearTotalLimit = true;
  }

  if (isUsed) {
    return body;
  }

  body.discountType = values.discountType;
  body.discountValue =
    values.discountType === "PERCENT"
      ? Number(values.discountValue.trim())
      : (parseDigits(values.discountValue) ?? 0);

  if (values.discountType === "PERCENT") {
    const maxCap = parseDigits(values.maxDiscountVnd);
    if (maxCap != null && maxCap > 0) {
      body.maxDiscountVnd = maxCap;
    } else if (detail.maxDiscountVnd != null) {
      body.clearMaxDiscountVnd = true;
    }
  }

  const initialStartsLocal = isoToVietnamDatetimeLocal(detail.startsAt);
  if (values.startsAt !== initialStartsLocal) {
    const startsIso = vietnamDatetimeLocalToIso(values.startsAt);
    if (startsIso) body.startsAt = startsIso;
  }

  if (detail.scope !== "BUNDLE" && values.scope !== "BUNDLE") {
    body.scope = values.scope;
    if (values.scope === "EXAM_KIND") {
      body.examKindIds = values.examKindIds;
    } else if (values.scope === "EXAM") {
      body.examIds = values.examIds;
    }
  }

  if (detail.kind === "VOUCHER") {
    body.code = values.code.trim().toUpperCase();
    body.combinable = values.combinable;
    body.audience = values.audience;

    const perUser = values.perUserLimit.trim() ? Number(values.perUserLimit.trim()) : null;
    if (perUser != null) {
      body.perUserLimit = perUser;
    } else if (detail.perUserLimit != null) {
      body.clearPerUserLimit = true;
    }

    if (values.audience === "SELECTED") {
      body.audienceClassIds = values.audienceClassIds;
      body.audienceUserIds = values.audienceUserIds;
    }
  }

  return body;
}
