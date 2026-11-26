import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { registerMatrixKindSlot } from "../subject-forms/matrix-form";
import { EXAM_ID } from "./fake-exams-server";
import { boot, checkCalls, openStructure, putCalls, requestLog, sectionsOf, wireBlock, wirePart, wireSection, type Wire } from "./subject-form-harness";
import { MATRIX_STRUCTURE_HEADING, matrixOf, rowCountInput, rowSetsInput, rowSetSizeInput, structureInput, wireBcaHistory, wireQdaScience } from "./subject-matrix-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

/**
 * CR-2 & UNI-2776 (MatrixForm v3) trên `StructureEditor` thật với cờ MT của QDA, BCA bật như ở sản phẩm.
 * Phần V-ACT (hai cột %, Tiếng Việt, Toán) ở `subject-matrix-vact.test.tsx`.
 */
const type = async (user: UserEvent, input: HTMLElement, text: string) => {
  await user.clear(input);
  if (text !== "") await user.type(input, text);
};
const openSection = async (section: string, parts: Wire[], kind = "QDA") => {
  const booted = boot({ kind, parts });
  openStructure(`?section=${section}`);
  await screen.findByRole("heading", { level: 2, name: /Cấu trúc câu|Môn/ });
  return booted;
};

const lyParts = (over: Parameters<typeof wireQdaScience>[2] = {}) => [wirePart([wireQdaScience("sec-ly", "LY", over), wireQdaScience("sec-hoa", "HOA")])];

afterEach(() => routerMock.push.mockReset());

