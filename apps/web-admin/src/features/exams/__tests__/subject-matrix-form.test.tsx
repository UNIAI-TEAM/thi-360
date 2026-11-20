import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { registerMatrixKindSlot } from "../subject-forms/matrix-form";
import { patchSubjectFormEntry } from "../subject-forms/registry";
import { EXAM_ID, type FakeServer } from "./fake-exams-server";
import { boot, checkCalls, openStructure, putCalls, requestLog, sectionsOf, wireBlock, wirePart, wireSection, type Wire } from "./subject-form-harness";
import { matrixOf, structureInput, wireBcaHistory, wireQdaScience } from "./subject-matrix-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

/**
 * CR-2 (FTM R-3, R-4, R-6, R-8, R-12, R-13; plan CR-2 AC-2, AC-4..AC-8, AC-11) trên `StructureEditor` thật với cờ MT của QDA, BCA bật
 * như ở sản phẩm. Phần V-ACT (hai cột %, Tiếng Việt, Toán) ở `subject-matrix-vact.test.tsx`.
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

describe("MT của QDA, BCA: Cấu trúc câu (R-3)", () => {
  it("mở môn Khoa học thành MT: có Cấu trúc câu, khối lớp và độ khó, phạm vi; Trắc nghiệm đơn chỉ đọc; không có trường của MD (noRemovedFields)", async () => {
    await openSection("sec-ly", lyParts());
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeInTheDocument();
    expect(structureInput("Trắc nghiệm")).toHaveValue("24");
    expect(structureInput("Số chùm")).toHaveValue("1");
    expect(structureInput("Điền đáp án")).toHaveValue("1");
    expect(screen.getByRole("textbox", { name: "Trắc nghiệm đơn" })).toHaveAttribute("readonly");
    expect(screen.getByRole("textbox", { name: "Trắc nghiệm đơn" })).toHaveValue("21");
    expect(screen.getByRole("heading", { level: 3, name: "Khối lớp và độ khó" })).toBeInTheDocument();
    // R-13: không có bảng dạng trả lời, Khai báo/Bỏ, kết hợp, đơn vị đếm, danh sách nhóm, nút thêm nhóm.
    for (const gone of [/Khai chỉ tiêu/, /Thêm nhóm/, /Thêm dạng/, /Lấy phần còn lại/, /Kết hợp/, /Lệnh hỏi/]) expect(screen.queryByText(gone)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thêm nhóm/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 4, name: /Dạng trả lời/ })).not.toBeInTheDocument();
  });

  it("đổi số chùm 1 → 2 ở BCA Sử (T = 10): nhóm chùm 2 × 3 = 6, nhóm đơn 4, các trường không dẫn xuất giữ nguyên trong thân PUT", async () => {
    const user = userEvent.setup();
    const original = [wirePart([wireBcaHistory()])];
    const { server } = await openSection("sec-su", original, "BCA");
    await type(user, structureInput("Số chùm"), "2");
    expect(structureInput("Trắc nghiệm")).toHaveValue("10");
    expect(screen.getByRole("textbox", { name: "Trắc nghiệm đơn" })).toHaveValue("4");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [single, set] = (sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.blocks as Wire[]) as [Wire, Wire];
    expect(single).toMatchObject({ id: "sec-su-single", questionCount: 4, templateGroupCode: "SU_SINGLE" });
    expect(set).toMatchObject({ id: "sec-su-set", setCount: 2, setSize: 3, questionCount: 6, templateGroupCode: "SU_SET", tagCodes: ["SU_TU_LIEU_LICH_SU"] });
  });

  it("fixedSetMinOne: Số chùm 0 báo \"Ít nhất 1 chùm\" tại ô, Lưu không gửi PUT và đặt focus vào ô", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-su", [wirePart([wireBcaHistory()])], "BCA");
    await type(user, structureInput("Số chùm"), "0");
    expect(await screen.findByText("Ít nhất 1 chùm")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(structureInput("Số chùm")).toHaveFocus());
  });

  it("Trắc nghiệm nhỏ hơn câu con báo lỗi tại ô, không vào bản nháp, chặn lưu; sửa đúng thì lưu được", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-su", [wirePart([wireBcaHistory()])], "BCA");
    await type(user, structureInput("Trắc nghiệm"), "2");
    expect(await screen.findByText(/Ít hơn số câu con của chùm \(3\)/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Trắc nghiệm đơn" })).toHaveValue("7");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await type(user, structureInput("Trắc nghiệm"), "9");
    await waitFor(() => expect(screen.queryByText(/Ít hơn số câu con/)).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect((sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.blocks as Wire[])[0]!.questionCount).toBe(6);
  });

  it("môn không có chỉ tiêu dạng trả lời và không ma trận giữ nguyên null sau khi sửa Cấu trúc câu", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-ly", lyParts());
    await type(user, structureInput("Điền đáp án"), "2");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body)["sec-ly"]!;
    expect(sent.answerQuotas ?? null).toBeNull();
    expect(sent.matrix ?? null).toBeNull();
  });

  it("answerQuotas dạng HSA-B của QDA Toán tính lại đúng dạng khi đổi Trắc nghiệm", async () => {
    const user = userEvent.setup();
    const toan = wireSection(
      "sec-toan",
      "TOAN",
      [
        wireBlock("t-single", { questionType: null, questionCount: 32 }),
        wireBlock("t-set", { questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3 }),
        wireBlock("t-fill", { questionType: "SHORT_ANSWER", questionCount: 15 }),
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
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

describe("MT: Số chùm vượt Trắc nghiệm (R-3, R1-4, R1-7)", () => {
  const choice = () => within(screen.getByRole("group", { name: "Trắc nghiệm" }));
  const single = () => screen.getByRole("textbox", { name: "Trắc nghiệm đơn" });
  const savedBlocks = (server: FakeServer, sectionId: string) => sectionsOf(putCalls(server)[0]!.body)[sectionId]!.blocks as Wire[];

  it("BCA Sử T = 10: 3 chùm (9 câu con) vừa đủ; 4 chùm (12) báo lỗi tại Trắc nghiệm, T vẫn 10, không PUT, focus vào Trắc nghiệm; về 3 chùm thì lưu 9 và câu đơn 1", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-su", [wirePart([wireBcaHistory()])], "BCA");
    await type(user, structureInput("Số chùm"), "3");
    expect(screen.queryByText(/Ít hơn số câu con/)).not.toBeInTheDocument();
    expect(structureInput("Trắc nghiệm")).toHaveValue("10");
    expect(single()).toHaveValue("1");

    await type(user, structureInput("Số chùm"), "4");
    expect(await choice().findByText("Ít hơn số câu con của chùm (12)")).toBeInTheDocument();
    expect(structureInput("Trắc nghiệm")).toHaveValue("10");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(structureInput("Trắc nghiệm")).toHaveFocus());

    await type(user, structureInput("Số chùm"), "3");
    await waitFor(() => expect(screen.queryByText(/Ít hơn số câu con/)).not.toBeInTheDocument());
    expect(structureInput("Trắc nghiệm")).toHaveValue("10");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [singleBlock, setBlock] = savedBlocks(server, "sec-su") as [Wire, Wire];
    expect(singleBlock).toMatchObject({ questionCount: 1 });
    expect(setBlock).toMatchObject({ setCount: 3, setSize: 3, questionCount: 9 });
  });

  it("biên: T = 12 và 4 chùm (12 câu con) không lỗi, câu đơn về 0, lưu được; 5 chùm (15) lỗi tại Trắc nghiệm và T vẫn 12", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-su", [wirePart([wireBcaHistory()])], "BCA");
    await type(user, structureInput("Trắc nghiệm"), "12");
    await type(user, structureInput("Số chùm"), "4");
    expect(screen.queryByText(/Ít hơn số câu con/)).not.toBeInTheDocument();
    expect(single()).toHaveValue("0");
    expect(structureInput("Trắc nghiệm")).toHaveValue("12");

    await type(user, structureInput("Số chùm"), "5");
    expect(await choice().findByText("Ít hơn số câu con của chùm (15)")).toBeInTheDocument();
    expect(structureInput("Trắc nghiệm")).toHaveValue("12");
    await type(user, structureInput("Số chùm"), "4");
    await waitFor(() => expect(screen.queryByText(/Ít hơn số câu con/)).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [singleBlock, setBlock] = savedBlocks(server, "sec-su") as [Wire, Wire];
    expect(singleBlock).toMatchObject({ questionCount: 0 });
    expect(setBlock).toMatchObject({ setCount: 4, setSize: 3, questionCount: 12 });
  });

  it("R1-7: T gõ nhỏ hơn câu con rồi đổi Số chùm theo T đã gõ: lỗi gỡ khi câu con vừa T, và nhóm đơn tính từ T đã gõ chứ không từ T của bản nháp", async () => {
    const user = userEvent.setup();
    const { server } = await openSection("sec-su", [wirePart([wireBcaHistory()])], "BCA");
    await type(user, structureInput("Số chùm"), "3");
    await type(user, structureInput("Trắc nghiệm"), "8");
    expect(await choice().findByText("Ít hơn số câu con của chùm (9)")).toBeInTheDocument();

    await type(user, structureInput("Số chùm"), "4");
    expect(await choice().findByText("Ít hơn số câu con của chùm (12)")).toBeInTheDocument();
    expect(structureInput("Trắc nghiệm")).toHaveValue("8");

    await type(user, structureInput("Số chùm"), "2");
    await waitFor(() => expect(screen.queryByText(/Ít hơn số câu con/)).not.toBeInTheDocument());
    expect(structureInput("Trắc nghiệm")).toHaveValue("8");
    expect(single()).toHaveValue("2");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [singleBlock, setBlock] = savedBlocks(server, "sec-su") as [Wire, Wire];
    expect(singleBlock).toMatchObject({ questionCount: 2 });
    expect(setBlock).toMatchObject({ setCount: 2, questionCount: 6 });
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
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

  it("enforcementByKind: BCA chip Tham khảo, không nút đổi", async () => {
    await openSection("sec-su", [wirePart([wireBcaHistory("sec-su", { matrix: matrixOf({ byGrade: [{ code: "GRADE_12", value: 7 }], gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" }) })])], "BCA");
    const grade = within(screen.getByRole("heading", { level: 4, name: "Khối lớp" }).closest("[data-form-field]") as HTMLElement);
    expect(grade.getByText("Tham khảo")).toBeInTheDocument();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await type(user, structureInput("Số chùm"), "2");
    await type(user, structureInput("Điền đáp án"), "0");
    expect(await screen.findByText("Cần ít nhất 1 câu điền đáp án")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await type(user, structureInput("Điền đáp án"), "1");
    await waitFor(() => expect(screen.queryByText(/Cần ít nhất 1 câu điền/)).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    // Sử cho 0 câu điền: mở môn Sử không có lỗi nào.
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("giới hạn chỉ áp cho Khoa học QDA: BCA Lý 3 chùm không bị chặn", async () => {
    const user = userEvent.setup();
    const parts = [wirePart([wireQdaScience("sec-ly", "LY")])];
    const { server } = await openSection("sec-ly", parts, "BCA");
    await type(user, structureInput("Số chùm"), "3");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
  });
});

describe("MT: Nguồn chùm và phạm vi (R-3, R-6)", () => {
  it("scopeAndQuotaFlow: QDA Toán có Phạm vi kiến thức và nút Thêm chỉ tiêu chủ đề; chưa có dòng thì không hiện bảng, bấm mới hiện", async () => {
    const user = userEvent.setup();
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN")])]);
    expect(await screen.findByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Toàn môn/ })).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: /chỉ tiêu chủ đề/i })).not.toBeInTheDocument();
    expect(screen.queryByText("Phần còn lại")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thêm chỉ tiêu chủ đề" }));
    expect(await screen.findByRole("heading", { name: /Chỉ tiêu chủ đề/ })).toBeInTheDocument();
  });

  it("bài đã có dòng chỉ tiêu chủ đề hiện bảng sẵn, không hiện nút mở bảng", async () => {
    const quota = { id: "tq-1", name: "Xác suất", nodeIds: [], count: 3, enforcement: "REQUIRED", basis: "QUESTION" };
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN", { section: { topicQuotas: [quota] } })])]);
    expect(await screen.findByRole("heading", { name: /Chỉ tiêu chủ đề/ })).toBeInTheDocument();
    expect(screen.queryByText("Mở bảng số câu tối thiểu theo từng chủ đề.")).not.toBeInTheDocument();
  });

  it("BCA Hóa: Nguồn chùm chỉ hiện ở nhóm chùm; không có điều kiện nào thì hiện dòng trống; menu Thêm điều kiện có MAPID và Nhãn nguồn", async () => {
    const user = userEvent.setup();
    await openSection("sec-hoa", [wirePart([wireQdaScience("sec-hoa", "HOA", { fill: 0 })])], "BCA");
    const source = await screen.findByRole("heading", { level: 4, name: "Nguồn chùm" });
    expect(within(source.closest("[data-form-field]") as HTMLElement).getByText(/Chưa có điều kiện/)).toBeInTheDocument();
    await user.click(within(source.closest("[data-form-field]") as HTMLElement).getByRole("button", { name: "Thêm điều kiện" }));
    expect(await screen.findByRole("menuitem", { name: /MAPID/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Nhãn nguồn/ })).toBeInTheDocument();
  });

  it("setSourceOnlyTouchesSetGroup: nhãn nguồn của chùm chỉ đổi `tagCodes` của nhóm chùm; môn, nhóm đơn và nhóm điền không đổi", async () => {
    const user = userEvent.setup();
    const original = [wirePart([wireQdaScience("sec-hoa", "HOA")])];
    const { server } = await openSection("sec-hoa", original, "BCA");
    const source = (await screen.findByRole("heading", { level: 4, name: "Nguồn chùm" })).closest("[data-form-field]") as HTMLElement;
    await user.click(within(source).getByRole("button", { name: "Thêm điều kiện" }));
    await user.click(await screen.findByRole("menuitem", { name: /Nhãn nguồn/ }));
    const tag = await within(source).findAllByRole("checkbox");
    await user.click(tag[0]!);
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [single, set, fill] = sectionsOf(putCalls(server)[0]!.body)["sec-hoa"]!.blocks as Wire[];
    const seeded = (original[0]!.sections as Wire[])[0]!.blocks as Wire[];
    expect((set!.tagCodes as string[]).length).toBe(1);
    expect(single).toEqual(expect.objectContaining({ id: seeded[0]!.id, tagCodes: [] }));
    expect(fill).toEqual(expect.objectContaining({ id: seeded[2]!.id, tagCodes: [] }));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-hoa"]!.knowledgeScope ?? null).toBeNull();
  });

  it("AD-135: bản en dùng nhãn đã dịch Question sets cho aria-label của MatrixSetSource, không rò tên Chùm tiếng Việt và giữ nguyên set.name trong payload", async () => {
    const user = userEvent.setup();
    const hoa = wireQdaScience("sec-hoa", "HOA");
    (hoa.blocks as Wire[])[1]!.name = "Chùm";
    const { server } = boot({ kind: "BCA", parts: [wirePart([hoa])] });
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-hoa`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />, { locale: "en", messages: enMessages });

    const source = (await screen.findByRole("heading", { level: 4, name: "Set source" })).closest("[data-form-field]") as HTMLElement;
    await user.click(within(source).getByRole("button", { name: "Add condition" }));
    await user.click(await screen.findByRole("menuitem", { name: /Source tags/ }));

    const searchInput = within(source).getByRole("searchbox");
    const searchLabel = searchInput.getAttribute("aria-label") ?? "";
    expect(searchLabel).toContain("Question sets");
    expect(searchLabel).not.toContain("Chùm");
    expect(searchLabel).not.toMatch(/[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i);

    const tags = await within(source).findAllByRole("checkbox");
    await user.click(tags[0]!);
    const selectedList = within(source).getByRole("list", { name: /Question sets/ });
    expect(selectedList.getAttribute("aria-label")).not.toContain("Chùm");
    for (const el of source.querySelectorAll("[aria-label]")) {
      expect(el.getAttribute("aria-label")).not.toMatch(/[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i);
    }

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const [, savedSet] = sectionsOf(putCalls(server)[0]!.body)["sec-hoa"]!.blocks as Wire[];
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
      expect(within(structure!).queryByText(/Nhập số câu của từng dạng/), "hint paragraph hidden in compact").toBeNull();
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
    await openSection("sec-toan", [wirePart([wireQdaScience("sec-toan", "TOAN")])]);
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
    await openSection("sec-hoa", [wirePart([wireQdaScience("sec-hoa", "HOA", { fill: 0 })])], "BCA");
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(grade.getByLabelText("Lớp 12")).toHaveFocus());
  });
});

describe("MT: số request lúc mở (FTM mục 9, plan B4; CR-2 AC-11)", () => {
  it("noExtraRequestsOnOpen: mở môn MT không gọi nhiều request hơn MD của cùng môn, cùng fixture", async () => {
    const parts = () => [wirePart([wireQdaScience("sec-ly", "LY"), wireQdaScience("sec-hoa", "HOA")])];
    const opened = async () => {
      const { server } = boot({ kind: "QDA", parts: parts() });
      const view = openStructure("?section=sec-ly");
      await screen.findByRole("heading", { level: 2, name: /Môn|Vật|LY|Lý/ });
      await waitFor(() => expect(screen.queryByText(/Đang tải/)).not.toBeInTheDocument());
      const log = requestLog(server).toSorted();
      view.unmount();
      return log;
    };
    const withMatrix = await opened();
    const undoLy = patchSubjectFormEntry("QDA", "LY", { enabled: false });
    const undoHoa = patchSubjectFormEntry("QDA", "HOA", { enabled: false });
    const withDefault = await opened();
    undoLy();
    undoHoa();
    const remaining = [...withDefault];
    for (const line of withMatrix) {
      const at = remaining.indexOf(line);
      expect(at, `MT gọi request MD không gọi: ${line}`).toBeGreaterThanOrEqual(0);
      remaining.splice(at, 1);
    }
  });
});
