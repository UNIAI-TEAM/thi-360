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
import { examFixture, ExamsBackend } from "./fake-exams-backend";

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
/** Đường dẫn ngữ cảnh "Sửa thông tin tổng quan" cạnh ô số câu chỉ đọc của phần đang mở (về `/exams/<id>`). */
async function headerEditLink() {
  return (await screen.findAllByRole("link", { name: "Sửa thông tin tổng quan" }))[0]!;
}
const dialogName = "Lưu thay đổi của Phần 1 · Toán học?";

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
    const coded = part("p-code", { codeSource: { source: "GLOBAL_LIST", branch: null }, subjectPool: [section("s-phy", "PHYSICS"), section("s-che", "CHEMISTRY")] });
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
      codeSource: { source: "GLOBAL_LIST", branch: null },
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

  it("UNI-1728 / UNI-2837: BCA hiển thị mã tổ hợp CA1..CA4 đúng thứ tự kể cả khi examCode là mã kỳ thi BCA", () => {
    const coded = part("p-bca", {
      codeSource: { source: "GLOBAL_LIST", branch: null },
      subjectPool: [section("s-ly", "LY"), section("s-hoa", "HOA"), section("s-sinh", "SINH"), section("s-dia", "DIA")],
    });
    renderWithProviders(
      <ExamStructureList
        parts={[coded]}
        combinations={[
          { code: "CA1", examCode: "BCA", partId: "p-bca", subjectCodes: ["LY"], missingSubjects: [], nameVi: "CA1", nameEn: "CA1" },
          { code: "CA2", examCode: "BCA", partId: "p-bca", subjectCodes: ["HOA"], missingSubjects: [], nameVi: "CA2", nameEn: "CA2" },
          { code: "CA3", examCode: "BCA", partId: "p-bca", subjectCodes: ["SINH"], missingSubjects: [], nameVi: "CA3", nameEn: "CA3" },
          { code: "CA4", examCode: "BCA", partId: "p-bca", subjectCodes: ["DIA"], missingSubjects: [], nameVi: "CA4", nameEn: "CA4" },
        ]}
        sectionStates={null}
        selection={null}
        issues={[]}
        names={names}
        onSelect={() => {}}
      />,
    );
    const codeItems = screen.getAllByRole("listitem").filter((item) => /^CA[1-4]/.test(item.textContent ?? ""));
    expect(codeItems.map((item) => item.textContent)).toEqual(["CA1LY", "CA2HOA", "CA3SINH", "CA4DIA"]);
    expect(screen.queryByText("BCA")).not.toBeInTheDocument();
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

  it("đọc structure/summary: thanh trạng thái gọn trên màn Phần/Môn, bấm 'Xem bảng tổng hợp' mở bảng và nhảy tới đúng môn/nhóm", async () => {
    const user = userEvent.setup();
    backend.seedSummary(QDA, [
      {
        name: "Phần 1 · Toán học",
        partId: "part-42",
        target: 50,
        allocated: 47,
        maxPoints: 50,
        children: [
          {
            name: "Toán học",
            partId: "part-42",
            sectionId: "sec-41",
            target: 50,
            allocated: 47,
            children: [
              { name: "Nhóm của Toán", partId: "part-42", sectionId: "sec-41", blockId: blockId(), target: 20, allocated: 17 },
            ],
          },
        ],
      },
    ]);
    openStructure(QDA, "?part=part-38");
    expect(await screen.findByTestId("structure-headline")).toHaveTextContent("Đã phân bổ 47/50 câu. Còn thiếu 3 câu.");
    expect(screen.queryByRole("table", { name: /Tổng hợp số câu theo phần/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xem bảng tổng hợp" }));
    await waitFor(() => expect(window.location.search).toContain("exam=1"));
    await user.click(await screen.findByRole("button", { name: "Đến Toán học" }));
    await waitFor(() => expect(window.location.search).toContain("section=sec-41"));

    await user.click(screen.getByRole("button", { name: "Xem bảng tổng hợp" }));
    await user.click(await screen.findByRole("button", { name: "Đến Nhóm của Toán" }));
    await waitFor(() => expect(window.location.search).toContain(`block=${blockId()}`));
  });

  it("máy chủ trả tổng hợp thập phân: tiêu đề và bảng ở Tổng quan bài hiện 1,25 / 2,5 theo dấu phẩy của vi, tối đa 2 chữ số", async () => {
    backend.seedSummary(QDA, [
      {
        name: "Phần 1 · Toán học",
        partId: "part-42",
        target: 2.5,
        allocated: 1.25,
        difference: -1.25,
        maxPoints: 1.239,
        children: [],
      },
    ]);
    openStructure(QDA, "?exam=1");
    expect(await screen.findByTestId("structure-headline")).toHaveTextContent(
      "Đã phân bổ 1,25/2,5 câu. Còn thiếu 1,25 câu.",
    );
    const table = await screen.findByRole("table", { name: /Tổng hợp số câu theo phần/ });
    const cells = within(table)
      .getAllByRole("cell")
      .map((cell) => cell.textContent);
    expect(cells).toEqual(["2,5", "1,25", "Thiếu 1,25 câu", "1,24"]);
  });

  it("tổng hợp dạng cây của máy chủ: ở Tổng quan bài, dòng phần, môn, nhóm đều thành dòng của bảng", async () => {
    backend.seedSummary(QDA, [
      {
        name: "Phần 1 · Toán học",
        partId: "part-42",
        target: 50,
        allocated: 47,
        children: [
          {
            name: "Toán học",
            partId: "part-42",
            sectionId: "sec-41",
            target: 50,
            allocated: 47,
            children: [
              { name: "Nhóm của Toán", partId: "part-42", sectionId: "sec-41", blockId: blockId(), target: 20, allocated: 17 },
            ],
          },
        ],
      },
    ]);
    openStructure(QDA, "?exam=1");
    const table = await screen.findByRole("table", { name: /Tổng hợp số câu theo phần/ });
    expect(within(table).getAllByRole("row")).toHaveLength(4);
    expect(within(table).getByRole("button", { name: "Đến Nhóm của Toán" })).toBeInTheDocument();
  });

  it("máy chủ lỗi khi đọc tổng hợp: dùng số trên màn hình, không bảng, không lỗi", async () => {
    backend.failSummary(QDA);
    openStructure(QDA);
    expect(await screen.findByText(/Chưa có tổng hợp từ máy chủ/)).toBeInTheDocument();
    expect(screen.getByTestId("structure-headline")).toHaveTextContent(/^Đã phân bổ đủ \d+ câu\.$|Đã phân bổ \d+\/\d+ câu/);
    expect(screen.queryByRole("table", { name: /Tổng hợp số câu theo phần/ })).not.toBeInTheDocument();
  });

  it("máy chủ trả tổng hợp rỗng: dùng số trên màn hình, không bảng", async () => {
    openStructure(QDA);
    expect(await screen.findByTestId("structure-headline")).toBeInTheDocument();
    expect(screen.queryByText(/Chưa có tổng hợp từ máy chủ/)).not.toBeInTheDocument();
    expect(screen.queryByRole("table", { name: /Tổng hợp số câu theo phần/ })).not.toBeInTheDocument();
  });

  it("lỗi cấu trúc trên màn hình nằm ở vùng (d) và dẫn tới chỗ sửa", async () => {
    const user = userEvent.setup();
    // Số câu của phần chỉ đọc ở bước 2 (UNI-2753): gây lỗi bằng "Số phương án phải chọn", ô vẫn sửa được.
    openStructure(QDA, "?part=part-49");
    const choose = await screen.findByRole("textbox", { name: "Số phương án phải chọn" });
    await user.clear(choose);
    await user.type(choose, "9");
    const heading = await screen.findByRole("heading", { name: /Lỗi cần xử lý \([1-9]/ });
    const issues = heading.closest("section")!;
    await user.click(within(issues).getAllByRole("button", { name: "Đến vị trí cần sửa" })[0]!);
    // Lỗi của chính phần đang sửa: ở lại phần đó, không hỏi chuyển.
    expect(window.location.search).toBe("?part=part-49");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("Khung bước 2: giữ nháp khi đổi mục và hộp thoại ba nút khi rời bước (UNI-1720)", () => {
  async function makeDirtyAndLeave() {
    const user = userEvent.setup();
    openStructure(QDA, "?part=part-42");
    const points = await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ });
    await user.type(points, "50");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.click(await headerEditLink());
    const dialog = await screen.findByRole("dialog", { name: dialogName });
    return { user, dialog };
  }

  it("đổi giữa các phần/môn trong bước 2 không hỏi và giữ nguyên thay đổi chưa lưu", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?part=part-42");
    const points = await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ });
    await user.type(points, "50");
    const nav = await list();
    await user.click(within(nav).getByRole("button", { name: /Phần 2 · Ngữ văn/ }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(window.location.search).toContain("part=part-38");
    await user.click(within(nav).getByRole("button", { name: /Phần 1/ }));
    expect(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("50");
  });

  it("có ba nút: Ở lại, Bỏ thay đổi và chuyển, Lưu và chuyển", async () => {
    const { dialog } = await makeDirtyAndLeave();
    expect(within(dialog).getAllByRole("button").map((button) => button.textContent)).toEqual(
      expect.arrayContaining(["Ở lại", "Bỏ thay đổi và chuyển", "Lưu và chuyển"]),
    );
  });

  it("tên mục đang mở ở tiêu đề, đích đến ở câu đầu, danh sách ghi thay đổi", async () => {
    const { dialog } = await makeDirtyAndLeave();
    expect(within(dialog).getByText(/Bạn đang quay lại Thông tin chung\./)).toBeInTheDocument();
    expect(within(dialog).getByTestId("structure-leave-changes")).toHaveTextContent("Phần 1 · Toán học: điểm tối đa chưa đặt → 50.");
  });

  it("Ở lại: đóng hộp thoại, vẫn ở mục cũ và giữ giá trị đã sửa", async () => {
    const { user, dialog } = await makeDirtyAndLeave();
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(window.location.search).toContain("part=part-42");
    expect(screen.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("50");
  });

  it("Bỏ thay đổi và chuyển: rời trang, không gọi PUT", async () => {
    const { user, dialog } = await makeDirtyAndLeave();
    await user.click(within(dialog).getByRole("button", { name: "Bỏ thay đổi và chuyển" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "general")));
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(0);
  });

  it("Lưu và chuyển: lưu bằng PUT rồi chuyển sang trang đích", async () => {
    const { user, dialog } = await makeDirtyAndLeave();
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    await waitFor(() => expect(backend.app(QDA)?.parts.find((item) => item.id === "part-42")?.maxPoints).toBe(50));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "general")));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Lưu và chuyển lỗi (422): ở lại, hộp thoại còn và hiện lỗi, giá trị đã sửa không mất", async () => {
    backend = new ExamsBackend();
    backend.server.on("PUT", api(QDA, "structure"), { status: 422, body: { code: "EXAM_STRUCTURE_INVALID", message: "x" } });
    backend.install();
    const { user, dialog } = await makeDirtyAndLeave();
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    expect(await within(dialog).findByRole("alert")).toBeInTheDocument();
    expect(window.location.search).toContain("part=part-42");
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("50");
  });

  it("rời Cấu trúc sang tab 3. Thông tin bán: hộp thoại gọi đúng tên tab và 'Bỏ thay đổi và chuyển' chuyển tới /listing", async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${QDA}/structure?part=part-42`);
    renderWithProviders(
      <>
        <ExamStructureView examId={QDA} />
        <a href={examTabHref(QDA, "listing")}>tab-listing</a>
      </>,
    );
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    await user.click(screen.getByRole("link", { name: "tab-listing" }));
    const dialog = await screen.findByRole("dialog", { name: dialogName });
    expect(within(dialog).getByText(/Bạn đang chuyển sang tab 3\. Thông tin bán\./)).toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Bỏ thay đổi và chuyển" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${QDA}/listing`));
  });

  it("rời Cấu trúc sang tab Thông tin chung: hộp thoại gọi destination.general và 'Bỏ thay đổi và chuyển' chuyển tới /exams/[id]", async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${QDA}/structure?part=part-42`);
    renderWithProviders(
      <>
        <ExamStructureView examId={QDA} />
        <a href={examTabHref(QDA, "general")}>tab-general</a>
      </>,
    );
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    await user.click(screen.getByRole("link", { name: "tab-general" }));
    const dialog = await screen.findByRole("dialog", { name: dialogName });
    expect(within(dialog).getByText(/Bạn đang quay lại Thông tin chung\./)).toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Bỏ thay đổi và chuyển" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "general")));
  });

  it("đổi nhóm trong cùng môn không hỏi", async () => {
    const user = userEvent.setup();
    openStructure(QDA, "?section=sec-41");
    await screen.findByRole("navigation", { name: "Cấu trúc" });
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

describe("Khung bước 2: kiểm tra cấu trúc và lưu nháp (UNI-1720)", () => {
  const checkPath = (id: string) => api(id, "check");

  it("'Kiểm tra cấu trúc' chạy tại chỗ không gọi PUT hay POST /check và báo Cấu trúc hợp lệ", async () => {
    const user = userEvent.setup();
    openStructure(QDA);
    await user.click(await screen.findByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(await screen.findByTestId("structure-quick-check-result")).toHaveTextContent("Cấu trúc hợp lệ");
    expect(backend.server.calls("POST", checkPath(QDA))).toHaveLength(0);
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(0);
  });

  it("'Tiếp tục → Thông tin bán': bản sạch sang thẳng bước 3 không gọi PUT; bản bẩn lưu 1 lần rồi sang bước 3", async () => {
    const user = userEvent.setup();
    const first = openStructure(QDA);
    await user.click(await screen.findByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "listing"));
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(0);
    first.unmount();

    routerMock.push.mockReset();
    openStructure(QDA, "?part=part-42");
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(1));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "listing")));
    expect(backend.app(QDA)?.parts.find((item) => item.id === "part-42")?.maxPoints).toBe(50);
  });

  it("nút thao tác nằm ở thanh công cụ đầu khung Master-Detail", async () => {
    const titleRow = document.createElement("div");
    document.body.append(titleRow);
    window.history.replaceState(null, "", `/exams/${QDA}/structure?section=sec-41`);
    renderWithProviders(
      <ExamEditorActionsSlot.Provider value={titleRow}>
        <ExamStructureView examId={QDA} />
      </ExamEditorActionsSlot.Provider>,
    );
    await screen.findByRole("navigation", { name: "Cấu trúc" });
    const toolbar = screen.getByTestId("structure-toolbar");
    for (const name of ["Quay lại", "Lưu nháp và thoát", "Kiểm tra cấu trúc", "Tiếp tục → Thông tin bán"]) {
      expect(within(toolbar).getByRole("button", { name })).toBeInTheDocument();
      expect(within(titleRow).queryByRole("button", { name })).not.toBeInTheDocument();
    }
    expect(within(titleRow).queryByRole("link", { name: "Sửa thông tin tổng quan" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Tổng hợp cấu trúc")).not.toBeInTheDocument();
    titleRow.remove();
  });

  it("PUT lưu chưa xong thì các nút lưu khóa: click đúp chỉ gửi một PUT", async () => {
    const user = userEvent.setup();
    backend.server.hold("PUT", api(QDA, "structure"));
    openStructure(QDA, "?part=part-42");
    await user.type(await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ }), "50");
    const nextBtn = await screen.findByRole("button", { name: "Tiếp tục → Thông tin bán" });
    await user.click(nextBtn);
    await waitFor(() => expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(1));
    await user.click(nextBtn);
    await user.click(screen.getByRole("button", { name: "Lưu nháp và thoát" }));
    expect(backend.server.calls("PUT", api(QDA, "structure"))).toHaveLength(1);
    backend.server.release("PUT", api(QDA, "structure"));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(examTabHref(QDA, "listing")));
  });
});

describe("Khung bước 2: đọc lựa chọn từ URL (?exam=)", () => {
  function seedExam1(over: Partial<ReturnType<typeof examFixture>> = {}) {
    const base = examFixture(HSA);
    backend.seedExam({ ...base, ...over, id: "exam-1" });
  }

  it("?exam=<uuid> (chuyển trang từ /exams/new?exam=<uuid>): mở môn mặc định, không mở Tổng quan bài", async () => {
    seedExam1();
    window.history.replaceState(null, "", "/exams/exam-1/structure?exam=01a1f9da-798b-7c95-83ed-0deb9e963179");
    renderWithProviders(<ExamStructureView examId="exam-1" />);
    expect(await screen.findByRole("heading", { level: 2, name: /^Môn / })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 2, name: /^Tổng quan bài · / })).not.toBeInTheDocument();
  });

  it("?exam=<uuid> trên bài ôn (buildMode: REVIEW): mở Bài ôn, không mở Tổng quan bài", async () => {
    const base = examFixture("ex-hsa-l-toan-001");
    const firstPart = base.parts[0]!;
    const firstSection = firstPart.sections[0]!;
    const firstBlock = { ...firstSection.blocks[0]!, questionType: "SINGLE_CHOICE" as const, questionCount: firstPart.questionCount };
    seedExam1({
      ...base,
      scope: "SUBJECT",
      partCode: null,
      subjectCode: "MATH",
      buildMode: "REVIEW",
      parts: [{ ...firstPart, sections: [{ ...firstSection, blocks: [firstBlock] }] }],
    });
    window.history.replaceState(null, "", "/exams/exam-1/structure?exam=01a1f9da-798b-7c95-83ed-0deb9e963179");
    renderWithProviders(<ExamStructureView examId="exam-1" />);
    expect(await screen.findByRole("heading", { level: 2, name: /^Bài ôn · / })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 2, name: /^Tổng quan bài · / })).not.toBeInTheDocument();
  });

  it("?exam=1: vẫn mở Tổng quan bài", async () => {
    seedExam1();
    window.history.replaceState(null, "", "/exams/exam-1/structure?exam=1");
    renderWithProviders(<ExamStructureView examId="exam-1" />);
    expect(await screen.findByRole("heading", { level: 2, name: /^Tổng quan bài · / })).toBeInTheDocument();
  });
});
