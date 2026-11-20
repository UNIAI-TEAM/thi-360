import { screen, within } from "@testing-library/react";
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
  const picker = await screen.findByRole("dialog", { name: /Chọn kiến thức|Pick knowledge/ });
  await u.type(within(picker).getByRole("searchbox"), code);
  await u.click(await within(picker).findByRole("checkbox", { name: new RegExp(`^${code}\\b`) }));
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

  it("AC-1707-7: EQUIVALENT blocks and opens existing row; SUBSUMES offers Thay quy tắc cũ; Group sum > Subject EXACT blocks with open buttons", async () => {
    const u = userEvent.setup();
    const initialRules: MapidRule[] = [
      { id: "r-pres", type: "PRESENT", groupId: null, nodeIds: ["kn-0D2-1"] },
    ];
    const latest = renderTable(makeSection({ mapidRules: initialRules }));

    // 1. Equivalent: COUNT AT_LEAST 1 on same node/scope -> blocks & opens r-pres
    let dialog = await openAdd(u);
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

    // 3. Allowed: add Group Trả lời ngắn EXACT 2 (2 <= 3)
    dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "2");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.rules).toHaveLength(2);

    // 4. Conflict: add second Group Trắc nghiệm AT_LEAST 2 (2 + 2 = 4 > 3) -> blocked, shows message + open buttons
    dialog = await openAdd(u);
    await selectScope(u, /^Trắc nghiệm/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "2");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByTestId("mapid-rule-cross-check")).toHaveTextContent(
      "Các nhóm đang yêu cầu tối thiểu 4 câu Hàm số, vượt mức 3 câu của toàn môn.",
    );
    // Clicking open on subject rule switches dialog to edit that rule
    const subjectRuleId = latest.rules[0]!.id;
    await u.click(within(dialog).getByTestId(`mapid-rule-open-${subjectRuleId}`));
    expect(within(dialogOf()).getByTestId("mapid-rule-count")).toHaveValue("3");
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
});
