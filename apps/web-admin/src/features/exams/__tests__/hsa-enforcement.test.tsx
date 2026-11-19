import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import { renderWithProviders } from "@/test/render";
import { BlockMatrixEditor } from "../components/block-matrix-editor";
import { EnforcementToggle } from "../components/enforcement-toggle";
import { TopicQuotaTable } from "../components/topic-quota-table";
import { HsaExamContext, hsaNeedsNormalizing, hsaQuotaAwaitsExpert, normalizeHsaParts, normalizeHsaQuotas } from "../hsa-enforcement";
import { emptyMatrix } from "../matrix";
import type { BlockMatrix, ExamPart, ExamQuota, QuestionBlock, QuotaEnforcement, TopicQuota } from "../types";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const reference = referenceFixture as unknown as ContentReference;
const axes = { grades: ["GRADE_10", "GRADE_11"], difficulties: ["RECOGNITION", "COMPREHENSION"] };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };

const block = (matrix: BlockMatrix): QuestionBlock & { matrix: BlockMatrix; questionCount: number } => ({
  id: "blk-1",
  name: "Nhóm",
  questionType: "SINGLE_CHOICE",
  questionCount: 10,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: null,
  knowledgeScope: null,
  tagCodes: [],
  matrix,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
});

function Matrix({ matrix, hsa }: { matrix: BlockMatrix; hsa: boolean }) {
  const [value, setValue] = useState(matrix);
  return (
    <HsaExamContext.Provider value={hsa}>
      <BlockMatrixEditor
        block={block(value)}
        axes={axes}
        labels={labels}
        competencies={[]}
        onChange={setValue}
        available={undefined}
        availableState="loading"
      />
    </HsaExamContext.Provider>
  );
}

const referenceMatrix = (over: Partial<BlockMatrix> = {}): BlockMatrix => ({
  ...emptyMatrix(),
  gradeEnforcement: "REFERENCE",
  difficultyEnforcement: "REFERENCE",
  ...over,
});

const topic = (over: Partial<TopicQuota> = {}): TopicQuota => ({
  id: "q1",
  name: "Xác suất",
  nodeIds: [],
  count: 3,
  enforcement: "REFERENCE",
  basis: "QUESTION",
  ...over,
});

