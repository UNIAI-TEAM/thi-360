import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { QuestionNav, type QuestionNavItem } from "./question-nav";

const SAMPLE_ITEMS: QuestionNavItem[] = [
  { itemId: "q-16", displayNo: 16, unitIndex: 0, completion: "complete", flagged: false, unsaved: false, isCurrent: false },
  { itemId: "q-17", displayNo: 17, unitIndex: 1, completion: "partial", flagged: true, unsaved: true, isCurrent: true },
  { itemId: "q-18", displayNo: 18, unitIndex: 2, completion: "blank", flagged: false, unsaved: false, isCurrent: false },
  { itemId: "q-19", displayNo: 19, unitIndex: 3, completion: "complete", flagged: true, unsaved: true, isCurrent: false },
];

describe("QuestionNav (AC-8, AC-16)", () => {
  it("hiển thị đánh số liên tục, phân biệt rõ đang đứng/đã làm/một phần/chưa làm, badge chờ lưu và cờ xem lại", async () => {
    const user = userEvent.setup();
    const onSelectUnit = vi.fn();

    renderWithProviders(
      <QuestionNav
        items={SAMPLE_ITEMS}
        currentUnitIndex={1}
        totalUnits={4}
        currentDisplayNumber={17}
        totalDisplayItems={4}
        currentFlagged={true}
        onToggleCurrentFlag={vi.fn()}
        onSelectUnit={onSelectUnit}
        onPrev={vi.fn()}
        onNext={vi.fn()}
        canPrev={true}
        canNext={true}
      />,
    );

    const btn16 = screen.getByRole("button", { name: /^16,/ });
    const btn17 = screen.getByRole("button", { name: /^17,/ });
    const btn18 = screen.getByRole("button", { name: /^18,/ });
    const btn19 = screen.getByRole("button", { name: /^19,/ });

    // Current marker
    expect(btn17).toHaveAttribute("aria-current", "step");
    expect(btn16).not.toHaveAttribute("aria-current");

    // 3 completion levels
    expect(btn16).toHaveAttribute("data-completion", "complete");
    expect(btn17).toHaveAttribute("data-completion", "partial");
    expect(btn18).toHaveAttribute("data-completion", "blank");

    // Unsaved queue badge separate from completion status (AC-16)
    expect(btn16).toHaveAttribute("data-unsaved", "false");
    expect(btn17).toHaveAttribute("data-unsaved", "true");
    expect(btn19).toHaveAttribute("data-unsaved", "true");
    expect(btn19.getAttribute("aria-label")).toContain("chưa lưu");
    expect(btn16.getAttribute("aria-label")).toContain("đã lưu");

    // Flagged marker
    expect(btn17).toHaveAttribute("data-flagged", "true");
    expect(btn18).toHaveAttribute("data-flagged", "false");

    // Legend shows distinct labels
    expect(screen.getByText("Đang xem")).toBeInTheDocument();
    expect(screen.getByText("Chưa lưu")).toBeInTheDocument();
    expect(screen.getByText("Trả lời một phần")).toBeInTheDocument();

    // Filter Unanswered keeps blank + partial (17, 18), excludes answered (16, 19)
    await user.click(screen.getByRole("button", { name: "Chưa làm" }));
    expect(screen.queryByRole("button", { name: /^16,/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^17,/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^18,/ })).toBeInTheDocument();

    // Filter Flagged keeps 17 and 19
    await user.click(screen.getByRole("button", { name: "Đánh dấu" }));
    expect(screen.queryByRole("button", { name: /^18,/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^17,/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^19,/ })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^19,/ }));
    expect(onSelectUnit).toHaveBeenCalledWith(3);
  });

  it("chỉ render một bản DOM QuestionNavGrid dù mở ngăn điều hướng mobile", async () => {
    const user = userEvent.setup();
    const onSelectUnit = vi.fn();

    renderWithProviders(
      <QuestionNav
        items={SAMPLE_ITEMS}
        currentUnitIndex={0}
        totalUnits={4}
        currentDisplayNumber={16}
        totalDisplayItems={4}
        onSelectUnit={onSelectUnit}
        onPrev={vi.fn()}
        onNext={vi.fn()}
        canPrev={false}
        canNext={true}
      />,
    );

    // Desktop sidebar holds single instance of question 16 button
    expect(screen.getAllByRole("button", { name: /^16,/ })).toHaveLength(1);

    // Opening mobile drawer unmounts desktop grid and mounts modal dialog grid (still single DOM)
    await user.click(screen.getByRole("button", { name: /Danh sách câu \(16\/4\)/ }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /^16,/ })).toHaveLength(1);

    // Clicking an item inside dialog closes drawer and invokes onSelectUnit
    await user.click(screen.getByRole("button", { name: /^18,/ }));
    expect(onSelectUnit).toHaveBeenCalledWith(2);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /^16,/ })).toHaveLength(1);
  });
});
