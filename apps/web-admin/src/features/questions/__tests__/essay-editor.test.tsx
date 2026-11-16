import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QuestionDetailContent } from "../components/question-detail-content";
import { QuestionDetailLayout } from "../components/question-detail-layout";
import { QuestionEditorView } from "../components/question-editor-view";
import { renderEn, rich } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ pathname: "/questions/new", push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => navigation.pathname,
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  navigation.push.mockReset();
});

describe("soan cau Tu luan", () => {
  it("tieu chi tuy chon, co thi tong diem phai khop diem cua cau; huong dan cham bat buoc", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="ESSAY" />);
    const section = (await screen.findByRole("heading", { name: "Grading guide" })).closest("section");
    if (!section) throw new Error("no grading section");
    expect(within(section).getByText("Graded by hand")).toBeInTheDocument();
    // Tiêu chí chấm là tùy chọn (D20): biểu mẫu mới chưa có tiêu chí, hướng dẫn chấm là bắt buộc.
    expect(within(section).queryByText(/Criteria total/)).not.toBeInTheDocument();

    await user.click(within(section).getByRole("button", { name: "Add criterion" }));
    const first = within(section).getByRole("textbox", { name: "Criterion 1 description" });
    expect(first).toHaveFocus();
    rich(first).set("<p>Lap luan</p>");
    await user.type(within(section).getByRole("textbox", { name: "Criterion 1 points" }), "0,5");
    expect(within(section).getByText("Criteria total 0.5 / 1: does not match the question points yet")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("The criteria must add up to the question points.")).toBeInTheDocument();
    expect(screen.getByText("Enter the grading guide.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
  });

  it("xoa tieu chi va doi thu tu bang nut", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="edit" questionId="q-8001" />);
    const first = await screen.findByRole("textbox", { name: "Criterion 1 description" });
    expect(rich(first).text).toBe("Đảm bảo hình thức đoạn văn khoảng 200 chữ");
    await user.click(screen.getByRole("button", { name: "Move criterion 1 down" }));
    expect(rich(screen.getByRole("textbox", { name: "Criterion 2 description" })).text).toBe(
      "Đảm bảo hình thức đoạn văn khoảng 200 chữ",
    );
    await user.click(screen.getByRole("button", { name: "Delete criterion 4" }));
    expect(screen.queryByRole("textbox", { name: "Criterion 4 description" })).not.toBeInTheDocument();
    expect(screen.getByText("Criteria total 1.5 / 2: does not match the question points yet")).toBeInTheDocument();
  });
});

describe("bang diem Dung/Sai va quy tac nhieu dap an", () => {
  it("them y thi bang chia deu lai; nap bang mau THPTQG cho cau 4 y", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="TRUE_FALSE" />);
    const table = await screen.findByRole("group", { name: "Score by number of correct statements" });
    expect(within(table).getAllByRole("textbox").map((input) => (input as HTMLInputElement).value)).toEqual(["0,5", "1"]);

    await user.click(within(table).getByRole("button", { name: "From exam template…" }));
    expect(await screen.findByRole("menuitem", { name: /THPTQG/ })).toHaveAttribute("aria-disabled", "true");
    await user.keyboard("{Escape}");

    await user.click(screen.getByRole("button", { name: "Add statement" }));
    await user.click(screen.getByRole("button", { name: "Add statement" }));
    expect(within(table).getAllByRole("textbox").map((input) => (input as HTMLInputElement).value)).toEqual([
      "0.25",
      "0.5",
      "0.75",
      "1",
    ]);

    await user.click(within(table).getByRole("button", { name: "From exam template…" }));
    await user.click(await screen.findByRole("menuitem", { name: /THPTQG/ }));
    expect(within(table).getAllByRole("textbox").map((input) => (input as HTMLInputElement).value)).toEqual([
      "0.1",
      "0.25",
      "0.5",
      "1",
    ]);
  });

  it("doi diem cua cau thi bang diem nhan theo ti le", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="edit" questionId="q-8004" />);
    const table = await screen.findByRole("group", { name: "Score by number of correct statements" });
    const points = screen.getByRole("textbox", { name: /^Points/ });
    await user.clear(points);
    await user.type(points, "2");
    expect(within(table).getAllByRole("textbox").map((input) => (input as HTMLInputElement).value)).toEqual([
      "0.2",
      "0.5",
      "1",
      "2",
    ]);
  });

  it("cau nhieu dap an chon cach cham, mac dinh dung het moi co diem", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="MULTIPLE_CHOICE" />);
    const group = await screen.findByRole("group", { name: "Scoring" });
    expect(within(group).getByRole("radio", { name: "All correct or nothing" })).toBeChecked();
    await user.click(within(group).getByRole("radio", { name: "Partial credit" }));
    expect(within(group).getByRole("radio", { name: "Partial credit" })).toBeChecked();
    expect(within(group).getByText(/picking any wrong option gives 0/)).toBeInTheDocument();
  });

  it("phan loai co o nang luc", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    expect(await screen.findByRole("combobox", { name: "Competency" })).toHaveTextContent("None");
  });
});

describe("chi tiet cau Tu luan va Dung/Sai", () => {
  it("hien nhan Cham tay, tieu chi cham va bai lam mau", async () => {
    navigation.pathname = "/questions/q-8001";
    renderEn(
      <QuestionDetailLayout questionId="q-8001">
        <QuestionDetailContent questionId="q-8001" />
      </QuestionDetailLayout>,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Question CH-008001" })).toBeInTheDocument();
    expect((await screen.findAllByText("Graded by hand")).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Rubric")).toBeInTheDocument();
    expect(screen.getByText("Triển khai vấn đề: lí lẽ rõ ràng, dẫn chứng phù hợp")).toBeInTheDocument();
    expect(screen.getByText("Sample answer")).toBeInTheDocument();
    expect(screen.getByText(/Maximum 2 points · about 200 words/)).toBeInTheDocument();
  });

  it("hien bang diem theo so y dung va nang luc", async () => {
    navigation.pathname = "/questions/q-8004";
    renderEn(<QuestionDetailContent questionId="q-8004" />);
    expect(await screen.findByText("Score by number of correct statements")).toBeInTheDocument();
    expect(screen.getByText("4 correct")).toBeInTheDocument();
    expect(screen.getByText("0.25")).toBeInTheDocument();
  });

  it("hien nang luc o thong tin", async () => {
    navigation.pathname = "/questions/q-8008";
    renderEn(<QuestionDetailContent questionId="q-8008" />);
    expect(await screen.findByText("Competency")).toBeInTheDocument();
    expect(await screen.findByText("Mathematical competence")).toBeInTheDocument();
  });
});
