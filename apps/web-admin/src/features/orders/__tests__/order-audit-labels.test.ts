import { describe, expect, it } from "vitest";

import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

import {
  COMMERCE_AUDIT_ACTIONS_IN_USER_HISTORY,
  KNOWN_ORDER_AUDIT_ACTIONS,
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
});
