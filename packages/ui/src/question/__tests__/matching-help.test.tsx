import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UiStringsProvider } from "../../components/ui-strings";
import { UI_STRINGS_BY_LOCALE, type UiLocale } from "../../messages/ui-strings";
import type { KitQuestion } from "../types";
import { Live, matching } from "./fixtures";

const reusable: KitQuestion = { ...matching, body: { ...matching.body, reuseRight: true } as KitQuestion["body"] };

describe("Hướng dẫn Ghép đôi chỉ nói những gì cách đó đang hiện (R1-21)", () => {
  it.each(["vi", "en"] as UiLocale[])("%s: cách chọn trong danh sách (không nhắc nút Ghép với, nhãn Đã dùng)", (locale) => {
    const t = UI_STRINGS_BY_LOCALE[locale].question;
    render(
      <UiStringsProvider locale={locale}>
        <Live question={matching} />
      </UiStringsProvider>,
    );
    expect(screen.getByText(t.matchExclusive)).toBeInTheDocument();
    // Cách này chỉ làm mờ lựa chọn đã ghép; không có nhãn "Đã dùng" như cách bấm hai lượt.
    expect(t.matchExclusive).not.toContain(t.matchUsedTag("1").split(" ")[0]!);
    expect(t.matchExclusive).not.toContain(t.matchWith);
  });

  it.each(["vi", "en"] as UiLocale[])("%s: cách bấm hai lượt nói nút Ghép với và nút X, không nhắc danh sách chọn", (locale) => {
    const t = UI_STRINGS_BY_LOCALE[locale].question;
    render(
      <UiStringsProvider locale={locale}>
        <Live question={matching} matchUi="pick" />
      </UiStringsProvider>,
    );
    expect(screen.getByText(`${t.matchHelp} ${t.matchExclusivePick}`)).toBeInTheDocument();
    expect(t.matchHelp).toContain(t.matchWith);
    expect(t.matchHelp).not.toMatch(/danh sách|from the list|its list/);
    // Nhãn trên thẻ cột phải khi đã dùng đúng là chữ mà hướng dẫn nhắc.
    expect(t.matchExclusivePick).toContain(t.matchUsedTag("1").split(" ")[0]!);
  });

  it("cho dùng lại: cả hai cách hiện câu quy tắc dùng lại, không nhắc dùng một lần", () => {
    const t = UI_STRINGS_BY_LOCALE.vi.question;
    const { rerender } = render(<Live key="select" question={reusable} />);
    expect(screen.getByText(t.matchReusable)).toBeInTheDocument();
    rerender(<Live key="pick" question={reusable} matchUi="pick" />);
    expect(screen.getByText(`${t.matchHelp} ${t.matchReusable}`)).toBeInTheDocument();
  });
});