describe("EnforcementToggle: mức cố định của kỳ thi HSA (UNI-1701 AC-2, AC-3)", () => {
  it("HSA, đơn vị câu: chip Bắt buộc kèm lý do, không có nút radio nào, kể cả Tham khảo", () => {
    renderWithProviders(
      <HsaExamContext.Provider value>
        <EnforcementToggle legend="ma trận" name="m" value="REFERENCE" onChange={() => undefined} basis="QUESTION" />
      </HsaExamContext.Provider>,
    );
    const group = screen.getByRole("group", { name: "Mức của ma trận" });
    expect(within(group).getByText("Bắt buộc")).toBeInTheDocument();
    expect(within(group).getByText("Kỳ thi HSA: sinh đề luôn theo ma trận")).toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
    expect(screen.queryByText("Tham khảo")).not.toBeInTheDocument();
  });

  it("H7: HSA nhưng đơn vị không phải câu: Tham khảo cố định kèm lý do của H7", () => {
    renderWithProviders(
      <HsaExamContext.Provider value>
        <EnforcementToggle legend="ma trận" name="m" value="REFERENCE" onChange={() => undefined} basis="POINTS" />
      </HsaExamContext.Provider>,
    );
    const group = screen.getByRole("group", { name: "Mức của ma trận" });
    expect(group).toHaveAttribute("data-fixed", "REFERENCE");
    expect(within(group).getByText("Tham khảo")).toBeInTheDocument();
    expect(within(group).getByText(/Phân bố bắt buộc phải tính theo câu/)).toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("H6: HSA, chỉ tiêu trải nhiều môn: Tham khảo cố định kèm lý do của H6", () => {
    renderWithProviders(
      <HsaExamContext.Provider value>
        <EnforcementToggle
          legend="Lớp"
          name="q"
          value="REFERENCE"
          onChange={() => undefined}
          basis="QUESTION"
          lockRequiredReason="Chỉ tiêu trải nhiều môn chỉ ở mức Tham khảo."
        />
      </HsaExamContext.Provider>,
    );
    const group = screen.getByRole("group", { name: "Mức của Lớp" });
    expect(group).toHaveAttribute("data-fixed", "REFERENCE");
    expect(within(group).getByText("Chỉ tiêu trải nhiều môn chỉ ở mức Tham khảo.")).toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("kỳ thi khác HSA: vẫn là hai nút radio và chuyển mức được", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn<(value: QuotaEnforcement) => void>();
    renderWithProviders(<EnforcementToggle legend="ma trận" name="m" value="REQUIRED" onChange={onChange} basis="QUESTION" />);
    expect(screen.getAllByRole("radio")).toHaveLength(2);
    await user.click(screen.getByRole("radio", { name: "Tham khảo" }));
    expect(onChange).toHaveBeenCalledWith("REFERENCE");
  });
});

describe("Ma trận và chỉ tiêu chủ đề trong bài HSA (UNI-1701 AC-2)", () => {
  it("ma trận kết hợp: không có nút Tham khảo", () => {
    renderWithProviders(<Matrix matrix={referenceMatrix()} hsa />);
    expect(screen.queryByRole("radio", { name: "Tham khảo" })).not.toBeInTheDocument();
    expect(screen.getByText("Kỳ thi HSA: sinh đề luôn theo ma trận")).toBeInTheDocument();
  });

  it("ma trận riêng từng chiều: cả hai chiều cố định, không có nút Tham khảo", () => {
    renderWithProviders(<Matrix matrix={referenceMatrix({ mode: "SEPARATE" })} hsa />);
    expect(screen.queryByRole("radio", { name: "Tham khảo" })).not.toBeInTheDocument();
    expect(screen.getAllByText("Kỳ thi HSA: sinh đề luôn theo ma trận")).toHaveLength(2);
  });

  it("đối chứng, kỳ thi khác: ma trận vẫn có nút Tham khảo", () => {
    renderWithProviders(<Matrix matrix={referenceMatrix()} hsa={false} />);
    expect(screen.getByRole("radio", { name: "Tham khảo" })).toBeChecked();
  });

  it("chỉ tiêu chủ đề: không có nút Tham khảo", () => {
    renderWithProviders(
      <HsaExamContext.Provider value>
        <TopicQuotaTable
          id="s1"
          quotas={[topic()]}
          onChange={() => undefined}
          total={30}
          levelName="Toán học"
          subjectCode="MATH"
          reference={reference}
          axes={axes}
          labels={labels}
        />
      </HsaExamContext.Provider>,
    );
    expect(screen.queryByRole("radio", { name: "Tham khảo" })).not.toBeInTheDocument();
    expect(screen.getByText("Kỳ thi HSA: sinh đề luôn theo ma trận")).toBeInTheDocument();
  });
});

describe("normalizeHsaParts, normalizeHsaQuotas (UNI-1701 AC-4)", () => {
  const section = (over: Record<string, unknown> = {}) => ({
    id: "s1",
    subjectCode: "MATH",
    questionCount: 10,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: referenceMatrix({ cells: [{ gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 5 }] }),
    choiceGroup: null,
    blocks: [{ ...block(referenceMatrix({ basis: "PROMPT", cells: [{ gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 5 }] })), topicQuotas: [topic(), topic({ id: "q2", basis: "POINTS" })] }],
    topicQuotas: [topic({ id: "q3" })],
    ...over,
  });
  const part = (sections: unknown[]) => ({ id: "p1", order: 1, name: "P", mode: "FIXED", sections, options: [] }) as unknown as ExamPart;

  it("đổi Tham khảo sang Bắt buộc ở ma trận môn và chỉ tiêu chủ đề đơn vị câu, giữ Tham khảo của đơn vị khác câu", () => {
    const [result] = normalizeHsaParts([part([section()])]);
    const sec = result!.sections[0]!;
    expect(sec.matrix).toMatchObject({ gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" });
    expect(sec.topicQuotas![0]!.enforcement).toBe("REQUIRED");
    const group = sec.blocks[0]!;
    expect(group.matrix).toMatchObject({ gradeEnforcement: "REFERENCE" });
    expect(group.topicQuotas!.map((item) => item.enforcement)).toEqual(["REQUIRED", "REFERENCE"]);
  });

  it("không có gì đổi thì trả đúng mảng cũ (bản nháp không bị coi là đã sửa)", () => {
    const parts = [part([section({ matrix: null, topicQuotas: [], blocks: [] })])];
    expect(normalizeHsaParts(parts)).toBe(parts);
    const already = [part([section({ matrix: { ...emptyMatrix(), gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" }, topicQuotas: [], blocks: [] })])];
    expect(normalizeHsaParts(already)).toBe(already);
  });

  const filled = (over: Partial<BlockMatrix>): BlockMatrix => referenceMatrix({ mode: "SEPARATE", ...over });
  const matrixOf = (matrix: BlockMatrix) => normalizeHsaParts([part([section({ matrix, topicQuotas: [], blocks: [] })])])[0]!.sections[0]!.matrix!;

  it("R1-2: ma trận Tiếng Anh của mẫu HSA (hai chiều Tham khảo, chưa có số) giữ nguyên, bản nháp không đổi", () => {
    const parts = [part([section({ matrix: filled({ basis: "QUESTION" }), topicQuotas: [], blocks: [] })])];
    expect(normalizeHsaParts(parts)).toBe(parts);
  });

  it("R1-2: ma trận riêng chỉ có số ở chiều lớp: chỉ chiều lớp thành Bắt buộc, chiều độ khó trống ở lại Tham khảo", () => {
    const matrix = matrixOf(filled({ byGrade: [{ code: "GRADE_10", value: 10 }] }));
    expect(matrix).toMatchObject({ gradeEnforcement: "REQUIRED", difficultyEnforcement: "REFERENCE" });
    const other = matrixOf(filled({ byDifficulty: [{ code: "RECOGNITION", value: 10 }] }));
    expect(other).toMatchObject({ gradeEnforcement: "REFERENCE", difficultyEnforcement: "REQUIRED" });
  });

  it("R1-2: ma trận kết hợp chưa có ô nào lớn hơn 0 giữ nguyên", () => {
    expect(matrixOf(referenceMatrix({ cells: [{ gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 0 }] }))).toMatchObject({
      gradeEnforcement: "REFERENCE",
      difficultyEnforcement: "REFERENCE",
    });
  });

  it("R1-4: hsaNeedsNormalizing báo khi bản đã lưu còn Tham khảo mà HSA không cho, và không báo ngoài HSA", () => {
    const saved = [part([section()])];
    expect(hsaNeedsNormalizing(true, saved, "[]")).toBe(true);
    expect(hsaNeedsNormalizing(false, saved, "[]")).toBe(false);
    const clean = [part([section({ matrix: null, topicQuotas: [], blocks: [] })])];
    expect(hsaNeedsNormalizing(true, clean, "[]")).toBe(false);
    const quota = { id: "q", name: "q", dimension: "GRADE", buckets: [], basis: "QUESTION", enforcement: "REFERENCE", subjectCodes: ["MATH"] };
    expect(hsaNeedsNormalizing(true, clean, JSON.stringify([quota]))).toBe(true);
  });

  it("chỉ tiêu cấp bài: một môn thành Bắt buộc, nhiều môn ở lại Tham khảo (H6)", () => {
    const quota = (id: string, subjectCodes: string[]): ExamQuota => ({ id, name: id, dimension: "GRADE", buckets: [], basis: "QUESTION", enforcement: "REFERENCE", subjectCodes });
    const result = normalizeHsaQuotas([quota("one", ["MATH"]), quota("many", ["MATH", "LIT"])]);
    expect(result.map((item) => item.enforcement)).toEqual(["REQUIRED", "REFERENCE"]);
  });

  describe("R1-1: chỉ tiêu chờ chuyên gia của mẫu HSA (ADV:FE-3b:2, AD-29)", () => {
    /** Đúng như migration V202611180652: ba mẫu mang cùng một chỉ tiêu và không chỉ tiêu nào khác. */
    const englishGrade: ExamQuota = {
      id: "hsa-english-grade",
      name: "Lớp Tiếng Anh tham khảo",
      dimension: "GRADE",
      basis: "QUESTION",
      enforcement: "REFERENCE",
      subjectCodes: ["ANH"],
      buckets: [
        { codes: ["G10", "G11"], percent: 30 },
        { codes: ["G12"], percent: 70 },
      ],
    };
    const mathDifficulty: ExamQuota = { ...englishGrade, id: "toan-difficulty", name: "Độ khó Toán", dimension: "DIFFICULTY", subjectCodes: ["TOAN"] };

    it.each(["HSA_FULL", "HSA_PART_ENGLISH", "HSA_SUBJECT_ANH"])("%s: chỉ tiêu khối lớp Tiếng Anh ở lại Tham khảo, bản nháp không đổi", () => {
      const quotas = [englishGrade];
      expect(normalizeHsaQuotas(quotas)).toBe(quotas);
      expect(hsaNeedsNormalizing(true, [], JSON.stringify(quotas))).toBe(false);
    });

    it("mọi chỉ tiêu một môn khác vẫn thành Bắt buộc, chỉ chỉ tiêu Tiếng Anh khối lớp ở lại Tham khảo", () => {
      expect(normalizeHsaQuotas([englishGrade, mathDifficulty]).map((item) => item.enforcement)).toEqual(["REFERENCE", "REQUIRED"]);
      expect(normalizeHsaQuotas([{ ...englishGrade, dimension: "DIFFICULTY" }]).map((item) => item.enforcement)).toEqual(["REQUIRED"]);
      expect(normalizeHsaQuotas([{ ...englishGrade, subjectCodes: ["VAN"] }]).map((item) => item.enforcement)).toEqual(["REQUIRED"]);
    });

    it("hsaQuotaAwaitsExpert: đúng một môn Tiếng Anh, chiều khối lớp", () => {
      expect(hsaQuotaAwaitsExpert(englishGrade)).toBe(true);
      expect(hsaQuotaAwaitsExpert({ ...englishGrade, subjectCodes: ["ANH", "VAN"] })).toBe(false);
      expect(hsaQuotaAwaitsExpert(mathDifficulty)).toBe(false);
    });

    it("EnforcementToggle: Tham khảo cố định kèm lý do chờ chuyên gia, không nút radio", () => {
      renderWithProviders(
        <HsaExamContext.Provider value>
          <EnforcementToggle legend="Lớp Tiếng Anh" name="e" value="REFERENCE" basis="QUESTION" expertPending onChange={() => {}} />
        </HsaExamContext.Provider>,
      );
      expect(screen.queryByRole("radio")).not.toBeInTheDocument();
      expect(screen.getByText("Tham khảo")).toBeInTheDocument();
      expect(screen.getByText(/Chờ chuyên gia xác nhận/)).toBeInTheDocument();
      expect(screen.queryByText("Kỳ thi HSA: sinh đề luôn theo ma trận")).not.toBeInTheDocument();
    });
  });
});
