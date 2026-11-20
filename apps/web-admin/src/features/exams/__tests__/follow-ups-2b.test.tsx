import { act, render, renderHook, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ContentReference } from "@/features/content/types";
import { renderWithProviders } from "@/test/render";
import { getContentReferenceFixture } from "@/test/fixtures/content-reference";
import { BlockMatrixEditor } from "../components/block-matrix-editor";
import { CreateExamScopeFields } from "../components/create-exam-scope-fields";
import { ExamEditorActions, ExamEditorActionsSlot } from "../components/exam-editor-actions";
import { ExamEditorLayout } from "../components/exam-editor-layout";
import { ExamPreviewCompliance } from "../components/exam-preview-compliance";
import { describeStructureChanges } from "../components/exam-structure-changes";
import { ExamStructureLeaveDialog } from "../components/exam-structure-leave-dialog";
import { KnowledgeScopeField } from "../components/knowledge-scope-field";
import { overviewRowsFromTemplate } from "../create-exam-overview";
import { overviewRowLabel } from "../create-exam-row-label";
import { emptyMatrix } from "../matrix";
import type { PreviewCompliance } from "../review-types";
import type { ExamPart, ExamTemplate, KnowledgeScope, QuestionBlock } from "../types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";
import { block as previewBlock, paperOf, partOf, question as previewQuestion, single } from "./preview-paper-fixtures";
import { mapBlock, mapExamTemplate, mapParts } from "../api-mapping";
import { BlockEditor } from "../components/block-editor";
import { blockFieldAnchor } from "../components/block-form-fields";
import { consumeBlockFieldRequest, requestBlockFieldFocus } from "../components/block-field-focus";
import { ReloadStructureDialog } from "../components/reload-structure-dialog";
import { partTypeCounts } from "../part-type-counts";
import { previewMinutesLeft, usePreviewMinutesLeft } from "../preview-retention";
import { issueStructureHref } from "../check";
import type { CheckIssue } from "../review-types";
import reference from "@/test/fixtures/content-reference.json";

const nav = vi.hoisted(() => ({ pathname: "/" }));
vi.mock("next/navigation", () => ({
  usePathname: () => nav.pathname,
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useAvailableQuestions: () => ({ isError: false, data: undefined }) as never };
});

beforeEach(() => {
  nav.pathname = `/exams/${EXAM_ID}`;
  new FakeServer().on("GET", EXAM_PATH, { status: 200, body: examDetailWire() }).install();
});

/* ---------- Dữ liệu ---------- */

function template(over: Partial<ExamTemplate>): ExamTemplate {
  return {
    code: "T",
    kind: "THPTQG",
    scope: "SUBJECT",
    name: "Mẫu",
    timing: "PER_PART",
    durationMinutes: null,
    maxScore: 10,
    buildMode: "STANDARD",
    isDefault: true,
    parts: [],
    ...over,
  };
}

const comparison = (over: Partial<PreviewCompliance>): PreviewCompliance => ({
  criterion: "TOPIC:pending-1",
  scope: "EXAM",
  required: 5,
  actual: 0,
  enforcement: "REFERENCE",
  basis: "QUESTION",
  independent: false,
  note: null,
  originalRequired: null,
  topicName: null,
  ...over,
});

const structureWire = (topicQuotas: unknown[]) => ({
  examId: EXAM_ID,
  revision: 1,
  parts: [{ id: "p1", name: "Phần I", sections: [{ id: "s1", subjectCode: "VIETNAMESE", topicQuotas, blocks: [] }] }],
});

const PENDING_TOPIC = { id: "pending-1", name: "Chính tả, từ vựng và ngữ nghĩa", enforcement: "REFERENCE", nodeIds: [], difficultyCodes: ["RECOGNITION"], count: 5 };

function block(id: string, over: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id,
    name: `Nhóm ${id}`,
    questionType: "SINGLE_CHOICE",
    questionCount: 10,
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
  };
}

function part(blocks: QuestionBlock[], over: Partial<ExamPart> = {}): ExamPart {
  return {
    id: "p1",
    order: 1,
    name: "Phần 1",
    durationMinutes: 60,
    questionCount: 20,
    questionCountOnPaper: 20,
    questionCountRequired: 20,
    participation: { mode: "ALL", choose: null },
    mode: "FIXED",
    sections: [
      {
        id: "s1",
        subjectCode: "MATH",
        questionCount: 20,
        knowledgeScope: null,
        answerQuotas: null,
        matrix: null,
        choiceGroup: null,
        blocks,
      },
    ],
    options: [],
    ...over,
  };
}

