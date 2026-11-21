import { createTranslator } from "next-intl";
import { describe, expect, it } from "vitest";

import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

import {
  COMMERCE_AUDIT_ACTIONS_IN_USER_HISTORY,
  KNOWN_ORDER_AUDIT_ACTIONS,
  formatStructuredOrderReason,
  resolveOrderActorLabel,
  resolveOrderAuditActionLabel,
} from "../audit-labels";

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

describe("order audit action labels (AC-23)", () => {
  it("đủ nhãn REFUND_*, ENTITLEMENT_REVOKED, ORDER_FULFILLED_MANUALLY, PAYMENT_LATE_SUCCESS_* ở cả vi và en (user-history và orders)", () => {
    const viUserActions = vi.users.auditActions as Record<string, string>;
    const enUserActions = en.users.auditActions as Record<string, string>;
    const viOrderActions = vi.orders.auditActions as Record<string, string>;
    const enOrderActions = en.orders.auditActions as Record<string, string>;

    for (const action of COMMERCE_AUDIT_ACTIONS_IN_USER_HISTORY) {
      expect(viUserActions[action], `vi.users.auditActions.${action}`).toBeTruthy();
      expect(enUserActions[action], `en.users.auditActions.${action}`).toBeTruthy();
      expect(
        VIETNAMESE_DIACRITIC_RE.test(enUserActions[action]!),
        `en.users.auditActions.${action} has Vietnamese diacritics`,
      ).toBe(false);

      expect(viOrderActions[action], `vi.orders.auditActions.${action}`).toBeTruthy();
      expect(enOrderActions[action], `en.orders.auditActions.${action}`).toBeTruthy();
      expect(
        VIETNAMESE_DIACRITIC_RE.test(enOrderActions[action]!),
        `en.orders.auditActions.${action} has Vietnamese diacritics`,
      ).toBe(false);

      expect(KNOWN_ORDER_AUDIT_ACTIONS[action]).toBe(true);
      expect(resolveOrderAuditActionLabel(action, (k) => viOrderActions[k] ?? k)).toBe(
        viOrderActions[action],
      );
      expect(resolveOrderAuditActionLabel(action, (k) => enOrderActions[k] ?? k)).toBe(
        enOrderActions[action],
      );
    }

    expect(resolveOrderAuditActionLabel("FUTURE_UNKNOWN_ACTION", (k) => k)).toBe(
      "FUTURE_UNKNOWN_ACTION",
    );
  });

  it("dịch các mã lý do lịch sử đơn hàng và bỏ tiền tố raw (D-6)", () => {
    const tViReasons = createTranslator({
      locale: "vi",
      messages: vi,
      namespace: "orders.historyReasons",
    });
    const tEnReasons = createTranslator({
      locale: "en",
      messages: en,
      namespace: "orders.historyReasons",
    });

    const translateVi = (key: string, values?: Record<string, string>) =>
      (tViReasons as unknown as (k: string, v?: Record<string, string>) => string)(key, values);
    const translateEn = (key: string, values?: Record<string, string>) =>
      (tEnReasons as unknown as (k: string, v?: Record<string, string>) => string)(key, values);

    expect(formatStructuredOrderReason("FULFILLED", translateVi)).toEqual({
      localized: "Đã hoàn tất cấp lượt thi",
      rawText: null,
    });
    expect(formatStructuredOrderReason("FULFILLED", translateEn)).toEqual({
      localized: "Entitlements granted",
      rawText: null,
    });

    expect(formatStructuredOrderReason("FULFILLED:TXN-01", translateEn)).toEqual({
      localized: "Entitlements granted (TXN-01)",
      rawText: null,
    });

    expect(formatStructuredOrderReason("ORDER_CREATED", translateVi)).toEqual({
      localized: "Tạo đơn hàng mới",
      rawText: null,
    });
    expect(formatStructuredOrderReason("PAYMENT_ATTEMPT_CREATED:DH01", translateEn)).toEqual({
      localized: "Created payment attempt (DH01)",
      rawText: null,
    });
    expect(formatStructuredOrderReason("PAYMENT_SUCCEEDED:DH01", translateEn)).toEqual({
      localized: "Payment succeeded (DH01)",
      rawText: null,
    });
    expect(formatStructuredOrderReason("VNPAY_QUERYDR_STATUS_02:DH01", translateEn)).toEqual({
      localized: "VNPAY QueryDR status requires review (02 · DH01)",
      rawText: null,
    });
    expect(formatStructuredOrderReason("Custom admin note", translateEn)).toEqual({
      localized: null,
      rawText: "Custom admin note",
    });
  });

  it("dịch mọi mã actor hệ thống và giữ nguyên username thật (D-6)", () => {
    const tViHistory = createTranslator({
      locale: "vi",
      messages: vi,
      namespace: "orders.detail.history",
    });
    const tEnHistory = createTranslator({
      locale: "en",
      messages: en,
      namespace: "orders.detail.history",
    });

    const trVi = (key: string) => (tViHistory as unknown as (k: string) => string)(key);
    const trEn = (key: string) => (tEnHistory as unknown as (k: string) => string)(key);

    expect(resolveOrderActorLabel("SYSTEM", "SYSTEM", trVi)).toEqual({
      label: "Hệ thống",
      isRawUsername: false,
    });
    expect(resolveOrderActorLabel("WORKER", undefined, trEn)).toEqual({
      label: "System",
      isRawUsername: false,
    });
    expect(resolveOrderActorLabel("PLATFORM", "PLATFORM", trVi)).toEqual({
      label: "Quản trị nền tảng",
      isRawUsername: false,
    });
    expect(resolveOrderActorLabel("LEARNER", undefined, trEn)).toEqual({
      label: "Learner",
      isRawUsername: false,
    });
    expect(resolveOrderActorLabel("VNPAY_IPN", undefined, trEn)).toEqual({
      label: "VNPAY IPN",
      isRawUsername: false,
    });
    expect(resolveOrderActorLabel("VNPAY_QUERYDR", undefined, trEn)).toEqual({
      label: "VNPAY QueryDR",
      isRawUsername: false,
    });
    expect(resolveOrderActorLabel("ops.thanh", "USER", trEn)).toEqual({
      label: "ops.thanh",
      isRawUsername: true,
    });
  });

  it("đủ số ít/số nhiều tiếng Anh cho toàn bộ bộ đếm trong orders (D-6)", () => {
    const tEn = createTranslator({ locale: "en", messages: en, namespace: "orders" });
    const translate = (key: string, values: Record<string, unknown>) =>
      (tEn as unknown as (k: string, v: Record<string, unknown>) => string)(key, values);

    expect(translate("buyers.paidCount", { count: 1 })).toBe("1 paid order");
    expect(translate("buyers.paidCount", { count: 3 })).toBe("3 paid orders");
    expect(translate("attention.countLabel", { count: 1 })).toBe("1 order");
    expect(translate("attention.countLabel", { count: 4 })).toBe("4 orders");
    expect(translate("list.totalItems", { total: 1 })).toBe("Total 1 order");
    expect(translate("list.totalItems", { total: 12 })).toBe("Total 12 orders");
    expect(translate("list.attentionCountTag", { count: 1 })).toBe("1 attention flag");
    expect(translate("list.attentionCountTag", { count: 2 })).toBe("2 attention flags");
    expect(translate("detail.banners.lateSuccessPendingBody", { count: 1 })).toContain("1 payment settled");
    expect(translate("detail.banners.lateSuccessPendingBody", { count: 2 })).toContain("2 payments settled");
    expect(translate("dialogs.continueFulfill.descriptionPrefix", { code: "DH1", attempts: 1 })).toBe(
      "Order DH1 will grant 1 attempt for",
    );
    expect(translate("dialogs.continueFulfill.descriptionPrefix", { code: "DH1", attempts: 5 })).toBe(
      "Order DH1 will grant 5 attempts for",
    );
  });
});
