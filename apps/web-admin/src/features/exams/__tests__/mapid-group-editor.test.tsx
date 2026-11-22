import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { BlockEditor } from "../components/block-editor";
import { revealMapidRulesTable } from "../mapid-rules";
import type { ExamSection, MapidRule, QuestionBlock } from "../types";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useAvailableQuestions: () => ({ isError: false, data: undefined }) as never };
});

const GROUP: QuestionBlock = {
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
};
const OTHER: QuestionBlock = { ...GROUP, id: "b-single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 };

const OWN: MapidRule = { id: "r-own", type: "COUNT", groupId: "b-short", nodeIds: ["kn-0D1"], comparator: "EXACT", count: 3 };
const SOURCE: MapidRule = { id: "r-source", type: "SOURCE", groupId: null, nodeIds: ["kn-0D2"] };
const ELSEWHERE: MapidRule = { id: "r-else", type: "ONLY_IN_GROUP", groupId: "b-single", nodeIds: ["kn-0D2-2"] };
const OF_OTHER_GROUP: MapidRule = { id: "r-other", type: "PRESENT", groupId: "b-single", nodeIds: ["kn-0D1"] };

function makeSection(over: Partial<ExamSection> = {}): ExamSection {
  return {
    id: "s1",
    subjectCode: "TOAN",
    questionCount: 47,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: null,
    choiceGroup: null,
    blocks: [OTHER, GROUP],
    mapidMode: "MATRIX",
    mapidRules: [OWN, SOURCE, ELSEWHERE, OF_OTHER_GROUP],
    ...over,
  };
}

function renderEditor(section: ExamSection, { examKind = "HSA", onOpenSection = () => {} }: { examKind?: string; onOpenSection?: () => void } = {}) {
  const latest = { block: GROUP };
  function Harness() {
    const [block, setBlock] = useState(GROUP);
    return (
      <BlockEditor
        block={block}
        section={{ ...section, blocks: section.blocks.map((item) => (item.id === block.id ? block : item)) }}
        subjectCode={section.subjectCode}
        locationText="Phần 1 · Toán · nhóm 2 / 2"
        position={2}
        siblingCount={2}
        axes={{ grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) }}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={reference as unknown as ContentReference}
        examKind={examKind}
        scoringDefaults={null}
        competencies={[{ code: "TD", name: "Tư duy" }] as never}
        onChange={(next) => {
          latest.block = next;
          setBlock(next);
        }}
        onMove={() => {}}
        onRemove={() => {}}
        onOpenSection={onOpenSection}
      />
    );
  }
  renderWithProviders(<Harness />);
  return latest;
}

const scopeField = () => screen.queryByText("Phạm vi kiến thức");
const quotaTable = () => screen.queryByRole("heading", { name: "Chỉ tiêu chủ đề" });

