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

// UNI-2824: quy tắc MAPID "ngân sách lồng nhau" (quyết định 2026-11-26) trên hộp thoại và bảng quy tắc.

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

function makeSection(rules: MapidRule[], shortCount = 15): ExamSection {
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
      block({ id: "b-short", name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: shortCount }),
      block({ id: "b-set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 }),
    ],
    mapidMode: "MATRIX",
    mapidRules: rules,
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
  await u.click(within(within(dialogOf()).getByTestId("mapid-rule-scope")).getByRole("combobox"));
  await u.click(await screen.findByRole("option", { name: optionPattern }));
}

async function pickNode(u: UserEvent, code: string) {
  await u.click(within(dialogOf()).getByTestId("mapid-rule-mapid-picker"));
  const picker = await screen.findByRole("dialog", { name: /Chọn kiến thức|Choose knowledge/ });
  await u.type(within(picker).getByRole("searchbox"), code);
  await u.click(await within(picker).findByRole("checkbox", { name: new RegExp(`^${code} `) }));
  await u.click(within(picker).getByRole("button", { name: /Áp dụng|Apply/ }));
}

async function setCount(u: UserEvent, dialog: HTMLElement, value: string) {
  await u.clear(within(dialog).getByTestId("mapid-rule-count"));
  await u.type(within(dialog).getByTestId("mapid-rule-count"), value);
}

const exact = (id: string, groupId: string | null, nodeId: string, count: number): MapidRule => ({
  id,
  type: "COUNT",
  groupId,
  nodeIds: [nodeId],
  comparator: "EXACT",
  count,
});
const atLeast = (id: string, groupId: string | null, nodeId: string, count: number): MapidRule => ({
  id,
  type: "COUNT",
  groupId,
  nodeIds: [nodeId],
  comparator: "AT_LEAST",
  count,
});

const twoGroupsNeedFour = (): MapidRule[] => [
  atLeast("r-short", "b-short", "kn-0D2-1", 2),
  atLeast("r-set", "b-set", "kn-0D2-1-1", 2),
];

describe("UNI-2824 hộp thoại: ô số báo giới hạn ngay khi gõ", () => {
  it("toàn môn 'đúng N' không thấp hơn phần các nhóm đã cần; 'ít nhất N' không bao giờ vướng nhóm", async () => {
    const u = userEvent.setup();
    const latest = renderTable(makeSection(twoGroupsNeedFour()));
    const dialog = await openAdd(u);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await setCount(u, dialog, "3");
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "Các nhóm đã cần 4 câu Hàm số (Trả lời ngắn 2, Chùm cuối 2). Nhập ít nhất 4.",
    );
    expect(within(dialog).getByTestId("mapid-rule-count")).toHaveAttribute("aria-invalid", "true");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).queryByTestId("mapid-rule-cross-check")).not.toBeInTheDocument();

    await setCount(u, dialog, "5");
    expect(within(dialog).getByTestId("mapid-rule-budget")).toHaveTextContent(
      "Các nhóm đã cần 4 câu Hàm số (Trả lời ngắn 2, Chùm cuối 2); còn 1 câu lấy ngẫu nhiên.",
    );
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();

    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Ít nhất" }));
    await setCount(u, dialog, "3");
    expect(within(dialog).getByTestId("mapid-rule-budget")).toHaveTextContent("Đã đủ nhờ các nhóm (Trả lời ngắn 2, Chùm cuối 2).");
    await setCount(u, dialog, "6");
    expect(within(dialog).getByTestId("mapid-rule-budget")).toHaveTextContent("toàn môn còn cần thêm 2 câu.");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.rules).toHaveLength(3);
  });

  it("MAPID chỉ xuất hiện ở nhóm A: số toàn môn không vượt số câu của nhóm A", async () => {
    const u = userEvent.setup();
    renderTable(makeSection([{ id: "r-only", type: "ONLY_IN_GROUP", groupId: "b-set", nodeIds: ["kn-0D2"] }]));
    const dialog = await openAdd(u);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickNode(u, "0D2-1");
    await setCount(u, dialog, "4");
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "Hàm số chỉ xuất hiện ở nhóm Chùm cuối (3 câu) nên toàn môn có tối đa 3 câu.",
    );
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    await setCount(u, dialog, "3");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
  });
});

