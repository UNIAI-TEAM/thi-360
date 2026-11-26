import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { getContentReferenceFixture } from "@/test/fixtures/content-reference";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";
import {
  boot,
  checkCalls,
  openStructure,
  putCalls,
  requestLog,
  sectionsOf,
  wireBlock,
  wirePart,
  wireSection,
  type Wire,
} from "./subject-form-harness";
import { MatrixFormHsa } from "../subject-forms/matrix-form-hsa";
import { subjectFormFor } from "../subject-forms/registry";
import { subjectFormLimits } from "../subject-forms/limits";
import { MATRIX_STRUCTURE_HEADING, rowCountInput, rowSetsInput, rowSetSizeInput, structureInput } from "./subject-matrix-harness";

// Alias mã môn danh mục để lọc MAPID theo mã môn HSA, kèm nhãn tư liệu cho ca Địa lí (chỉ trong mock test).
const { adaptRef } = vi.hoisted(() => ({
  adaptRef: (raw: unknown): ContentReference => {
    const text = JSON.stringify(raw)
      .replaceAll('"MATH"', '"TOAN"')
      .replaceAll('"PHYSICS"', '"LY"')
      .replaceAll('"CHEMISTRY"', '"HOA"')
      .replaceAll('"BIOLOGY"', '"SINH"')
      .replaceAll('"HISTORY"', '"SU"')
      .replaceAll('"GEOGRAPHY"', '"DIA"');
    const parsed = JSON.parse(text) as ContentReference;
    return {
      ...parsed,
      tags: [
        ...parsed.tags,
        { code: "DIA_BAN_DO", name: "Bản đồ", nameEn: "Map" } as ContentReference["tags"][number],
        { code: "DIA_ATLAT", name: "Át-lát", nameEn: "Atlas" } as ContentReference["tags"][number],
      ],
    };
  },
}));

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async (locale?: Parameters<typeof getContentReferenceFixture>[0]) =>
    adaptRef(await getContentReferenceFixture(locale));
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

afterEach(() => routerMock.push.mockReset());

const type = async (user: UserEvent, input: HTMLElement, text: string) => {
  await user.clear(input);
  if (text !== "") await user.type(input, text);
};

function wireHsaMath(id = "sec-toan", over: Wire = {}, singleOver: Wire = {}): Wire {
  return wireSection(
    id,
    "TOAN",
    [
      wireBlock(`${id}-single`, { name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: 32, templateGroupCode: "HSA_MATH_SINGLE", ...singleOver }),
      wireBlock(`${id}-fill`, { name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 15, templateGroupCode: "HSA_MATH_SINGLE" }),
      wireBlock(`${id}-set`, { name: "Chùm cuối", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, templateGroupCode: "HSA_MATH_CONTEXT" }),
    ],
    {
      answerQuotas: {
        unit: "COUNT",
        items: [
          { value: 32, answerType: "SINGLE_CHOICE" },
          { value: 15, answerType: "SHORT_ANSWER" },
          { value: 3, answerType: "QUESTION_SET" },
        ],
      },
      matrix: {
        mode: "SEPARATE",
        unit: "COUNT",
        basis: "QUESTION",
        cells: [],
        byGrade: [
          { code: "GRADE_10", value: 3 },
          { code: "GRADE_11", value: 20 },
          { code: "GRADE_12", value: 27 },
        ],
        byDifficulty: [
          { code: "RECOGNITION", value: 2 },
          { code: "COMPREHENSION", value: 21 },
          { code: "APPLICATION", value: 20 },
          { code: "HIGH_APPLICATION", value: 7 },
        ],
        gradeEnforcement: "REQUIRED",
        difficultyEnforcement: "REQUIRED",
      },
      ...over,
    },
  );
}

