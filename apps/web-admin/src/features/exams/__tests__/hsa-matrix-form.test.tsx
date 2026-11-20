import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID } from "./fake-exams-server";
import {
  boot,
  openStructure,
  putCalls,
  requestLog,
  sectionsOf,
  wireBlock,
  wirePart,
  wireSection,
  type Wire,
} from "./subject-form-harness";
import { patchSubjectFormEntry } from "../subject-forms/registry";
import { subjectFormLimits } from "../subject-forms/limits";
import { structureInput } from "./subject-matrix-harness";

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

function wireHsaMath(id = "sec-toan", over: Wire = {}): Wire {
  return wireSection(
    id,
    "TOAN",
    [
      wireBlock(`${id}-single`, { name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: 32, templateGroupCode: "HSA_MATH_SINGLE" }),
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
  await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" });
  return { ...booted, view };
}

describe("HSA MT: Cấu trúc câu, chỉ tiêu và giới hạn (CR-2H AC-1..AC-5)", () => {
  it("hsaMathDefaultsAndGap: Toán nạp 35 / 15 / 1×3 / 32; đổi Điền 15 -> 12 ra 47/50, thiếu 3 và vẫn lưu nháp được", async () => {
    const user = userEvent.setup();
    const { server } = await openHsa("sec-toan", [wirePart([wireHsaMath()])]);
    expect(structureInput("Trắc nghiệm")).toHaveValue("35");
    expect(structureInput("Điền đáp án")).toHaveValue("15");
    expect(structureInput("Số chùm")).toHaveValue("1");
    expect(screen.getByRole("textbox", { name: "Trắc nghiệm đơn" })).toHaveValue("32");

    await type(user, structureInput("Điền đáp án"), "12");
    const header = screen.getByTestId("subject-form-header");
    expect(header).toHaveTextContent(/47\s*\/\s*50/);
    expect(header).toHaveTextContent(/Thiếu 3/);

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
  });

  it("recomputesQuotasInStoredShape & legacyQuotaShape: HSA-B (SINGLE/SHORT/SET) và dạng 35/15 đều tính lại đúng dạng khi đổi Điền đáp án", async () => {
    const user = userEvent.setup();
    const hsaB = await openHsa("sec-toan", [wirePart([wireHsaMath()])]);
    await type(user, structureInput("Điền đáp án"), "12");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const afterZero = (sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.blocks as Wire[]).find((b) => b.id === "sec-su-fill")!;
    expect(afterZero).toMatchObject({ id: "sec-su-fill", questionCount: 0, requiredPointsPerQuestion: 1, templateGroupCode: "SU_FILL_CUSTOM", tagCodes: [] });
    view.unmount();

    openStructure("?section=sec-su");
    await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" });
    await type(user, fill(), "1");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
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

    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Tỷ lệ/ })).not.toBeInTheDocument();
    mathView.view.unmount();

    const suView = await openHsa("sec-su", [wirePart([wireHsaSocial("sec-su", "SU")])]);
    expect(screen.getByRole("heading", { level: 4, name: "Khối lớp" })).toBeInTheDocument();
    suView.view.unmount();

    boot({ kind: "HSA", parts: [wirePart([wireHsaEnglish()])] });
    openStructure("?section=sec-anh");
    expect(await screen.findByRole("heading", { name: "Các dòng lấy câu" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3, name: "Cấu trúc câu" })).not.toBeInTheDocument();
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
    for (const label of ["Lưu", "Lưu và chuyển", "Kiểm tra toàn bài", "Tiếp tục"]) {
      const btn = screen.queryByRole("button", { name: label });
      if (btn) {
        await user.click(btn);
        expect(putCalls(server)).toHaveLength(0);
      }
    }

    await type(user, structureInput("Số chùm"), "2");
    await type(user, structureInput("Điền đáp án"), "0");
    expect(await screen.findByText("Cần ít nhất 1 câu điền đáp án")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);

    await type(user, structureInput("Điền đáp án"), "1");
    await waitFor(() => expect(screen.queryByText(/Cần ít nhất 1 câu điền/)).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));

    await user.click(within(screen.getByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /Lịch sử|SU/ }));
    expect(await screen.findByTestId("hsa-strand-section")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("noExtraRequestsOnOpen: mở HSA MT gọi <= số HTTP request so với HSA MD trên cùng dữ liệu", async () => {
    const parts = [wirePart([wireHsaMath()])];
    const mtBoot = await openHsa("sec-toan", parts);
    const mtRequests = requestLog(mtBoot.server);
    mtBoot.view.unmount();

    const undo = patchSubjectFormEntry("HSA", "TOAN", { enabled: false });
    try {
      const mdBoot = boot({ kind: "HSA", parts });
      openStructure("?section=sec-toan");
      await screen.findByRole("button", { name: /Thêm nhóm/ });
      const mdRequests = requestLog(mdBoot.server);
      expect(mtRequests.length).toBeLessThanOrEqual(mdRequests.length);
    } finally {
      undo();
    }
  });
});

