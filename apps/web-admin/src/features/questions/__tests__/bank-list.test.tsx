import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { QuestionBankView } from "../components/question-bank-view";
import { questionStore } from "../mock/store";

const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/questions",
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

/** Dai "N cau hoi khop bo loc" (LiveStatus cung doc cau nay nen co the co hai phan tu). */
async function expectCount(text: string) {
  await waitFor(() =>
    expect(screen.getAllByText(/câu hỏi khớp bộ lọc/).some((element) => element.textContent === text)).toBe(true),
  );
}

/** So cau mau tinh tu kho (cac nhanh khac co the them cau mau vao kho). */
function countWhere(predicate: (question: ReturnType<typeof questionStore.all>[number]) => boolean = () => true): number {
  return questionStore.all().filter(predicate).length;
}

function setUrl(search: string) {
  window.history.replaceState(null, "", `/questions${search}`);
}

beforeEach(() => setUrl(""));

afterEach(() => {
  questionStore.reset();
  routerMock.push.mockReset();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setUrl("");
});

describe("QuestionBankView", () => {
  it("hien danh sach, so cau theo trang thai, dai du lieu mau va lien ket tao/nhap", async () => {
    renderWithProviders(<QuestionBankView />);

    expect(await screen.findByRole("heading", { level: 1, name: "Ngân hàng câu hỏi" })).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Dữ liệu mẫu.");
    expect(screen.getByRole("link", { name: "Tạo câu hỏi" })).toHaveAttribute("href", "/questions/new");
    expect(screen.getByRole("link", { name: "Nhập từ tệp" })).toHaveAttribute("href", "/questions/import");

    const table = await screen.findByRole("table", { name: "Danh sách câu hỏi khớp bộ lọc" });
    await waitFor(() => expect(within(table).getAllByRole("row")).toHaveLength(21));
    const total = countWhere();
    await expectCount(`${total} câu hỏi khớp bộ lọc`);

    const statusGroup = screen.getByRole("group", { name: "Trạng thái" });
    await waitFor(() =>
      expect(within(statusGroup).getByRole("radio", { name: new RegExp(`Tất cả\\s*${total}`) })).toBeChecked(),
    );

    // Cau Nhap thieu kien thuc chinh: dong goi y vi tri can sua.
    expect(screen.getAllByText("Chưa chọn kiến thức chính.").length).toBeGreaterThan(0);
  });

  it("loc theo trang thai ghi len URL va giu tham so khac", async () => {
    setUrl("?mockState=data");
    renderWithProviders(<QuestionBankView />);
    await screen.findByRole("table", { name: "Danh sách câu hỏi khớp bộ lọc" });

    await userEvent.click(screen.getByRole("radio", { name: /^Nháp/ }));
    await waitFor(() => expect(window.location.search).toContain("status=DRAFT"));
    expect(window.location.search).toContain("mockState=data");
    await expectCount(`${countWhere((question) => question.status === "DRAFT")} câu hỏi khớp bộ lọc`);
    // Chip "Dang loc" bo duoc dieu kien.
    await userEvent.click(screen.getByRole("button", { name: "Bỏ lọc Trạng thái: Nháp" }));
    await waitFor(() => expect(window.location.search).not.toContain("status="));
  });

  it("doc bo loc tu URL khi mo trang", async () => {
    setUrl("?subject=PHYSICS&status=READY");
    renderWithProviders(<QuestionBankView />);
    const physicsReady = countWhere((question) => question.subjectCode === "PHYSICS" && question.status === "READY");
    await expectCount(`${physicsReady} câu hỏi khớp bộ lọc`);
    expect(screen.getByRole("radio", { name: /^Sẵn sàng/ })).toBeChecked();
  });

  it("trang thai rong theo ?mockState=empty", async () => {
    setUrl("?mockState=empty");
    renderWithProviders(<QuestionBankView />);
    expect(await screen.findByText("Ngân hàng chưa có câu hỏi")).toBeInTheDocument();
  });

  it("trang thai loi theo ?mockState=error co nut Thu lai", async () => {
    setUrl("?mockState=error");
    renderWithProviders(<QuestionBankView />);
    expect(await screen.findByText("Không tải được ngân hàng câu hỏi")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("menu dong: chuyen cau du dieu kien sang San sang", async () => {
    setUrl("?status=DRAFT&subject=MATH&grade=GRADE_11");
    renderWithProviders(<QuestionBankView />);
    await screen.findByRole("table", { name: "Danh sách câu hỏi khớp bộ lọc" });
    await userEvent.click(await screen.findByRole("button", { name: "Thao tác khác với câu hỏi CH-000002" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Chuyển sang Sẵn sàng" }));

    const dialog = await screen.findByRole("dialog", { name: "Chuyển câu hỏi sang Sẵn sàng" });
    await userEvent.click(await within(dialog).findByRole("button", { name: "Chuyển sang Sẵn sàng" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(questionStore.require("q-0002").status).toBe("READY");
  });

  it("chon nhieu dong va ngung su dung hang loat", async () => {
    setUrl("?status=READY&subject=MATH&grade=GRADE_11");
    renderWithProviders(<QuestionBankView />);
    await screen.findByRole("table", { name: "Danh sách câu hỏi khớp bộ lọc" });
    await userEvent.click(await screen.findByRole("checkbox", { name: "Chọn câu hỏi CH-000001" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn câu hỏi CH-000003" }));

    const bar = screen.getByRole("group", { name: "Thao tác hàng loạt" });
    expect(bar).toHaveTextContent("Đã chọn 2 câu");
    await userEvent.click(within(bar).getByRole("button", { name: "Ngừng sử dụng" }));

    const dialog = await screen.findByRole("dialog", { name: "Ngừng sử dụng 2 câu?" });
    expect(dialog).toHaveTextContent("CH-000001, CH-000003");
    await userEvent.click(within(dialog).getByRole("button", { name: "Ngừng sử dụng" }));
    await waitFor(() => expect(questionStore.require("q-0001").status).toBe("RETIRED"));
    expect(questionStore.require("q-0003").status).toBe("RETIRED");
  });

  it("khung hep hien danh sach the", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    setUrl("?q=CH-000001");
    renderWithProviders(<QuestionBankView />);
    const link = await screen.findByRole("link", { name: "Tính giới hạn lim (2n + 1)/(n + 3)." });
    expect(link).toHaveAttribute("href", "/questions/q-0001");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});
