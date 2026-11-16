import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetQuestionHistoryForTests } from "../editor-api.mock";
import { QuestionEditorView } from "../components/question-editor-view";
import { questionStore } from "../mock/store";
import { renderEn, seedQuestion, storedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
});

afterEach(() => {
  questionStore.reset();
  resetQuestionHistoryForTests();
});

function seedEssay(gradingGuide: string) {
  return seedQuestion("ESSAY", (values) => {
    values.points = "2";
    values.body.gradingGuide = gradingGuide;
  });
}

describe("trinh soan Tu luan: diem toi da theo so it/so nhieu (en)", () => {
  it("1 diem la 'point', 1.5 va 2 la 'points'", async () => {
    for (const [points, text] of [
      ["1", "Maximum 1 point (the question's Points field)."],
      ["1.5", "Maximum 1.5 points (the question's Points field)."],
      ["2", "Maximum 2 points (the question's Points field)."],
    ] as const) {
      const id = await seedQuestion("ESSAY", (values) => {
        values.points = points;
        values.body.gradingGuide = "Guide";
      });
      const { unmount } = renderEn(<QuestionEditorView mode="edit" questionId={id} />);
      expect(await screen.findByText(text)).toBeInTheDocument();
      unmount();
    }
  });
});

describe("trinh soan Tu luan: huong dan cham bat buoc", () => {
  it("de trong hoac chi co khoang trang thi chan luu va danh dau o; nhap xong luu duoc khong can tieu chi", async () => {
    const user = userEvent.setup();
    const id = await seedEssay("Cho diem theo y");
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const guide = await screen.findByRole("textbox", { name: /^Grading guide/ });
    expect(guide).toHaveValue("Cho diem theo y");
    expect(guide).toBeRequired();

    await user.clear(guide);
    await user.type(guide, "   ");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByText("Enter the grading guide.")).toBeInTheDocument();
    expect(guide).toHaveAttribute("aria-invalid", "true");
    await waitFor(() => expect(guide).toHaveFocus());
    expect(navigation.push).not.toHaveBeenCalled();
    expect(storedQuestion(id).body).toMatchObject({ gradingGuide: "Cho diem theo y" });

    await user.clear(guide);
    await user.type(guide, "  Diem noi dung 1, trinh bay 1  ");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    // Tiêu chí chấm là tùy chọn (D20): không có tiêu chí vẫn lưu được; điểm tối đa là điểm của câu.
    expect(storedQuestion(id)).toMatchObject({
      points: 2,
      body: { type: "ESSAY", gradingGuide: "Diem noi dung 1, trinh bay 1", rubric: [], wordLimit: null },
    });
  });

  it("cau tu luan moi: luu nhap khi chua co huong dan cham bao loi ngay tai o", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="ESSAY" />);
    expect(await screen.findByText("Graded by hand")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("Enter the grading guide.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Grading guide/ })).toHaveAttribute("aria-invalid", "true");
    expect(navigation.push).not.toHaveBeenCalled();
  });

  it("so chu goi y khong phai so nguyen duong thi bao loi, de trong thi khong gioi han", async () => {
    const user = userEvent.setup();
    const id = await seedEssay("Cho diem theo y");
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const limit = await screen.findByRole("textbox", { name: /^Suggested length/ });
    await user.type(limit, "abc");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByText("The length must be a whole number greater than 0.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();

    await user.clear(limit);
    await user.type(limit, "500");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalled());
    expect(storedQuestion(id).body).toMatchObject({ wordLimit: 500 });
  });
});
