import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { GradingListView } from "../components/grading-list-view";
import { attemptStore } from "../mock/store";

const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/grading",
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

/** Dai "N luot thi khop bo loc" (LiveStatus cung doc cau nay nen co the co hai phan tu). */
async function expectCount(text: string) {
  await waitFor(() =>
    expect(screen.getAllByText(/lượt thi khớp bộ lọc/).some((element) => element.textContent === text)).toBe(true),
  );
}

function setUrl(search: string) {
  window.history.replaceState(null, "", `/grading${search}`);
}

beforeEach(() => setUrl(""));

afterEach(() => {
  attemptStore.reset();
  routerMock.push.mockReset();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setUrl("");
});

describe("GradingListView", () => {
  it("hien danh sach, so luot theo trang thai, dai du lieu mau", async () => {
    renderWithProviders(<GradingListView />);

    expect(await screen.findByRole("heading", { level: 1, name: "Chấm tự luận" })).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Dữ liệu mẫu.");

    const table = await screen.findByRole("table", { name: "Danh sách lượt thi cần chấm khớp bộ lọc" });
    await waitFor(() => expect(within(table).getAllByRole("row")).toHaveLength(12));
    await expectCount("11 lượt thi khớp bộ lọc");

    const statusGroup = screen.getByRole("group", { name: "Trạng thái" });
    await waitFor(() => expect(within(statusGroup).getByRole("radio", { name: /Tất cả\s*11/ })).toBeChecked());
    expect(within(statusGroup).getByRole("radio", { name: /Đang chấm\s*7/ })).toBeInTheDocument();
    expect(within(statusGroup).getByRole("radio", { name: /Đã có điểm\s*4/ })).toBeInTheDocument();

    // Luot da co diem: tong = trac nghiem + tu luan; luot dang cham: chua co diem tu luan.
    const scoredRow = within(table).getByRole("link", { name: "Vũ Quốc Bảo" }).closest("tr")!;
    expect(scoredRow).toHaveTextContent("58,25/100");
    expect(scoredRow).toHaveTextContent("Trắc nghiệm 47,25 + tự luận 11");
    const gradingRow = within(table).getByRole("link", { name: "Phạm Gia Huy" }).closest("tr")!;
    expect(gradingRow).toHaveTextContent("Tự luận chưa có điểm");
    expect(within(gradingRow).getByRole("link", { name: "Phạm Gia Huy" })).toHaveAttribute(
      "href",
      "/grading/att-bca-001",
    );

    await userEvent.click(within(gradingRow).getByRole("button", { name: "Chấm bài của Phạm Gia Huy" }));
    expect(routerMock.push).toHaveBeenCalledWith("/grading/att-bca-001");
  });

  it("loc theo trang thai ghi len URL, giu tham so khac va bo duoc bang chip", async () => {
    setUrl("?mockState=data");
    renderWithProviders(<GradingListView />);
    await screen.findByRole("table", { name: "Danh sách lượt thi cần chấm khớp bộ lọc" });

    await userEvent.click(screen.getByRole("radio", { name: /^Đã có điểm/ }));
    await waitFor(() => expect(window.location.search).toContain("status=SCORED"));
    expect(window.location.search).toContain("mockState=data");
    await expectCount("4 lượt thi khớp bộ lọc");

    await userEvent.click(screen.getByRole("button", { name: "Bỏ lọc Trạng thái: Đã có điểm" }));
    await waitFor(() => expect(window.location.search).not.toContain("status="));
  });

  it("doc bo loc ky thi va nguoi cham tu URL", async () => {
    setUrl("?kind=SPT&grader=UNASSIGNED");
    renderWithProviders(<GradingListView />);
    await expectCount("1 lượt thi khớp bộ lọc");
    expect(screen.getByRole("link", { name: "Bùi Khánh Linh" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Đang lọc" })).toHaveTextContent("Người chấm: Chưa có người chấm");
  });

  it("trang thai rong theo ?mockState=empty", async () => {
    setUrl("?mockState=empty");
    renderWithProviders(<GradingListView />);
    expect(await screen.findByText("Chưa có lượt thi cần chấm")).toBeInTheDocument();
  });

  it("trang thai loi theo ?mockState=error co nut Thu lai", async () => {
    setUrl("?mockState=error");
    renderWithProviders(<GradingListView />);
    expect(await screen.findByText("Không tải được danh sách lượt thi")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("trang thai dang tai theo ?mockState=loading", async () => {
    setUrl("?mockState=loading");
    renderWithProviders(<GradingListView />);
    expect(await screen.findByText("Đang tải danh sách lượt thi…")).toBeInTheDocument();
  });

  it("khung hep hien danh sach the", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    setUrl("?q=HV-001047");
    renderWithProviders(<GradingListView />);
    const link = await screen.findByRole("link", { name: "Vũ Quốc Bảo" });
    expect(link).toHaveAttribute("href", "/grading/att-bca-003");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});