describe("UNI-2824 nguồn toàn môn là biên ngoài", () => {
  it("hộp chọn MAPID của nhóm chỉ hiện nút nằm trong nguồn toàn môn; tổ tiên chỉ để dẫn đường", async () => {
    const u = userEvent.setup();
    renderTable(makeSection([{ id: "r-src", type: "SOURCE", groupId: null, nodeIds: ["kn-0D2-1"] }]));
    const dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn/);
    await u.click(within(dialog).getByTestId("mapid-rule-mapid-picker"));
    const picker = await screen.findByRole("dialog", { name: /Chọn kiến thức/ });
    expect(picker).toHaveTextContent("chỉ hiện MAPID nằm trong nguồn của toàn môn");
    expect(await within(picker).findByRole("checkbox", { name: /^0D2-1-1 / })).toBeInTheDocument();
    expect(within(picker).getByRole("checkbox", { name: /^0D2-1 / })).toBeInTheDocument();
    expect(within(picker).getByText("Hàm số bậc hai")).toBeInTheDocument();
    expect(within(picker).queryByRole("checkbox", { name: /^0D2 / })).not.toBeInTheDocument();
    await u.type(within(picker).getByRole("searchbox"), "0D1");
    expect(within(picker).queryByRole("checkbox", { name: /^0D1/ })).not.toBeInTheDocument();
    await u.click(within(picker).getByRole("button", { name: "Hủy" }));

    // Quy tắc toàn môn thì không bị giới hạn.
    await selectScope(u, /^Toàn môn/);
    await pickNode(u, "0D1");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
  });

  it("thu hẹp nguồn toàn môn làm quy tắc nhóm rơi ra ngoài thì hỏi trước, liệt kê dòng bị ảnh hưởng", async () => {
    const u = userEvent.setup();
    const latest = renderTable(
      makeSection([
        { id: "r-out", type: "PRESENT", groupId: "b-short", nodeIds: ["kn-0D1-1"] },
        { id: "r-in", type: "PRESENT", groupId: "b-single", nodeIds: ["kn-0D2-1-1"] },
      ]),
    );
    let dialog = await openAdd(u);
    await u.click(within(dialog).getByTestId("mapid-rule-type-SOURCE"));
    await pickNode(u, "0D2-1");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    let ask = within(dialog).getByTestId("mapid-rule-narrow-source");
    expect(ask).toHaveTextContent("không còn chứa MAPID của 1 quy tắc nhóm dưới đây");
    expect(within(ask).getAllByRole("listitem")).toHaveLength(1);
    expect(within(ask).getByRole("listitem")).toHaveTextContent("Mệnh đề");
    expect(latest.rules).toHaveLength(2);
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();

    await u.click(within(ask).getByRole("button", { name: "Quay lại sửa" }));
    expect(within(dialog).queryByTestId("mapid-rule-narrow-source")).not.toBeInTheDocument();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    dialog = dialogOf();
    ask = within(dialog).getByTestId("mapid-rule-narrow-source");
    await u.click(within(ask).getByTestId("mapid-rule-narrow-confirm"));
    expect(latest.rules).toHaveLength(3);
    expect(screen.queryByTestId("mapid-rule-save")).not.toBeInTheDocument();

    const warning = screen.getByTestId("mapid-rule-warning-r-out");
    expect(warning).toHaveAttribute("data-code", "OUTSIDE_SUBJECT_SOURCE");
    expect(warning).toHaveTextContent("MAPID nằm ngoài nguồn của toàn môn");
    expect(screen.queryByTestId("mapid-rule-warning-r-in")).not.toBeInTheDocument();
  });
});

describe("UNI-2824 bảng quy tắc: cảnh báo trên dòng kèm nút sửa nhanh", () => {
  const brokenBudget = (): MapidRule[] => [exact("r-subj", null, "kn-0D2-1", 3), ...twoGroupsNeedFour()];

  it("giảm nhóm Chùm còn 1 thì các cảnh báo hết", async () => {
    const u = userEvent.setup();
    const latest = renderTable(makeSection(brokenBudget()));
    expect(screen.getByTestId("mapid-rule-warning-r-subj")).toHaveTextContent(
      "Các nhóm đang cần 4 câu (Trả lời ngắn 2, Chùm cuối 2), nhiều hơn số câu của quy tắc này.",
    );
    expect(screen.getByTestId("mapid-rule-fix-r-subj")).toHaveTextContent("Nâng toàn môn lên 4");
    expect(screen.getByTestId("mapid-rule-warning-r-short")).toHaveTextContent("Toàn môn chỉ còn 1 câu Hàm số chưa chia cho nhóm này");
    expect(screen.getByTestId("mapid-rule-fix-r-set")).toHaveTextContent("Giảm nhóm Chùm cuối còn 1");
    await u.click(screen.getByTestId("mapid-rule-fix-r-set"));
    expect(latest.rules.find((rule) => rule.id === "r-set")).toMatchObject({ count: 1 });
    expect(screen.queryAllByTestId(/^mapid-rule-warning-/)).toHaveLength(0);
  });

  it("nâng toàn môn lên 4 thì các cảnh báo hết", async () => {
    const u = userEvent.setup();
    const latest = renderTable(makeSection(brokenBudget()));
    await u.click(screen.getByTestId("mapid-rule-fix-r-subj"));
    expect(latest.rules.find((rule) => rule.id === "r-subj")).toMatchObject({ count: 4 });
    expect(screen.queryAllByTestId(/^mapid-rule-warning-/)).toHaveLength(0);
  });

  it("sửa cấu trúc bớt câu của nhóm: vẫn hiện bảng, dòng vượt số câu có nút giảm về số câu của nhóm", async () => {
    const u = userEvent.setup();
    const latest = renderTable(makeSection([atLeast("r-short", "b-short", "kn-0D2-1", 8)], 5));
    expect(screen.getByTestId("mapid-rule-warning-r-short")).toHaveTextContent("Nhóm chỉ còn 5 câu sau khi sửa cấu trúc.");
    await u.click(screen.getByRole("button", { name: "Giảm nhóm Trả lời ngắn còn 5" }));
    expect(latest.rules[0]).toMatchObject({ count: 5 });
    expect(screen.queryByTestId("mapid-rule-warning-r-short")).not.toBeInTheDocument();
  });

  it("tiếng Anh: cảnh báo và nút sửa nhanh không lẫn tiếng Việt ngoài tên nhóm", () => {
    renderTable(makeSection(brokenBudget()), "en");
    expect(screen.getByTestId("mapid-rule-warning-r-subj")).toHaveTextContent(
      "The groups need 4 questions (Trả lời ngắn 2, Chùm cuối 2), more than this rule allows.",
    );
    expect(screen.getByTestId("mapid-rule-fix-r-subj")).toHaveTextContent("Raise whole subject to 4");
    expect(screen.getByTestId("mapid-rule-fix-r-set")).toHaveTextContent("Lower group Chùm cuối to 1");
    cleanup();
  });
});