describe("HSA Sử & Địa lí: AD-98 (thứ tự, gọn, phân bổ, khối lớp tùy chọn) và AD-99 (tư liệu động)", () => {
  it("hsaHistoryGeographyOrderAndAllocated: Sử/Địa đặt Mạch kiến thức trước Cấu trúc câu gọn, đếm đã phân bổ và còn tự do theo quy tắc & %; Toán/Lý/Hóa/Sinh giữ thứ tự chuẩn", async () => {
    const user = userEvent.setup();
    const parts = [
      wirePart([
        wireHsaMath(),
        wireHsaSocial("sec-su", "SU", {
          mapidMode: "MATRIX",
          mapidRules: [
            { id: "r1", type: "COUNT", groupId: null, nodeIds: ["kn-0D1"], comparator: "AT_LEAST", count: 5 },
            { id: "r2", type: "COUNT", groupId: "sec-su-single", nodeIds: ["kn-0D1"], comparator: "AT_LEAST", count: 25, percent: 25 },
            { id: "r3", type: "PRESENT", groupId: null, nodeIds: ["kn-0D1"] },
          ],
        }),
      ]),
    ];
    const suBoot = await openHsa("sec-su", parts);
    const strand = screen.getByTestId("hsa-strand-section");
    const structure = document.querySelector<HTMLElement>('[data-form-field="STRUCTURE"]')!;
    expect(structure).toHaveAttribute("data-structure-variant", "compact");
    expect(strand.compareDocumentPosition(structure) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);

    const alloc = screen.getByTestId("hsa-mapid-allocation");
    expect(alloc).toHaveTextContent(/10 \/ 17/);
    expect(alloc).toHaveTextContent(/còn tự do: 7/);
    expect(strand).toHaveTextContent(/không ấn định vị trí câu trong đề/);

    // Đổi Số chùm 1 -> 2 (trắc nghiệm đơn 14 -> 11) thì quy tắc 25% trên nhóm đơn giảm còn 3 -> phân bổ 5 + 3 + 1 = 9, còn 8.
    await type(user, structureInput("Số chùm"), "2");
    expect(alloc).toHaveTextContent(/9 \/ 17/);
    expect(alloc).toHaveTextContent(/còn tự do: 8/);
    suBoot.view.unmount();

    await openHsa("sec-toan", [wirePart([wireHsaMath("sec-toan", { mapidMode: "MATRIX", mapidRules: [] })])]);
    expect(screen.queryByTestId("hsa-strand-section")).not.toBeInTheDocument();
    expect(document.querySelector('[data-form-field="STRUCTURE"]')).not.toHaveAttribute("data-structure-variant");
  });

  it("hsaHistoryGeographyOptionalGrade: xóa 3 ô khối lớp trên HSA Sử/Địa hiện Chưa áp dụng và lưu byGrade rỗng không bị chặn", async () => {
    const user = userEvent.setup();
    const { server } = await openHsa("sec-su", [wirePart([wireHsaSocial("sec-su", "SU")])]);
    const gradeSection = screen.getByRole("heading", { level: 4, name: "Khối lớp" }).closest("[data-form-field]") as HTMLElement;
    expect(within(gradeSection).getByText("Bắt buộc")).toBeInTheDocument();

    for (const label of ["Lớp 10", "Lớp 11", "Lớp 12"]) {
      await user.clear(within(gradeSection).getByLabelText(label));
    }
    expect(within(gradeSection).getByText("Chưa áp dụng")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect((sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.matrix as Wire).byGrade).toEqual([]);
  });

  it("hsaGeographyMaterialTagsDynamic: HSA Địa lí đọc tag từ danh mục động, lưu vào set.tagCodes, nạp lại vẫn còn; Toán/Sử/Hóa không có mục tư liệu", async () => {
    const user = userEvent.setup();
    const firstDia = await openHsa("sec-dia", [wirePart([wireHsaSocial("sec-dia", "DIA")])]);
    const matSection = screen.getByTestId("hsa-dia-material-section");
    expect(within(matSection).getByText("Chưa áp dụng")).toBeInTheDocument();
    await user.click(screen.getByTestId("hsa-dia-material-add"));

    const picker = screen.getByTestId("hsa-dia-material-picker");
    await user.click(within(picker).getByRole("checkbox", { name: "Bản đồ" }));
    await user.click(within(picker).getByRole("checkbox", { name: "Át-lát" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(firstDia.server)).toHaveLength(1));
    const blocks = sectionsOf(putCalls(firstDia.server)[0]!.body)["sec-dia"]!.blocks as Wire[];
    expect(blocks[1]!.tagCodes).toEqual(["DIA_BAN_DO", "DIA_ATLAT"]);
    expect(blocks[0]!.tagCodes).toEqual([]);
    firstDia.view.unmount();

    // Nạp lại với server đã lưu -> mở sẵn picker với 2 nhãn đã chọn.
    const reloadedDia = await openHsa("sec-dia", [
      wirePart([
        {
          ...wireHsaSocial("sec-dia", "DIA"),
          blocks: blocks,
        },
      ]),
    ]);
    expect(screen.getByTestId("hsa-dia-material-picker")).toBeInTheDocument();
    reloadedDia.view.unmount();

    // Toán, Sử, Hóa không có mục điều kiện tư liệu Địa lí.
    const toanView = await openHsa("sec-toan", [wirePart([wireHsaMath()])]);
    expect(screen.queryByTestId("hsa-dia-material-section")).not.toBeInTheDocument();
    toanView.view.unmount();
    await openHsa("sec-su", [wirePart([wireHsaSocial("sec-su", "SU")])]);
    expect(screen.queryByTestId("hsa-dia-material-section")).not.toBeInTheDocument();
  });

  it("bảng tiếng Anh không lọt chữ Việt ở khối HSA MT (EN locale parity)", async () => {
    boot({ kind: "HSA", parts: [wirePart([wireHsaSocial("sec-dia", "DIA", { mapidMode: "MATRIX", mapidRules: [] })])] });
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-dia`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />, { locale: "en", messages: en });
    const strand = await screen.findByTestId("hsa-strand-section");
    const alloc = screen.getByTestId("hsa-mapid-allocation");
    const mat = screen.getByTestId("hsa-dia-material-section");
    const vnRe = /[àáảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]/i;
    expect(alloc.textContent).not.toMatch(vnRe);
    expect(mat.textContent).not.toMatch(vnRe);
    expect(strand.querySelector("h3")?.textContent).not.toMatch(vnRe);
  });
});