describe("nhóm lấy câu của môn HSA dùng ma trận MAPID", () => {
  it("ẩn Phạm vi kiến thức và Chỉ tiêu chủ đề; hiện quy tắc có hiệu lực của nhóm thành câu", () => {
    renderEditor(makeSection());

    expect(scopeField()).not.toBeInTheDocument();
    expect(quotaTable()).not.toBeInTheDocument();
    const summary = screen.getByTestId("mapid-group-rules");
    const lines = within(summary).getAllByRole("listitem");
    expect(lines.map((line) => line.textContent)).toEqual([
      "Đúng 3/15 câu thuộc Mệnh đề và tập hợp",
      "Chỉ lấy từ Hàm số bậc hai",
      "Không lấy Parabol (chỉ xuất hiện ở nhóm khác)",
    ]);
    expect(within(summary).queryByText(/Phải có/)).not.toBeInTheDocument();
  });

  it("giữ bộ lọc năng lực và các điều kiện khác; menu Thêm điều kiện không có MAPID", async () => {
    const user = userEvent.setup();
    renderEditor(makeSection());

    expect(screen.getByRole("checkbox", { name: "Tư duy" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thêm điều kiện" }));
    const menu = await screen.findByRole("menu");
    expect(within(menu).queryByRole("menuitem", { name: /Kiến thức \(MAPID\)/ })).not.toBeInTheDocument();
    expect(within(menu).getByRole("menuitem", { name: /Nhãn hoặc loại văn bản/ })).toBeInTheDocument();
    expect(within(menu).getByRole("menuitem", { name: /Điểm mỗi câu/ })).toBeInTheDocument();
  });

  it("nhóm chưa có quy tắc nào: một dòng nói rõ; môn ở Toàn bài: nói không giới hạn MAPID", () => {
    renderEditor(makeSection({ mapidRules: [OF_OTHER_GROUP] }));
    expect(within(screen.getByTestId("mapid-group-rules")).getByText("Chưa có quy tắc MAPID nào áp dụng cho nhóm này.")).toBeInTheDocument();
  });

  it("môn chưa có mapidMode và không có dữ liệu cũ (hiện Toàn bài) cũng ẩn hai mục cũ", () => {
    renderEditor(makeSection({ mapidMode: undefined, mapidRules: undefined }));
    expect(scopeField()).not.toBeInTheDocument();
    expect(quotaTable()).not.toBeInTheDocument();
    expect(within(screen.getByTestId("mapid-group-rules")).getByText("Môn đang ở chế độ Không giới hạn MAPID: không giới hạn MAPID cho nhóm này.")).toBeInTheDocument();
  });

  it("nút Mở bảng Quy tắc MAPID chuyển sang màn môn", async () => {
    const user = userEvent.setup();
    const onOpenSection = vi.fn();
    renderEditor(makeSection(), { onOpenSection });

    await user.click(screen.getByRole("button", { name: "Mở bảng Quy tắc MAPID" }));
    expect(onOpenSection).toHaveBeenCalledTimes(1);
  });

  it("nhóm cũ chưa chuyển được (còn dữ liệu kiểu cũ trong môn), kỳ thi khác HSA và môn Văn: giữ kiểu cũ", () => {
    const legacyQuota = { id: "q1", nodeIds: ["kn-0D1"], count: 2, percent: null, enforcement: "MANDATORY" };
    const legacy = makeSection({ mapidMode: undefined, mapidRules: undefined, topicQuotas: [legacyQuota as never], knowledgeScope: { nodeIds: ["kn-0D1"] } });
    for (const [section, kind] of [
      [legacy, "HSA"],
      [makeSection(), "VACT"],
      [makeSection({ subjectCode: "VAN", mapidMode: undefined, mapidRules: undefined }), "HSA"],
    ] as const) {
      renderEditor(section, { examKind: kind });
      expect(scopeField()).toBeInTheDocument();
      expect(screen.queryByTestId("mapid-group-rules")).not.toBeInTheDocument();
      expect(quotaTable()).toBeInTheDocument();
      document.body.innerHTML = "";
    }
  });
});

describe("revealMapidRulesTable", () => {
  it("chờ bảng xuất hiện sau khi màn môn mở rồi cuộn tới và đưa tiêu điểm vào", async () => {
    const root = document.createElement("div");
    document.body.append(root);
    const done = revealMapidRulesTable(root);
    const table = document.createElement("section");
    table.setAttribute("data-testid", "mapid-rules-table");
    table.tabIndex = -1;
    table.scrollIntoView = vi.fn();
    setTimeout(() => root.append(table), 40);

    await expect(done).resolves.toBe(true);
    expect(table.scrollIntoView).toHaveBeenCalledWith({ block: "start" });
    expect(table).toHaveFocus();
    root.remove();
  });

  it("không có bảng (môn ở Toàn bài) thì trả false, không treo", async () => {
    const root = document.createElement("div");
    await expect(revealMapidRulesTable(root, 3)).resolves.toBe(false);
  });
});
