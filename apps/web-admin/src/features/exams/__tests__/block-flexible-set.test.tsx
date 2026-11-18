import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { mapBlock, toApiParts } from "../api-mapping";
import { BlockEditor } from "../components/block-editor";
import type { ExamPart, ExamSection, QuestionBlock } from "../types";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useAvailableQuestions: () => ({ isError: false, data: undefined }) as never };
});

const SECTION: ExamSection = {
  id: "s1",
  subjectCode: "TOAN",
  questionCount: 30,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [],
};

function makeBlock(patch: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id: "b1",
    name: "Nhóm 2",
    questionType: "QUESTION_SET",
    questionCount: 18,
    setCount: 6,
    setSize: 3,
    requiredPointsPerQuestion: 10,
    knowledgeScope: null,
    tagCodes: [],
    matrix: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    ...patch,
  };
}

function renderEditor(initial: QuestionBlock, options: { templateCode?: string } = {}) {
  const latest = { block: initial };
  function Harness() {
    const [block, setBlock] = useState(initial);
    return (
      <BlockEditor
        block={block}
        section={SECTION}
        subjectCode="TOAN"
        locationText="Phần 2 · Toán học · nhóm 2 / 2"
        position={1}
        siblingCount={1}
        axes={{ grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) }}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={reference as unknown as ContentReference}
        examKind="VACT"
        templateCode={options.templateCode}
        scoringDefaults={null}
        competencies={[]}
        onChange={(next) => {
          latest.block = next;
          setBlock(next);
        }}
        onMove={() => {}}
        onRemove={() => {}}
        onOpenSection={() => {}}
      />
    );
  }
  renderWithProviders(<Harness />);
  return latest;
}

async function typeInto(user: ReturnType<typeof userEvent.setup>, name: string | RegExp, text: string) {
  const field = screen.getByRole("textbox", { name });
  await user.clear(field);
  await user.type(field, text);
}

describe("chùm cỡ linh hoạt", () => {
  it("chỉ nhập tổng 18 gửi questionCount, bỏ setCount và setSize, ẩn hai ô số chùm; chuyển lại 6 × 3 gửi lại hai số", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ setCount: 5, setSize: 5, questionCount: 25 }));

    await user.click(screen.getByRole("radio", { name: "Chỉ nhập tổng câu con" }));
    expect(screen.queryByRole("textbox", { name: /^Số chùm/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Số câu con mỗi chùm/ })).not.toBeInTheDocument();
    await typeInto(user, /^Tổng số câu con/, "18");
    expect(latest.block).toMatchObject({ questionCount: 18, setCount: null, setSize: null });
    expect(screen.getByText("18 câu con · tối đa 180 điểm")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Khai số chùm và số câu con" }));
    expect(screen.queryByRole("textbox", { name: /^Tổng số câu con/ })).not.toBeInTheDocument();
    await typeInto(user, /^Số chùm/, "6");
    await typeInto(user, /^Số câu con mỗi chùm/, "3");
    expect(latest.block).toMatchObject({ setCount: 6, setSize: 3, questionCount: 18 });
    expect(screen.getByText("6 chùm × 3 câu con = 18 câu con · tối đa 180 điểm")).toBeInTheDocument();
  });

  it("nhóm lưu ở chế độ tổng: gửi lên không có setCount, setSize; đọc lại mở đúng chế độ tổng", async () => {
    const flexible = makeBlock({ setCount: null, setSize: null });
    const part = { id: "p1", sections: [{ ...SECTION, blocks: [flexible] }] } as unknown as ExamPart;
    const sent = toApiParts([part]) as unknown as { sections: { blocks: Record<string, unknown>[] }[] }[];
    const apiBlock = sent[0]!.sections[0]!.blocks[0]!;
    expect(apiBlock).not.toHaveProperty("setCount");
    expect(apiBlock).not.toHaveProperty("setSize");
    expect(apiBlock).toMatchObject({ questionCount: 18 });

    const reloaded = mapBlock(apiBlock as Parameters<typeof mapBlock>[0]);
    expect(reloaded).toMatchObject({ questionCount: 18, setCount: null, setSize: null });
    renderEditor(reloaded);
    expect(screen.getByRole("radio", { name: "Chỉ nhập tổng câu con" })).toBeChecked();
    expect(screen.getByRole("textbox", { name: /^Tổng số câu con/ })).toHaveValue("18");
  });
});

describe("ô Mọi câu con thuộc chủ đề", () => {
  const scoped = { nodeIds: ["N1"] };

  it("chỉ hiện ở Theo chủ đề có MAPID của nhóm chùm; bật thì gửi everyChildInScope = true", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: scoped }));
    const box = screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" });
    expect(box).not.toBeChecked();
    expect(screen.getByText(/chùm 2 câu Xác suất và 1 câu Hình học không được chọn/)).toBeInTheDocument();
    await user.click(box);
    expect(latest.block.knowledgeScope).toEqual({ nodeIds: ["N1"], everyChildInScope: true });
  });

  it("Toàn môn và nhóm câu đơn không có ô", () => {
    renderEditor(makeBlock({ knowledgeScope: null }));
    expect(screen.queryByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).not.toBeInTheDocument();
  });

  it("nhóm câu đơn có MAPID không có ô", () => {
    renderEditor(makeBlock({ questionType: "SINGLE_CHOICE", setCount: null, setSize: null, knowledgeScope: scoped }));
    expect(screen.queryByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).not.toBeInTheDocument();
  });

  it("bài từ mẫu VACT_REVIEW bật sẵn; tắt thì gửi everyChildInScope = false", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: scoped }), { templateCode: "VACT_REVIEW" });
    const box = screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" });
    expect(box).toBeChecked();
    await user.click(box);
    expect(latest.block.knowledgeScope).toEqual({ nodeIds: ["N1"], everyChildInScope: false });
  });
});

describe("dòng tổng của nhóm Đúng/Sai", () => {
  const trueFalse = (count: number) =>
    makeBlock({
      questionType: "TRUE_FALSE",
      questionCount: count,
      setCount: null,
      setSize: null,
      requiredPointsPerQuestion: 1,
      itemShape: { statements: 4 },
    });

  it("4 câu, 4 mệnh đề, 1 điểm mỗi câu ghi 4 câu · 16 ý · tối đa 4 điểm; đổi còn 3 câu thì 3 câu · 12 ý · tối đa 3 điểm", async () => {
    const user = userEvent.setup();
    renderEditor(trueFalse(4));
    expect(screen.getByText("4 câu · 16 ý · tối đa 4 điểm")).toBeInTheDocument();
    await typeInto(user, /^Số câu cần lấy/, "3");
    expect(screen.getByText("3 câu · 12 ý · tối đa 3 điểm")).toBeInTheDocument();
  });
});
