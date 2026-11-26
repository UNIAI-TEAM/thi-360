import { cleanup, screen, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import referenceFixture from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { MapidRulesTable } from "../components/mapid-rules-table";
import type { ExamSection, MapidRule, QuestionBlock } from "../types";

const { asToan } = vi.hoisted(() => ({
  asToan: (ref: unknown) => JSON.parse(JSON.stringify(ref).replaceAll('"MATH"', '"TOAN"')) as ContentReference,
}));

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async (locale?: Parameters<typeof getContentReferenceFixture>[0]) =>
    asToan(await getContentReferenceFixture(locale));
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const reference = asToan(referenceFixture);

const block = (over: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id: "b-short",
  name: "Trả lời ngắn",
  questionType: "SHORT_ANSWER",
  questionCount: 15,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: null,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...over,
});

function makeSection(over: Partial<ExamSection> = {}): ExamSection {
  return {
    id: "s1",
    subjectCode: "TOAN",
    questionCount: 50,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: null,
    choiceGroup: null,
    blocks: [
      block({ id: "b-single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 }),
      block({ id: "b-short", name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 15 }),
      block({ id: "b-set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 }),
    ],
    mapidMode: "MATRIX",
    mapidRules: [],
    ...over,
  };
}

function renderTable(initial: ExamSection, locale: "vi" | "en" = "vi") {
  const latest = { rules: initial.mapidRules ?? [] };
  function Harness() {
    const [section, setSection] = useState(initial);
    return (
      <MapidRulesTable
        section={section}
        groups={section.blocks}
        subjectName="Toán học"
        reference={reference}
        onChange={(rules) => {
          latest.rules = rules;
          setSection((prev) => ({ ...prev, mapidRules: rules }));
        }}
      />
    );
  }
  renderWithProviders(<Harness />, locale === "en" ? { locale: "en", messages: en } : {});
  return latest;
}

const dialogOf = () => screen.getByTestId("mapid-rule-save").closest('[role="dialog"]') as HTMLElement;

async function openAdd(u: UserEvent) {
  await u.click(screen.getByTestId("mapid-rule-add"));
  return dialogOf();
}

async function selectScope(u: UserEvent, optionPattern: RegExp) {
  const scopeBox = within(dialogOf()).getByTestId("mapid-rule-scope");
  await u.click(within(scopeBox).getByRole("combobox"));
  await u.click(await screen.findByRole("option", { name: optionPattern }));
}

async function pickNode(u: UserEvent, code: string) {
  await u.click(within(dialogOf()).getByTestId("mapid-rule-mapid-picker"));
  const picker = await screen.findByRole("dialog", { name: /Chọn kiến thức|Choose knowledge/ });
  await u.type(within(picker).getByRole("searchbox"), code);
  await u.click(await within(picker).findByRole("checkbox", { name: new RegExp(`^${code} `) }));
  await u.click(within(picker).getByRole("button", { name: /Áp dụng|Apply/ }));
}

describe("UNI-1707 MapidRuleDialog 2-column UX & 3-way cross-row checks", () => {
  it("AC-1707-1..3: Scope uses Select defaulting to Toàn môn with 3 types; selecting a group shows 4 types vertically with single active description", async () => {
    const u = userEvent.setup();
    renderTable(makeSection());
    const dialog = await openAdd(u);

    // 2-column grid without inner max-h-[60vh] overflow-y-auto wrapper
    expect(dialog.querySelector(".max-h-\\[60vh\\]")).toBeNull();
    expect(dialog.querySelector(".md\\:grid-cols-2")).not.toBeNull();

    // Scope Select defaults to Toàn môn · 50 câu; Toàn môn shows EXACTLY 3 rule types (no ONLY_IN_GROUP)
    const scopeBox = within(dialog).getByTestId("mapid-rule-scope");
    expect(within(scopeBox).getByRole("combobox")).toHaveTextContent("Toàn môn · 50 câu");
    expect(within(dialog).getByTestId("mapid-rule-type-PRESENT")).toBeInTheDocument();
    expect(within(dialog).getByTestId("mapid-rule-type-COUNT")).toBeInTheDocument();
    expect(within(dialog).getByTestId("mapid-rule-type-SOURCE")).toBeInTheDocument();
    expect(within(dialog).queryByTestId("mapid-rule-type-ONLY_IN_GROUP")).not.toBeInTheDocument();

    // Only selected option (PRESENT) has description; unselected COUNT does not
    expect(within(dialog).getByTestId("mapid-rule-type-PRESENT")).toHaveTextContent(/ít nhất 1 câu/i);
    expect(within(dialog).getByTestId("mapid-rule-type-COUNT")).toHaveTextContent("Số câu theo MAPID");
    expect(within(dialog).getByTestId("mapid-rule-type-COUNT")).not.toHaveTextContent(/kể cả câu con/i);

    // Selecting a group reveals all 4 rule types
    await selectScope(u, /^Trả lời ngắn · 15 câu$/);
    expect(within(dialog).getByTestId("mapid-rule-type-ONLY_IN_GROUP")).toBeInTheDocument();
  });

  it("AC-1707-4 & AC-1707-5: multi-node hint, Bằng/Ít nhất, denominator on 1 chùm × 3 câu con = 3, and distinct summaries for ONLY_IN_GROUP vs SOURCE", async () => {
    const u = userEvent.setup();
    renderTable(makeSection());
    const dialog = await openAdd(u);

    // Multi-node hint always mentions "Khớp ít nhất một MAPID đã chọn"
    expect(dialog).toHaveTextContent("Khớp ít nhất một MAPID đã chọn");

    // Pick 0D2-1 Hàm số on Chùm cuối (1 chùm × 3 câu con) with COUNT PERCENT 50% -> denominator 3, converted 2
    await selectScope(u, /^Chùm cuối · 1 chùm × 3 câu con$/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.click(within(dialog).getByRole("radio", { name: "Tỷ lệ" }));
    const pct = within(dialog).getByTestId("mapid-rule-percent");
    await u.clear(pct);
    await u.type(pct, "50");
    expect(within(dialog).getByTestId("mapid-rule-percent-result")).toHaveTextContent("= 2 câu trên mẫu số 3");

    // Exceeding group capacity blocks saving with field error
    await u.click(within(dialog).getByRole("radio", { name: "Số câu" }));
    const count = within(dialog).getByTestId("mapid-rule-count");
    await u.clear(count);
    await u.type(count, "4");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(dialog).toHaveTextContent(/vượt số câu của nhóm/i);

    // Valid count -> live summary matches
    await u.clear(count);
    await u.type(count, "2");
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("Trong 3 câu Chùm cuối, lấy đúng 2 câu thuộc Hàm số.");

    // Unambiguously distinct ONLY_IN_GROUP vs SOURCE summaries
    await u.click(within(dialog).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("Hàm số chỉ được xuất hiện trong nhóm Chùm cuối");
    await u.click(within(dialog).getByTestId("mapid-rule-type-SOURCE"));
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("Nhóm Chùm cuối chỉ lấy câu thuộc Hàm số");
  });

  it("AC-1707-6: switching from group with ONLY_IN_GROUP to Toàn môn clears type, preserves nodeIds, disables Save; COUNT -> PRESENT drops stale numbers", async () => {
    const u = userEvent.setup();
    const latest = renderTable(makeSection());
    const dialog = await openAdd(u);

    await selectScope(u, /^Trả lời ngắn/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
    await pickNode(u, "0D2-1");

    // Switch back to Toàn môn -> type cleared, nodeIds preserved, Save disabled
    await selectScope(u, /^Toàn môn/);
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByText("0D2-1")).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/chọn loại quy tắc/i);

    // Select COUNT, enter 3, then switch to PRESENT -> hides inputs, summary shows "ít nhất 1 câu", saves clean PRESENT
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "3");
    await u.click(within(dialog).getByTestId("mapid-rule-type-PRESENT"));
    expect(within(dialog).queryByTestId("mapid-rule-count")).not.toBeInTheDocument();
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("có ít nhất 1 câu thuộc Hàm số");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.rules).toEqual([{ id: expect.any(String), type: "PRESENT", groupId: null, nodeIds: ["kn-0D2-1"] }]);
  });

  it("AC-1707-7: EQUIVALENT blocks and opens existing row; SUBSUMES offers Thay quy tắc cũ", async () => {
    const u = userEvent.setup();
    const initialRules: MapidRule[] = [
      { id: "r-pres", type: "PRESENT", groupId: null, nodeIds: ["kn-0D2-1"] },
    ];
    const latest = renderTable(makeSection({ mapidRules: initialRules }));

    // 1. Equivalent: COUNT AT_LEAST 1 on same node/scope -> blocks & opens r-pres
    const dialog = await openAdd(u);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByTestId("mapid-rule-cross-check")).toHaveTextContent("Quy tắc tương đương đã tồn tại");
    // Entered form stays intact
    expect(within(dialog).getByTestId("mapid-rule-count")).toHaveValue("1");

    // 2. Subsumes: switch to EXACT 3 -> Save enabled + Thay quy tắc cũ replaces r-pres atomically
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "3");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
    expect(within(dialog).getByTestId("mapid-rule-cross-check")).toHaveTextContent("Đúng 3 câu đã bao gồm yêu cầu phải xuất hiện");
    await u.click(within(dialog).getByTestId("mapid-rule-replace"));
    expect(latest.rules).toHaveLength(1);
    expect(latest.rules[0]).toMatchObject({ type: "COUNT", groupId: null, comparator: "EXACT", count: 3 });
  });

  it("uniworkos UNI-1719: cảnh báo không chặn khi tổng số câu các quy tắc vượt số câu của môn", async () => {
    const u = userEvent.setup();
    const initialRules: MapidRule[] = [
      { id: "r-a", type: "COUNT", groupId: null, nodeIds: ["kn-0D2-1-1"], comparator: "EXACT", count: 20 },
      { id: "r-b", type: "COUNT", groupId: null, nodeIds: ["kn-0D2-1-2"], comparator: "EXACT", count: 25 },
    ];
    const latest = renderTable(makeSection({ mapidRules: initialRules }));

    // 20 + 25 + 5 = 50 = số câu của môn: không cảnh báo.
    let dialog = await openAdd(u);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-2");
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "5");
    expect(within(dialog).queryByTestId("mapid-rule-over-total")).not.toBeInTheDocument();

    // 20 + 25 + 10 = 55 > 50: cảnh báo, vẫn lưu được.
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "10");
    const warning = within(dialog).getByTestId("mapid-rule-over-total");
    expect(warning).toHaveAttribute("role", "note");
    expect(warning).toHaveTextContent(
      "Các quy tắc của môn sẽ cần 55 câu, nhiều hơn 5 câu so với 50 câu đã chọn ở cấu trúc. Vẫn lưu được, nhưng đề có thể không ghép đủ các quy tắc.",
    );
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.rules).toHaveLength(3);

    // Sửa quy tắc 25 câu: đang 55 nên vẫn cảnh báo; hạ xuống 15 thì 20 + 15 + 10 = 45, không tính trùng chính nó.
    await u.click(screen.getByRole("button", { name: /^Sửa quy tắc: .*Sự biến thiên/ }));
    dialog = dialogOf();
    expect(within(dialog).getByTestId("mapid-rule-over-total")).toHaveTextContent("55 câu");
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "15");
    expect(within(dialog).queryByTestId("mapid-rule-over-total")).not.toBeInTheDocument();
  });

  it("uniworkos UNI-1719: quy tắc con nằm trong quy tắc rộng hơn không bị cộng hai lần", async () => {
    const u = userEvent.setup();
    renderTable(makeSection({ mapidRules: [{ id: "r-parent", type: "COUNT", groupId: null, nodeIds: ["kn-0D2-1"], comparator: "EXACT", count: 45 }] }));

    const dialog = await openAdd(u);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1-1");
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "10");
    expect(within(dialog).queryByTestId("mapid-rule-over-total")).not.toBeInTheDocument();
  });

  it("AC-1707-7 + UNI-2824: Subject EXACT 3 is a budget: group inputs show what is left, the group sum cannot pass it", async () => {
    const u = userEvent.setup();
    const initialRules: MapidRule[] = [
      { id: "r-subj", type: "COUNT", groupId: null, nodeIds: ["kn-0D2-1"], comparator: "EXACT", count: 3 },
    ];
    const latest = renderTable(makeSection({ mapidRules: initialRules }));

    // Allowed: add Group Trả lời ngắn EXACT 2 (2 <= 3); the input says how much of the subject is still unshared.
    let dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "2");
    expect(within(dialog).getByTestId("mapid-rule-budget")).toHaveTextContent("Toàn môn còn 3 câu Hàm số chưa chia.");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.rules).toHaveLength(2);

    // Field limit: Trắc nghiệm AT_LEAST 2 would need 2 + 2 = 4 > 3, so the input itself says 1 is left.
    dialog = await openAdd(u);
    await selectScope(u, /^Trắc nghiệm/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "2");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Toàn môn chỉ còn 1 câu Hàm số chưa chia");
    expect(within(dialog).getByTestId("mapid-rule-count")).toHaveAttribute("aria-invalid", "true");
    expect(within(dialog).queryByTestId("mapid-rule-cross-check")).not.toBeInTheDocument();
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "1");
    expect(within(dialog).getByTestId("mapid-rule-budget")).toHaveTextContent("Toàn môn còn 1 câu Hàm số chưa chia.");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.rules).toHaveLength(3);

    // A rule without a number (PRESENT in Chùm cuối) has no input to limit: the cross-row check still blocks it.
    dialog = await openAdd(u);
    await selectScope(u, /^Chùm cuối/);
    await pickNode(u, "0D2-1");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByTestId("mapid-rule-cross-check")).toHaveTextContent(
      "Các nhóm đang yêu cầu tối thiểu 4 câu Hàm số, vượt mức 3 câu của toàn môn.",
    );
    // R1-1: multi-rule mapid-rule-open-* buttons allow wrapping within alert container
    const openSubjBtn = within(dialog).getByTestId("mapid-rule-open-r-subj");
    expect(openSubjBtn).toHaveClass("whitespace-normal", "max-w-full", "h-auto", "min-h-9", "text-left");
    await u.click(openSubjBtn);
    // R1-8: switching rule via mapid-rule-open-* keeps focus inside the dialog
    const switchedDialog = dialogOf();
    expect(switchedDialog.contains(document.activeElement)).toBe(true);
    expect(document.activeElement).not.toBe(document.body);
    expect(within(switchedDialog).getByTestId("mapid-rule-count")).toHaveValue("3");
  });

  it("AC-1707-7: SUBSUMED_BY blocks weaker duplicate; another group cannot pick an ONLY_IN_GROUP MAPID (UNI-2824); Edit excludes self and preserves input", async () => {
    const u = userEvent.setup();
    const initialRules: MapidRule[] = [
      { id: "r-ex3", type: "COUNT", groupId: null, nodeIds: ["kn-0D2-1"], comparator: "EXACT", count: 3 },
      { id: "r-only", type: "ONLY_IN_GROUP", groupId: "b-short", nodeIds: ["kn-0D1"] },
    ];
    const latest = renderTable(makeSection({ mapidRules: initialRules }));

    // SUBSUMED_BY: adding PRESENT on 0D2-1 when EXACT 3 exists -> blocked, offers open button
    let dialog = await openAdd(u);
    await pickNode(u, "0D2-1");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByTestId("mapid-rule-cross-check")).toHaveTextContent(/đã nằm trong quy tắc chặt hơn/);
    expect(within(dialog).getByTestId("mapid-rule-open-r-ex3")).toBeInTheDocument();

    // UNI-2824 point 5: 0D1 may appear only in b-short, so the picker of another group does not offer it at all.
    await selectScope(u, /^Trắc nghiệm/);
    await u.click(within(dialog).getByTestId("mapid-rule-mapid-picker"));
    const picker = await screen.findByRole("dialog", { name: /Chọn kiến thức/ });
    expect(picker).toHaveTextContent("chỉ hiện MAPID nằm trong nguồn của toàn môn và không bị giới hạn ở nhóm khác");
    expect(await within(picker).findByRole("checkbox", { name: /^0D2-1 / })).toBeChecked();
    await u.type(within(picker).getByRole("searchbox"), "0D1");
    expect(within(picker).queryByRole("checkbox", { name: /^0D1 / })).not.toBeInTheDocument();
    await u.click(within(picker).getByRole("button", { name: "Hủy" }));

    // Close and edit r-ex3 -> excludes itself, allows changing count 3 -> 4
    await u.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await u.click(screen.getByRole("button", { name: /^Sửa quy tắc: Toàn môn · Đúng 3\/50/ }));
    dialog = dialogOf();
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "4");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.rules.find((item) => item.id === "r-ex3")).toMatchObject({ count: 4 });
  });

  it("EN locale renders clean English labels, summaries, and pluralization without Vietnamese diacritics", async () => {
    const u = userEvent.setup();
    renderTable(makeSection(), "en");
    const dialog = await openAdd(u);
    const scopeBox = within(dialog).getByTestId("mapid-rule-scope");
    expect(within(scopeBox).getByRole("combobox")).toHaveTextContent("Whole subject · 50 questions");
    await selectScope(u, /^Chùm cuối · 1 set × 3 sub-questions$/);
    await pickNode(u, "0D2-1");
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent(/at least 1 question/i);
  });

  it.each(["vi", "en"] as const)(
    "R1-2 & R1-10 (1b, %s): renders bidirectional EXACT_BELOW_MINIMUM messages cleanly",
    async (locale) => {
      const al3: MapidRule = { id: "r-al3", type: "COUNT", groupId: "b-short", nodeIds: ["kn-0D2-1"], comparator: "AT_LEAST", count: 3 };
      const ex2: MapidRule = { id: "r-ex2", type: "COUNT", groupId: "b-short", nodeIds: ["kn-0D2-1"], comparator: "EXACT", count: 2 };
      const cancelLabel = locale === "vi" ? "Hủy" : "Cancel";

      const u1 = userEvent.setup();
      renderTable(makeSection({ mapidRules: [al3] }), locale);
      let d1 = await openAdd(u1);
      await selectScope(u1, /^Trả lời ngắn/);
      await u1.click(within(d1).getByTestId("mapid-rule-type-COUNT"));
      await pickNode(u1, "0D2-1");
      await u1.click(within(within(d1).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: locale === "vi" ? "Bằng" : "Exact" }));
      await u1.clear(within(d1).getByTestId("mapid-rule-count"));
      await u1.type(within(d1).getByTestId("mapid-rule-count"), "2");
      const msg1 = within(d1).getByTestId("mapid-rule-cross-check").textContent ?? "";
      if (locale === "vi") {
        expect(msg1).toContain("điều kiện đúng 2 câu nhỏ hơn mức tối thiểu 3 câu");
        expect(msg1).not.toMatch(/quy tắc .* chỉ cho đúng 2 câu/);
      } else {
        expect(msg1).toContain("the exact limit of 2 questions is below the minimum of 3");
        expect(msg1).not.toMatch(/rule .* allows at most 2/);
      }
      await u1.click(within(d1).getByRole("button", { name: cancelLabel }));

      cleanup();
      const u2 = userEvent.setup();
      renderTable(makeSection({ mapidRules: [ex2] }), locale);
      d1 = await openAdd(u2);
      await selectScope(u2, /^Trả lời ngắn/);
      await u2.click(within(d1).getByTestId("mapid-rule-type-COUNT"));
      await pickNode(u2, "0D2-1");
      await u2.clear(within(d1).getByTestId("mapid-rule-count"));
      await u2.type(within(d1).getByTestId("mapid-rule-count"), "3");
      const msg2 = within(d1).getByTestId("mapid-rule-cross-check").textContent ?? "";
      if (locale === "vi") {
        expect(msg2).toContain("điều kiện đúng 2 câu nhỏ hơn mức tối thiểu 3 câu");
      } else {
        expect(msg2).toContain("the exact limit of 2 questions is below the minimum of 3");
      }
    },
  );

  it.each(["vi", "en"] as const)(
    "R1-2 & R1-10 (1e, %s): renders bidirectional ONLY_IN_GROUP vs PRESENT messages naming both groups",
    async (locale) => {
      const onlyShort: MapidRule = { id: "r-only-short", type: "ONLY_IN_GROUP", groupId: "b-short", nodeIds: ["kn-0D2-1"] };
      const presSingle: MapidRule = { id: "r-pres-single", type: "PRESENT", groupId: "b-single", nodeIds: ["kn-0D2-1"] };
      const cancelLabel = locale === "vi" ? "Hủy" : "Cancel";

      const u = userEvent.setup();
      renderTable(makeSection({ mapidRules: [presSingle] }), locale);
      let d = await openAdd(u);
      await selectScope(u, /^Trả lời ngắn/);
      await u.click(within(d).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
      await pickNode(u, "0D2-1");
      let text = within(d).getByTestId("mapid-rule-cross-check").textContent ?? "";
      expect(text).toContain("Trả lời ngắn");
      expect(text).toContain("Trắc nghiệm");
      await u.click(within(d).getByRole("button", { name: cancelLabel }));

      cleanup();
      renderTable(makeSection({ mapidRules: [onlyShort] }), locale);
      d = await openAdd(u);
      await selectScope(u, /^Trắc nghiệm/);
      await pickNode(u, "0D2-1");
      text = within(d).getByTestId("mapid-rule-cross-check").textContent ?? "";
      expect(text).toContain("Trả lời ngắn");
      expect(text).toContain("Trắc nghiệm");
    },
  );

  it.each(["vi", "en"] as const)(
    "R1-2 & R1-10 (1e source & overlap, %s): renders bidirectional ONLY_IN_GROUP vs SOURCE and ONLY_IN_GROUP vs ONLY_IN_GROUP messages",
    async (locale) => {
      const onlyShort: MapidRule = { id: "r-only-short", type: "ONLY_IN_GROUP", groupId: "b-short", nodeIds: ["kn-0D2-1"] };
      const srcSet: MapidRule = { id: "r-src-set", type: "SOURCE", groupId: "b-set", nodeIds: ["kn-0D2-1"] };
      const onlySet: MapidRule = { id: "r-only-set", type: "ONLY_IN_GROUP", groupId: "b-set", nodeIds: ["kn-0D2-1"] };
      const cancelLabel = locale === "vi" ? "Hủy" : "Cancel";

      const u = userEvent.setup();
      renderTable(makeSection({ mapidRules: [srcSet] }), locale);
      let d = await openAdd(u);
      await selectScope(u, /^Trả lời ngắn/);
      await u.click(within(d).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
      await pickNode(u, "0D2-1");
      let text = within(d).getByTestId("mapid-rule-cross-check").textContent ?? "";
      expect(text).toContain("Trả lời ngắn");
      expect(text).toContain("Chùm cuối");
      await u.click(within(d).getByRole("button", { name: cancelLabel }));

      cleanup();
      renderTable(makeSection({ mapidRules: [onlyShort] }), locale);
      d = await openAdd(u);
      await selectScope(u, /^Chùm cuối/);
      await u.click(within(d).getByTestId("mapid-rule-type-SOURCE"));
      await pickNode(u, "0D2-1");
      text = within(d).getByTestId("mapid-rule-cross-check").textContent ?? "";
      expect(text).toContain("Trả lời ngắn");
      expect(text).toContain("Chùm cuối");
      await u.click(within(d).getByRole("button", { name: cancelLabel }));

      cleanup();
      renderTable(makeSection({ mapidRules: [onlySet] }), locale);
      d = await openAdd(u);
      await selectScope(u, /^Trả lời ngắn/);
      await u.click(within(d).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
      await pickNode(u, "0D2-1");
      text = within(d).getByTestId("mapid-rule-cross-check").textContent ?? "";
      expect(text).toContain("Trả lời ngắn");
      expect(text).toContain("Chùm cuối");
    },
  );

  it("R1-3, R1-9 & R1-12: invalid '2x' in ONLY_IN_GROUP (withNumber) -> COUNT does not leave '2x' displayed; neutral summary on invalid input; 44px coarse chip remove target", async () => {
    const u = userEvent.setup();
    renderTable(makeSection());
    const dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
    await pickNode(u, "0D2-1");

    // R1-9: MAPID chip remove button expands hit target on coarse pointers
    const removeBtn = within(dialog).getByRole("button", { name: /Bỏ MAPID 0D2-1/ });
    expect(removeBtn).toHaveClass("relative", "size-8", "before:pointer-coarse:absolute", "before:pointer-coarse:-inset-1.5");

    await u.click(within(dialog).getByRole("checkbox", { name: "Kèm số lượng" }));
    const countInput = within(dialog).getByTestId("mapid-rule-count");
    await u.clear(countInput);
    await u.type(countInput, "2x");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    // R1-12: live summary does not display stale count while input is invalid
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("Nhập giá trị hợp lệ để xem câu tóm tắt quy tắc.");

    // R1-3: switch to COUNT -> BlockNumberInput remounts cleanly with valid form.count ("2"), never leaving stale "2x"
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    expect(within(dialog).getByTestId("mapid-rule-count")).toHaveValue("2");
    expect(within(dialog).getByTestId("mapid-rule-count")).not.toHaveValue("2x");
    expect(within(dialog).queryByText(/Số nhập chưa đúng/)).not.toBeInTheDocument();
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();

    // R1-12: PERCENT_RANGE (>100%) also shows neutral pending summary instead of out-of-range percentage
    await u.click(within(dialog).getByRole("radio", { name: "Tỷ lệ" }));
    const pctInput = within(dialog).getByTestId("mapid-rule-percent");
    await u.clear(pctInput);
    await u.type(pctInput, "150");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("Nhập giá trị hợp lệ để xem câu tóm tắt quy tắc.");
  });

  it("R1-4: mapid-rule-replace is absent or disabled when COUNT_ABOVE_TOTAL is active on remainder group with units === null", async () => {
    const u = userEvent.setup();
    const unknownRemSection = makeSection({
      questionCount: 50,
      blocks: [
        block({ id: "b-set-null", name: "Chùm chưa rõ", questionType: "QUESTION_SET", questionCount: null, setCount: null, setSize: 3 }),
        block({ id: "b-rem", name: "Phần còn lại", questionType: "SINGLE_CHOICE", questionCount: null }),
      ],
      mapidRules: [{ id: "r-pres", type: "PRESENT", groupId: "b-rem", nodeIds: ["kn-0D2-1"] }],
    });
    renderTable(unknownRemSection);
    const dialog = await openAdd(u);
    await selectScope(u, /^Phần còn lại/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "51");

    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(dialog).toHaveTextContent(/vượt số câu của môn/i);
    const replaceBtn = within(dialog).queryByTestId("mapid-rule-replace");
    if (replaceBtn) expect(replaceBtn).toBeDisabled();
  });

  it("R1-11: Edit-into-conflict keeps edited input intact & disables Save; ONLY_IN_GROUP (withNumber) -> PRESENT drops stale numbers", async () => {
    const u = userEvent.setup();
    const latest = renderTable(
      makeSection({
        mapidRules: [
          { id: "r-al4", type: "COUNT", groupId: "b-short", nodeIds: ["kn-0D2-1"], comparator: "AT_LEAST", count: 4 },
          { id: "r-ex5", type: "COUNT", groupId: "b-short", nodeIds: ["kn-0D2-1"], comparator: "EXACT", count: 5 },
        ],
      }),
    );

    // Edit r-ex5 from 5 -> 3 into EXACT_BELOW_MINIMUM conflict with r-al4 -> Save disabled & "3" preserved
    await u.click(screen.getByRole("button", { name: /^Sửa quy tắc: Trả lời ngắn · Đúng 5\/15/ }));
    let dialog = dialogOf();
    const countInput = within(dialog).getByTestId("mapid-rule-count");
    await u.clear(countInput);
    await u.type(countInput, "3");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByTestId("mapid-rule-cross-check")).toBeInTheDocument();
    expect(within(dialog).getByTestId("mapid-rule-count")).toHaveValue("3");
    await u.click(within(dialog).getByRole("button", { name: "Hủy" }));

    // Switching ONLY_IN_GROUP (withNumber: true) -> PRESENT drops stale count & comparator on Save
    dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
    await pickNode(u, "0D1");
    await u.click(within(dialog).getByRole("checkbox", { name: "Kèm số lượng" }));
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "4");
    await u.click(within(dialog).getByTestId("mapid-rule-type-PRESENT"));
    expect(within(dialog).queryByTestId("mapid-rule-count")).not.toBeInTheDocument();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    const saved = latest.rules.at(-1);
    expect(saved).toEqual({ id: expect.any(String), type: "PRESENT", groupId: "b-short", nodeIds: ["kn-0D1"] });
  });
});
