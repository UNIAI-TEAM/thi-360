import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { QuestionEditorView } from "../components/question-editor-view";
import { apiReferenceFromFixture } from "./api-fixtures";
import { renderEn, seedQuestion } from "./editor-test-kit";

vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

describe("Them nhan co o loc (UNI-2878)", () => {
  it("mo menu thi focus o loc; loc khong dau, Enter gan nhan, nhan da gan khong con trong danh sach", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("SINGLE_CHOICE", () => {});
    // Giao diện tiếng Anh, tên nhãn tiếng Việt có dấu để thử lọc không dấu.
    renderEn(<QuestionEditorView mode="edit" questionId={id} />, apiReferenceFromFixture("vi"));

    const trigger = await screen.findByRole("button", { name: "Add tag" });
    await user.click(trigger);
    const filter = await screen.findByRole("combobox", { name: "Filter tags" });
    expect(filter).toHaveFocus();
    expect(screen.getByRole("listbox", { name: "Tags" })).toBeInTheDocument();

    await user.keyboard("cau bay");
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Câu bẫy"]);
    await user.keyboard("{Enter}");

    expect(await screen.findByRole("button", { name: "Remove tag Câu bẫy" })).toBeInTheDocument();
    expect(trigger).toHaveFocus();
    await user.click(trigger);
    await screen.findByRole("combobox", { name: "Filter tags" });
    expect(screen.queryByRole("option", { name: "Câu bẫy" })).not.toBeInTheDocument();
    await user.keyboard("zzz");
    expect(screen.getByText("No matching items")).toBeInTheDocument();
  });
});