describe("MT của QDA, BCA: Cấu trúc thành phần câu hỏi v3 (R-3, UNI-2776)", () => {
  it("mở môn Khoa học thành MT v3: bảng cộng thẳng có Vị trí câu, banner đảo câu mặc định, Khớp 25 câu, không có trường của MD (noRemovedFields)", async () => {
    await openSection("sec-ly", lyParts());
    const structure = (await screen.findByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).closest("[data-form-field]") as HTMLElement;
    const banner = within(structure).getByTestId("matrix-shuffle-banner");
    expect(banner).toHaveAttribute("data-mixed", "false");
    expect(banner).toHaveTextContent(/Quy tắc đảo vị trí mặc định:/);
    expect(within(structure).getByText("1–21")).toBeInTheDocument();
    expect(within(structure).getByText("22–24")).toBeInTheDocument();
    expect(within(structure).getByText("Câu 25")).toBeInTheDocument();
    expect(rowCountInput("sec-ly don")).toHaveValue("21");
    expect(rowSetsInput("sec-ly chum")).toHaveValue("1");
    expect(rowSetSizeInput("sec-ly chum")).toHaveValue("3");
    expect(rowCountInput("sec-ly chum")).toHaveAttribute("readonly");
    expect(rowCountInput("sec-ly chum")).toHaveValue("3");
    expect(rowCountInput("sec-ly dien")).toHaveValue("1");
    expect(within(structure).getByText("Khớp 25 câu")).toBeInTheDocument();
    expect(within(structure).getByText(/Tóm tắt theo định dạng:/)).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 3, name: "Khối lớp và độ khó" })).toBeInTheDocument();
    // R-13: không có bảng dạng trả lời, Khai báo/Bỏ, kết hợp, đơn vị đếm, danh sách nhóm cũ, nút thêm nhóm cũ.
    for (const gone of [/Khai chỉ tiêu/, /Thêm nhóm/, /Thêm dạng/, /Lấy phần còn lại/, /Kết hợp/, /Lệnh hỏi/]) expect(screen.queryByText(gone)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thêm nhóm/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 4, name: /Dạng trả lời/ })).not.toBeInTheDocument();
  });

  it("câu chùm Cố định cỡ tùy ý (2 chùm × 4 câu/chùm = 8) và chuyển sang Linh hoạt cỡ: cộng thẳng, giữ nguyên trường không dẫn xuất trong PUT", async () => {
    const user = userEvent.setup();
    const original = [wirePart([wireBcaHistory()])];
    const { server } = await openSection("sec-su", original, "BCA");
    await type(user, rowSetsInput("Su chum"), "2");
    await type(user, rowSetSizeInput("Su chum"), "4");
    expect(rowCountInput("Su don")).toHaveValue("7");
    expect(rowCountInput("Su chum")).toHaveValue("8");
    const structure = document.querySelector<HTMLElement>('[data-form-field="STRUCTURE"]')!;
    expect(within(structure).getByText("Vượt 5 câu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [single, set] = (sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.blocks as Wire[]) as [Wire, Wire];
    expect(single).toMatchObject({ id: "sec-su-single", questionCount: 7, templateGroupCode: "SU_SINGLE" });
    expect(set).toMatchObject({ id: "sec-su-set", setCount: 2, setSize: 4, questionCount: 8, templateGroupCode: "SU_SET", tagCodes: ["SU_TU_LIEU_LICH_SU"] });

    // Chuyển sang Linh hoạt cỡ -> ẩn ô chùm × câu/chùm, mở khóa ô Số câu và lưu setCount/setSize = null.
    await user.click(screen.getByRole("radio", { name: "Linh hoạt cỡ" }));
    expect(screen.queryByRole("textbox", { name: "Số chùm của Su chum" })).not.toBeInTheDocument();
    expect(rowCountInput("Su chum")).not.toHaveAttribute("readonly");
    await type(user, rowCountInput("Su chum"), "3");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(2));
    const [, flexSet] = (sectionsOf(putCalls(server)[1]!.body)["sec-su"]!.blocks as Wire[]) as [Wire, Wire];
    expect(flexSet).toMatchObject({ id: "sec-su-set", questionCount: 3, tagCodes: ["SU_TU_LIEU_LICH_SU"] });
    expect(flexSet.setCount ?? null).toBeNull();
    expect(flexSet.setSize ?? null).toBeNull();
  });

  it("fixedSetMinOne & setSizeMinOne: Số chùm 0 hoặc Số câu/chùm 0 báo lỗi tại ô, Lưu không gửi PUT và đặt focus đúng ô", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-su", [wirePart([wireBcaHistory()])], "BCA");
    await type(user, rowSetsInput("Su chum"), "0");
    expect(await screen.findByText("Ít nhất 1 chùm")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(rowSetsInput("Su chum")).toHaveFocus());

    await type(user, rowSetsInput("Su chum"), "1");
    await type(user, rowSetSizeInput("Su chum"), "0");
    expect(await screen.findByText("Ít nhất 1 câu/chùm")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(rowSetSizeInput("Su chum")).toHaveFocus());
  });

  it("thêm dòng loại câu mới (kèm nhãn Chấm tay), đổi tên, đảo thứ tự ↑/↓, xóa dòng giữ tối thiểu 1 dòng và đặt lại Mặc định của môn", async () => {
    const user = userEvent.setup();
    const bcaSuTemplate = {
      code: "BCA_SUBJECT_SU",
      kindCode: "BCA",
      scope: "SUBJECT",
      subjectCode: "SU",
      timing: "SHARED",
      structure: { parts: [wirePart([wireBcaHistory()])] },
      parts: [],
    };
    const { server } = boot({ kind: "BCA", parts: [wirePart([wireBcaHistory()])], templates: [bcaSuTemplate] });
    openStructure("?section=sec-su");
    await screen.findByRole("heading", { level: 2, name: /Cấu trúc câu|Môn/ });

    await user.click(screen.getByRole("button", { name: /Thêm dòng loại câu/ }));
    const essayItem = await screen.findByRole("menuitem", { name: /\+ Tự luận/ });
    expect(within(essayItem).getByText("Chấm tay")).toBeInTheDocument();
    await user.click(essayItem);

    expect(screen.getByText("Tổng cộng (3 dòng thành phần)")).toBeInTheDocument();
    await type(user, screen.getByRole("textbox", { name: "Tên hiển thị dòng 3" }), "Bài luận mở");
    await type(user, rowCountInput("Bài luận mở"), "2");

    // Đưa Bài luận mở lên trên Su chum rồi lưu kiểm tra thứ tự.
    await user.click(screen.getByRole("button", { name: "Đưa dòng Bài luận mở lên trên" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const reordered = sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.blocks as Wire[];
    expect(reordered.map((item) => item.name)).toEqual(["Su don", "Bài luận mở", "Su chum"]);

    // Xóa 2 dòng cho tới khi chỉ còn 1 dòng -> nút xóa dòng cuối bị khóa.
    await user.click(screen.getByRole("button", { name: "Xóa dòng Su don" }));
    await user.click(screen.getByRole("button", { name: "Xóa dòng Su chum" }));
    expect(screen.getByText("Tổng cộng (1 dòng thành phần)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xóa dòng Bài luận mở" })).toHaveAttribute("aria-disabled", "true");

    // Khôi phục Mặc định của môn từ template chuẩn -> về lại đúng 2 dòng gốc (Su don 7 + Su chum 3 = 10) kể cả sau khi đã Lưu 3 dòng.
    await user.click(screen.getByRole("button", { name: /Mặc định của môn/ }));
    expect(screen.getByText("Tổng cộng (2 dòng thành phần)")).toBeInTheDocument();
    expect(rowCountInput("Su don")).toHaveValue("7");
    expect(rowCountInput("Su chum")).toHaveValue("3");
  });

  it("công tắc Trộn lẫn giữa các loại câu: bật hiện ⇄ Trộn chung, đổi banner thông báo, khóa ↑/↓ và gửi mixBlocks: true; tắt gỡ mixBlocks", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-su", [wirePart([wireBcaHistory()])], "BCA");
    const mixSwitch = screen.getByRole("switch", { name: "Trộn lẫn giữa các loại câu" });
    expect(mixSwitch).not.toBeChecked();

    await user.click(mixSwitch);
    expect(mixSwitch).toBeChecked();
    const banner = screen.getByTestId("matrix-shuffle-banner");
    expect(banner).toHaveAttribute("data-mixed", "true");
    expect(banner).toHaveTextContent(/Đang bật trộn lẫn giữa các loại câu:/);
    expect(screen.getAllByText("⇄ Trộn chung")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "Đưa dòng Su don xuống dưới" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("button", { name: "Đưa dòng Su chum lên trên" })).toHaveAttribute("aria-disabled", "true");

    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.mixBlocks).toBe(true);

    await user.click(mixSwitch);
    expect(mixSwitch).not.toBeChecked();
    expect(banner).toHaveAttribute("data-mixed", "false");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(2));
    expect(sectionsOf(putCalls(server)[1]!.body)["sec-su"]).not.toHaveProperty("mixBlocks");
  });

  it("môn không có chỉ tiêu dạng trả lời và không ma trận giữ nguyên null sau khi sửa Cấu trúc câu", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-ly", lyParts());
    await type(user, structureInput("Điền đáp án"), "2");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body)["sec-ly"]!;
    expect(sent.answerQuotas ?? null).toBeNull();
    expect(sent.matrix ?? null).toBeNull();
  });

  it("answerQuotas dạng HSA-B của QDA Toán tính lại đúng dạng khi đổi Điền đáp án", async () => {
    const user = userEvent.setup();
    const toan = wireSection(
      "sec-toan",
      "TOAN",
      [
        wireBlock("t-single", { name: "Trắc nghiệm đơn", questionType: null, questionCount: 32 }),
        wireBlock("t-set", { name: "Chùm", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3 }),
        wireBlock("t-fill", { name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 15 }),
      ],
      {
        answerQuotas: {
          unit: "COUNT",
          items: [
            { answerType: "SINGLE_CHOICE", value: 32 },
            { answerType: "SHORT_ANSWER", value: 15 },
            { answerType: "QUESTION_SET", value: 3 },
          ],
        },
      },
    );
    const { server } = await openSection("sec-toan", [wirePart([toan])]);
    await type(user, structureInput("Điền đáp án"), "12");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.answerQuotas).toEqual({
      unit: "COUNT",
      items: [
        { answerType: "SINGLE_CHOICE", value: 32 },
        { answerType: "SHORT_ANSWER", value: 12 },
        { answerType: "QUESTION_SET", value: 3 },
      ],
    });
  });
});

