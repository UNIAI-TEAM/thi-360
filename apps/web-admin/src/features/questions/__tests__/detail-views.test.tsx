import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fail, fakeApi } from "@/test/fake-api";
import { QuestionDetailContent } from "../components/question-detail-content";
import { QuestionDetailLayout, questionTabFromPathname } from "../components/question-detail-layout";
import { QuestionUsageView } from "../components/question-usage-view";
import { QuestionVersionsView } from "../components/question-versions-view";
import { questionStore, renderEn } from "./editor-test-kit";
import { apiReferenceFromFixture } from "./api-fixtures";

const navigation = vi.hoisted(() => ({ pathname: "/questions/q-0003", push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => navigation.pathname,
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));



beforeEach(() => {
  window.history.replaceState(null, "", "/");
  navigation.pathname = "/questions/q-0003";
  navigation.push.mockReset();
  questionStore.server().setUsage("q-0003", {
    examIds: ["ex-1", "ex-2", "ex-3"],
    ledger: [{ examId: "ex-1", kind: "ATTEMPT", versionNo: 1 }],
  });
});

afterEach(() => {
  window.history.replaceState(null, "", "/");

});

describe("chi tiết câu hỏi", () => {
  it("tìm tab theo đường dẫn", () => {
    expect(questionTabFromPathname("/questions/q-1")).toBe("content");
    expect(questionTabFromPathname("/questions/q-1/usage")).toBe("usage");
    expect(questionTabFromPathname("/questions/q-1/versions")).toBe("versions");
  });

  it("hiện tiêu đề, trạng thái theo phiên bản, thao tác và tab đang mở", async () => {
    renderEn(
      <QuestionDetailLayout questionId="q-0003">
        <QuestionDetailContent questionId="q-0003" />
      </QuestionDetailLayout>,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Question CH-000003" })).toBeInTheDocument();
    expect(screen.getByText(/· v2$/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Edit/ })).toHaveAttribute("href", "/questions/q-0003/edit");
    expect(screen.getByRole("button", { name: /Duplicate/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Retire/ })).toBeInTheDocument();
    const tabs = screen.getByRole("navigation", { name: "Question details" });
    expect(within(tabs).getByRole("link", { name: "Content" })).toHaveAttribute("aria-current", "page");
    expect(within(tabs).getByRole("link", { name: /Version history/ })).toHaveAttribute("href", "/questions/q-0003/versions");
    expect(await screen.findByRole("heading", { name: "Question and answers" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Details" })).toBeInTheDocument();
  });

  it("giao diện en hiện tên kiến thức tiếng Anh từ dữ liệu tham chiếu, không phải tên tiếng Việt lưu trong câu (F-01)", async () => {
    const reference = apiReferenceFromFixture("en");
    const rename = (nodes: typeof reference.knowledgeTree): void => {
      for (const node of nodes) {
        if (node.id === "kn-1D4-3-1") node.name = "Continuous functions at a point";
        rename(node.children);
      }
    };
    rename(reference.knowledgeTree);
    renderEn(
      <QuestionDetailLayout questionId="q-0003">
        <QuestionDetailContent questionId="q-0003" />
      </QuestionDetailLayout>,
      reference,
    );
    expect(await screen.findByText("1D4-3-1 · Continuous functions at a point")).toBeInTheDocument();
    expect(screen.queryByText(/Hàm số liên tục tại một điểm/)).not.toBeInTheDocument();
  });

  it("câu không tồn tại thì báo không tìm thấy", async () => {
    renderEn(
      <QuestionDetailLayout questionId="q-missing">
        <p>content</p>
      </QuestionDetailLayout>,
    );
    expect(await screen.findByText("Question not found")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Question bank" })).toHaveAttribute("href", "/questions");
    expect(screen.queryByText("content")).not.toBeInTheDocument();
  });

  it("lỗi tải thì có nút thử lại", async () => {
    fakeApi.get("/api/v1/admin/questions/{id}", fail(500, { code: "INTERNAL" }));
    renderEn(
      <QuestionDetailLayout questionId="q-0003">
        <p>content</p>
      </QuestionDetailLayout>,
    );
    expect(await screen.findByText("Could not load the question")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });

  it("nhân bản mở trang sửa của bản sao", async () => {
    const user = userEvent.setup();
    renderEn(
      <QuestionDetailLayout questionId="q-0003">
        <p>content</p>
      </QuestionDetailLayout>,
    );
    await user.click(await screen.findByRole("button", { name: /Duplicate/ }));
    await vi.waitFor(() => expect(navigation.push).toHaveBeenCalledWith(expect.stringMatching(/^\/questions\/q-.+\/edit$/)));
  });
});

describe("nơi đang dùng", () => {
  it("liệt kê bài thi kèm lượt thi theo phiên bản", async () => {
    renderEn(<QuestionUsageView questionId="q-0003" />);
    expect(await screen.findByText(/^Used in 3 exams/)).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Exams using this question" });
    expect(within(table).getAllByRole("row").length).toBeGreaterThan(1);
  });

  it("hiện phần, lựa chọn, khối và trạng thái bài từ dòng nguồn", async () => {
    questionStore.server().setUsage("q-0003", {
      examIds: [],
      blocks: [
        { examId: "ex-1", examCode: "HSA-01", examName: "HSA mẫu 1", examStatus: "PUBLISHED", partName: "Phần 3", optionName: "Tổ hợp A00", blockName: "Khối Lý" },
      ],
    });
    renderEn(<QuestionUsageView questionId="q-0003" />);
    expect(await screen.findByText("HSA mẫu 1")).toBeInTheDocument();
    expect(screen.getByText(/Phần 3 › Tổ hợp A00 ›.*› Khối Lý/)).toBeInTheDocument();
    expect(screen.getByText("HSA-01")).toBeInTheDocument();
  });

  it("câu chưa dùng thì hiện trạng thái trống", async () => {
    renderEn(<QuestionUsageView questionId="q-0002" />);
    expect(await screen.findByText("Not used in any exam")).toBeInTheDocument();
  });
});

describe("lịch sử phiên bản", () => {
  it("liệt kê phiên bản mới nhất trước và so sánh với bản trước", async () => {
    renderEn(<QuestionVersionsView questionId="q-0021" />);
    expect(await screen.findByRole("heading", { name: "Compare v2 → v3" })).toBeInTheDocument();
    expect(screen.getAllByText("Ready").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Superseded").length).toBe(2);
  });

  it("chọn so sánh bản khác thì đổi khối so sánh", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionVersionsView questionId="q-0021" />);
    await user.click(await screen.findByRole("button", { name: "Compare v1 with v3" }));
    expect(await screen.findByRole("heading", { name: "Compare v1 → v3" })).toBeInTheDocument();
  });

  it("bản nháp đang sửa nằm trên bản hiện hành", async () => {
    renderEn(<QuestionVersionsView questionId="q-0006" />);
    expect(await screen.findByRole("link", { name: /Continue editing/ })).toHaveAttribute("href", "/questions/q-0006/edit");
    expect(screen.getAllByText("Draft").length).toBeGreaterThan(0);
  });
});