function wireHsaScience(id: string, subjectCode: "LY" | "HOA" | "SINH", over: Wire = {}): Wire {
  return wireSection(
    id,
    subjectCode,
    [
      wireBlock(`${id}-single`, { name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: 13, templateGroupCode: `HSA_${subjectCode}_FLEX` }),
      wireBlock(`${id}-set`, { name: "Chùm", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, templateGroupCode: `HSA_${subjectCode}_CLUSTER` }),
      wireBlock(`${id}-fill`, { name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 1, templateGroupCode: `HSA_${subjectCode}_FLEX` }),
    ],
    {
      choiceGroup: "NATURAL",
      matrix: {
        mode: "SEPARATE",
        unit: "COUNT",
        basis: "QUESTION",
        cells: [],
        byGrade: [
          { code: "GRADE_10", value: 5 },
          { code: "GRADE_11", value: 6 },
          { code: "GRADE_12", value: 6 },
        ],
        byDifficulty: [
          { code: "RECOGNITION", value: 3 },
          { code: "COMPREHENSION", value: 1 },
          { code: "APPLICATION", value: 13 },
        ],
        gradeEnforcement: "REQUIRED",
        difficultyEnforcement: "REQUIRED",
      },
      ...over,
    },
  );
}

function wireHsaSocial(id: string, subjectCode: "SU" | "DIA", { withFill = false, ...over }: { withFill?: boolean } & Wire = {}): Wire {
  const blocks: Wire[] = [
    wireBlock(`${id}-single`, { name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: withFill ? 13 : 14, templateGroupCode: `HSA_${subjectCode}_FLEX` }),
    wireBlock(`${id}-set`, { name: "Chùm", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, templateGroupCode: `HSA_${subjectCode}_CLUSTER` }),
  ];
  if (withFill) {
    blocks.push(wireBlock(`${id}-fill`, { name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 1, templateGroupCode: `${subjectCode}_FILL_CUSTOM`, requiredPointsPerQuestion: 1, tagCodes: [] }));
  }
  return wireSection(id, subjectCode, blocks, {
    choiceGroup: "SOCIAL",
    matrix: {
      mode: "SEPARATE",
      unit: "COUNT",
      basis: "QUESTION",
      cells: [],
      byGrade: [
        { code: "GRADE_10", value: 2 },
        { code: "GRADE_11", value: 5 },
        { code: "GRADE_12", value: 10 },
      ],
      byDifficulty: [
        { code: "RECOGNITION", value: 7 },
        { code: "COMPREHENSION", value: 8 },
        { code: "APPLICATION", value: 2 },
      ],
      gradeEnforcement: "REQUIRED",
      difficultyEnforcement: "REQUIRED",
    },
    ...over,
  });
}

function wireHsaEnglish(id = "sec-anh"): Wire {
  return wireSection(id, "ANH", [
    wireBlock(`${id}-1`, { name: "Hoàn thành câu", questionType: "SINGLE_CHOICE", questionCount: 10 }),
    wireBlock(`${id}-2`, { name: "Đọc hiểu", questionType: "QUESTION_SET", setCount: 1, setSize: 5, questionCount: 5 }),
  ]);
}

async function openHsa(sectionId: string, parts: Wire[], { checkBody }: { checkBody?: Wire } = {}) {
  const booted = boot({ kind: "HSA", parts, ...(checkBody ? { checkBody } : {}) });
  const view = openStructure(`?section=${sectionId}`);
  await screen.findByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING });
  return { ...booted, view };
}