describe("lỗi soạn đề 2B", () => {
  describe("thanh tab của trang soạn bài (FU-NAVTABS-REVEAL)", () => {
    /** Khung điện thoại 390px: thanh tab rộng 342px từ x=24, mỗi tab rộng 150px xếp liền nhau, trừ phần đã cuộn. */
    function phoneLayout(list: HTMLElement) {
      list.getBoundingClientRect = () => ({ left: 24, right: 366, top: 0, bottom: 40, width: 342, height: 40 }) as DOMRect;
      within(list)
        .getAllByRole("link")
        .forEach((link, index) => {
          Object.defineProperty(link, "offsetHeight", { configurable: true, value: 40 });
          link.getBoundingClientRect = () => {
            const left = 24 + index * 150 - list.scrollLeft;
            return { left, right: left + 150, top: 0, bottom: 40, width: 150, height: 40 } as DOMRect;
          };
        });
    }

    it("mở tab cuối thì thanh cuộn tới tab đó, quay về tab đầu thì cuộn về đầu", async () => {
      const { rerender } = renderWithProviders(<ExamEditorLayout examId={EXAM_ID}>nội dung</ExamEditorLayout>);
      const bar = await screen.findByRole("navigation", { name: "Các phần của bài thi" });
      const list = bar.querySelector("ul") as HTMLElement;
      phoneLayout(list);

      nav.pathname = `/exams/${EXAM_ID}/versions`;
      rerender(<ExamEditorLayout examId={EXAM_ID}>nội dung</ExamEditorLayout>);
      // Tab thứ 5 chiếm x=624..774; thanh chỉ tới x=366, cộng phần lộ của tab kế bên (40px).
      await waitFor(() => expect(list.scrollLeft).toBe(448));
      const active = within(bar).getByRole("link", { name: "Phiên bản và công bố" });
      expect(active).toHaveAttribute("aria-current", "page");
      expect(active.getBoundingClientRect().right).toBeLessThanOrEqual(366);

      nav.pathname = `/exams/${EXAM_ID}`;
      rerender(<ExamEditorLayout examId={EXAM_ID}>nội dung</ExamEditorLayout>);
      // Tab đầu chiếm x=24..174 khi đã cuộn 448: cần lùi quá đầu, trình duyệt giới hạn về 0.
      await waitFor(() => expect(list.scrollLeft).toBeLessThanOrEqual(0));
    });
  });

  describe("vùng thông báo của Ngữ văn (FU-FE5-R2-01)", () => {
    const selection = { kind: "THPTQG", scope: "SUBJECT", partCode: "", subjectCode: "", buildMode: "STANDARD" } as const;

    async function renderFields(subjectCode: string) {
      const reference: ContentReference = await getContentReferenceFixture("vi");
      const kinds = reference.examKinds.map((kind) => (kind.code === "THPTQG" ? { ...kind, scopes: ["SUBJECT"] } : kind)) as ContentReference["examKinds"];
      const props = {
        kinds,
        subjects: reference.subjects,
        templates: [
          template({ code: "THPTQG_MATH", subjectCode: "MATH" }),
          template({ code: "THPTQG_LITERATURE", subjectCode: "LITERATURE", publishable: false }),
        ],
        errors: {},
        onChange: () => undefined,
      };
      const view = renderWithProviders(<CreateExamScopeFields {...props} selection={{ ...selection, subjectCode }} />);
      return { ...view, props };
    }

    it("vùng role=status có sẵn trong DOM trước khi chọn Ngữ văn, rồi nhận nội dung ở chính nút đó", async () => {
      const { rerender, props } = await renderFields("MATH");
      const region = screen.getByRole("status");
      expect(region).toBeEmptyDOMElement();

      rerender(<CreateExamScopeFields {...props} selection={{ ...selection, subjectCode: "LITERATURE" }} />);
      // Cùng một nút DOM: trình đọc màn hình đọc được thay đổi nội dung của vùng đã có sẵn.
      expect(screen.getByRole("status")).toBe(region);
      expect(region).toHaveTextContent("Toàn bộ môn Ngữ văn là tự luận – tạm bỏ qua trong V1");

      rerender(<CreateExamScopeFields {...props} selection={{ ...selection, subjectCode: "MATH" }} />);
      expect(screen.getByRole("status")).toBe(region);
      expect(region).toBeEmptyDOMElement();
    });
  });

  describe("ô chọn môn ở bước 1 (FU-FE1-2)", () => {
    it("chỉ có môn có mẫu, mỗi môn một lần dù danh mục có dòng trùng mã", async () => {
      const reference = await getContentReferenceFixture("vi");
      const math = reference.subjects.find((subject) => subject.code === "MATH")!;
      const history = reference.subjects.find((subject) => subject.code === "HISTORY")!;
      const physics = reference.subjects.find((subject) => subject.code === "PHYSICS")!;
      const user = userEvent.setup();
      renderWithProviders(
        <CreateExamScopeFields
          kinds={reference.examKinds.map((kind) => (kind.code === "THPTQG" ? { ...kind, scopes: ["SUBJECT"] } : kind)) as ContentReference["examKinds"]}
          // Danh mục dev: Toán hai lần cùng mã, Vật lí không có mẫu.
          subjects={[math, { ...math }, history, physics]}
          templates={[template({ code: "THPTQG_MATH", subjectCode: "MATH" }), template({ code: "THPTQG_HISTORY", subjectCode: "HISTORY" })]}
          selection={{ kind: "THPTQG", scope: "SUBJECT", partCode: "", subjectCode: "", buildMode: "STANDARD" }}
          errors={{}}
          onChange={() => undefined}
        />,
      );

      await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
      const options = (await screen.findAllByRole("option")).map((option) => option.textContent);
      expect(options).toEqual([math.name, history.name]);
    });
  });

  describe("tên phần của bảng tổng quan (FU-FE5-R3-01)", () => {
    const parts = [
      { name: "Phần Toán", questionCount: 10, sections: [{ subjectCode: "MATH", questionCount: 10 }] },
      { name: "Phần Văn", questionCount: 5, sections: [{ subjectCode: "LITERATURE", questionCount: 5 }] },
    ] as unknown as ExamTemplate["parts"];

    it("dòng phần mang chỉ số phần của mẫu, tên tra theo chỉ số chứ không cắt từ khóa dòng", () => {
      const rows = overviewRowsFromTemplate(template({ kind: "VACT", scope: "FULL", parts }), "FULL");
      const partRows = rows.filter((row) => row.kind === "part");
      expect(partRows.map((row) => row.partIndex)).toEqual([0, 1]);

      // Khóa dòng đổi định dạng (ví dụ id phần thay cho chỉ số) không làm lệch tên: nhãn không đọc khóa.
      const renamed = partRows.map((row) => ({ ...row, key: `part-uuid-${row.partIndex}` }));
      const sectionsOf = vi.fn((row: (typeof renamed)[number]) => (row.partIndex === undefined ? [] : (parts[row.partIndex]?.sections ?? [])));
      const labels = renamed.map((row) =>
        overviewRowLabel(
          { vact: true, partName: (code) => code, subjectOf: (code) => `Môn ${code}`, partSections: sectionsOf },
          row,
        ),
      );
      expect(labels).toEqual(["Môn MATH", "Môn LITERATURE"]);
    });
  });

  describe("hàng tham chiếu khi cấu trúc chưa có (FU-FE4A-R1-7)", () => {
    const paper = paperOf([partOf({ blocks: [previewBlock({ items: [single(previewQuestion(1))] })] })]);

    function renderCompliance() {
      return renderWithProviders(
        <ExamPreviewCompliance paper={paper} examId={EXAM_ID} dropped={[]} compliance={[comparison({})]} />,
      );
    }
    const tableName = "Yêu cầu và thực tế của đề thử";

    it("đang tải: hàng chủ đề tham khảo trung tính, không là lệch và không đếm vào số dòng lệch", async () => {
      const server = new FakeServer().on("GET", `${EXAM_PATH}/structure`, { status: 200, body: structureWire([PENDING_TOPIC]) }).hold("GET", `${EXAM_PATH}/structure`).install();
      renderCompliance();

      const only = within(await screen.findByRole("table", { name: tableName })).getAllByRole("row")[1]!;
      expect(within(only).getByText("Chờ mapping")).toBeInTheDocument();
      expect(within(only).getAllByText("Chưa có dữ liệu đối chiếu")).toHaveLength(2);
      expect(within(only).queryByText("Lệch · tham khảo")).not.toBeInTheDocument();
      expect(screen.queryByText(/dòng lệch/)).not.toBeInTheDocument();
      expect(screen.getByText("Đạt mọi tiêu chí")).toBeInTheDocument();

      server.release("GET", `${EXAM_PATH}/structure`);
      // Có cấu trúc: dòng đã gắn MAPID là lệch thật, dòng rỗng nodeIds vẫn chờ mapping.
      await screen.findByText("Chính tả, từ vựng và ngữ nghĩa · Nhận biết");
      expect(screen.getByText("Chờ mapping")).toBeInTheDocument();
    });

    it("tải lỗi: vẫn trung tính và có dòng nhắn chưa đối chiếu được", async () => {
      new FakeServer().on("GET", `${EXAM_PATH}/structure`, { status: 500, body: { code: "INTERNAL", message: "x" } }).install();
      renderCompliance();

      const note = await screen.findByText(/chưa đối chiếu được/);
      expect(note).toBeInTheDocument();
      const only = within(screen.getByRole("table", { name: tableName })).getAllByRole("row")[1]!;
      expect(within(only).getByText("Chờ mapping")).toBeInTheDocument();
      expect(within(only).queryByText("Lệch · tham khảo")).not.toBeInTheDocument();
    });
  });

  describe("danh sách thay đổi của nháp (FU-FE2-3 FE2-R2-01)", () => {
    const subjectName = (code: string) => (code === "MATH" ? "Toán" : code);
    const base = [part([block("b1"), block("b2"), block("b3")])];

    it("nháp chỉ đổi thứ tự nhóm: báo đổi thứ tự, không rỗng", () => {
      const draft = structuredClone(base);
      draft[0]!.sections[0]!.blocks.reverse();
      expect(describeStructureChanges(base, draft, subjectName)).toEqual([{ kind: "blocksReordered", subject: "Toán" }]);
    });

    it("phần hoặc môn không có ở bản đã lưu không bị bỏ qua", () => {
      const extraSection = { ...structuredClone(base[0]!.sections[0]!), id: "s2", subjectCode: "PHYSICS", blocks: [] };
      const withSection = structuredClone(base);
      withSection[0]!.sections.push(extraSection);
      expect(describeStructureChanges(base, withSection, subjectName)).toEqual([{ kind: "sectionAdded", subject: "PHYSICS" }]);

      const withPart = [...structuredClone(base), part([], { id: "p2", name: "Phần 2" })];
      expect(describeStructureChanges(base, withPart, subjectName)).toEqual([{ kind: "partAdded", name: "Phần 2" }]);
      expect(describeStructureChanges(withPart, base, subjectName)).toEqual([{ kind: "partRemoved", name: "Phần 2" }]);
      expect(describeStructureChanges(withSection, base, subjectName)).toEqual([{ kind: "sectionRemoved", subject: "PHYSICS" }]);
    });

    it("đổi thứ tự hai phần: vẫn có dòng, không để hộp thoại hỏi lưu với danh sách rỗng", () => {
      const twoParts = [part([]), part([], { id: "p2", name: "Phần 2" })];
      const swapped = [twoParts[1]!, twoParts[0]!];
      expect(describeStructureChanges(twoParts, swapped, subjectName)).toEqual([{ kind: "structureChanged" }]);
    });

    it("hộp thoại chưa lưu liệt kê dòng đổi thứ tự thay vì để trống", () => {
      const draft = structuredClone(base);
      draft[0]!.sections[0]!.blocks.reverse();
      renderWithProviders(
        <ExamStructureLeaveDialog
          open
          saving={false}
          error={null}
          itemName="Toán"
          destination={{ kind: "general" }}
          changes={describeStructureChanges(base, draft, subjectName)}
          onSaveAndGo={() => undefined}
          onDiscard={() => undefined}
          onStay={() => undefined}
        />,
      );
      expect(within(screen.getByTestId("structure-leave-changes")).getByText("Toán: đổi thứ tự các nhóm.")).toBeInTheDocument();
    });
  });

  describe("hàng nút của trang con (FU-FE2-3 FE2-R2-02)", () => {
    const actions = (
      <ExamEditorActions>
        <button type="button">Lưu thử</button>
      </ExamEditorActions>
    );

    it("khung đã dựng mà chỗ đặt chưa gắn: chưa vẽ gì, nút không nháy dưới nội dung rồi mới nhảy lên hàng tiêu đề", () => {
      render(<ExamEditorActionsSlot.Provider value={null}>{actions}</ExamEditorActionsSlot.Provider>);
      expect(screen.queryByRole("button", { name: "Lưu thử" })).toBeNull();
    });

    it("chỗ đặt đã gắn thì nút vào đúng chỗ đó; ở trong khung thật nút nằm ở hàng tiêu đề", async () => {
      const slot = document.createElement("div");
      document.body.append(slot);
      render(<ExamEditorActionsSlot.Provider value={slot}>{actions}</ExamEditorActionsSlot.Provider>);
      expect(within(slot).getByRole("button", { name: "Lưu thử" })).toBeInTheDocument();
      slot.remove();

      renderWithProviders(<ExamEditorLayout examId={EXAM_ID}>{actions}</ExamEditorLayout>);
      const button = await screen.findByRole("button", { name: "Lưu thử" });
      expect(button.closest("header")).not.toBeNull();
    });

    it("không có khung bao thì hàng nút vẫn nằm ngay tại chỗ gọi", () => {
      render(actions);
      expect(screen.getByRole("button", { name: "Lưu thử" })).toBeInTheDocument();
    });
  });

  describe("bảng ma trận khi cơ sở không phải câu (FU-FE3B-1)", () => {
    const axes = { grades: ["GRADE_10", "GRADE_11"], difficulties: ["RECOGNITION", "COMPREHENSION"] };
    const labels = { grade: (code: string) => code, difficulty: (code: string) => code };

    it("tổng % lệch 100: bảng báo PERCENT_TOTAL cùng lúc việc lưu bị chặn", () => {
      const percent = {
        ...emptyMatrix(),
        unit: "PERCENT" as const,
        basis: "PROMPT" as const,
        cells: [
          { gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 30 },
          { gradeCode: "GRADE_10", difficultyCode: "COMPREHENSION", value: 30 },
          { gradeCode: "GRADE_11", difficultyCode: "RECOGNITION", value: 10 },
          { gradeCode: "GRADE_11", difficultyCode: "COMPREHENSION", value: 10 },
        ],
      };
      renderWithProviders(
        <BlockMatrixEditor
          block={{ ...block("m"), questionCount: 20, matrix: percent }}
          axes={axes}
          labels={labels}
          competencies={[]}
          onChange={() => undefined}
          available={{ total: 0, cells: [], byAnswerType: [] }}
          availableState="ready"
        />,
      );
      expect(screen.getByText(/Tổng tỷ lệ .* đang là 80%|tổng tỷ lệ .* là 80%/i)).toBeInTheDocument();
    });
  });

  describe("chế độ của ô phạm vi kiến thức (FU-FE3AA-V1 V1-04)", () => {
    const NODE = "node-1";
    it("bỏ chip cuối thì ô về Toàn môn cùng dữ liệu, không kẹt ở Theo chủ đề trống", async () => {
      const user = userEvent.setup();
      renderWithProviders(<ScopeHarness initial={{ nodeIds: [NODE] }} />);
      const group = screen.getByRole("group", { name: /Phạm vi kiến thức/ });
      expect(within(group).getByRole("radio", { name: "Theo chủ đề" })).toBeChecked();

      await user.click(within(group).getByRole("button", { name: /^Bỏ/ }));

      expect(within(group).getByRole("radio", { name: "Toàn môn" })).toBeChecked();
      expect(within(group).queryByText("Chưa chọn chủ đề nào.")).not.toBeInTheDocument();
      expect(screen.getByTestId("scope-json")).toHaveTextContent("null");
    });
  });
});