describe("MT của QDA, BCA: khối lớp, độ khó và mức áp dụng (R-4, R-8)", () => {
  it("declaredDimensions: QDA Lý khai 10/10/5 và để trống Vận dụng cao lưu REQUIRED với 3 giá trị; \"Đã khai x/y\"; trống hết ghi mảng rỗng", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-ly", lyParts());
    const difficulty = () => within(screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement);
    await type(user, difficulty().getByLabelText("Nhận biết"), "10");
    await type(user, difficulty().getByLabelText("Thông hiểu"), "10");
    await type(user, difficulty().getByLabelText("Vận dụng"), "5");
    // 25 = 22 trắc nghiệm + 1 điền... số câu của môn là 25 (21 + 3 + 1); tổng khai 25/25.
    expect(await screen.findByText("Đã khai 25/25")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const matrix = sectionsOf(putCalls(server)[0]!.body)["sec-ly"]!.matrix as Wire;
    expect(matrix.byDifficulty).toEqual([
      { code: "RECOGNITION", value: 10 },
      { code: "COMPREHENSION", value: 10 },
      { code: "APPLICATION", value: 5 },
    ]);
    expect(matrix.byGrade).toEqual([]);
    expect(matrix.difficultyEnforcement ?? "REQUIRED").toBe("REQUIRED");
    expect(matrix.unit).toBe("COUNT");
  });

  it("khai 10/5/5 (tổng 20) vẫn lưu nháp được và hiện \"Đã khai 20/25\"; Kiểm tra báo lệch ở máy chủ", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-ly", lyParts());
    const difficulty = () => within(screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement);
    await type(user, difficulty().getByLabelText("Nhận biết"), "10");
    await type(user, difficulty().getByLabelText("Thông hiểu"), "5");
    await type(user, difficulty().getByLabelText("Vận dụng"), "5");
    expect(await screen.findByText("Đã khai 20/25")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(checkCalls(server)).toHaveLength(0);
  });

  it("enforcementByKind: QDA chip theo chiều đã khai, BCA chip Tham khảo; không nút Bắt buộc/Tham khảo chung hay Áp dụng khi sinh đề", async () => {
    await openSection("sec-ly", lyParts({ matrix: matrixOf({ byGrade: [{ code: "GRADE_12", value: 25 }] }) }));
    const grade = within(screen.getByRole("heading", { level: 4, name: "Khối lớp" }).closest("[data-form-field]") as HTMLElement);
    const difficulty = within(screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement);
    expect(grade.getByText("Bắt buộc")).toBeInTheDocument();
    expect(difficulty.getByText("Chưa khai báo")).toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: "Áp dụng khi sinh đề" })).not.toBeInTheDocument();
  });

  it("enforcementByKind: BCA chip Tham khảo, không nút đổi; Sử ẩn Khối lớp theo chuẩn HSA (UNI-1729)", async () => {
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN", { matrix: matrixOf({ byGrade: [{ code: "GRADE_12", value: 25 }], gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" }) })])], "BCA");
    const grade = within(screen.getByRole("heading", { level: 4, name: "Khối lớp" }).closest("[data-form-field]") as HTMLElement);
    expect(grade.getByText("Tham khảo")).toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: "Áp dụng khi sinh đề" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Bắt buộc|Tham khảo/ })).not.toBeInTheDocument();
  });

  it("MT của QDA không có cột Liên lớp, BCA cũng không; hàng khối lớp có đủ Lớp 10, 11, 12", async () => {
    await openSection("sec-ly", lyParts());
    const grade = within(screen.getByRole("heading", { level: 4, name: "Khối lớp" }).closest("[data-form-field]") as HTMLElement);
    expect(grade.getAllByRole("textbox")).toHaveLength(3);
    expect(grade.queryByLabelText("Liên lớp")).not.toBeInTheDocument();
  });
});

