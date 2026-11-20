import type { ReactElement } from "react";

import { SessionProvider } from "@/features/session/session-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";

import type { PromotionDetail, PromotionSummary } from "../types";

export const PROMO_LIST_PATH = "/api/v1/admin/promotions";
export const PROMO_DETAIL_PATH = "/api/v1/admin/promotions/{id}";
export const PROMO_STOP_PATH = "/api/v1/admin/promotions/{id}/stop";
export const PROMO_RESUME_PATH = "/api/v1/admin/promotions/{id}/resume";
export const PROMO_SUGGEST_CODE_PATH = "/api/v1/admin/promotions/suggested-code";
export const PROMO_PREVIEW_PATH = "/api/v1/admin/promotions/price-preview";
export const EXAM_KINDS_PATH = "/api/v1/catalog/options";
export const EXAMS_PATH = "/api/v1/admin/exams";
export const CLASSES_PATH = "/api/v1/admin/classes";
export const USERS_PATH = "/api/v1/admin/users";

export const PROMO_ID_1 = "11111111-1111-4111-8111-111111111111";
export const PROMO_ID_2 = "22222222-2222-4222-8222-222222222222";
export const EXAM_ID_1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
export const EXAM_ID_2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
export const CLASS_ID_1 = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
export const USER_ID_1 = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
export const KIND_ID_1 = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";

export const FULL_PROMO_PERMISSIONS = [
  "PROMOTION.VIEW",
  "PROMOTION.MODIFY",
  "EXAM.VIEW",
  "CLASS.VIEW",
  "USER.VIEW",
];
export const VIEW_ONLY_PROMO_PERMISSIONS = ["PROMOTION.VIEW"];
export const OPS_PROMO_PERMISSIONS = ["PROMOTION.VIEW", "PROMOTION.MODIFY", "CLASS.VIEW", "USER.VIEW"];

export function promotionSummaryFixture(overrides: Partial<PromotionSummary> = {}): PromotionSummary {
  return {
    id: PROMO_ID_1,
    kind: "AUTO",
    name: "Midterm Auto Deal",
    description: "Automatic discount for midterm practice",
    discountType: "PERCENT",
    discountValue: 20,
    maxDiscountVnd: 50000,
    startsAt: "2026-11-20T01:00:00Z",
    endsAt: "2026-12-20T16:59:00Z",
    totalLimit: 100,
    usedCount: 0,
    overLimitCount: 0,
    scope: "ALL",
    state: "RUNNING",
    status: "ACTIVE",
    revision: 1,
    capWarnings: [],
    createdAt: "2026-11-19T03:00:00Z",
    updatedAt: "2026-11-19T03:00:00Z",
    ...overrides,
  };
}

export function promotionDetailFixture(overrides: Partial<PromotionDetail> = {}): PromotionDetail {
  return {
    id: PROMO_ID_1,
    kind: "VOUCHER",
    name: "Welcome K12 Voucher",
    description: "Applies to K12 learners",
    code: "WELCOME20",
    combinable: true,
    audience: "ALL",
    audienceClasses: [],
    audienceUsers: [],
    discountType: "PERCENT",
    discountValue: 20,
    maxDiscountVnd: 50000,
    startsAt: "2026-11-20T01:00:00Z",
    endsAt: "2026-12-20T16:59:00Z",
    totalLimit: 100,
    perUserLimit: 1,
    usedCount: 0,
    confirmedCount: 0,
    reservedCount: 0,
    totalDiscountPaid: 0,
    overLimitCount: 0,
    scope: "ALL",
    scopeItems: [],
    state: "RUNNING",
    status: "ACTIVE",
    revision: 3,
    capWarnings: [],
    redemptions: {
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    },
    history: [],
    createdAt: "2026-11-19T03:00:00Z",
    updatedAt: "2026-11-20T01:00:00Z",
    createdByUsername: "ops.admin",
    updatedByUsername: "ops.admin",
    ...overrides,
  };
}

export function pageOfPromotions(items: PromotionSummary[], totalItems = items.length, page = 0, size = 20) {
  return {
    items,
    page,
    size,
    totalItems,
    totalPages: totalItems > 0 ? Math.ceil(totalItems / size) : 0,
  };
}

export function renderWithPermissions(
  ui: ReactElement,
  options: { permissions?: string[]; locale?: string } = {},
) {
  const { permissions = FULL_PROMO_PERMISSIONS, locale = "vi" } = options;
  return renderWithProviders(
    <SessionProvider loadSession={async () => ({ ...TEST_ADMIN_SESSION, permissions })}>
      {ui}
    </SessionProvider>,
    { locale },
  );
}
