import { screen, within } from "@testing-library/react";
import { createElement } from "react";
import { describe, expect, it, vi } from "vitest";

import type { AuditEvent, UsersApiDeps } from "@/features/users/api";
import { UserHistoryTab } from "@/features/users/components/user-history-tab";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { installContainerWidth } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";

import { BUNDLE_AUDIT_ACTIONS } from "../audit-labels";

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

describe("Nhãn nhật ký BUNDLE_* (AC-16)", () => {
  it("vi.json và en.json có đủ 4 khóa BUNDLE_* dưới users.auditActions, khác rỗng, phân biệt vi/en", () => {
    const viActions = viMessages.users.auditActions as Record<string, string>;
    const enActions = en.users.auditActions as Record<string, string>;

    expect(BUNDLE_AUDIT_ACTIONS).toEqual([
      "BUNDLE_CREATED",
      "BUNDLE_UPDATED",
      "BUNDLE_PUBLISHED",
      "BUNDLE_DISCONTINUED",
    ]);

    for (const action of BUNDLE_AUDIT_ACTIONS) {
      expect(viActions[action], `vi.users.auditActions.${action}`).toBeTruthy();
      expect(enActions[action], `en.users.auditActions.${action}`).toBeTruthy();
      expect(viActions[action]).not.toBe(enActions[action]);
      expect(
        VIETNAMESE_DIACRITIC_RE.test(enActions[action]!),
        `en.users.auditActions.${action} has Vietnamese diacritics`,
      ).toBe(false);
    }
  });

  it("UserHistoryTab dịch đầy đủ cả 4 mã BUNDLE_* ở cả vi và en", async () => {
    installContainerWidth(900);
    const userId = "00000000-0000-4000-8000-000000000101";
    const events: AuditEvent[] = BUNDLE_AUDIT_ACTIONS.map((action, idx) => ({
      id: `bundle-evt-${idx}`,
      occurredAt: `2026-11-21T0${idx}:00:00Z`,
      actorUsername: "ops.admin",
      actorRoles: ["ADMIN"],
      actorType: "USER",
      module: "COMMERCE",
      action,
      targetType: "bundle",
      targetId: userId,
      outcome: "SUCCESS",
    }));

    const listUserAuditEvents = vi.fn<UsersApiDeps["listUserAuditEvents"]>(async () => ({
      items: events,
      page: 0,
      size: 20,
      totalItems: events.length,
      totalPages: 1,
    }));

    const { unmount } = renderWithProviders(
      createElement(UserHistoryTab, { userId, listUserAuditEvents }),
    );
    const tableVi = await screen.findByRole("table", { name: "Lịch sử thay đổi tài khoản" });
    for (const action of BUNDLE_AUDIT_ACTIONS) {
      const expected = (viMessages.users.auditActions as Record<string, string>)[action]!;
      expect(await within(tableVi).findByText(expected)).toBeInTheDocument();
      expect(within(tableVi).queryByText(action)).toBeNull();
    }
    unmount();

    renderWithProviders(
      createElement(UserHistoryTab, { userId, listUserAuditEvents }),
      { locale: "en", messages: en },
    );
    const tableEn = await screen.findByRole("table", { name: "Account change history" });
    for (const action of BUNDLE_AUDIT_ACTIONS) {
      const expected = (en.users.auditActions as Record<string, string>)[action]!;
      expect(await within(tableEn).findByText(expected)).toBeInTheDocument();
      expect(within(tableEn).queryByText(action)).toBeNull();
    }
  });
});