describe("MT: giới hạn Khoa học của QDA (R-12)", () => {
  it("scienceLimits: QDA Lý 3 chùm hoặc 0 câu điền báo lỗi tại ô và chặn Lưu; QDA Sử 0 câu điền không lỗi", async () => {
    const user = userEvent.setup();
    const parts = [wirePart([wireQdaScience("sec-ly", "LY"), wireQdaScience("sec-su", "SU", { fill: 0 })])];
    const { server } = await openSection("sec-ly", parts);
    await type(user, structureInput("Số chùm"), "3");
    expect(await screen.findByText("Số chùm từ 1 đến 2")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await type(user, structureInput("Số chùm"), "2");
    await type(user, structureInput("Điền đáp án"), "0");
    expect(await screen.findByText("Cần ít nhất 1 câu điền đáp án")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await type(user, structureInput("Điền đáp án"), "1");
    await waitFor(() => expect(screen.queryByText(/Cần ít nhất 1 câu điền/)).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    // Sử cho 0 câu điền: mở môn Sử không có lỗi nào.
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("giới hạn chỉ áp cho Khoa học QDA: BCA Lý 3 chùm không bị chặn", async () => {
    const user = userEvent.setup();
    const parts = [wirePart([wireQdaScience("sec-ly", "LY")])];
    const { server } = await openSection("sec-ly", parts, "BCA");
    await type(user, structureInput("Số chùm"), "3");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
  });
});

describe("MT: QDA/BCA dùng quy tắc MAPID giống HSA (UNI-1729, UNI-1731) & V-ACT giữ Nguồn chùm + Phạm vi (R-3, R-6)", () => {
  it("UNI-1731: QDA Toán dùng quy tắc MAPID giống HSA, không còn Nguồn chùm hay Phạm vi & chỉ tiêu chủ đề", async () => {
    const user = userEvent.setup();
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN")])], "QDA");
    expect(await screen.findByRole("radio", { name: /Theo ma trận MAPID/ })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 4, name: "Nguồn chùm" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: /Theo ma trận MAPID/ }));
    expect(await screen.findByTestId("mapid-rules-table")).toBeInTheDocument();
  });

  it("UNI-1729: BCA Lịch sử dùng quy tắc MAPID & mạch kiến thức giống HSA, ẩn Khối lớp và giữ Nguồn chùm chỉ cho Nhãn nguồn", async () => {
    const user = userEvent.setup();
    await openSection("sec-su", [wirePart([wireBcaHistory("sec-su")])], "BCA");
    expect(await screen.findByTestId("hsa-strand-section")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 4, name: "Khối lớp" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: "Độ khó" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: "Nguồn chùm" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thêm điều kiện" }));
    expect(screen.getByRole("menuitem", { name: /Nhãn nguồn/ })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Phạm vi MAPID/ })).not.toBeInTheDocument();
  });

  it("bài QDA cũ có knowledgeScope.nodeIds mở lên không bị báo có thay đổi chưa lưu và không tự chuyển sang mapidMode", async () => {
    await openSection(
      "sec-toan",
      [wirePart([wireQdaScience("sec-toan", "TOAN", { section: { knowledgeScope: { nodeIds: ["n-alg"] } } })])],
      "QDA",
    );
    expect(await screen.findByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" })).toBeInTheDocument();
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
  });

  it("môn ẩn khối lớp (BCA Sử): sửa độ khó không xóa âm thầm byGrade REQUIRED đã lưu, nhưng xóa byGrade REFERENCE", async () => {
    const user = userEvent.setup();
    const { server } = await openSection(
      "sec-su",
      [
        wirePart([
          wireBcaHistory("sec-su", {
            matrix: matrixOf({
              byGrade: [{ code: "GRADE_11", value: 4 }, { code: "GRADE_12", value: 6 }],
              gradeEnforcement: "REQUIRED",
              difficultyEnforcement: "REFERENCE",
            }),
          }),
        ]),
      ],
      "BCA",
    );
    const difficulty = within(screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement);
    await type(user, difficulty.getByLabelText("Nhận biết"), "3");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.matrix as Wire;
    expect(saved.byGrade).toEqual([{ code: "GRADE_11", value: 4 }, { code: "GRADE_12", value: 6 }]);
    expect(saved.gradeEnforcement).toBe("REQUIRED");
  });

  it("scopeAndQuotaFlow: V-ACT Toán có Phạm vi kiến thức và nút Thêm chỉ tiêu chủ đề; chưa có dòng thì không hiện bảng, bấm mới hiện", async () => {
    const user = userEvent.setup();
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN")])], "VACT");
    expect(await screen.findByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Toàn môn/ })).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: /chỉ tiêu chủ đề/i })).not.toBeInTheDocument();
    expect(screen.queryByText("Phần còn lại")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thêm chỉ tiêu chủ đề" }));
    expect(await screen.findByRole("heading", { name: /Chỉ tiêu chủ đề/ })).toBeInTheDocument();
  });

  it("bài đã có dòng chỉ tiêu chủ đề hiện bảng sẵn, không hiện nút mở bảng", async () => {
    const quota = { id: "tq-1", name: "Xác suất", nodeIds: [], count: 3, enforcement: "REQUIRED", basis: "QUESTION" };
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN", { section: { topicQuotas: [quota] } })])], "VACT");
    expect(await screen.findByRole("heading", { name: /Chỉ tiêu chủ đề/ })).toBeInTheDocument();
    expect(screen.queryByText("Mở bảng số câu tối thiểu theo từng chủ đề.")).not.toBeInTheDocument();
  });

  it("V-ACT Toán: Nguồn chùm chỉ hiện ở nhóm chùm; không có điều kiện nào thì hiện dòng trống; menu Thêm điều kiện có MAPID và Nhãn nguồn", async () => {
    const user = userEvent.setup();
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN", { fill: 0 })])], "VACT");
    const source = await screen.findByRole("heading", { level: 4, name: "Nguồn chùm" });
    expect(within(source.closest("[data-form-field]") as HTMLElement).getByText(/Chưa có điều kiện/)).toBeInTheDocument();
    await user.click(within(source.closest("[data-form-field]") as HTMLElement).getByRole("button", { name: "Thêm điều kiện" }));
    expect(await screen.findByRole("menuitem", { name: /MAPID/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Nhãn nguồn/ })).toBeInTheDocument();
  });

  it("setSourceOnlyTouchesSetGroup: nhãn nguồn của chùm chỉ đổi `tagCodes` của nhóm chùm; môn, nhóm đơn và nhóm điền không đổi", async () => {
    const user = userEvent.setup();
    const original = [wirePart([wireQdaScience("sec-toan", "TOAN")])];
    const { server } = await openSection("sec-toan", original, "VACT");
    const source = (await screen.findByRole("heading", { level: 4, name: "Nguồn chùm" })).closest("[data-form-field]") as HTMLElement;
    await user.click(within(source).getByRole("button", { name: "Thêm điều kiện" }));
    await user.click(await screen.findByRole("menuitem", { name: /Nhãn nguồn/ }));
    const tag = await within(source).findAllByRole("checkbox");
    await user.click(tag[0]!);
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [single, set, fill] = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.blocks as Wire[];
    const seeded = (original[0]!.sections as Wire[])[0]!.blocks as Wire[];
    expect((set!.tagCodes as string[]).length).toBe(1);
    expect(single).toEqual(expect.objectContaining({ id: seeded[0]!.id, tagCodes: [] }));
    expect(fill).toEqual(expect.objectContaining({ id: seeded[2]!.id, tagCodes: [] }));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.knowledgeScope ?? null).toBeNull();
  });

  it("AD-135: bản en dùng nhãn đã dịch Question sets cho aria-label của MatrixSetSource, không rò tên Chùm tiếng Việt và giữ nguyên set.name trong payload", async () => {
    const user = userEvent.setup();
    const toan = wireQdaScience("sec-toan", "TOAN");
    (toan.blocks as Wire[])[1]!.name = "Chùm";
    const { server } = boot({ kind: "VACT", parts: [wirePart([toan])] });
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-toan`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />, { locale: "en", messages: enMessages });

    const source = (await screen.findByRole("heading", { level: 4, name: "Set source" })).closest("[data-form-field]") as HTMLElement;
    await user.click(within(source).getByRole("button", { name: "Add condition" }));
    await user.click(await screen.findByRole("menuitem", { name: /Source tags/ }));

    const searchInput = within(source).getByRole("searchbox");
    const searchLabel = searchInput.getAttribute("aria-label") ?? "";
    expect(searchLabel).toBe("Search tags of Question sets");
    expect(searchLabel).not.toContain("Chùm");
    expect(searchLabel).not.toMatch(/[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i);

    const tags = await within(source).findAllByRole("checkbox");
    await user.click(tags[0]!);
    const selectedList = within(source).getByRole("list", { name: /Question sets/ });
    expect(selectedList.getAttribute("aria-label")).not.toContain("Chùm");

    // R1-5: mở nhánh MAPID (`KnowledgeScopeField`, matrix-set-source.tsx:80) trên bản en → KnowledgePickerDialog phải dùng "Question sets", không rò "Chùm".
    await user.click(within(source).getByRole("button", { name: "Add condition" }));
    await user.click(await screen.findByRole("menuitem", { name: /^MAPID/ }));
    const mapidCondition = source.querySelector('[data-condition="MAPID"]') as HTMLElement | null;
    expect(mapidCondition).not.toBeNull();
    await user.click(within(mapidCondition!).getByRole("radio", { name: "By topic" }));
    await user.click(within(mapidCondition!).getByRole("button", { name: "Choose knowledge" }));

    const picker = await screen.findByRole("dialog", { name: "Choose knowledge (MAPID)" });
    const pickerDesc = within(picker).getByText(/^Scope of /);
    expect(pickerDesc).toHaveTextContent("Question sets");
    expect(pickerDesc).not.toHaveTextContent("Chùm");
    expect(pickerDesc.textContent).not.toMatch(/[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i);
    await user.click(within(picker).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    for (const el of source.querySelectorAll("[aria-label]")) {
      expect(el.getAttribute("aria-label")).not.toMatch(/[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i);
    }

    await user.click(screen.getByRole("button", { name: "Continue → Listing" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [, savedSet] = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.blocks as Wire[];
    expect(savedSet!.name).toBe("Chùm");
    expect((savedSet!.tagCodes as string[]).length).toBe(1);
  });
});

describe("MT: chỗ gắn theo kỳ (ADV:CR-2:2; R-3, R-15)", () => {
  const slotBlock = () => <section data-testid="kind-slot" aria-label="Phần thử theo kỳ" />;

  it("slot trước Cấu trúc câu với biến thể gọn: phần thử đứng trên khối Cấu trúc câu, khối mang data-structure-variant compact, gỡ đăng ký khôi phục", async () => {
    const unregister = registerMatrixKindSlot("QDA", slotBlock, { placement: "before-structure", structureVariant: "compact" });
    try {
      await openSection("sec-ly", lyParts());
      const slot = screen.getByTestId("kind-slot");
      const structure = document.querySelector<HTMLElement>('[data-form-field="STRUCTURE"]');
      expect(structure).not.toBeNull();
      expect(slot.compareDocumentPosition(structure!) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
      expect(structure).toHaveAttribute("data-structure-variant", "compact");
      // Bản gọn: ô xếp ngang, không câu nhắc; vẫn giữ toàn bộ ô và lỗi như bản mặc định.
      expect(within(structure!).queryByText(/Khai báo số lượng câu theo từng loại câu hỏi/), "hint paragraph hidden in compact").toBeNull();
    } finally {
      unregister();
    }
    await openSection("sec-hoa", lyParts());
    expect(screen.queryByTestId("kind-slot")).toBeNull();
    expect(document.querySelector('[data-form-field="STRUCTURE"]')).not.toHaveAttribute("data-structure-variant");
  });

  it("slot không tuỳ chọn giữ vị trí mặc định: đứng sau Cấu trúc câu, trước Khối lớp và Độ khó, khối không mang biến thể", async () => {
    const unregister = registerMatrixKindSlot("QDA", slotBlock);
    try {
      await openSection("sec-ly", lyParts());
      const slot = screen.getByTestId("kind-slot");
      const structure = document.querySelector<HTMLElement>('[data-form-field="STRUCTURE"]');
      const distribution = screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]");
      expect(slot.compareDocumentPosition(structure!) & Node.DOCUMENT_POSITION_PRECEDING).not.toBe(0);
      expect(distribution!.compareDocumentPosition(slot) & Node.DOCUMENT_POSITION_PRECEDING).not.toBe(0);
      expect(structure).not.toHaveAttribute("data-structure-variant");
    } finally {
      unregister();
    }
  });
});

describe("MT: bàn phím và focus (CR-2 AC-10; R1-3, R1-6)", () => {
  it("Thêm chỉ tiêu chủ đề bằng bàn phím: Enter mở bảng và đưa focus về nút Thêm chỉ tiêu chủ đề của bảng; Enter nữa thêm dòng chờ mapping", async () => {
    const user = userEvent.setup();
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN")])], "VACT");
    const reveal = await screen.findByRole("button", { name: "Thêm chỉ tiêu chủ đề" });
    reveal.focus();
    await user.keyboard("{Enter}");
    await waitFor(() => expect(document.activeElement).toHaveAttribute("data-topic-add"));
    expect(document.activeElement).toHaveAccessibleName("Thêm chỉ tiêu chủ đề");
    await user.keyboard("{Enter}");
    expect(await screen.findByText("Chờ mapping")).toBeInTheDocument();
  });

  it("Nguồn chùm bằng bàn phím: Enter mở menu, ArrowDown tới Nhãn nguồn, Enter chọn; mục Nhãn nguồn hiện, menu đóng và focus về nút Thêm điều kiện", async () => {
    const user = userEvent.setup();
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN", { fill: 0 })])], "VACT");
    const source = (await screen.findByRole("heading", { level: 4, name: "Nguồn chùm" })).closest("[data-form-field]") as HTMLElement;
    const trigger = within(source).getByRole("button", { name: "Thêm điều kiện" });
    trigger.focus();
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("menuitem", { name: /MAPID/ })).toHaveFocus();
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("menuitem", { name: /Nhãn nguồn/ })).toHaveFocus();
    await user.keyboard("{Enter}");
    expect(await within(source).findByRole("heading", { level: 5, name: "Nhãn nguồn" })).toBeInTheDocument();
    expect(source.querySelector('[data-condition="MAPID"]')).toBeNull();
    await waitFor(() => expect(screen.queryByRole("menu")).not.toBeInTheDocument());
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("ô lớp gõ sai: Lưu không gửi PUT và đặt focus vào chính ô đó", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-ly", lyParts());
    const grade = within(screen.getByRole("heading", { level: 4, name: "Khối lớp" }).closest("[data-form-field]") as HTMLElement);
    await type(user, grade.getByLabelText("Lớp 12"), "abc");
    expect(await grade.findByRole("alert")).toHaveTextContent("Nhập một số không âm.");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(grade.getByLabelText("Lớp 12")).toHaveFocus());
  });
});

describe("MT: số request lúc mở (FTM mục 9, plan B4; CR-2 AC-11)", () => {
  it("noExtraRequestsOnOpen: mở môn MT không gọi nhiều request hơn MD của cùng môn, cùng fixture", async () => {
    const opened = async (sets: number) => {
      const parts = [wirePart([wireQdaScience("sec-ly", "LY", { sets }), wireQdaScience("sec-hoa", "HOA")])];
      const { server } = boot({ kind: "QDA", parts });
      const view = openStructure("?section=sec-ly");
      await screen.findByRole("heading", { level: 2, name: /Môn|Vật|LY|Lý/ });
      await waitFor(() => expect(screen.queryByText(/Đang tải/)).not.toBeInTheDocument());
      const log = requestLog(server).toSorted();
      view.unmount();
      return log;
    };
    const withMatrix = await opened(1);
    const withDefault = await opened(3);
    const remaining = [...withDefault];
    for (const line of withMatrix) {
      const at = remaining.indexOf(line);
      expect(at, `MT gọi request MD không gọi: ${line}`).toBeGreaterThanOrEqual(0);
      remaining.splice(at, 1);
    }
  });
});
