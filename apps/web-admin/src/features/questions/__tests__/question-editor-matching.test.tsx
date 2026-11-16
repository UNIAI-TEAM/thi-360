import { screen, waitFor, within } from "@testing-library/react";
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

/** 3 mục trái, 4 lựa chọn phải (một lựa chọn dư), điểm cặp 0,33 / 0,33 / 0,34 = 1. `rightOf` đổi lựa chọn đúng của từng mục. */
function seedMatching(rightOf: [string, string, string] = ["r1", "r2", "r3"]) {
  return seedQuestion("MATCHING", (values) => {
    values.points = "1";
    values.body.left = [
      { id: "l1", content: "Việt Nam", rightId: rightOf[0], points: "0,33" },
      { id: "l2", content: "Nhật Bản", rightId: rightOf[1], points: "0,33" },
      { id: "l3", content: "Pháp", rightId: rightOf[2], points: "0,34" },
    ];
    values.body.right = [
      { id: "r1", content: "Hà Nội" },
      { id: "r2", content: "Tokyo" },
      { id: "r3", content: "Paris" },
      { id: "r4", content: "Dương" },
    ];
  });
}

const pairPoints = () =>
  screen.getAllByRole("textbox", { name: "Pair points" }).map((input) => (input as HTMLInputElement).value);

describe("trinh soan Ghep doi", () => {
  it("hai cot khac so muc: dem lua chon du, them cap chia deu lai diem va tong luon khop diem cau", async () => {
    const user = userEvent.setup();
    const id = await seedMatching();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    expect(await screen.findByText("Left column: 3 items; right column: 4 choices (1 spare).")).toBeInTheDocument();
    expect(screen.getByText("Points total: 1 / 1")).toBeInTheDocument();
    expect(pairPoints()).toEqual(["0,33", "0,33", "0,34"]);

    await user.click(screen.getByRole("button", { name: "Add pair" }));
    expect(pairPoints()).toEqual(["0,25", "0,25", "0,25", "0,25"]);
    expect(screen.getByText("Points total: 1 / 1")).toBeInTheDocument();
    // Cặp mới chưa chọn lựa chọn đúng nên lựa chọn thứ tư vẫn dư.
    expect(screen.getByText("Left column: 4 items; right column: 4 choices (1 spare).")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Delete pair 4" }));
    expect(pairPoints()).toEqual(["0,33", "0,33", "0,34"]);
  });

  it("xoa lua chon dang la dap an thi muc do phai chon lai, luu khong duoc cho toi khi chon", async () => {
    const user = userEvent.setup();
    const id = await seedMatching();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const section = (await screen.findByRole("heading", { name: "Matching pairs" })).closest("section");
    if (!section) throw new Error("no matching section");
    const choices = within(section).getAllByRole("combobox", { name: /^Correct choice/ });
    expect(choices[0]).toHaveTextContent("A. Hà Nội");

    await user.click(within(section).getByRole("button", { name: "Delete choice A" }));
    expect(within(section).getAllByRole("combobox", { name: /^Correct choice/ })[0]).toHaveTextContent("Select");
    expect(within(section).getByText("Left column: 3 items; right column: 3 choices (1 spare).")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByText("Choose the correct choice for this item.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();

    await user.click(within(section).getAllByRole("combobox", { name: /^Correct choice/ })[0]!);
    await user.click(await screen.findByRole("option", { name: "C. Dương" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({
      type: "MATCHING",
      left: [{ id: "l1" }, { id: "l2" }, { id: "l3" }],
      right: [{ id: "r2" }, { id: "r3" }, { id: "r4" }],
      pairs: [
        { leftId: "l1", rightId: "r4", points: 0.33 },
        { leftId: "l2", rightId: "r2", points: 0.33 },
        { leftId: "l3", rightId: "r3", points: 0.34 },
      ],
    });
  });

  it("lua chon dung mot lan khong gan cho hai muc, bat dung lai thi luu duoc", async () => {
    const user = userEvent.setup();
    const id = await seedMatching(["r1", "r1", "r3"]);
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await user.click(await screen.findByRole("button", { name: "Save question" }));
    expect(
      await screen.findByText(
        "This choice is already assigned to another item. Turn on “A choice can be used for several items” or pick another choice.",
      ),
    ).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();

    await user.click(screen.getByRole("checkbox", { name: "A choice can be used for several items" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({
      reuseRight: true,
      shuffleLeft: false,
      shuffleRight: true,
      pairs: [{ rightId: "r1" }, { rightId: "r1" }, { rightId: "r3" }],
    });
  });

  it("tong diem cac cap lech diem cau thi chan luu, Chia deu sua lai", async () => {
    const user = userEvent.setup();
    const id = await seedMatching();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const first = (await screen.findAllByRole("textbox", { name: "Pair points" }))[0]!;
    await user.clear(first);
    await user.type(first, "0,5");
    expect(screen.getByText("Points total: 1.17 / 1, does not match the question points yet")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByText("The pair points must add up to the question points.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Split evenly" }));
    expect(pairPoints()).toEqual(["0,33", "0,33", "0,34"]);
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalled());
  });

  it("cau moi co hai cap trong; luu nhap bao loi tung o", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="MATCHING" />);
    expect(await screen.findByText("Left column: 2 items; right column: 2 choices (0 spare).")).toBeInTheDocument();
    expect(pairPoints()).toEqual(["0,5", "0,5"]);
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findAllByText("Enter the left column item.")).toHaveLength(2);
    expect(screen.getAllByText("Enter the choice text or delete the choice.")).toHaveLength(2);
    expect(navigation.push).not.toHaveBeenCalled();
  });
});