describe("xem trước và đổi phiên bản 2B", () => {
  const paper = paperOf([partOf({ blocks: [previewBlock({ items: [single(previewQuestion(1))] })] })]);
  const tableName = "Yêu cầu và thực tế của đề thử";

  function renderCompliance(rows: PreviewCompliance[]) {
    return renderWithProviders(<ExamPreviewCompliance paper={paper} dropped={[]} compliance={rows} />);
  }

  describe("tên chủ đề của từng hàng (FU-FE4-PREVIEW)", () => {
    it("mỗi hàng chủ đề hiện tên do máy chủ gửi kèm; hàng chưa có tên hiện nhãn chung", async () => {
      renderCompliance([
        comparison({ criterion: "TOPIC:t-1", enforcement: "REQUIRED", required: 4, actual: 4, topicName: "Hàm số và đồ thị" }),
        comparison({ criterion: "TOPIC:t-2", enforcement: "REQUIRED", required: 3, actual: 3, topicName: "Tích phân" }),
        comparison({ criterion: "TOPIC:t-3", enforcement: "REQUIRED", required: 2, actual: 2, topicName: null }),
      ]);

      const rows = within(await screen.findByRole("table", { name: tableName })).getAllByRole("row").slice(1);
      expect(within(rows[0]!).getByText("Hàm số và đồ thị")).toBeInTheDocument();
      expect(within(rows[1]!).getByText("Tích phân")).toBeInTheDocument();
      expect(within(rows[2]!).getByText("Chỉ tiêu chủ đề")).toBeInTheDocument();
      // Tên chủ đề là nội dung đề: đánh dấu để bộ kiểm tra lẫn ngôn ngữ bỏ qua.
      expect(within(rows[0]!).getByText("Hàm số và đồ thị").closest("[data-exam-content]")).not.toBeNull();
    });
  });

  describe("giá trị gốc của câu bị bỏ (FU-FE4-PREVIEW)", () => {
    it("hàng đã bỏ 1 câu ghi cả số gốc từ máy chủ; hàng chưa có số gốc chỉ nêu số câu bỏ", async () => {
      renderCompliance([
        comparison({ criterion: "GRADE:GRADE_12", enforcement: "REQUIRED", required: 16, actual: 16, note: "dropped 1", originalRequired: 17 }),
        comparison({ criterion: "GRADE:GRADE_11", enforcement: "REQUIRED", required: 15, actual: 15, note: "dropped 1", originalRequired: null }),
        comparison({ criterion: "GRADE:GRADE_10", enforcement: "REQUIRED", required: 14, actual: 14, note: null, originalRequired: null }),
      ]);

      const rows = within(await screen.findByRole("table", { name: tableName })).getAllByRole("row").slice(1);
      expect(within(rows[0]!).getByText("16 (17, đã bỏ 1 câu)")).toBeInTheDocument();
      expect(within(rows[1]!).getByText("15 (môn đã bỏ 1 câu)")).toBeInTheDocument();
      expect(within(rows[2]!).getAllByText("14")).toHaveLength(2);
    });
  });

  describe("đếm ngược thời gian giữ đề thử (FU-FE4-PREVIEW)", () => {
    const at = (minutes: number, base = Date.parse("2026-11-20T10:00:00Z")) => new Date(base + minutes * 60_000).toISOString();
    const NOW = Date.parse("2026-11-20T10:00:00Z");

    it("số phút còn lại tính từ hạn máy chủ gửi, làm tròn lên, về 0 khi quá hạn, trống khi không có hạn", () => {
      expect(previewMinutesLeft(at(30), NOW)).toBe(30);
      expect(previewMinutesLeft(at(26.5), NOW)).toBe(27);
      expect(previewMinutesLeft(at(0.2), NOW)).toBe(1);
      expect(previewMinutesLeft(at(-5), NOW)).toBe(0);
      expect(previewMinutesLeft(null, NOW)).toBeNull();
    });

    it("đồng hồ chạy theo thời gian thật: giảm dần đến hết hạn rồi dừng", () => {
      vi.useFakeTimers({ toFake: ["Date", "setInterval", "clearInterval"] });
      try {
        vi.setSystemTime(NOW);
        const { result } = renderHook(() => usePreviewMinutesLeft(at(2)));
        expect(result.current).toBe(2);

        act(() => vi.advanceTimersByTime(60_000));
        expect(result.current).toBe(1);

        act(() => vi.advanceTimersByTime(60_000));
        expect(result.current).toBe(0);
        // Quá hạn thì số phút giữ ở 0, không âm.
        act(() => vi.advanceTimersByTime(120_000));
        expect(result.current).toBe(0);
      } finally {
        vi.useRealTimers();
      }
    });
  });

  describe("bấm lỗi đưa tiêu điểm vào đúng ô (FU-FE4-PREVIEW)", () => {
    const issue = (over: Partial<CheckIssue>) => ({ blockId: "b1", sectionId: "s1", partId: "p1", optionId: null, ...over }) as CheckIssue;

    function renderEditor(blockOver: Partial<QuestionBlock>) {
      const initial = block("b1", { questionType: "QUESTION_SET", setCount: 5, setSize: 5, questionCount: 25, ...blockOver });
      return renderWithProviders(
        <BlockEditor
          block={initial}
          section={{ id: "s1", subjectCode: "MATH", questionCount: 25, knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: null, blocks: [initial] }}
          subjectCode="MATH"
          locationText="Phần 1"
          position={1}
          siblingCount={1}
          axes={{ grades: [], difficulties: [] }}
          labels={{ grade: (code) => code, difficulty: (code) => code }}
          reference={reference as unknown as ContentReference}
          scoringDefaults={null}
          competencies={[]}
          onChange={() => {}}
          onMove={() => {}}
          onRemove={() => {}}
          onOpenSection={() => {}}
        />,
      );
    }

    it("đường dẫn của lỗi gắn một ô mang tên ô; lỗi không gắn ô chỉ mở khối", () => {
      expect(issueStructureHref("x", issue({ code: "SET_SIZE_MISMATCH" }))).toBe("/exams/x/structure?block=b1&field=setCount");
      expect(issueStructureHref("x", issue({ code: "BLOCK_COUNT_LOCKED", dimension: "SET_SIZE" }))).toBe("/exams/x/structure?block=b1&field=setSize");
      expect(issueStructureHref("x", issue({ code: "CELL_SHORTAGE" }))).toBe("/exams/x/structure?block=b1");
    });

    it("mở bằng địa chỉ ?block=&field=: tiêu điểm vào đúng ô rồi bỏ tham số field khỏi địa chỉ", async () => {
      window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?block=b1&field=setSize`);
      const { container } = renderEditor({});

      const target = container.querySelector(`[data-field="${blockFieldAnchor("b1", "setSize")}"]`) as HTMLElement;
      await waitFor(() => expect(target).toHaveFocus());
      expect(window.location.search).toBe("?block=b1");
    });

    it("yêu cầu từ vùng tổng hợp của chính màn Cấu trúc: tiêu điểm vào ô được xin, không vào ô khác", async () => {
      window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure`);
      const { container } = renderEditor({});
      const setCount = container.querySelector(`[data-field="${blockFieldAnchor("b1", "setCount")}"]`) as HTMLElement;
      const setSize = container.querySelector(`[data-field="${blockFieldAnchor("b1", "setSize")}"]`) as HTMLElement;

      act(() => requestBlockFieldFocus("b1", "setCount"));
      await waitFor(() => expect(setCount).toHaveFocus());
      expect(setSize).not.toHaveFocus();
      consumeBlockFieldRequest();
    });
  });

  describe("mapBlock ánh xạ đủ ba trường lọc (FU-FE4-PREVIEW)", () => {
    it("hình dạng câu, định dạng đáp án và điều kiện câu con từ máy chủ vào được khối, cả ở khối của lựa chọn", () => {
      const raw = {
        id: "b1",
        name: "Khối",
        questionType: "QUESTION_SET",
        questionCount: 10,
        itemShape: { statements: 4, options: 4 },
        answerFormats: ["INTEGER", "FRACTION"],
        childConditions: [{ from: 1, to: 2, nodeIds: ["n1"], answerType: "TRUE_FALSE", itemShape: { statements: 4 } }],
      } as Parameters<typeof mapBlock>[0];

      expect(mapBlock(raw)).toMatchObject({
        itemShape: { statements: 4, options: 4 },
        answerFormats: ["INTEGER", "FRACTION"],
        childConditions: [{ from: 1, to: 2, nodeIds: ["n1"], answerType: "TRUE_FALSE", itemShape: { statements: 4 } }],
      });
      // Chi tiết phiên bản đã công bố đọc cùng hàm: không còn bản ánh xạ riêng thiếu ba trường này.
      const parts = mapParts([{ id: "p1", name: "Phần", sections: [{ id: "s1", subjectCode: "MATH", blocks: [raw] }] }] as Parameters<typeof mapParts>[0]);
      expect(parts[0]!.sections[0]!.blocks[0]).toMatchObject({ itemShape: { statements: 4, options: 4 }, answerFormats: ["INTEGER", "FRACTION"] });
    });
  });

  describe("DoiPhienBan2B: dòng đổi theo dạng câu từ API (FU-FE2-1)", () => {
    const wireTemplate = {
      active: true,
      publishable: true,
      fixedCounts: false,
      fixedBlockCounts: false,
      omittedLocked: false,
      buildMode: "STANDARD",
      isDefault: true,
      code: "T_2026",
      kindCode: "THPTQG",
      scope: "SUBJECT",
      subjectCode: "MATH",
      timing: "PER_PART",
      structure: { parts: [{ id: "p1", order: 1, name: "Phần I", questionCount: 20, blocks: [] }] },
      parts: [
        {
          partId: "p1",
          order: 1,
          name: "Phần I",
          questionCount: 20,
          questionTypes: [
            { questionType: "SINGLE_CHOICE", questionCount: 5 },
            { questionType: "MATCHING", questionCount: 10 },
            { questionType: "ANY", questionCount: 5 },
          ],
        },
      ],
    } as unknown as Parameters<typeof mapExamTemplate>[0];

    it("dạng câu của mẫu mới lấy từ API, so với cấu trúc đang có: chỉ dòng có đổi, dạng mới, dạng bỏ", async () => {
      const template = mapExamTemplate(wireTemplate);
      expect(template.parts[0]!.questionTypes).toEqual([
        { questionType: "SINGLE_CHOICE", questionCount: 5 },
        { questionType: "MATCHING", questionCount: 10 },
        { questionType: "ANY", questionCount: 5 },
      ]);
      const current = part(
        [block("c1", { questionType: "SINGLE_CHOICE", questionCount: 10 }), block("c2", { questionType: "TRUE_FALSE", questionCount: 6 }), block("c3", { questionType: "ANY" as never, questionCount: 5 })],
        { name: "Phần I" },
      );
      expect(partTypeCounts(current)).toEqual([
        { questionType: "SINGLE_CHOICE", questionCount: 10 },
        { questionType: "TRUE_FALSE", questionCount: 6 },
        { questionType: "ANY", questionCount: 5 },
      ]);

      renderWithProviders(
        <ReloadStructureDialog
          open
          onOpenChange={() => {}}
          title="Đổi sang T_2026?"
          description="Mô tả"
          confirmLabel="Nạp lại cấu trúc"
          parts={[
            {
              name: "Phần I",
              questionCount: 20,
              durationMinutes: 60,
              shared: false,
              typeCounts: template.parts[0]!.questionTypes,
              before: { name: "Phần I", questionCount: 21, durationMinutes: 60, shared: false, typeCounts: partTypeCounts(current) },
            },
          ]}
          onConfirm={() => {}}
        />,
      );

      const dialog = await screen.findByRole("dialog");
      const changes = within(dialog).getAllByRole("listitem").map((item) => item.textContent);
      expect(changes).toContain("Một đáp án: 10 → 5 câu");
      expect(changes).toContain("Ghép đôi: 0 → 10 câu");
      expect(changes).toContain("Đúng/Sai: 6 → 0 câu");
      // Dạng câu giữ nguyên số câu (ANY 5 → 5) không có dòng.
      expect(changes.some((line) => line?.startsWith("Dạng câu chưa ghi"))).toBe(false);
    });

    it("phần mới chưa có phía trước: liệt kê dạng câu của phần sẽ nạp", async () => {
      const template = mapExamTemplate(wireTemplate);
      renderWithProviders(
        <ReloadStructureDialog
          open
          onOpenChange={() => {}}
          title="Đổi sang T_2026?"
          description="Mô tả"
          confirmLabel="Nạp lại cấu trúc"
          parts={[{ name: "Phần I", questionCount: 20, durationMinutes: 60, shared: false, typeCounts: template.parts[0]!.questionTypes, before: null }]}
          onConfirm={() => {}}
        />,
      );

      const dialog = await screen.findByRole("dialog");
      const changes = within(dialog).getAllByRole("listitem").map((item) => item.textContent);
      expect(changes).toContain("Ghép đôi: 10 câu");
      expect(changes).toContain("Dạng câu chưa ghi: 5 câu");
    });
  });
});

function ScopeHarness({ initial }: { initial: KnowledgeScope | null }) {
  const [scope, setScope] = useState<KnowledgeScope | null>(initial);
  return (
    <>
      <KnowledgeScopeField value={scope} onChange={setScope} reference={undefined} subjectCode="MATH" name="Nhóm" anchor="knowledgeScope" />
      <output data-testid="scope-json">{JSON.stringify(scope)}</output>
    </>
  );
}
