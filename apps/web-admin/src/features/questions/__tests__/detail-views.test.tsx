import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { resetQuestionHistoryForTests } from "../editor-api";
import { QuestionDetailContent } from "../components/question-detail-content";
import { QuestionDetailLayout, questionTabFromPathname } from "../components/question-detail-layout";
import { QuestionUsageView } from "../components/question-usage-view";
import { QuestionVersionsView } from "../components/question-versions-view";
import { questionStore } from "../mock/store";

const navigation = vi.hoisted(() => ({ pathname: "/questions/q-0003", push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => navigation.pathname,
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

function renderEn(ui: ReactElement) {
  return renderWithProviders(ui, { locale: "en", messages: en });
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  navigation.pathname = "/questions/q-0003";
  navigation.push.mockReset();
});

afterEach(() => {
  window.history.replaceState(null, "", "/");
  questionStore.reset();
  resetQuestionHistoryForTests();
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
    window.history.replaceState(null, "", "/?mockState=error");
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

  it("câu chưa dùng thì hiện trạng thái trống", async () => {
    renderEn(<QuestionUsageView questionId="q-0002" />);
    expect(await screen.findByText("Not used in any exam")).toBeInTheDocument();
  });

  it("kịch bản empty cũng hiện trạng thái trống", async () => {
    window.history.replaceState(null, "", "/?mockState=empty");
    renderEn(<QuestionUsageView questionId="q-0003" />);
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
