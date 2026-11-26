import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import standardTemplates from "./fixtures/exam-templates-dot3.json";
import { boot, checkCalls, openStructure, putCalls, sectionsOf, wireBlock, wirePart, wireSection, type Wire } from "./subject-form-harness";
import { matrixOf } from "./subject-matrix-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

/**
 * CR-2a′ (FTM R-4, R-8, R-9, R-6; CR-2 AC-3..AC-5) trên `StructureEditor` thật: V-ACT Toán và Tiếng Việt mở MT, nhập % ở hai cột Số câu /
 * Tỷ lệ cho Cấu trúc câu, khối lớp, độ khó; lưu theo loại; chùm cỡ linh hoạt giữ `null`.
 */
const type = async (user: UserEvent, input: HTMLElement, text: string) => {
  await user.clear(input);
  if (text !== "") await user.type(input, text);
};

const GRADES = [
  { code: "GRADE_10", value: 12 },
  { code: "GRADE_11", value: 9 },
  { code: "GRADE_12", value: 8 },
  { code: "GRADE_MIXED", value: 1 },
];
const DIFFICULTIES = [
  { code: "RECOGNITION", value: 4 },
  { code: "COMPREHENSION", value: 10 },
  { code: "APPLICATION", value: 16 },
];
const reference = { gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" };

/** V-ACT Toán đúng như mẫu: 12 câu đơn + 18 câu con (chùm cỡ linh hoạt), lớp 12/9/8/1, độ khó 4/10/16, chỉ đối chiếu. */
function wireVactMath(over: Wire = {}, matrix: Wire | null = matrixOf({ byGrade: GRADES, byDifficulty: DIFFICULTIES, ...reference })): Wire {
  return wireSection(
    "sec-toan",
    "TOAN",
    [
      wireBlock("toan-single", { name: "Toan don", questionCount: 12 }),
      wireBlock("toan-set", { name: "Toan chum", questionType: "QUESTION_SET", questionCount: 18, childAnswerType: "SINGLE_CHOICE" }),
    ],
    { questionCount: 30, matrix, knowledgeScope: { nodeIds: [], competencyCodes: ["VACT-TOAN"] }, ...over },
  );
}

const openToan = async (section: Wire = wireVactMath()) => {
  const booted = boot({ kind: "VACT", parts: [wirePart([section])] });
  openStructure("?section=sec-toan");
  await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" });
  return booted;
};

const dimension = (title: "Khối lớp" | "Độ khó") => within(screen.getByRole("heading", { level: 4, name: title }).closest("[data-form-field]") as HTMLElement);

describe("V-ACT Toán MT: hai cột Số câu / Tỷ lệ (R-9, CR-2 AC-5)", () => {
  it("vactMathMixedGrade: V-ACT Toán có cột Liên lớp; mức áp dụng Chỉ đối chiếu kèm nút Áp dụng khi sinh đề", async () => {
    await openToan();
    const grade = dimension("Khối lớp");
    expect(grade.getByLabelText("Liên lớp")).toHaveValue("1");
    expect(grade.getAllByRole("textbox").length).toBeGreaterThanOrEqual(4);
    expect(grade.getByText("Chỉ đối chiếu")).toBeInTheDocument();
    expect(grade.getByRole("switch", { name: "Áp dụng khi sinh đề" })).not.toBeChecked();
  });

  it("vactPercentStorage: độ khó 25/50/25% của 30 hiện 8/15/7 và lưu unit PERCENT 25/50/25; ghi chỉ trên hai cột", async () => {
    const user = userEvent.setup();
    const { server } = await openToan(wireVactMath({}, matrixOf({ byDifficulty: [], ...reference })));
    const difficulty = dimension("Độ khó");
    await type(user, difficulty.getByRole("textbox", { name: "Tỷ lệ của Nhận biết" }), "25");
    await type(user, difficulty.getByRole("textbox", { name: "Tỷ lệ của Thông hiểu" }), "50");
    await type(user, difficulty.getByRole("textbox", { name: "Tỷ lệ của Vận dụng" }), "25");
    expect(difficulty.getByLabelText("Nhận biết")).toHaveValue("8");
    expect(difficulty.getByLabelText("Thông hiểu")).toHaveValue("15");
    expect(difficulty.getByLabelText("Vận dụng")).toHaveValue("7");
    expect(await screen.findByText("Tổng tỷ lệ 100%")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const matrix = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix as Wire;
    expect(matrix.unit).toBe("PERCENT");
    expect(matrix.byDifficulty).toEqual([
      { code: "RECOGNITION", value: 25 },
      { code: "COMPREHENSION", value: 50 },
      { code: "APPLICATION", value: 25 },
    ]);
    expect(matrix.difficultyEnforcement).toBe("REFERENCE");
    expect(matrix.byGrade).toEqual([]);
  });

  it("vactPercentStorage: sau tải lại vẫn hiện 25/50/25% (không đổi sang số) và số câu 8/15/7", async () => {
    const percent = matrixOf({ unit: "PERCENT", byGrade: [], byDifficulty: [{ code: "RECOGNITION", value: 25 }, { code: "COMPREHENSION", value: 50 }, { code: "APPLICATION", value: 25 }], ...reference });
    await openToan(wireVactMath({}, percent));
    const difficulty = dimension("Độ khó");
    expect(difficulty.getByRole("textbox", { name: "Tỷ lệ của Nhận biết" })).toHaveValue("25");
    expect(difficulty.getByLabelText("Nhận biết")).toHaveValue("8");
    expect(difficulty.getByLabelText("Thông hiểu")).toHaveValue("15");
    expect(difficulty.getByLabelText("Vận dụng")).toHaveValue("7");
  });

  it("vactPercentStorage: mở sau khi tổng đã thành 40 (ngoài phiên) tính lại từ % đã lưu ra 10/20/10, không hỏi xác nhận", async () => {
    const percent = matrixOf({ unit: "PERCENT", byGrade: [], byDifficulty: [{ code: "RECOGNITION", value: 25 }, { code: "COMPREHENSION", value: 50 }, { code: "APPLICATION", value: 25 }], ...reference });
    await openToan(wireVactMath({ questionCount: 40 }, percent));
    const difficulty = dimension("Độ khó");
    expect(difficulty.getByLabelText("Nhận biết")).toHaveValue("10");
    expect(difficulty.getByLabelText("Thông hiểu")).toHaveValue("20");
    expect(difficulty.getByLabelText("Vận dụng")).toHaveValue("10");
    expect(screen.queryByText(/Tổng đổi thành/)).not.toBeInTheDocument();
  });

  it("vactPercentStorage: ma trận độ khó tổng 90% hiện \"cần 100%\"; Lưu không gửi PUT (guard MATRIX_PERCENT giữ nguyên)", async () => {
    const user = userEvent.setup();
    const { server } = await openToan(wireVactMath({}, matrixOf({ byDifficulty: [], ...reference })));
    const difficulty = dimension("Độ khó");
    for (const name of ["Nhận biết", "Thông hiểu", "Vận dụng"]) await type(user, difficulty.getByRole("textbox", { name: `Tỷ lệ của ${name}` }), "30");
    expect(await screen.findByText("Tổng tỷ lệ 90%, cần 100%")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
  });

  it("vactPercentStorage: Cấu trúc câu 40%/60% của 30 ra 12/18 (tải lại hiện 12/18, % gợi ý 40/60 tính từ số); 40%/50% hiện \"cần 100%\", không quy ra số, chặn lưu", async () => {
    const user = userEvent.setup();
    const { server } = await openToan(wireVactMath({}, null));
    // Tải lại hiện số đã lưu; ô Tỷ lệ chưa gõ gợi ý % tính từ số.
    expect(screen.getByRole("textbox", { name: "Tỷ lệ của Toan don" })).toHaveAttribute("placeholder", "40");
    expect(screen.getByRole("textbox", { name: "Tỷ lệ của Toan chum" })).toHaveAttribute("placeholder", "60");
    await type(user, screen.getByRole("textbox", { name: "Tỷ lệ của Toan don" }), "40");
    expect(await screen.findByText("Tổng tỷ lệ 40%, cần 100%")).toBeInTheDocument();
    await type(user, screen.getByRole("textbox", { name: "Tỷ lệ của Toan chum" }), "50");
    expect(await screen.findByText("Tổng tỷ lệ 90%, cần 100%")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Số câu của Toan don" })).toHaveValue("12");
    // Nhập thô chưa đủ 100% chặn lưu dù bản nháp sạch: Kiểm tra cấu trúc không gửi request.
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    await type(user, screen.getByRole("textbox", { name: "Tỷ lệ của Toan chum" }), "60");
    await waitFor(() => expect(screen.queryByText(/cần 100%/)).not.toBeInTheDocument());
    expect(screen.getByRole("textbox", { name: "Số câu của Toan don" })).toHaveValue("12");
    expect(screen.getByRole("textbox", { name: "Số câu của Toan chum" })).toHaveValue("18");
    // Số không đổi nên bản nháp sạch và hết nhập thô: không còn chặn.
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
  });

  it("vactPercentStorage: tỷ lệ 30%/70% quy ra 9/21 theo phần dư lớn nhất và lưu số câu, `setCount`, `setSize` vẫn null", async () => {
    const user = userEvent.setup();
    const { server } = await openToan(wireVactMath({}, null));
    await type(user, screen.getByRole("textbox", { name: "Tỷ lệ của Toan don" }), "30");
    await type(user, screen.getByRole("textbox", { name: "Tỷ lệ của Toan chum" }), "70");
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Số câu của Toan don" })).toHaveValue("9"));
    expect(screen.getByRole("textbox", { name: "Số câu của Toan chum" })).toHaveValue("21");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [single, set] = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.blocks as Wire[];
    expect(single!.questionCount).toBe(9);
    expect(set).toMatchObject({ questionCount: 21 });
    expect(set!.setCount ?? null).toBeNull();
    expect(set!.setSize ?? null).toBeNull();
  });

  it("vactFlexibleKeepsNulls: sửa tổng câu con 18 → 15 giữ `setCount`, `setSize` là null trong thân PUT", async () => {
    const user = userEvent.setup();
    const { server } = await openToan();
    await type(user, screen.getByRole("textbox", { name: "Số câu của Toan chum" }), "15");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const set = (sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.blocks as Wire[])[1]!;
    expect(set.questionCount).toBe(15);
    expect(set.setCount ?? null).toBeNull();
    expect(set.setSize ?? null).toBeNull();
  });

  it("chip và nút Áp dụng khi sinh đề: bật ghi REQUIRED của riêng chiều đó, chiều kia vẫn REFERENCE", async () => {
    const user = userEvent.setup();
    const { server } = await openToan();
    await user.click(dimension("Độ khó").getByRole("switch", { name: "Áp dụng khi sinh đề" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const matrix = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix as Wire;
    expect(matrix).toMatchObject({ difficultyEnforcement: "REQUIRED", gradeEnforcement: "REFERENCE" });
  });
});

const rawTemplate = (code: string) => standardTemplates.templates.find((item) => item.code === code)!;

describe("V-ACT Tiếng Việt MT (R-4, R-6, CR-2 AC-4)", () => {
  const openVietnamese = async (code: string, examOverrides: Wire = {}, scope = "SUBJECT") => {
    const template = rawTemplate(code);
    const wireParts = template.structure.parts as unknown as Wire[];
    const sectionId = (wireParts[0]!.sections as Wire[])[0]!.id as string;
    const booted = boot({ kind: "VACT", parts: wireParts, templates: [template], examOverrides: { templateCode: code, scope, ...examOverrides } });
    openStructure(`?section=${sectionId}`);
    await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" });
    return { ...booted, sectionId };
  };

  it("vactVietnameseScopes: không có hàng khối lớp khi chưa có số; \"Thêm điều kiện\" thêm hàng; bài đã khai thì hàng hiện sẵn", async () => {
    const user = userEvent.setup();
    await openVietnamese("VACT_SUBJECT_VIETNAMESE");
    expect(screen.queryByRole("heading", { level: 4, name: "Khối lớp" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: "Độ khó" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thêm điều kiện" }));
    await user.click(await screen.findByRole("menuitem", { name: "Khối lớp" }));
    expect(await screen.findByRole("heading", { level: 4, name: "Khối lớp" })).toBeInTheDocument();
  });

  it("vactVietnameseScopes: bài cũ đã khai số lớp hiện hàng khối lớp sẵn với số đó", async () => {
    const section = wireSection("sec-vn", "VIETNAMESE", [wireBlock("vn-single", { name: "Tieng Viet", questionCount: 30 })], {
      questionCount: 30,
      matrix: matrixOf({ byGrade: [{ code: "GRADE_12", value: 30 }], byDifficulty: [], ...reference }),
    });
    boot({ kind: "VACT", parts: [wirePart([section])] });
    openStructure("?section=sec-vn");
    await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" });
    expect(dimension("Khối lớp").getByLabelText("Lớp 12")).toHaveValue("30");
  });

  it("vactVietnameseScopes: bài FULL khóa tổng 30 chỉ đọc kèm lý do; bài tiểu mục sửa được số", async () => {
    await openVietnamese("VACT_FULL", {}, "FULL");
    const locked = screen.getByRole("textbox", { name: /^Số câu của / });
    expect(locked).toHaveAttribute("readonly");
    // Lý do nằm ở đầu môn (số câu của mục) và ngay cạnh ô số câu dòng duy nhất.
    expect(screen.getAllByText("Số câu của mục theo mẫu, không đổi").length).toBeGreaterThanOrEqual(2);
  });

  it("vactVietnameseScopes bằng bàn phím: Enter mở Thêm điều kiện, Enter chọn Khối lớp; hàng Khối lớp hiện và focus vào ô lớp đầu tiên (R1-3, R1-6)", async () => {
    const user = userEvent.setup();
    await openVietnamese("VACT_SUBJECT_VIETNAMESE");
    const trigger = screen.getByRole("button", { name: "Thêm điều kiện" });
    trigger.focus();
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("menuitem", { name: "Khối lớp" })).toHaveFocus();
    await user.keyboard("{Enter}");
    const grade = await screen.findByRole("heading", { level: 4, name: "Khối lớp" });
    expect(grade).toBeInTheDocument();
    const first = within(grade.closest("[data-form-field]") as HTMLElement).getAllByRole("textbox")[0]!;
    await waitFor(() => expect(first).toHaveFocus());
    expect(screen.queryByRole("button", { name: "Thêm điều kiện" })).not.toBeInTheDocument();
  });

  it("ô lớp thêm từ Thêm điều kiện gõ sai: Lưu không gửi PUT, focus vào ô lớp đó và hàng vẫn hiện (R1-5)", async () => {
    const user = userEvent.setup();
    const { server } = await openVietnamese("VACT_SUBJECT_VIETNAMESE");
    await user.click(screen.getByRole("button", { name: "Thêm điều kiện" }));
    await user.click(await screen.findByRole("menuitem", { name: "Khối lớp" }));
    const cell = await within((await screen.findByRole("heading", { level: 4, name: "Khối lớp" })).closest("[data-form-field]") as HTMLElement).findByLabelText("Lớp 12");
    await type(user, cell, "abc");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(dimension("Khối lớp").getByLabelText("Lớp 12")).toHaveFocus());
    expect(dimension("Khối lớp").getByRole("alert")).toBeInTheDocument();
  });

  it("vactVietnameseScopes: tiểu mục (không khóa tổng) sửa được số câu; 8 dòng mạch chờ mapping hiện sẵn và lưu được nguyên vẹn", async () => {
    const user = userEvent.setup();
    const { server, sectionId } = await openVietnamese("VACT_SUBJECT_VIETNAMESE");
    const singleCount = screen.getByRole("textbox", { name: /^Số câu của / });
    expect(singleCount).not.toHaveAttribute("readonly");
    expect((await screen.findAllByText(/Chờ mapping/)).length).toBeGreaterThanOrEqual(1);
    await type(user, singleCount, "12");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body)[sectionId]!;
    const seeded = ((rawTemplate("VACT_SUBJECT_VIETNAMESE").structure.parts[0] as unknown as { sections: Wire[] }).sections[0]!.topicQuotas as Wire[]);
    expect((sent.topicQuotas as Wire[]).length).toBe(seeded.length);
    expect((sent.topicQuotas as Wire[]).every((quota) => (quota.nodeIds as string[]).length === 0)).toBe(true);
    expect((sent.blocks as Wire[])[0]!.questionCount).toBe(12);
  });
});
