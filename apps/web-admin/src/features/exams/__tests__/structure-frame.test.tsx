import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { examTabHref } from "../editor-tabs";
import { ExamStructureList } from "../components/exam-structure-list";
import { ExamStructureView } from "../components/exam-structure-view";
import { defaultSelection } from "../components/exam-structure-draft";
import { ExamEditorActionsSlot } from "../components/exam-editor-actions";
import type { ExamPart, ExamSection } from "../types";
import checkHsa from "./fixtures/check-hsa-f-001.json";
import { ExamsBackend } from "./fake-exams-backend";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

let backend: ExamsBackend;

beforeEach(() => {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  backend.install();
});

const QDA = "ex-qda-f-001";
const HSA = "ex-hsa-f-001";
const api = (id: string, tail: string) => `/api/v1/admin/exams/${id}/${tail}`;

function openStructure(examId: string, search = "") {
  window.history.replaceState(null, "", `/exams/${examId}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={examId} />);
}

const list = () => screen.findByRole("navigation", { name: "Cấu trúc" });
const dialogName = "Còn thay đổi chưa lưu";

/* ---------- dữ liệu dựng tay cho các phép thử thuần ---------- */

const section = (id: string, subjectCode: string, over: Partial<ExamSection> = {}): ExamSection => ({
  id,
  subjectCode,
  questionCount: 10,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [],
  ...over,
});

const part = (id: string, over: Partial<ExamPart> = {}): ExamPart => ({
  id,
  order: 1,
  name: `Phần ${id}`,
  durationMinutes: 60,
  questionCount: 10,
  questionCountOnPaper: 10,
  questionCountRequired: 10,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [],
  options: [],
  ...over,
});

const names = { subject: (code: string) => code, combination: (code: string) => code };

describe("Khung bước 2: mục mở đầu tiên (R-03)", () => {
  it("bài chỉ có một môn: mở môn đó", () => {
    const parts = [part("p1", { sections: [section("s1", "MATH")] })];
    expect(defaultSelection(parts)).toEqual({ kind: "section", sectionId: "s1" });
  });

  it("bài FULL: mở môn Toán của Phần 1", () => {
    const parts = [
      part("p1", { sections: [section("s-math", "MATH")] }),
      part("p2", { sections: [section("s-lit", "LITERATURE")] }),
    ];
    expect(defaultSelection(parts)).toEqual({ kind: "section", sectionId: "s-math" });
  });

  it("bài theo phần Khoa học: mở tổng quan Khoa học (phương án chọn môn)", () => {
    const science = part("p-sci", {
      mode: "CHOICE",
      options: [
        {
          id: "opt-sci",
          kind: "SUBJECT_CHOICE",
          code: "SCIENCE",
          sections: [section("s-phy", "PHYSICS", { questionCount: null })],
          chooseSubjects: 3,
          countsByPick: [17, 17, 16],
        },
      ],
    });
    expect(defaultSelection([science])).toEqual({ kind: "option", partId: "p-sci", optionId: "opt-sci" });
  });

  it("phần đầu tiên theo mã: mở môn đầu của danh sách môn", () => {
    const coded = part("p-code", { codeSource: "GLOBAL_LIST", subjectPool: [section("s-phy", "PHYSICS"), section("s-che", "CHEMISTRY")] });
    expect(defaultSelection([coded])).toEqual({ kind: "section", sectionId: "s-phy" });
  });

  it("trang mở bài FULL: Toán của Phần 1 là mục đang mở trong danh sách", async () => {
    openStructure(HSA);
    const nav = await list();
    const current = await within(nav).findByRole("button", { name: /Toán/, current: true });
    expect(current).toBeInTheDocument();
    expect(within(nav).getAllByRole("button", { current: true })).toHaveLength(1);
  });
});

describe("Khung bước 2: vùng danh sách", () => {
  it("huy hiệu trạng thái theo môn từ sectionStates; chữ đi kèm màu", async () => {
    backend.seedStructureExtras(QDA, {
      sectionStates: [
        { sectionId: "sec-41", state: "CHECKED" },
        { sectionId: "sec-35", state: "NEEDS_CHECK" },
      ],
    });
    openStructure(QDA);
    const nav = await list();
    expect(await within(nav).findByText("Đã kiểm tra")).toBeInTheDocument();
    expect(within(nav).getByText("Cần kiểm tra lại")).toBeInTheDocument();
  });

  it("sectionStates null (BE-5 chưa có): không hiện huy hiệu và không lỗi", async () => {
    openStructure(QDA);
    const nav = await list();
    expect(within(nav).queryByText(/Đã kiểm tra|Cần kiểm tra lại|Chưa hoàn tất/)).not.toBeInTheDocument();
    expect(within(nav).getByRole("button", { name: /Phần 1/ })).toBeInTheDocument();
  });

  it("phần có mã: mỗi môn một lần, dưới đó là dòng mã chỉ đọc kèm môn còn thiếu", () => {
    const coded = part("p-code", {
      codeSource: "GLOBAL_LIST",
      subjectPool: [section("s-phy", "PHYSICS"), section("s-che", "CHEMISTRY")],
    });
    renderWithProviders(
      <ExamStructureList
        parts={[coded]}
        combinations={[
          { code: "QD01", examCode: "QD01", partId: "p-code", subjectCodes: ["PHYSICS", "CHEMISTRY"], missingSubjects: [], nameVi: null, nameEn: null },
          { code: "QD02", examCode: "QD02", partId: "p-code", subjectCodes: ["CHEMISTRY", "BIOLOGY"], missingSubjects: ["BIOLOGY"], nameVi: null, nameEn: null },
        ]}
        sectionStates={null}
        selection={null}
        issues={[]}
        names={names}
        onSelect={() => {}}
      />,
    );
    // Mỗi môn đúng một nút, không lặp theo mã.
    expect(screen.getAllByRole("button", { name: /^PHYSICS/ })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: /^CHEMISTRY/ })).toHaveLength(1);
    expect(screen.getByText("Mã bài thi (chỉ đọc)")).toBeInTheDocument();
    const codes = screen.getAllByRole("listitem").filter((item) => /^QD0\d/.test(item.textContent ?? ""));
    expect(codes).toHaveLength(2);
    expect(codes[0]).toHaveTextContent("PHYSICS – CHEMISTRY");
    expect(codes[1]).toHaveTextContent("thiếu môn BIOLOGY");
    // Không có thao tác chọn mã hay gán môn cho mã (R-24).
    expect(screen.queryByRole("button", { name: /QD0/ })).not.toBeInTheDocument();
  });

  it("HSA Khoa học: 'Môn tiếp theo' sang môn kế, 'Về tổng quan' mở tổng quan có nút 'Thiết lập' cho 5 môn", async () => {
    const user = userEvent.setup();
    openStructure(HSA, "?section=sec-sci-1-chemistry");
    const navigation = await screen.findByRole("navigation", { name: /Chuyển môn của/ });
    expect(navigation).toHaveTextContent("môn 2/5");
    await user.click(within(navigation).getByRole("button", { name: "Môn tiếp theo: Sinh học" }));
    await waitFor(() => expect(window.location.search).toContain("section=sec-sci-1-biology"));
    const third = await screen.findByRole("navigation", { name: /Chuyển môn của/ });
    expect(third).toHaveTextContent("môn 3/5");
    await user.click(within(third).getByRole("button", { name: /^Về tổng quan / }));
    await waitFor(() => expect(window.location.search).toContain("option=opt-sci-1"));
    expect(await screen.findAllByRole("button", { name: /^Thiết lập môn / })).toHaveLength(5);
  });
});

describe("Khung bước 2: vùng tổng hợp (R-04)", () => {
  const blockId = () => backend.app(QDA).parts[0]!.sections[0]!.blocks[0]!.id;

  it("đọc structure/summary: 'Đã phân bổ 47/50 câu. Còn thiếu 3 câu.'; bấm dòng chọn đúng môn và nhóm", async () => {
    const user = userEvent.setup();
    backend.seedSummary(QDA, [
      { name: "Phần 1 · Toán học", partId: "part-42", target: 50, allocated: 47, maxPoints: 50 },
      { name: "Toán học", partId: "part-42", sectionId: "sec-41", target: 50, allocated: 47 },
      { name: "Nhóm của Toán", partId: "part-42", sectionId: "sec-41", blockId: blockId(), target: 20, allocated: 17 },
    ]);
    openStructure(QDA, "?part=part-38");
    expect(await screen.findByTestId("structure-headline")).toHaveTextContent("Đã phân bổ 47/50 câu. Còn thiếu 3 câu.");

    await user.click(screen.getByRole("button", { name: "Đến Toán học" }));
    await waitFor(() => expect(window.location.search).toContain("section=sec-41"));
    await user.click(screen.getByRole("button", { name: "Đến Nhóm của Toán" }));
    await waitFor(() => expect(window.location.search).toContain(`block=${blockId()}`));
  });

  it("máy chủ chưa có tổng hợp (501): dùng số trên màn hình, không bảng, không lỗi", async () => {
    openStructure(QDA);
    expect(await screen.findByText(/Chưa có tổng hợp từ máy chủ/)).toBeInTheDocument();
    expect(screen.getByTestId("structure-headline")).toHaveTextContent(/^Đã phân bổ đủ \d+ câu\.$|Đã phân bổ \d+\/\d+ câu/);
    expect(screen.queryByRole("table", { name: /Tổng hợp số câu theo phần/ })).not.toBeInTheDocument();
  });

  it("lỗi cấu trúc trên màn hình nằm ở vùng (d) và dẫn tới chỗ sửa", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?part=part-42");
    const required = await screen.findByRole("textbox", { name: /Số câu phải làm/ });
    await user.clear(required);
    await user.type(required, "7");
    const heading = await screen.findByRole("heading", { name: /Lỗi cần xử lý \([1-9]/ });
    const issues = heading.closest("section")!;
    await user.click(within(issues).getAllByRole("button", { name: "Đến chỗ sửa" })[0]!);
    // Lỗi của chính phần đang sửa: ở lại phần đó, không hỏi chuyển.
    expect(window.location.search).toBe("?part=part-42");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("Khung bước 2: hộp thoại ba nút khi còn thay đổi chưa lưu (R-05)", () => {
  async function makeDirty() {
    const user = userEvent.setup();
    openStructure(QDA, "?part=part-42");
    const points = await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ });
    await user.type(points, "50");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    const nav = await list();
    await user.click(within(nav).getByRole("button", { name: /Phần 2 · Ngữ văn/ }));
    const dialog = await screen.findByRole("dialog", { name: dialogName });
    return { user, nav, dialog };
  }

  it("có ba nút: Ở lại, Bỏ thay đổi, Lưu và chuyển", async () => {
    const { dialog } = await makeDirty();
    expect(within(dialog).getAllByRole("button").map((button) => button.textContent)).toEqual(
      expect.arrayContaining(["Ở lại", "Bỏ thay đổi", "Lưu và chuyển"]),
    );
  });

  it("Ở lại: đóng hộp thoại, vẫn ở mục cũ và giữ giá trị đã sửa", async () => {
    const { user, dialog } = await makeDirty();
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(window.location.search).toContain("part=part-42");
    expect(screen.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("50");
  });

  it("Bỏ thay đổi: về giá trị đã lưu rồi chuyển sang mục mới, không gọi PUT", async () => {
    const { user, nav, dialog } = await makeDirty();
    await user.click(within(dialog).getByRole("button", { name: "Bỏ thay đổi" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(window.location.search).toContain("part=part-38");
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(0);
    // Về lại Phần 1 (không còn thay đổi chưa lưu nên không hỏi): ô điểm về giá trị đã lưu.
    await user.click(within(nav).getByRole("button", { name: /Phần 1/ }));
    expect(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Lưu và chuyển: lưu bằng PUT rồi chuyển sang mục mới", async () => {
    const { user, dialog } = await makeDirty();
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    await waitFor(() => expect(backend.app(QDA)?.parts.find((item) => item.id === "part-42")?.maxPoints).toBe(50));
    await waitFor(() => expect(window.location.search).toContain("part=part-38"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Lưu và chuyển lỗi (422): ở lại, hộp thoại còn và hiện lỗi, giá trị đã sửa không mất", async () => {
    backend = new ExamsBackend();
    backend.server.on("PUT", api(QDA, "structure"), { status: 422, body: { code: "EXAM_STRUCTURE_INVALID", message: "x" } });
    backend.install();
    const { user, dialog } = await makeDirty();
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    expect(await within(dialog).findByRole("alert")).toBeInTheDocument();
    expect(window.location.search).toContain("part=part-42");
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("50");
  });

  it("quay lại bước Thông tin chung ('Sửa thông tin tổng quan') cũng hỏi bằng hộp thoại ba nút; Bỏ thay đổi mới rời trang", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?part=part-42");
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    await user.click(screen.getByRole("link", { name: "Sửa thông tin tổng quan" }));
    const dialog = await screen.findByRole("dialog", { name: dialogName });
    expect(routerMock.push).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Bỏ thay đổi" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "general")));
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(0);
  });

  it("rời trang bằng 'Lưu và chuyển': lưu xong mới chuyển", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?part=part-42");
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    await user.click(screen.getByRole("link", { name: "Sửa thông tin tổng quan" }));
    const dialog = await screen.findByRole("dialog", { name: dialogName });
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "general")));
    expect(backend.app(QDA)?.parts.find((item) => item.id === "part-42")?.maxPoints).toBe(50);
  });

  it("đổi nhóm trong cùng môn không hỏi", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?section=sec-41");
    await screen.findByRole("navigation", { name: "Cấu trúc" });
    // Làm bản nháp bẩn tại môn đang mở rồi mở nhóm khác của cùng môn: cùng phạm vi nên không hỏi (R-05).
    await user.click((await screen.findAllByRole("button", { name: "Khai chỉ tiêu" }))[0]!);
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "Mở nhóm Điền đáp án" }));
    await waitFor(() => expect(window.location.search).toBe("?block=blk-40"));
    expect(screen.queryByRole("dialog", { name: dialogName })).not.toBeInTheDocument();
  });

  it("xóa nhóm đang mở khi bản nháp bẩn: thao tác của người dùng nên không hỏi, mở phần chứa nhóm", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?block=blk-39");
    await user.type(await screen.findByRole("textbox", { name: "Tên nhóm" }), " x");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xóa nhóm Trắc nghiệm x" }));
    const confirm = await screen.findByRole("dialog", { name: /Xóa nhóm Trắc nghiệm x\?/ });
    await user.click(within(confirm).getByRole("button", { name: "Xóa nhóm" }));
    await waitFor(() => expect(window.location.search).toBe("?part=part-42"));
    expect(screen.queryByRole("dialog", { name: dialogName })).not.toBeInTheDocument();
  });
});

describe("Khung bước 2: nút kiểm tra", () => {
  const checkPath = (id: string) => api(id, "check");
  const lastBody = (id: string) => backend.server.calls("POST", checkPath(id)).at(-1)?.body;

  it("'Kiểm tra toàn bài' ở đầu trang gửi phạm vi EXAM và hiện kết quả ở vùng (d)", async () => {
    const user = userEvent.setup();
    openStructure(QDA);
    await user.click(await screen.findByRole("button", { name: "Kiểm tra toàn bài" }));
    await waitFor(() => expect(lastBody(QDA)).toEqual({ scope: "EXAM" }));
    expect(await screen.findByTestId("structure-check-result")).toHaveTextContent("Đạt");
  });

  it("'Kiểm tra môn' ở vùng (c) gửi phạm vi SECTION của môn đang mở; phần thì gửi PART", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?section=sec-41");
    await user.click(await screen.findByRole("button", { name: "Kiểm tra môn" }));
    await waitFor(() => expect(lastBody(QDA)).toEqual({ scope: "SECTION", sectionId: "sec-41" }));
    const nav = await list();
    await user.click(within(nav).getByRole("button", { name: /Phần 1/ }));
    await user.click(await screen.findByRole("button", { name: "Kiểm tra phần" }));
    await waitFor(() => expect(lastBody(QDA)).toEqual({ scope: "PART", partId: "part-42" }));
  });

  it("'Tiếp tục': kiểm tra cả bài, không lỗi thì sang tab Kiểm tra", async () => {
    const user = userEvent.setup();
    openStructure(QDA);
    await user.click(await screen.findByRole("button", { name: "Tiếp tục" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "review")));
    expect(lastBody(QDA)).toEqual({ scope: "EXAM" });
  });

  it("'Tiếp tục': có lỗi thì ở lại và dẫn tới lỗi đầu tiên, lỗi và cảnh báo hiện riêng", async () => {
    const user = userEvent.setup();
    backend.checkReplies.set(HSA, [{ status: 200, body: checkHsa }]);
    openStructure(HSA);
    await user.click(await screen.findByRole("button", { name: "Tiếp tục" }));
    await waitFor(() => expect(window.location.search).toContain("section=sec-sci-1-chemistry"));
    expect(routerMock.push).not.toHaveBeenCalled();
    const result = await screen.findByTestId("structure-check-result");
    expect(within(result).getByTestId("structure-check-errors")).toBeInTheDocument();
  });

  it("còn thay đổi chưa lưu: lưu trước rồi mới kiểm tra", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?part=part-42");
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await waitFor(() => expect(backend.server.calls("POST", checkPath(QDA))).toHaveLength(1));
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(1);
    expect(backend.app(QDA)?.parts.find((item) => item.id === "part-42")?.maxPoints).toBe(50);
  });

  it("nút đầu trang nằm ở hàng tiêu đề của khung, 'Kiểm tra môn' nằm ở đầu vùng chi tiết", async () => {
    const titleRow = document.createElement("div");
    document.body.append(titleRow);
    window.history.replaceState(null, "", `/exams/${QDA}/structure?section=sec-41`);
    renderWithProviders(
      <ExamEditorActionsSlot.Provider value={titleRow}>
        <ExamStructureView examId={QDA} />
      </ExamEditorActionsSlot.Provider>,
    );
    await screen.findByRole("navigation", { name: "Cấu trúc" });
    for (const name of ["Lưu", "Kiểm tra toàn bài", "Tiếp tục"]) {
      expect(within(titleRow).getByRole("button", { name })).toBeInTheDocument();
    }
    expect(within(titleRow).queryByRole("link", { name: "Sửa thông tin tổng quan" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sửa thông tin tổng quan" })).toBeInTheDocument();
    const detailTitle = await screen.findByRole("heading", { level: 2, name: /Toán/ });
    expect(detailTitle.parentElement!.parentElement!).toContainElement(screen.getByRole("button", { name: "Kiểm tra môn" }));
    titleRow.remove();
  });
  it("PUT lưu trước chưa xong thì các nút kiểm tra khóa: click đúp chỉ gửi một PUT", async () => {
    const user = userEvent.setup();
    backend.server.hold("PUT", api(QDA, "structure"));
    openStructure(QDA, "?part=part-42");
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    const checkAll = await screen.findByRole("button", { name: "Kiểm tra toàn bài" });
    await user.click(checkAll);
    // PUT đầu đã ghi nhận nhưng phản hồi còn treo; click tiếp không được phát thêm PUT (cùng expectedRevision sẽ 412).
    await waitFor(() => expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(1));
    await user.click(checkAll);
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(1);
    backend.server.release("PUT", api(QDA, "structure"));
    await waitFor(() => expect(backend.server.calls("POST", checkPath(QDA))).toHaveLength(1));
  });
});