describe("HSA MT: Cấu trúc câu, chỉ tiêu và giới hạn (CR-2H AC-1..AC-5)", () => {
  it("hsaMathDefaultsAndGap: Toán nạp 32 / 15 / 1×3 cộng thẳng = 50; hiện đủ cột bảng v3; đổi Trả lời ngắn 15 -> 12 ra 47/50, thiếu 3 và vẫn lưu nháp được", async () => {
    const user = userEvent.setup();
    const { server } = await openHsa("sec-toan", [wirePart([wireHsaMath()])]);
    const structureBlock = document.querySelector('[data-form-field="STRUCTURE"]') as HTMLElement;
    for (const label of ["Vị trí câu", "Tên nhóm trên đề", "Loại câu hỏi", "Quy cách lấy câu", "Số câu", "Tỷ lệ", "Thao tác"]) {
      expect(within(structureBlock).getByRole("columnheader", { name: label })).toBeVisible();
    }
    expect(within(structureBlock).getByText("1–32")).toBeVisible();
    expect(within(structureBlock).getByText("33–47")).toBeVisible();
    expect(within(structureBlock).getByText("48–50")).toBeVisible();
    expect(rowCountInput("Trắc nghiệm độc lập")).toHaveValue("32");
    expect(rowCountInput("Trả lời ngắn")).toHaveValue("15");
    expect(rowSetsInput("Chùm cuối")).toHaveValue("1");
    expect(rowSetSizeInput("Chùm cuối")).toHaveValue("3");
    expect(rowCountInput("Chùm cuối")).toHaveValue("3");
    expect(rowCountInput("Chùm cuối")).toHaveAttribute("readonly");

    await type(user, rowCountInput("Trả lời ngắn"), "12");
    const header = screen.getByTestId("subject-form-header");
    expect(header).toHaveTextContent(/47\s*\/\s*50/);
    expect(header).toHaveTextContent(/Thiếu 3/);
    expect(within(structureBlock).getByText(/Thiếu 3 câu/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
  });

  it("recomputesQuotasInStoredShape & legacyQuotaShape: HSA-B (SINGLE/SHORT/SET) và dạng 35/15 đều tính lại đúng dạng khi đổi Điền đáp án", async () => {
    const user = userEvent.setup();
    const hsaB = await openHsa("sec-toan", [wirePart([wireHsaMath()])]);
    await type(user, structureInput("Điền đáp án"), "12");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(hsaB.server)).toHaveLength(1));
    expect(sectionsOf(putCalls(hsaB.server)[0]!.body)["sec-toan"]!.answerQuotas).toEqual({
      unit: "COUNT",
      items: [
        { value: 32, answerType: "SINGLE_CHOICE" },
        { value: 12, answerType: "SHORT_ANSWER" },
        { value: 3, answerType: "QUESTION_SET" },
      ],
    });
    hsaB.view.unmount();

    const legacy = await openHsa("sec-toan", [
      wirePart([
        wireHsaMath("sec-toan", {
          answerQuotas: {
            unit: "COUNT",
            items: [
              { value: 35, answerType: "SINGLE_CHOICE" },
              { value: 15, answerType: "SHORT_ANSWER" },
            ],
          },
        }),
      ]),
    ]);
    await type(user, structureInput("Điền đáp án"), "12");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(legacy.server)).toHaveLength(1));
    expect(sectionsOf(putCalls(legacy.server)[0]!.body)["sec-toan"]!.answerQuotas).toEqual({
      unit: "COUNT",
      items: [
        { value: 35, answerType: "SINGLE_CHOICE" },
        { value: 12, answerType: "SHORT_ANSWER" },
      ],
    });
  });

  it("hsaHistoryFillZeroUnit: HSA Sử giữ nhóm Điền đáp án qua 1 -> 0 -> PUT -> nạp lại -> 1 -> PUT nguyên id, điểm, tagCodes và templateGroupCode", async () => {
    const user = userEvent.setup();
    const parts = [wirePart([wireHsaSocial("sec-su", "SU", { withFill: true })])];
    const { server, view } = await openHsa("sec-su", parts);
    const fill = () => structureInput("Điền đáp án");
    await type(user, fill(), "0");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const afterZero = (sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.blocks as Wire[]).find((b) => b.id === "sec-su-fill")!;
    expect(afterZero).toMatchObject({ id: "sec-su-fill", questionCount: 0, requiredPointsPerQuestion: 1, templateGroupCode: "SU_FILL_CUSTOM", tagCodes: [] });
    view.unmount();

    openStructure("?section=sec-su");
    await screen.findByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING });
    await type(user, fill(), "1");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(2));
    const afterRestore = (sectionsOf(putCalls(server)[1]!.body)["sec-su"]!.blocks as Wire[]).find((b) => b.id === "sec-su-fill")!;
    expect(afterRestore).toMatchObject({ id: "sec-su-fill", questionCount: 1, requiredPointsPerQuestion: 1, templateGroupCode: "SU_FILL_CUSTOM", tagCodes: [] });
  });

  it("gradeAndDifficultyRows, hsaEnforcementNoPercent & hsaHistoryKeepsGrade: đủ hàng khối lớp 3/20/27, độ khó 2/21/20/7, SEPARATE/QUESTION, Bắt buộc cố định; Sử giữ hàng khối lớp; Anh là DS", async () => {
    const mathView = await openHsa("sec-toan", [wirePart([wireHsaMath()])]);
    const gradeSection = screen.getByRole("heading", { level: 4, name: "Khối lớp" }).closest("[data-form-field]") as HTMLElement;
    const diffSection = screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement;
    expect(within(gradeSection).getByLabelText("Lớp 10")).toHaveValue("3");
    expect(within(gradeSection).getByLabelText("Lớp 11")).toHaveValue("20");
    expect(within(gradeSection).getByLabelText("Lớp 12")).toHaveValue("27");
    expect(within(gradeSection).queryByLabelText("Liên lớp")).not.toBeInTheDocument();
    expect(within(gradeSection).getByText("Bắt buộc")).toBeInTheDocument();

    expect(within(diffSection).getByLabelText("Nhận biết")).toHaveValue("2");
    expect(within(diffSection).getByLabelText("Thông hiểu")).toHaveValue("21");
    expect(within(diffSection).getByLabelText("Vận dụng")).toHaveValue("20");
    expect(within(diffSection).getByLabelText("Vận dụng cao")).toHaveValue("7");
    expect(within(diffSection).getByText("Bắt buộc")).toBeInTheDocument();

    expect(screen.queryByRole("switch", { name: "Áp dụng khi sinh đề" })).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Trộn lẫn giữa các loại câu" })).not.toBeChecked();
    expect(screen.queryByRole("button", { name: /Tỷ lệ/ })).not.toBeInTheDocument();
    mathView.view.unmount();

    const suView = await openHsa("sec-su", [wirePart([wireHsaSocial("sec-su", "SU")])]);
    expect(screen.queryByRole("heading", { level: 4, name: "Khối lớp" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: "Độ khó" })).toBeInTheDocument();
    suView.view.unmount();

    boot({ kind: "HSA", parts: [wirePart([wireHsaEnglish()])] });
    openStructure("?section=sec-anh");
    expect(await screen.findByRole("heading", { name: "Các dòng lấy câu" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).not.toBeInTheDocument();
  });

  it("hsaScienceLimits: khớp 1:1 SubjectFormLimits — Hóa 3 chùm hoặc 0 điền chặn Lưu, Lưu và chuyển, Kiểm tra toàn bài và Tiếp tục; Sử 0 điền không lỗi", async () => {
    expect(subjectFormLimits("HSA", "LY")).toEqual({ setCount: { min: 1, max: 2, size: 3 }, shortAnswerMin: 1 });
    expect(subjectFormLimits("HSA", "HOA")).toEqual({ setCount: { min: 1, max: 2, size: 3 }, shortAnswerMin: 1 });
    expect(subjectFormLimits("HSA", "SINH")).toEqual({ setCount: { min: 1, max: 2, size: 3 }, shortAnswerMin: 1 });
    expect(subjectFormLimits("HSA", "SU")).toEqual({ setCount: { min: 1, max: 2, size: 3 }, shortAnswerMin: 0 });
    expect(subjectFormLimits("HSA", "DIA")).toEqual({ setCount: { min: 1, max: 2, size: 3 }, shortAnswerMin: 0 });

    const user = userEvent.setup();
    const { server } = await openHsa("sec-hoa", [wirePart([wireHsaScience("sec-hoa", "HOA"), wireHsaSocial("sec-su", "SU")])]);

    await type(user, structureInput("Số chùm"), "3");
    expect(await screen.findByText("Số chùm từ 1 đến 2")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Lưu nháp và thoát" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Quay lại" }));
    const leaveDialog = await screen.findByTestId("structure-leave-dialog");
    await user.click(within(leaveDialog).getByRole("button", { name: "Lưu và chuyển" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    expect(within(leaveDialog).getByRole("alert")).toHaveTextContent("Còn lỗi cần sửa trước khi lưu hoặc kiểm tra.");
    await user.click(within(leaveDialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByTestId("structure-leave-dialog")).not.toBeInTheDocument());

    await type(user, structureInput("Số chùm"), "2");
    await type(user, structureInput("Điền đáp án"), "0");
    expect(await screen.findByText("Cần ít nhất 1 câu điền đáp án")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Lưu nháp và thoát" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);

    await type(user, structureInput("Điền đáp án"), "1");
    await waitFor(() => expect(screen.queryByText(/Cần ít nhất 1 câu điền/)).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));

    await user.click(within(screen.getByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /Lịch sử|SU/ }));
    expect(await screen.findByTestId("hsa-strand-section")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("noExtraRequestsOnOpen: mở HSA MT gọi <= số HTTP request so với HSA MD trên cùng dữ liệu", async () => {
    const mtParts = [wirePart([wireHsaMath()])];
    const mtBoot = await openHsa("sec-toan", mtParts);
    const mtRequests = requestLog(mtBoot.server);
    mtBoot.view.unmount();

    const mdParts = [wirePart([wireHsaMath("sec-toan", {}, { tagCodes: ["MD"] })])];
    const mdBoot = boot({ kind: "HSA", parts: mdParts });
    openStructure("?section=sec-toan");
    await screen.findByRole("button", { name: /Thêm nhóm/ });
    const mdRequests = requestLog(mdBoot.server);
    expect(mtRequests.length).toBeLessThanOrEqual(mdRequests.length);
  });
});

describe("HSA Sử & Địa lí: UNI-1723 & UNI-1724 (Cấu trúc lên đầu, ẩn khối lớp, bỏ điều kiện tư liệu Địa lí)", () => {
  it("hsaHistoryGeographyOrderAndAllocated: Sử/Địa đặt Cấu trúc câu lên trước Mạch kiến thức, đếm đã phân bổ và còn tự do theo quy tắc & %", async () => {
    const user = userEvent.setup();
    const parts = [
      wirePart([
        wireHsaMath(),
        wireHsaSocial("sec-su", "SU", {
          mapidMode: "MATRIX",
          mapidRules: [
            { id: "r1", type: "COUNT", groupId: null, nodeIds: ["kn-0D1"], comparator: "AT_LEAST", count: 5 },
            { id: "r2", type: "COUNT", groupId: "sec-su-single", nodeIds: ["kn-0D2"], comparator: "AT_LEAST", count: 25, percent: 25 },
            { id: "r3", type: "PRESENT", groupId: null, nodeIds: ["kn-1D4"] },
          ],
        }),
      ]),
    ];
    const suBoot = await openHsa("sec-su", parts);
    const strand = screen.getByTestId("hsa-strand-section");
    const structure = document.querySelector<HTMLElement>('[data-form-field="STRUCTURE"]')!;
    expect(structure).toHaveAttribute("data-structure-variant", "compact");
    expect(structure.compareDocumentPosition(strand) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);

    const alloc = screen.getByTestId("hsa-mapid-allocation");
    expect(alloc).toHaveTextContent(/10 \/ 17/);
    expect(alloc).toHaveTextContent(/còn tự do: 7/);
    expect(strand).toHaveTextContent(/không ấn định vị trí câu trong đề/);

    await type(user, rowCountInput("Trắc nghiệm độc lập"), "11");
    await type(user, structureInput("Số chùm"), "2");
    expect(alloc).toHaveTextContent(/9 \/ 17/);
    expect(alloc).toHaveTextContent(/còn tự do: 8/);
    suBoot.view.unmount();

    await openHsa("sec-toan", [wirePart([wireHsaMath("sec-toan", { mapidMode: "MATRIX", mapidRules: [] })])]);
    expect(screen.queryByTestId("hsa-strand-section")).not.toBeInTheDocument();
    expect(document.querySelector('[data-form-field="STRUCTURE"]')).not.toHaveAttribute("data-structure-variant");
  });

  it("hsaHistoryGeographyHidesGrade: HSA Sử và Địa ẩn Khối lớp (chỉ giữ Độ khó) và giữ nguyên byGrade REQUIRED đã lưu khi chỉnh sửa độ khó", async () => {
    const user = userEvent.setup();
    const { server } = await openHsa("sec-su", [wirePart([wireHsaSocial("sec-su", "SU")])]);
    expect(screen.queryByRole("heading", { level: 4, name: "Khối lớp" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 3, name: "Độ khó" })).toBeInTheDocument();
    const diffSection = screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement;
    await type(user, within(diffSection).getByLabelText("Nhận biết"), "6");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect((sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.matrix as Wire).byGrade).toEqual([
      { code: "GRADE_10", value: 2 },
      { code: "GRADE_11", value: 5 },
      { code: "GRADE_12", value: 10 },
    ]);
  });

  it("hsaGeographyOmitsMaterialAndGrade: HSA Địa lí không còn Điều kiện tư liệu và Khối lớp, Cấu trúc câu đứng trước Mạch kiến thức", async () => {
    const firstDia = await openHsa("sec-dia", [wirePart([wireHsaSocial("sec-dia", "DIA")])]);
    expect(screen.queryByTestId("hsa-dia-material-section")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 4, name: "Khối lớp" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: "Độ khó" })).toBeInTheDocument();
    const strand = screen.getByTestId("hsa-strand-section");
    const structure = document.querySelector<HTMLElement>('[data-form-field="STRUCTURE"]')!;
    expect(structure.compareDocumentPosition(strand) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
    firstDia.view.unmount();

    const toanView = await openHsa("sec-toan", [wirePart([wireHsaMath()])]);
    expect(screen.queryByTestId("hsa-dia-material-section")).not.toBeInTheDocument();
    toanView.view.unmount();
    await openHsa("sec-su", [wirePart([wireHsaSocial("sec-su", "SU")])]);
    expect(screen.queryByTestId("hsa-dia-material-section")).not.toBeInTheDocument();
  });

  it("R1-5: đếm phân bổ loại bỏ quy tắc nhóm đã xóa, không đếm trùng quy tắc lồng và không báo alert vượt khi chưa rõ tổng câu", async () => {
    // 1. total === null và blocks rỗng: hiện allocationUnknown, không báo alert allocationOver.
    const ref = adaptRef(await getContentReferenceFixture());
    const rawNoop = { get: () => null, hasPrefix: () => false, set: () => {}, clear: () => {} };
    const emptyUnknownView = renderWithProviders(
      <MatrixFormHsa
        section={{
          id: "sec-su-empty",
          subjectCode: "SU",
          questionCount: null,
          knowledgeScope: null,
          answerQuotas: null,
          matrix: null,
          choiceGroup: null,
          blocks: [],
          mapidMode: "MATRIX",
          mapidRules: [{ id: "r-unk", type: "COUNT", groupId: null, nodeIds: ["kn-0D1"], comparator: "AT_LEAST", count: 3 }],
        }}
        entry={subjectFormFor("HSA", "SU")!}
        kindCode="HSA"
        subjectName="Lịch sử"
        total={null}
        axes={{ grades: [], difficulties: [] }}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={ref}
        onChange={() => {}}
        templateLocked={false}
        raw={rawNoop}
      />,
    );
    expect(screen.getByTestId("hsa-mapid-allocation")).toHaveTextContent("Đã phân bổ MAPID: 3 câu");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    emptyUnknownView.unmount();

    // 2. Quy tắc trỏ nhóm đã xóa bị loại khỏi số phân bổ; quy tắc toàn môn + nhóm cùng nút không cộng dồn (UNI-1707 §4).
    const overlapAndDeleted = await openHsa("sec-su", [
      wirePart([
        wireHsaSocial("sec-su", "SU", {
          mapidMode: "MATRIX",
          mapidRules: [
            { id: "r-sub", type: "COUNT", groupId: null, nodeIds: ["kn-0D1"], comparator: "EXACT", count: 10 },
            { id: "r-grp", type: "COUNT", groupId: "sec-su-single", nodeIds: ["kn-0D1"], comparator: "EXACT", count: 8 },
            { id: "r-del", type: "COUNT", groupId: "deleted-block", nodeIds: ["kn-0D2"], comparator: "EXACT", count: 20 },
          ],
        }),
      ]),
    ]);
    const alloc = screen.getByTestId("hsa-mapid-allocation");
    expect(alloc).toHaveTextContent(/10 \/ 17/);
    expect(alloc).toHaveTextContent(/còn tự do: 7/);
    expect(within(alloc).queryByRole("alert")).not.toBeInTheDocument();
    overlapAndDeleted.view.unmount();

    // 3. Hai nhóm riêng lẻ vượt tổng quy tắc toàn môn (2 + 2 > 3) vẫn tính 4; vượt tổng 17 bật role="alert".
    await openHsa("sec-su", [
      wirePart([
        wireHsaSocial("sec-su", "SU", {
          mapidMode: "MATRIX",
          mapidRules: [
            { id: "r-sub-3", type: "COUNT", groupId: null, nodeIds: ["kn-0D1"], comparator: "EXACT", count: 3 },
            { id: "r-g1", type: "COUNT", groupId: "sec-su-single", nodeIds: ["kn-0D1"], comparator: "AT_LEAST", count: 2 },
            { id: "r-g2", type: "COUNT", groupId: "sec-su-set", nodeIds: ["kn-0D1"], comparator: "AT_LEAST", count: 2 },
            { id: "r-extra", type: "COUNT", groupId: null, nodeIds: ["kn-0D2"], comparator: "AT_LEAST", count: 16 },
          ],
        }),
      ]),
    ]);
    const overAlloc = screen.getByTestId("hsa-mapid-allocation");
    expect(overAlloc).toHaveTextContent(/20 \/ 17/);
    expect(within(overAlloc).getByRole("alert")).toHaveTextContent("Đã vượt tổng số câu của môn 3 câu");
  });

  it("R1-8: fallback mapidMode === null của HSA vẽ KnowledgeScopeField, TopicQuotaTable, xóa everyChildInScope khi bỏ chọn và hiện topicError 422", async () => {
    const user = userEvent.setup();
    const legacySection = wireHsaMath("sec-toan", {
      knowledgeScope: { nodeIds: ["kn-0D1"], everyChildInScope: true },
      topicQuotas: [
        { id: "q1", name: "Hàm số", nodeIds: ["kn-0D1"], count: 3, enforcement: "REQUIRED", gradeCodes: ["GRADE_11"] },
      ],
    });
    const parts = [wirePart([legacySection])];
    const server = new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "HSA", revision: 7 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", `${EXAM_PATH}/structure`, { status: 200, body: { examId: EXAM_ID, revision: 7, parts, examQuotas: [] } })
      .on("PUT", `${EXAM_PATH}/structure`, {
        status: 422,
        body: {
          code: "EXAM_STRUCTURE_INVALID",
          message: "Quá giới hạn chỉ tiêu",
          fieldErrors: [{ field: "topicQuotas", code: "MAX_TOPIC_QUOTAS", message: "Tối đa 8" }],
        },
      })
      .install();

    openStructure("?section=sec-toan");
    expect(await screen.findByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Theo chủ đề" })).toBeChecked();
    expect(screen.getByRole("heading", { level: 4, name: "Chỉ tiêu chủ đề" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Tên chủ đề của chỉ tiêu 1" })).toHaveValue("Hàm số");
    const everyChild = screen.getByRole("checkbox", { name: /Mọi câu con thuộc chủ đề/ });
    expect(everyChild).toBeChecked();
    await user.click(everyChild);
    expect(everyChild).not.toBeChecked();

    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sentScope = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.knowledgeScope as Wire;
    expect(sentScope).not.toHaveProperty("everyChildInScope");
    expect(await screen.findByRole("alert")).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề/);
  });

  it("bảng tiếng Anh không lọt chữ Việt ở khối HSA MT và dùng số ít/số nhiều ICU đúng chuẩn (EN locale parity & R1-11)", async () => {
    const user = userEvent.setup();
    boot({
      kind: "HSA",
      parts: [
        wirePart([
          wireHsaSocial("sec-dia", "DIA", {
            mapidMode: "MATRIX",
            mapidRules: [
              { id: "r1", type: "COUNT", groupId: null, nodeIds: ["kn-0D1"], comparator: "EXACT", count: 16 },
            ],
          }),
          wireHsaScience("sec-hoa", "HOA"),
        ]),
      ],
    });
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-dia`);
    const enView = renderWithProviders(<ExamStructureView examId={EXAM_ID} />, { locale: "en", messages: en });
    const strand = await screen.findByTestId("hsa-strand-section");
    const alloc = screen.getByTestId("hsa-mapid-allocation");
    expect(screen.queryByTestId("hsa-dia-material-section")).not.toBeInTheDocument();
    const vnRe = /[àáảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]/i;
    expect(alloc.textContent).not.toMatch(vnRe);
    expect(strand.querySelector("h3")?.textContent).not.toMatch(vnRe);
    expect(alloc).toHaveTextContent("MAPID allocated: 16 / 17 questions · remaining free: 1 question");
    enView.unmount();

    boot({
      kind: "HSA",
      parts: [
        wirePart([
          wireHsaSocial("sec-dia", "DIA", {
            mapidMode: "MATRIX",
            mapidRules: [{ id: "r-over", type: "COUNT", groupId: null, nodeIds: ["kn-0D1"], comparator: "EXACT", count: 18 }],
          }),
          wireHsaScience("sec-hoa", "HOA"),
        ]),
      ],
    });
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-dia`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />, { locale: "en", messages: en });
    expect(await screen.findByTestId("hsa-mapid-allocation")).toHaveTextContent("Exceeds the subject total by 1 question");

    await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: /Chemistry|HOA/ }));
    const structureEn = (await screen.findByRole("heading", { level: 3, name: "Question component structure" })).closest("[data-form-field]") as HTMLElement;
    const strippedStructure = structureEn.cloneNode(true) as HTMLElement;
    for (const contentNode of strippedStructure.querySelectorAll("[data-exam-content]")) contentNode.remove();
    expect(strippedStructure.textContent).not.toMatch(vnRe);
    for (const el of structureEn.querySelectorAll("[aria-label], [title]")) {
      if (el.closest("[data-exam-content]")) continue;
      expect(el.getAttribute("aria-label") ?? "").not.toMatch(vnRe);
      expect(el.getAttribute("title") ?? "").not.toMatch(vnRe);
    }
    await type(user, screen.getByRole("textbox", { name: "Questions of Short answer" }), "0");
    expect(await screen.findByText("At least 1 fill-in answer needed")).toBeInTheDocument();
  });
});
